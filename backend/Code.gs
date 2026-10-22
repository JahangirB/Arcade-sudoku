/**
 * Arcade Sudoku Battle - Google Apps Script backend.
 *
 * The host deploys this script as a web app and shares the web app URL plus a
 * secret code with the opponent. The script keeps each room in the script
 * cache, generates one puzzle per round so both players solve the same grid,
 * schedules a shared start time, validates boards and decides the winner.
 *
 * If the script is bound to a Google Sheet (Extensions > Apps Script), every
 * finished match is also logged to a "Matches" sheet.
 *
 * All requests are GET (or POST) calls to the web app URL with an `action`
 * parameter. Every response is JSON: { ok, serverTime, ... } or
 * { ok: false, error, serverTime }.
 */

var API_VERSION = 1;
// Cache entries live at most 6 hours, which is also the lifetime of a room.
var ROOM_TTL_SECONDS = 6 * 60 * 60;
var COUNTDOWN_MS = 5000;
// A player who stops syncing this long during a match forfeits it.
var INACTIVE_FORFEIT_MS = 45000;
// How often a sync that changes nothing else still records that the player is around.
// Most syncs are pure reads and skip both the lock and the cache write.
var LAST_SEEN_WRITE_MS = 10000;
// Empty cells per level. The generator removes cells only while the puzzle keeps a
// single solution, so the hardest levels may end up with a few cells fewer.
var DIFFICULTY_HOLES = { beginner: 30, easy: 38, medium: 46, hard: 50, expert: 54, master: 56 };
var ROOM_PREFIX = 'room_';
// Avatar and reaction keys the app can show; anything else is ignored. Must match the app.
var AVATARS = ['fox', 'cat', 'dog', 'panda', 'owl', 'frog', 'tiger', 'unicorn', 'robot', 'alien', 'lion', 'penguin'];
var EMOJIS = ['thumbsup', 'clap', 'fire', 'laugh', 'wow', 'sweat', 'think', 'cool', 'heart', 'hurry'];
var EMOJI_INTERVAL_MS = 1500;

function doGet(e) {
  return respond_(handleRequest_((e && e.parameter) || {}));
}

function doPost(e) {
  var params = {};
  if (e && e.postData && e.postData.contents) {
    try {
      params = JSON.parse(e.postData.contents) || {};
    } catch (err) {
      params = {};
    }
  }
  if (e && e.parameter) {
    for (var key in e.parameter) {
      if (!(key in params)) params[key] = e.parameter[key];
    }
  }
  return respond_(handleRequest_(params));
}

function respond_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function handleRequest_(p) {
  try {
    var action = String(p.action || '');
    if (action === 'ping') {
      return { ok: true, serverTime: Date.now(), version: API_VERSION };
    }
    var now = Date.now();
    // sync is by far the most frequent call; it takes the lock only when it has to write.
    var result = action === 'sync'
      ? sync_(p, now)
      : withLock_(function () { return dispatch_(action, p, now); });
    result.ok = true;
    result.serverTime = Date.now();
    return result;
  } catch (err) {
    return {
      ok: false,
      error: (err && err.message) || String(err),
      // Fatal errors mean the room is gone for this player; the app returns to the start screen.
      fatal: !!(err && err.fatal),
      serverTime: Date.now()
    };
  }
}

/** Runs fn while holding the script lock, which serialises every write to the cache. */
function withLock_(fn) {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(10000)) {
    throw new Error('Server is busy, please try again.');
  }
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

