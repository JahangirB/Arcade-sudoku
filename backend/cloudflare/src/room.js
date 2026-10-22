// Battle rules for one room, as plain functions over a JSON-serialisable object so the
// Durable Object can store it and tests can run without Cloudflare. The rules match
// ../Code.gs; the difference is that "away" means a closed connection instead of
// missing polls.

import { generatePuzzle } from './sudoku.js';
import * as connect4 from './connect4.js';
import * as dots from './dots.js';
import * as memory from './memory.js';
import * as words from './words.js';

// Empty cells per level; must match Difficulty in the Android app.
export const DIFFICULTY_HOLES = { beginner: 30, easy: 38, medium: 46, hard: 50, expert: 54, master: 56 };
export const COUNTDOWN_MS = 5000;
// Avatar and reaction keys the app can show; anything else is ignored. Must match the app.
export const AVATARS = ['fox', 'cat', 'dog', 'panda', 'owl', 'frog', 'tiger', 'unicorn', 'robot', 'alien', 'lion', 'penguin'];
export const EMOJIS = ['thumbsup', 'clap', 'fire', 'laugh', 'wow', 'sweat', 'think', 'cool', 'heart', 'hurry'];
// At most one reaction per player this often.
export const EMOJI_INTERVAL_MS = 1500;
// A player disconnected this long during a match loses it.
export const INACTIVE_FORFEIT_MS = 45_000;
// A player disconnected this long outside a match is removed (a host closes the room).
export const LOBBY_ABANDON_MS = 5 * 60_000;
// A room with nobody connected is deleted after this long (1 minute once closed).
export const ROOM_IDLE_MS = 6 * 60 * 60_000;
// Turn games can go on across time zones, a move now and then: keep their rooms a week.
export const TURN_ROOM_IDLE_MS = 7 * 24 * 60 * 60_000;
const CLOSED_ROOM_MS = 60_000;

export const GAMES = ['sudoku', 'ships', 'connect4', 'dots', 'memory', 'words'];
// What apps from before the version check could play; they don't say which games they know.
const OLD_APP_GAMES = ['sudoku', 'ships'];

export class GameError extends Error {
  /**
   * `fatal`: the room is gone for this player; the app returns to the start screen.
   * `reason`: a stable code the app can act on (not_found, closed, not_member, full, code_taken).
   */
  constructor(message, fatal = false, reason = null) {
    super(message);
    this.fatal = fatal;
    this.reason = reason;
  }
}

