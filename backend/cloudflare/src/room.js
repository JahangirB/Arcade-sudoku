// Battle rules for one room, as plain functions over a JSON-serialisable object so the
// Durable Object can store it and tests can run without Cloudflare. The rules match
// ../Code.gs; the difference is that "away" means a closed connection instead of
// missing polls.

import { generatePuzzle } from './sudoku.js';

// Empty cells per level; must match Difficulty in the Android app.
export const DIFFICULTY_HOLES = { beginner: 30, easy: 38, medium: 46, hard: 50, expert: 54, master: 56 };
export const COUNTDOWN_MS = 5000;
// Avatar and reaction keys the app can show; anything else is ignored. Must match the app.
export const AVATARS = ['fox', 'cat', 'dog', 'panda', 'owl', 'frog', 'tiger', 'unicorn', 'robot', 'alien', 'ninja', 'wizard'];
export const EMOJIS = ['thumbsup', 'clap', 'fire', 'laugh', 'wow', 'sweat', 'think', 'cool', 'heart', 'hurry'];
// At most one reaction per player this often.
export const EMOJI_INTERVAL_MS = 1500;
// A player disconnected this long during a match loses it.
export const INACTIVE_FORFEIT_MS = 45_000;
// A player disconnected this long outside a match is removed (a host closes the room).
export const LOBBY_ABANDON_MS = 5 * 60_000;
// A room with nobody connected is deleted after this long (1 minute once closed).
export const ROOM_IDLE_MS = 6 * 60 * 60_000;
const CLOSED_ROOM_MS = 60_000;

export class GameError extends Error {
  constructor(message, fatal = false) {
    super(message);
    // Fatal: the room is gone for this player; the app returns to the start screen.
    this.fatal = fatal;
  }
}

export function normalizeCode(code) {
  const clean = String(code || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (clean.length < 4 || clean.length > 12) {
    throw new GameError('The secret code must be 4 to 12 letters or digits.', true);
  }
  return clean;
}

export function cleanName(name) {
  return String(name || '').replace(/\s+/g, ' ').trim().slice(0, 16) || 'Player';
}

export function cleanAvatar(avatar) {
  return AVATARS.includes(avatar) ? avatar : null;
}

function newPlayer(name, avatar, now, uuid) {
  return {
    id: uuid(), name: cleanName(name), avatar: cleanAvatar(avatar), emoji: null, emojiAt: 0, ready: false, difficulty: null, board: null,
    progress: 0, finishedAt: null, left: false, connected: false, awaySince: now,
  };
}

export function createRoom(code, hostName, now, uuid, avatar = null) {
  return {
    code, createdAt: now, version: 0, emojiSeq: 0, difficulty: null, status: 'waiting', round: 0,
    puzzle: null, solution: null, startAt: null, winner: null, endReason: null,
    players: { host: newPlayer(hostName, avatar, now, uuid), guest: null },
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

/** Joins as guest, or reconnects when `playerId` belongs to the room. Returns the role. */
export function joinRoom(room, name, playerId, now, uuid, avatar = null) {
  if (room.status === 'closed') throw new GameError('The host closed this battle.', true);
  const known = findRole(room, playerId);
  if (known) return known;
  if (playerId) throw new GameError('You are not part of this battle.', true);
  if (room.players.guest && !room.players.guest.left) {
    throw new GameError('This battle already has two players.', true);
  }
  room.players.guest = newPlayer(name, avatar, now, uuid);
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
    if (awayFor >= (room.status === 'closed' ? CLOSED_ROOM_MS : ROOM_IDLE_MS)) return 'expired';
  }
  if (room.status === 'closed') return null;

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
    deadlines.push(lastAway + (room.status === 'closed' ? CLOSED_ROOM_MS : ROOM_IDLE_MS));
  }
  if (room.status !== 'closed') {
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
    me: playerView(me, room, status === 'finished', now),
    opponent: opponent ? playerView(opponent, room, true, now) : null,
  };
}

function playerView(player, room, includeProgress, now) {
  return {
    name: player.name,
    avatar: player.avatar || null,
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