function dispatch_(action, p, now) {
  switch (action) {
    case 'create': return createRoom_(p, now);
    case 'join': return joinRoom_(p, now);
    case 'ready': return setReady_(p, now);
    case 'leave': return leave_(p, now);
    case 'emoji': return emoji_(p, now);
    default: throw new Error('Unknown action: ' + action);
  }
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

function createRoom_(p, now) {
  var code = normalizeCode_(p.code);

  var existing = loadRoom_(code);
  if (existing && existing.status !== 'closed') {
    throw new Error('That secret code is already in use. Pick another one.');
  }

  var room = {
    code: code,
    createdAt: now,
    updatedAt: now,
    // Bumped on every change a player can see; lets sync answer "unchanged".
    version: 0,
    // Set when a round starts: the level both players agreed on.
    difficulty: null,
    status: 'waiting',
    round: 0,
    puzzle: null,
    solution: null,
    startAt: null,
    winner: null,
    endReason: null,
    emojiSeq: 0,
    players: { host: newPlayer_(p.name, p.avatar), guest: null }
  };
  saveRoom_(room, now);
  var view = view_(room, 'host', now);
  view.playerId = room.players.host.id;
  return view;
}

function joinRoom_(p, now) {
  var room = requireRoom_(p.code);
  var role = findRole_(room, p.playerId);
  if (role) {
    // Reconnecting with a known player id.
    room.players[role].lastSeen = now;
    saveRoom_(room, now);
    var rejoin = view_(room, role, now);
    rejoin.playerId = room.players[role].id;
    return rejoin;
  }
  if (room.players.guest && !room.players.guest.left) {
    throw new Error('This battle already has two players.');
  }

  room.players.guest = newPlayer_(p.name, p.avatar);
  room.players.host.ready = false;
  room.players.host.difficulty = null;
  room.status = 'lobby';
  saveRoom_(room, now);
  var view = view_(room, 'guest', now);
  view.playerId = room.players.guest.id;
  return view;
}

function setReady_(p, now) {
  var room = requireRoom_(p.code);
  var role = requireRole_(room, p.playerId);
  room.players[role].lastSeen = now;
  if (room.status !== 'lobby' && room.status !== 'finished') {
    throw new Error('You can only get ready in the lobby or after a match.');
  }
  if (!room.players.guest) throw new Error('Waiting for an opponent to join.');
  if (room.players[otherRole_(role)].left) throw new Error('Your opponent has left the battle.');

  var me = room.players[role];
  if (String(p.ready) === 'false') {
    me.ready = false;
    me.difficulty = null;
  } else {
    var difficulty = String(p.difficulty || '').toLowerCase();
    if (!DIFFICULTY_HOLES[difficulty]) throw new Error('Choose a difficulty level.');
    me.ready = true;
    me.difficulty = difficulty;
  }
  // A round starts only when both players are ready with the same level.
  var host = room.players.host;
  var guest = room.players.guest;
  if (host.ready && guest.ready && host.difficulty === guest.difficulty) {
    startRound_(room, now);
  }
  saveRoom_(room, now);
  return view_(room, role, now);
}

/**
 * Uploads the player's board (if any) and returns the room. Reads happen without the lock;
 * only a new board, a timeout or a stale "last seen" time cause a locked write.
 * When the client already has the current version (`v`), a tiny "unchanged" reply is sent.
 */
function sync_(p, now) {
  var room = requireRoom_(p.code);
  var role = requireRole_(room, p.playerId);
  if (!needsWrite_(room, role, p, now)) return syncReply_(room, role, p, now);

  return withLock_(function () {
    // Re-read under the lock: another request may have changed the room meanwhile.
    var fresh = requireRoom_(p.code);
    var freshRole = requireRole_(fresh, p.playerId);
    var changed = applySync_(fresh, freshRole, p, now);
    saveRoom_(fresh, now, !changed);
    return syncReply_(fresh, freshRole, p, now);
  });
}

function needsWrite_(room, role, p, now) {
  var player = room.players[role];
  return now - (player.lastSeen || 0) >= LAST_SEEN_WRITE_MS ||
    newBoard_(room, player, p, now) !== null ||
    opponentTimedOut_(room, role, now);
}

/** The uploaded board if it should be stored, otherwise null. */
function newBoard_(room, player, p, now) {
  if (!p.board || room.status !== 'started' || now < room.startAt || player.finishedAt) return null;
  var board = String(p.board);
  return board === player.board ? null : board;
}

function opponentTimedOut_(room, role, now) {
  var opponent = room.players[otherRole_(role)];
  return room.status === 'started' && now >= room.startAt &&
    now - Math.max(opponent.lastSeen, room.startAt) > INACTIVE_FORFEIT_MS;
}

/** Applies a sync to the room; returns true if the other player will see a difference. */
function applySync_(room, role, p, now) {
  var player = room.players[role];
  player.lastSeen = now;
  var changed = false;
  var board = newBoard_(room, player, p, now);
  if (board !== null) {
    validateBoard_(board, room.puzzle);
    player.board = board;
    player.progress = progressOf_(board, room.puzzle, room.solution);
    changed = true;
    if (board === room.solution) {
      player.finishedAt = now;
      endMatch_(room, role, 'solved');
    }
  }
  if (opponentTimedOut_(room, role, now)) {
    endMatch_(room, role, 'timeout');
    changed = true;
  }
  return changed;
}

function syncReply_(room, role, p, now) {
  if (p.v !== undefined && p.v !== null && String(p.v) === String(room.version)) {
    var opponent = room.players[otherRole_(role)];
    return {
      unchanged: true,
      version: room.version,
      opponentIdleMs: opponent ? Math.max(0, now - (opponent.lastSeen || 0)) : null
    };
  }
  return view_(room, role, now);
}

/** Shows a predefined reaction to the opponent on their next sync. */
function emoji_(p, now) {
  var room = requireRoom_(p.code);
  var role = requireRole_(room, p.playerId);
  var emoji = String(p.emoji || '');
  if (EMOJIS.indexOf(emoji) < 0) throw new Error('Unknown reaction.');
  if (!room.players[otherRole_(role)]) throw new Error('Nobody to send it to yet.');
  var player = room.players[role];
  if (now - (player.emojiAt || 0) < EMOJI_INTERVAL_MS) throw new Error('Slow down a little.');
  room.emojiSeq = (room.emojiSeq || 0) + 1;
  player.emoji = { key: emoji, seq: room.emojiSeq };
  player.emojiAt = now;
  player.lastSeen = now;
  saveRoom_(room, now);
  return view_(room, role, now);
}

function leave_(p, now) {
  var room = loadRoom_(normalizeCode_(p.code));
  if (!room) return { left: true };
  var role = findRole_(room, p.playerId);
  if (!role) return { left: true };
  var other = otherRole_(role);

  if (room.status === 'started') {
    endMatch_(room, other, 'forfeit');
  }

  if (role === 'host') {
    // Without a host the room is gone; the guest sees "closed" on next sync.
    room.status = 'closed';
    room.players.host.left = true;
  } else if (room.status === 'finished') {
    room.players.guest.left = true;
  } else {
    room.players.guest = null;
    room.status = 'waiting';
    room.players.host.ready = false;
    room.players.host.difficulty = null;
  }
  saveRoom_(room, now);
  return { left: true };
}

// ---------------------------------------------------------------------------
// Room helpers
// ---------------------------------------------------------------------------

function newPlayer_(name, avatar) {
  return {
    id: Utilities.getUuid(),
    name: cleanName_(name),
    avatar: AVATARS.indexOf(avatar) >= 0 ? avatar : null,
    emoji: null,
    emojiAt: 0,
    ready: false,
    difficulty: null,
    board: null,
    progress: 0,
    finishedAt: null,
    left: false,
    lastSeen: Date.now()
  };
}

function endMatch_(room, winnerRole, reason) {
  room.winner = winnerRole;
  room.endReason = reason;
  room.status = 'finished';
  resetReady_(room);
  logMatch_(room);
}

function startRound_(room, now) {
  room.difficulty = room.players.host.difficulty;
  var generated = generatePuzzle_(DIFFICULTY_HOLES[room.difficulty]);
  room.puzzle = generated.puzzle;
  room.solution = generated.solution;
  room.round += 1;
  room.startAt = now + COUNTDOWN_MS;
  room.status = 'started';
  room.winner = null;
  room.endReason = null;
  ['host', 'guest'].forEach(function (r) {
    var pl = room.players[r];
    pl.ready = false;
    pl.difficulty = null;
    pl.board = room.puzzle;
    pl.progress = 0;
    pl.finishedAt = null;
  });
}

function resetReady_(room) {
  ['host', 'guest'].forEach(function (r) {
    if (room.players[r]) {
      room.players[r].ready = false;
      room.players[r].difficulty = null;
    }
  });
}

function view_(room, role, now) {
  var status = room.status;
  if (status === 'started') status = now < room.startAt ? 'countdown' : 'playing';
  var other = otherRole_(role);
  var me = room.players[role];
  var opponent = room.players[other];
  var showPuzzle = status === 'countdown' || status === 'playing' || (status === 'finished' && room.round > 0);
  return {
    code: room.code,
    version: room.version || 0,
    role: role,
    status: status,
    difficulty: room.difficulty,
    round: room.round,
    startAt: room.startAt,
    puzzle: showPuzzle ? room.puzzle : null,
    // The solution is only revealed once the match is over.
    solution: status === 'finished' ? room.solution : null,
    winner: room.winner ? (room.winner === role ? 'me' : 'opponent') : null,
    endReason: room.endReason,
    me: playerView_(me, room, status === 'finished'),
    opponent: opponent ? playerView_(opponent, room, true) : null
  };
}

function playerView_(player, room, includeProgress) {
  return {
    name: player.name,
    avatar: player.avatar || null,
    // Latest reaction; the app shows it when seq goes up.
    emoji: player.emoji || null,
    ready: player.ready,
    // The level this player is ready for (null until they choose one).
    difficulty: player.difficulty,
    // A player never gets their own server-side progress during a match,
    // otherwise it would reveal whether a guess was correct.
    progress: includeProgress ? player.progress : null,
    finishMs: player.finishedAt ? player.finishedAt - room.startAt : null,
    left: !!player.left,
    idleMs: Math.max(0, Date.now() - (player.lastSeen || 0))
  };
}

function findRole_(room, playerId) {
  if (!playerId) return null;
  if (room.players.host && room.players.host.id === playerId) return 'host';
  if (room.players.guest && room.players.guest.id === playerId) return 'guest';
  return null;
}

function requireRole_(room, playerId) {
  var role = findRole_(room, playerId);
  if (!role) throw fatal_('You are not part of this battle.');
  return role;
}

function fatal_(message) {
  var err = new Error(message);
  err.fatal = true;
  return err;
}

function otherRole_(role) {
  return role === 'host' ? 'guest' : 'host';
}

function requireRoom_(code) {
  var room = loadRoom_(normalizeCode_(code));
  if (!room) throw fatal_('No battle found for that secret code.');
  if (room.status === 'closed') throw fatal_('The host closed this battle.');
  return room;
}

function loadRoom_(code) {
  var raw = CacheService.getScriptCache().get(ROOM_PREFIX + code);
  return raw ? JSON.parse(raw) : null;
}

/** Stores the room. A quiet save (only "last seen" times changed) keeps the version. */
function saveRoom_(room, now, quiet) {
  room.updatedAt = now;
  if (!quiet) room.version = (room.version || 0) + 1;
  CacheService.getScriptCache().put(ROOM_PREFIX + room.code, JSON.stringify(room), ROOM_TTL_SECONDS);
}

function normalizeCode_(code) {
  var clean = String(code || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (clean.length < 4 || clean.length > 12) {
    throw new Error('The secret code must be 4 to 12 letters or digits.');
  }
  return clean;
}

function cleanName_(name) {
  var clean = String(name || '').replace(/\s+/g, ' ').trim().slice(0, 16);
  return clean || 'Player';
}

function validateBoard_(board, puzzle) {
  if (!/^[0-9]{81}$/.test(board)) throw new Error('Invalid board.');
  for (var i = 0; i < 81; i++) {
    if (puzzle.charAt(i) !== '0' && board.charAt(i) !== puzzle.charAt(i)) {
      throw new Error('Invalid board: a given number was changed.');
    }
  }
}

/** Percentage of the empty cells that hold the correct digit. */
function progressOf_(board, puzzle, solution) {
  var empty = 0;
  var correct = 0;
  for (var i = 0; i < 81; i++) {
    if (puzzle.charAt(i) !== '0') continue;
    empty++;
    if (board.charAt(i) === solution.charAt(i)) correct++;
  }
  return empty === 0 ? 100 : Math.floor((correct * 100) / empty);
}

function logMatch_(room) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    if (!ss) return;
    var sheet = ss.getSheetByName('Matches') || ss.insertSheet('Matches');
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(['Finished', 'Code', 'Round', 'Difficulty', 'Host', 'Guest', 'Winner', 'Reason', 'Winner time (s)', 'Host %', 'Guest %']);
    }
    var winner = room.players[room.winner];
    var finishMs = winner && winner.finishedAt ? winner.finishedAt - room.startAt : null;
    sheet.appendRow([
      new Date(),
      room.code,
      room.round,
      room.difficulty,
      room.players.host.name,
      room.players.guest ? room.players.guest.name : '',
      winner ? winner.name : '',
      room.endReason,
      finishMs === null ? '' : Math.round(finishMs / 1000),
      room.players.host.progress,
      room.players.guest ? room.players.guest.progress : ''
    ]);
  } catch (err) {
    // Logging is optional; a standalone script has no spreadsheet.
  }
}