export function normalizeCode(code) {
  const clean = String(code || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (clean.length < 4 || clean.length > 12) {
    throw new GameError('The secret code must be 4 to 12 letters or digits.', true, 'bad_code');
  }
  return clean;
}

export function cleanName(name) {
  return String(name || '').replace(/\s+/g, ' ').trim().slice(0, 16) || 'Player';
}

export function cleanAvatar(avatar) {
  return AVATARS.includes(avatar) ? avatar : null;
}

/** The phone's permanent id (used for invitations); anything odd is dropped. */
export function cleanDeviceId(deviceId) {
  return /^[A-Za-z0-9-]{8,64}$/.test(String(deviceId || '')) ? String(deviceId) : null;
}

function newPlayer(name, avatar, now, uuid, deviceId = null) {
  return {
    id: uuid(), name: cleanName(name), avatar: cleanAvatar(avatar), deviceId: cleanDeviceId(deviceId), emoji: null, emojiAt: 0, ready: false, difficulty: null, board: null,
    progress: 0, finishedAt: null, left: false, connected: false, awaySince: now,
  };
}

export function createRoom(code, hostName, now, uuid, avatar = null, deviceId = null, game = 'sudoku') {
  return {
    code, game: GAMES.includes(game) ? game : 'sudoku', createdAt: now, version: 0, emojiSeq: 0, difficulty: null, status: 'waiting', round: 0,
    puzzle: null, solution: null, startAt: null, winner: null, endReason: null,
    players: { host: newPlayer(hostName, avatar, now, uuid, deviceId), guest: null },
  };
}

export function otherRole(role) {
  return role === 'host' ? 'guest' : 'host';
}

export function findRole(room, playerId) {
  if (!playerId) return null;
  for (const role of ['host', 'guest']) {
    if (room.players[role] && room.players[role].id === playerId) return role;
  }
  return null;
}

/**
 * Joins as guest, or reconnects when `playerId` belongs to the room. Returns the role.
 * `games` lists the games the joining app knows (null for apps that predate the list).
 */
export function joinRoom(room, name, playerId, now, uuid, avatar = null, deviceId = null, games = null) {
  if (room.status === 'closed') throw new GameError('The host closed this battle.', true, 'closed');
  if (!(games || OLD_APP_GAMES).includes(room.game)) {
    throw new GameError('This game needs the latest Game Suite. Update the app to join.', true, 'update_needed');
  }
  const known = findRole(room, playerId);
  if (known) {
    const id = cleanDeviceId(deviceId);
    if (id) room.players[known].deviceId = id;
    return known;
  }
  if (playerId) throw new GameError('You are not part of this battle.', true, 'not_member');
  if (room.players.guest && !room.players.guest.left) {
    throw new GameError('This battle already has two players.', true, 'full');
  }
  room.players.guest = newPlayer(name, avatar, now, uuid, deviceId);
  room.players.host.ready = false;
  room.players.host.difficulty = null;
  room.status = 'lobby';
  bump(room);
  return 'guest';
}

/** Ready for a level (or not ready). The round starts when both pick the same level. */
export function setReady(room, role, ready, difficulty, now, generate = generatePuzzle) {
  if (room.status !== 'lobby' && room.status !== 'finished') {
    throw new GameError('You can only get ready in the lobby or after a match.');
  }
  if (!room.players.guest) throw new GameError('Waiting for an opponent to join.');
  if (room.players[otherRole(role)].left) throw new GameError('Your opponent has left the battle.');

  const me = room.players[role];
  if (ready === false) {
    me.ready = false;
    me.difficulty = null;
  } else {
    const level = String(difficulty || '').toLowerCase();
    if (!DIFFICULTY_HOLES[level]) throw new GameError('Choose a difficulty level.');
    me.ready = true;
    me.difficulty = level;
  }
  const { host, guest } = room.players;
  if (host.ready && guest.ready && host.difficulty === guest.difficulty) {
    startRound(room, now, generate);
  }
  bump(room);
}

function startRound(room, now, generate) {
  room.difficulty = room.players.host.difficulty;
  const { puzzle, solution } = generate(DIFFICULTY_HOLES[room.difficulty]);
  Object.assign(room, {
    puzzle, solution, round: room.round + 1, startAt: now + COUNTDOWN_MS,
    status: 'started', winner: null, endReason: null,
  });
  for (const role of ['host', 'guest']) {
    Object.assign(room.players[role], {
      ready: false, difficulty: null, board: room.puzzle, progress: 0, finishedAt: null,
    });
  }
}

// ---------------------------------------------------------------------------------------
// Ship Battle: each player hides a fleet on a 10x10 sea; players take turns firing at the
// other's sea. A hit lets you fire again; the first to sink the whole fleet wins.

export const SEA = 10;
export const FLEET = [5, 4, 3, 3, 2];
export const FLEET_CELLS = FLEET.reduce((a, b) => a + b, 0);

/** The cells (y * 10 + x) a ship covers. */
export function shipCells(ship) {
  const cells = [];
  for (let i = 0; i < ship.len; i++) {
    cells.push(ship.horizontal ? ship.y * SEA + ship.x + i : (ship.y + i) * SEA + ship.x);
  }
  return cells;
}

/** Checks a fleet: the five classic ships, inside the sea, not overlapping. */
export function validateFleet(fleet) {
  if (!Array.isArray(fleet) || fleet.length !== FLEET.length) throw new GameError('Place all five ships.');
  const ships = fleet.map((s) => ({
    x: Number(s?.x), y: Number(s?.y), len: Number(s?.len), horizontal: !!s?.horizontal,
  }));
  const lengths = ships.map((s) => s.len).sort((a, b) => b - a);
  if (lengths.join() !== FLEET.join()) throw new GameError('The fleet must be ships of 5, 4, 3, 3 and 2.');
  const taken = new Set();
  for (const ship of ships) {
    const endX = ship.horizontal ? ship.x + ship.len - 1 : ship.x;
    const endY = ship.horizontal ? ship.y : ship.y + ship.len - 1;
    if (![ship.x, ship.y].every(Number.isInteger) || ship.x < 0 || ship.y < 0 || endX >= SEA || endY >= SEA) {
      throw new GameError('A ship is outside the sea.');
    }
    for (const cell of shipCells(ship)) {
      if (taken.has(cell)) throw new GameError('Ships cannot overlap.');
      taken.add(cell);
    }
  }
  return ships;
}

/** Ready with a fleet (or not ready). The battle starts when both fleets are in. */
export function setShipsReady(room, role, ready, fleet, now, random = Math.random) {
  if (room.status !== 'lobby' && room.status !== 'finished') {
    throw new GameError('You can only get ready in the lobby or after a match.');
  }
  if (!room.players.guest) throw new GameError('Waiting for an opponent to join.');
  if (room.players[otherRole(role)].left) throw new GameError('Your opponent has left the battle.');
  const me = room.players[role];
  if (ready === false) {
    me.ready = false;
    me.pendingFleet = null;
  } else {
    me.pendingFleet = validateFleet(fleet);
    me.ready = true;
  }
  const { host, guest } = room.players;
  if (host.ready && guest.ready) {
    Object.assign(room, {
      round: room.round + 1, status: 'started', startAt: now, winner: null, endReason: null,
      turn: random() < 0.5 ? 'host' : 'guest', lastShot: null,
    });
    for (const p of [host, guest]) {
      Object.assign(p, { fleet: p.pendingFleet, pendingFleet: null, shots: [], progress: 0, ready: false, finishedAt: null });
    }
  }
  bump(room);
}

/** Fires at the opponent's sea. Returns { hit, sunk }. */
export function fire(room, role, x, y, now) {
  if (room.game !== 'ships' || room.status !== 'started') throw new GameError('The battle is not running.');
  if (room.turn !== role) throw new GameError("It's not your turn.");
  x = Number(x);
  y = Number(y);
  if (![x, y].every(Number.isInteger) || x < 0 || y < 0 || x >= SEA || y >= SEA) throw new GameError('Fire inside the sea.');
  const me = room.players[role];
  const enemy = room.players[otherRole(role)];
  if (me.shots.some((s) => s.x === x && s.y === y)) throw new GameError('You already fired there.');

  const cell = y * SEA + x;
  const ship = enemy.fleet.find((s) => shipCells(s).includes(cell));
  const hit = !!ship;
  me.shots.push({ x, y, hit });
  const sunk = hit && isSunk(ship, me.shots);
  const hits = me.shots.filter((s) => s.hit).length;
  me.progress = Math.floor((hits * 100) / FLEET_CELLS);
  room.lastShot = { by: role, x, y, hit, sunk };
  if (hits === FLEET_CELLS) {
    me.finishedAt = now;
    endMatch(room, role, 'sunk');
  } else if (!hit) {
    room.turn = otherRole(role);
  }
  bump(room);
  return { hit, sunk };
}

function isSunk(ship, shots) {
  return shipCells(ship).every((c) => shots.some((s) => s.hit && s.y * SEA + s.x === c));
}

function shipsView(room, role, status) {
  const me = room.players[role];
  const opponent = room.players[otherRole(role)];
  const last = room.lastShot;
  return {
    turn: status === 'playing' ? (room.turn === role ? 'me' : 'opponent') : null,
    // Your own fleet: the one in play, or the one you are ready with.
    myFleet: me.fleet || me.pendingFleet || [],
    myShots: me.shots || [],
    theirShots: opponent?.shots || [],
    // Enemy ships are revealed one by one as they sink, and all at the end.
    sunkEnemy: opponent?.fleet ? opponent.fleet.filter((s) => isSunk(s, me.shots || [])) : [],
    enemyFleet: status === 'finished' && opponent?.fleet ? opponent.fleet : null,
    lastShot: last ? { ...last, by: last.by === role ? 'me' : 'opponent' } : null,
  };
}

// ---------------------------------------------------------------------------------------
// Turn games (Connect Four, Dots & Boxes, Memory Pairs): both players get ready, then take
// turns making moves. Each game's own rules live in its module (start, play, view); the
// player who starts alternates from round to round. A player may close the app between
// moves: turn games are never forfeited for being away, the room just waits.

const TURN_RULES = { connect4, dots, memory };
export const TURN_GAMES = Object.keys(TURN_RULES);

/** Ready (or not ready) for the next round of a turn game; it starts when both are ready. */
export function setTurnReady(room, role, ready, now, random = Math.random) {
  if (room.status !== 'lobby' && room.status !== 'finished') {
    throw new GameError('You can only get ready in the lobby or after a match.');
  }
  if (!room.players.guest) throw new GameError('Waiting for an opponent to join.');
  if (room.players[otherRole(role)].left) throw new GameError('Your opponent has left the battle.');
  room.players[role].ready = ready !== false;
  const { host, guest } = room.players;
  if (host.ready && guest.ready) {
    const first = room.firstTurn ? otherRole(room.firstTurn) : (random() < 0.5 ? 'host' : 'guest');
    Object.assign(room, {
      round: room.round + 1, status: 'started', startAt: now, winner: null, endReason: null,
      firstTurn: first, turn: first, turnState: TURN_RULES[room.game].start(random),
    });
    for (const p of [host, guest]) Object.assign(p, { ready: false, finishedAt: null });
  }
  bump(room);
}

/** Plays `move` for `role`: { col } in Connect Four, { line } in Dots & Boxes, { card } in Memory Pairs. */
export function makeMove(room, role, move, now) {
  if (!TURN_GAMES.includes(room.game) || room.status !== 'started') throw new GameError('The game is not running.');
  if (room.turn !== role) throw new GameError("It's not your turn.");
  let result;
  try {
    result = TURN_RULES[room.game].play(room.turnState, role[0], move);
  } catch (err) {
    throw new GameError(err.message);
  }
  if (result.over) {
    const winner = result.winner === 'h' ? 'host' : result.winner === 'g' ? 'guest' : null;
    if (winner) room.players[winner].finishedAt = now;
    endMatch(room, winner, winner ? result.reason : 'draw');
  } else if (!result.again) {
    room.turn = otherRole(role);
  }
  bump(room);
}

function turnView(room, role, status) {
  return {
    turn: status === 'playing' ? (room.turn === role ? 'me' : 'opponent') : null,
    ...(room.turnState ? TURN_RULES[room.game].view(room.turnState, role[0]) : {}),
  };
}

// ---------------------------------------------------------------------------------------
// Word Race: a race like Sudoku. Both get ready, a countdown starts, and each guesses the same
// secret word on their own. Fewest tries wins; a tie goes to the faster player. The match ends
// as soon as the result is certain.

/** Ready (or not ready) for the next Word Race round; it starts when both are ready. */
export function setWordsReady(room, role, ready, now, random = Math.random) {
  if (room.status !== 'lobby' && room.status !== 'finished') {
    throw new GameError('You can only get ready in the lobby or after a match.');
  }
  if (!room.players.guest) throw new GameError('Waiting for an opponent to join.');
  if (room.players[otherRole(role)].left) throw new GameError('Your opponent has left the battle.');
  room.players[role].ready = ready !== false;
  const { host, guest } = room.players;
  if (host.ready && guest.ready) {
    Object.assign(room, {
      round: room.round + 1, status: 'started', startAt: now + COUNTDOWN_MS, winner: null, endReason: null,
      answer: words.randomAnswer(random),
    });
    for (const p of [host, guest]) Object.assign(p, { ready: false, finishedAt: null, guesses: [], solved: false });
  }
  bump(room);
}

/** One guess at the secret word. */
export function guessWord(room, role, word, now) {
  if (room.game !== 'words' || room.status !== 'started') throw new GameError('The game is not running.');
  if (now < room.startAt) throw new GameError('Wait for the countdown.');
  const me = room.players[role];
  if (me.finishedAt) throw new GameError('You are done with this word.');
  word = String(word || '').toLowerCase();
  if (!/^[a-z]{5}$/.test(word)) throw new GameError('Guess a five-letter word.');
  if (!words.isWord(word)) throw new GameError('Not in the word list.');
  const colours = words.score(word, room.answer);
  me.guesses.push({ word, score: colours });
  if (colours === 'ggggg') {
    me.solved = true;
    me.finishedAt = now;
  } else if (me.guesses.length >= words.TRIES) {
    me.finishedAt = now;
  }
  decideWords(room);
  bump(room);
}

/** Ends the round once nobody can change the result any more. */
function decideWords(room) {
  const { host, guest } = room.players;
  const better = (a, b) => {
    if (a.solved !== b.solved) return a.solved;
    if (a.guesses.length !== b.guesses.length) return a.guesses.length < b.guesses.length;
    return a.finishedAt < b.finishedAt;
  };
  if (host.finishedAt && guest.finishedAt) {
    if (!host.solved && !guest.solved) return endMatch(room, null, 'draw');
    const hostWins = better(host, guest);
    return endMatch(room, hostWins ? 'host' : 'guest', 'solved');
  }
  // One solved: the other can only still win by solving in fewer tries.
  for (const [solver, other] of [['host', 'guest'], ['guest', 'host']]) {
    const s = room.players[solver];
    const o = room.players[other];
    if (s.solved && !o.finishedAt && o.guesses.length + 1 >= s.guesses.length) return endMatch(room, solver, 'solved');
  }
}

function wordsView(room, role, status) {
  const me = room.players[role];
  const opponent = room.players[otherRole(role)];
  const over = status === 'finished';
  const showWords = status === 'playing' || over;
  return {
    tries: words.TRIES,
    mine: showWords ? (me.guesses || []) : [],
    // The opponent's colours during the round; their words too once it is over.
    theirs: showWords && opponent ? (opponent.guesses || []).map((g) => (over ? g : { score: g.score })) : [],
    meDone: !!me.finishedAt,
    theyDone: !!opponent?.finishedAt,
    answer: over && room.answer ? room.answer : null,
  };
}

/** Stores a player's board during a match. Returns true if anything changed. */
export function submitBoard(room, role, board, now) {
  const player = room.players[role];
  if (room.status !== 'started' || now < room.startAt || player.finishedAt) return false;
  board = String(board || '');
  if (board === player.board) return false;
  if (!/^[0-9]{81}$/.test(board)) throw new GameError('Invalid board.');
  for (let i = 0; i < 81; i++) {
    if (room.puzzle[i] !== '0' && board[i] !== room.puzzle[i]) {
      throw new GameError('Invalid board: a given number was changed.');
    }
  }
  player.board = board;
  player.progress = progressOf(board, room.puzzle, room.solution);
  if (board === room.solution) {
    player.finishedAt = now;
    endMatch(room, role, 'solved');
  }
  bump(room);
  return true;
}

/** Percentage of the empty cells that hold the correct digit. */
export function progressOf(board, puzzle, solution) {
  let empty = 0;
  let correct = 0;
  for (let i = 0; i < 81; i++) {
    if (puzzle[i] !== '0') continue;
    empty++;
    if (board[i] === solution[i]) correct++;
  }
  return empty === 0 ? 100 : Math.floor((correct * 100) / empty);
}

function endMatch(room, winnerRole, reason) {
  room.winner = winnerRole;
  room.endReason = reason;
  room.status = 'finished';
  for (const role of ['host', 'guest']) {
    if (room.players[role]) {
      room.players[role].ready = false;
      room.players[role].difficulty = null;
    }
  }
}

export function leave(room, role) {
  const other = otherRole(role);
  if (room.status === 'started') endMatch(room, other, 'forfeit');
  if (role === 'host') {
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
  bump(room);
}

/** Shows a predefined reaction to the opponent. Throws when it is not allowed. */
export function sendEmoji(room, role, emoji, now) {
  if (!EMOJIS.includes(emoji)) throw new GameError('Unknown reaction.');
  if (!room.players[otherRole(role)]) throw new GameError('Nobody to send it to yet.');
  const player = room.players[role];
  if (now - (player.emojiAt || 0) < EMOJI_INTERVAL_MS) throw new GameError('Slow down a little.');
  room.emojiSeq = (room.emojiSeq || 0) + 1;
  player.emoji = { key: emoji, seq: room.emojiSeq };
  player.emojiAt = now;
  bump(room);
}

export function setConnected(room, role, connected, now) {
  const player = room.players[role];
  if (!player) return;
  player.connected = connected;
  player.awaySince = connected ? null : now;
  bump(room);
}

/**
 * Applies time-based rules: forfeits for players away during a match and removal of
 * players away from a lobby. Returns 'changed', 'expired' (delete the room) or null.
 */
export function checkTimers(room, now) {
  const roles = ['host', 'guest'].filter((r) => room.players[r]);
  const away = (r) => room.players[r].connected ? 0 : now - room.players[r].awaySince;

  if (roles.every((r) => !room.players[r].connected)) {
    const awayFor = Math.min(...roles.map(away));
    if (awayFor >= idleLimit(room)) return 'expired';
  }
  if (room.status === 'closed' || TURN_GAMES.includes(room.game)) return null;

  if (room.status === 'started' && now >= room.startAt) {
    // The player away the longest loses; time before the start does not count.
    const gone = roles
      .filter((r) => !room.players[r].connected && now - Math.max(room.players[r].awaySince, room.startAt) >= INACTIVE_FORFEIT_MS)
      .sort((a, b) => room.players[a].awaySince - room.players[b].awaySince);
    if (gone.length) {
      endMatch(room, otherRole(gone[0]), 'timeout');
      bump(room);
      return 'changed';
    }
    return null;
  }
  if (room.status !== 'started') {
    if (!room.players.host.connected && away('host') >= LOBBY_ABANDON_MS) {
      leave(room, 'host');
      return 'changed';
    }
    const guest = room.players.guest;
    if (guest && !guest.left && !guest.connected && away('guest') >= LOBBY_ABANDON_MS) {
      leave(room, 'guest');
      return 'changed';
    }
  }
  return null;
}

/** When checkTimers should run next, or null if nothing is pending. */
export function nextDeadline(room) {
  const roles = ['host', 'guest'].filter((r) => room.players[r]);
  const deadlines = [];
  if (roles.every((r) => !room.players[r].connected)) {
    const lastAway = Math.max(...roles.map((r) => room.players[r].awaySince));
    deadlines.push(lastAway + idleLimit(room));
  }
  if (room.status !== 'closed' && !TURN_GAMES.includes(room.game)) {
    for (const role of roles) {
      const player = room.players[role];
      if (player.connected || player.left) continue;
      if (room.status === 'started') {
        deadlines.push(Math.max(player.awaySince, room.startAt) + INACTIVE_FORFEIT_MS);
      } else {
        deadlines.push(player.awaySince + LOBBY_ABANDON_MS);
      }
    }
  }
  return deadlines.length ? Math.min(...deadlines) : null;
}

function idleLimit(room) {
  if (room.status === 'closed') return CLOSED_ROOM_MS;
  return TURN_GAMES.includes(room.game) ? TURN_ROOM_IDLE_MS : ROOM_IDLE_MS;
}

function bump(room) {
  room.version = (room.version || 0) + 1;
}

/** What one player is allowed to see; the same shape the Apps Script backend sends. */
export function view(room, role, now) {
  let status = room.status;
  if (status === 'started') status = now < room.startAt ? 'countdown' : 'playing';
  const me = room.players[role];
  const opponent = room.players[otherRole(role)];
  const showPuzzle = status === 'countdown' || status === 'playing' || (status === 'finished' && room.round > 0);
  return {
    code: room.code,
    game: room.game || 'sudoku',
    version: room.version,
    role,
    status,
    difficulty: room.difficulty,
    round: room.round,
    startAt: room.startAt,
    puzzle: showPuzzle ? room.puzzle : null,
    solution: status === 'finished' ? room.solution : null,
    winner: room.winner ? (room.winner === role ? 'me' : 'opponent') : null,
    endReason: room.endReason,
    // In Ship Battle both players' progress (share of the enemy fleet hit) is public.
    me: playerView(me, room, status === 'finished' || room.game === 'ships', now),
    opponent: opponent ? playerView(opponent, room, true, now) : null,
    ships: room.game === 'ships' ? shipsView(room, role, status) : null,
    turns: TURN_GAMES.includes(room.game) ? turnView(room, role, status) : null,
    words: room.game === 'words' ? wordsView(room, role, status) : null,
  };
}

function playerView(player, room, includeProgress, now) {
  return {
    name: player.name,
    avatar: player.avatar || null,
    deviceId: player.deviceId || null,
    // Latest reaction; the app shows it when seq goes up.
    emoji: player.emoji || null,
    ready: player.ready,
    difficulty: player.difficulty,
    // Your own progress stays hidden during a match so it can't be used to test guesses.
    progress: includeProgress ? player.progress : null,
    finishMs: player.finishedAt ? player.finishedAt - room.startAt : null,
    left: !!player.left,
    idleMs: player.connected ? 0 : Math.max(0, now - player.awaySince),
    // Whether the player has a live connection right now.
    online: !!player.connected,
  };
}