// ---------------------------------------------------------------------------
// Sudoku generation
// ---------------------------------------------------------------------------

/**
 * Returns { puzzle, solution } as 81-character strings ('0' = empty cell).
 * The puzzle always has exactly one solution.
 */
function generatePuzzle_(holes) {
  var solution = new Array(81).fill(0);
  fillGrid_(solution);
  var puzzle = solution.slice();
  var cells = shuffle_(range_(81));
  var removed = 0;
  for (var i = 0; i < cells.length && removed < holes; i++) {
    var idx = cells[i];
    var backup = puzzle[idx];
    puzzle[idx] = 0;
    if (countSolutions_(puzzle.slice(), 2) === 1) {
      removed++;
    } else {
      puzzle[idx] = backup;
    }
  }
  return { puzzle: puzzle.join(''), solution: solution.join('') };
}

function fillGrid_(grid) {
  var idx = grid.indexOf(0);
  if (idx === -1) return true;
  var digits = shuffle_([1, 2, 3, 4, 5, 6, 7, 8, 9]);
  for (var i = 0; i < 9; i++) {
    if (canPlace_(grid, idx, digits[i])) {
      grid[idx] = digits[i];
      if (fillGrid_(grid)) return true;
      grid[idx] = 0;
    }
  }
  return false;
}

function canPlace_(grid, idx, digit) {
  var row = Math.floor(idx / 9);
  var col = idx % 9;
  var boxRow = row - (row % 3);
  var boxCol = col - (col % 3);
  for (var i = 0; i < 9; i++) {
    if (grid[row * 9 + i] === digit) return false;
    if (grid[i * 9 + col] === digit) return false;
    if (grid[(boxRow + Math.floor(i / 3)) * 9 + boxCol + (i % 3)] === digit) return false;
  }
  return true;
}

/** Counts solutions up to `limit`, always branching on the most constrained cell. */
function countSolutions_(grid, limit) {
  var best = -1;
  var bestCandidates = null;
  for (var idx = 0; idx < 81; idx++) {
    if (grid[idx] !== 0) continue;
    var candidates = [];
    for (var d = 1; d <= 9; d++) {
      if (canPlace_(grid, idx, d)) candidates.push(d);
    }
    if (candidates.length === 0) return 0;
    if (best === -1 || candidates.length < bestCandidates.length) {
      best = idx;
      bestCandidates = candidates;
      if (candidates.length === 1) break;
    }
  }
  if (best === -1) return 1;
  var count = 0;
  for (var i = 0; i < bestCandidates.length && count < limit; i++) {
    grid[best] = bestCandidates[i];
    count += countSolutions_(grid, limit - count);
  }
  grid[best] = 0;
  return count;
}

function range_(n) {
  var out = [];
  for (var i = 0; i < n; i++) out.push(i);
  return out;
}

function shuffle_(arr) {
  for (var i = arr.length - 1; i > 0; i--) {
    var j = Math.floor(Math.random() * (i + 1));
    var tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
  }
  return arr;
}
