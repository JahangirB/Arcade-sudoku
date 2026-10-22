import test from 'node:test';
import assert from 'node:assert/strict';
import {
  INACTIVE_FORFEIT_MS, LOBBY_ABANDON_MS, ROOM_IDLE_MS, checkTimers, createRoom, joinRoom, leave,
  nextDeadline, setConnected, setReady, submitBoard, view,
} from '../src/room.js';
import { countSolutions, generatePuzzle } from '../src/sudoku.js';

let ids = 0;
const uuid = () => `id-${++ids}`;
const T0 = 1_700_000_000_000;

function lobby() {
  const room = createRoom('ABC123', 'Alice', T0, uuid);
  setConnected(room, 'host', true, T0);
  const guestRole = joinRoom(room, 'Bob', null, T0, uuid);
  setConnected(room, guestRole, true, T0);
  return room;
}

function started(level = 'easy') {
  const room = lobby();
  setReady(room, 'host', true, level, T0);
  setReady(room, 'guest', true, level, T0);
  return room;
}

test('generator makes unique-solution puzzles', () => {
  for (const holes of [30, 46, 56]) {
    const { puzzle, solution } = generatePuzzle(holes);
    assert.match(solution, /^[1-9]{81}$/);
    assert.equal(countSolutions(puzzle.split('').map(Number), 2), 1);
    assert.ok(puzzle.split('').filter((c) => c === '0').length >= holes - 4);
  }
});

test('the round starts only when both pick the same level', () => {
  const room = lobby();
  setReady(room, 'host', true, 'hard', T0);
  setReady(room, 'guest', true, 'easy', T0);
  assert.equal(room.status, 'lobby');
  assert.equal(view(room, 'guest', T0).opponent.difficulty, 'hard');
  setReady(room, 'guest', true, 'hard', T0);
  assert.equal(room.status, 'started');
  assert.equal(room.difficulty, 'hard');
  assert.equal(view(room, 'host', T0).status, 'countdown');
  assert.equal(view(room, 'host', T0 + 5000).status, 'playing');
  assert.equal(view(room, 'host', T0).puzzle, view(room, 'guest', T0).puzzle);
});

test('boards count only after the start and the first solver wins', () => {
  const room = started();
  assert.equal(submitBoard(room, 'host', room.solution, T0 + 1000), false, 'too early');
  const now = room.startAt + 30_000;
  assert.throws(() => submitBoard(room, 'host', '1'.repeat(81), now), /given number/);
  assert.equal(submitBoard(room, 'guest', room.solution, now), true);
  const guestView = view(room, 'guest', now);
  assert.equal(guestView.status, 'finished');
  assert.equal(guestView.winner, 'me');
  assert.equal(guestView.me.finishMs, 30_000);
  assert.equal(view(room, 'host', now).winner, 'opponent');
});

test('own progress is hidden during the match, the opponent sees it', () => {
  const room = started();
  const board = room.puzzle.split('');
  const empty = board.indexOf('0');
  board[empty] = room.solution[empty];
  submitBoard(room, 'host', board.join(''), room.startAt + 1);
  assert.equal(view(room, 'host', room.startAt + 1).me.progress, null);
  assert.ok(view(room, 'guest', room.startAt + 1).opponent.progress > 0);
});

test('a player disconnected for 45 s during a match loses', () => {
  const room = started();
  setConnected(room, 'guest', false, room.startAt + 1000);
  assert.equal(view(room, 'host', room.startAt + 2000).opponent.idleMs, 1000);
  assert.equal(nextDeadline(room), room.startAt + 1000 + INACTIVE_FORFEIT_MS);
  assert.equal(checkTimers(room, room.startAt + 20_000), null);
  // Reconnecting in time keeps the match going.
  setConnected(room, 'guest', true, room.startAt + 30_000);
  assert.equal(checkTimers(room, room.startAt + 60_000), null);
  setConnected(room, 'guest', false, room.startAt + 61_000);
  assert.equal(checkTimers(room, room.startAt + 61_000 + INACTIVE_FORFEIT_MS), 'changed');
  assert.equal(view(room, 'host', room.startAt + 200_000).winner, 'me');
  assert.equal(room.endReason, 'timeout');
});

test('players away from the lobby are removed and empty rooms expire', () => {
  const room = lobby();
  setConnected(room, 'guest', false, T0);
  assert.equal(checkTimers(room, T0 + LOBBY_ABANDON_MS), 'changed');
  assert.equal(room.players.guest, null);
  assert.equal(room.status, 'waiting');

  setConnected(room, 'host', false, T0 + LOBBY_ABANDON_MS);
  assert.equal(checkTimers(room, T0 + 2 * LOBBY_ABANDON_MS), 'changed');
  assert.equal(room.status, 'closed');
  assert.equal(checkTimers(room, T0 + 2 * LOBBY_ABANDON_MS + 60_000), 'expired');

  const idle = started();
  setConnected(idle, 'host', false, T0);
  setConnected(idle, 'guest', false, T0);
  idle.status = 'finished';
  assert.equal(checkTimers(idle, T0 + ROOM_IDLE_MS), 'expired');
});

test('joining rules: full room, reconnect, strangers', () => {
  const room = lobby();
  const guestId = room.players.guest.id;
  assert.throws(() => joinRoom(room, 'Eve', null, T0, uuid), /two players/);
  assert.equal(joinRoom(room, 'Bob', guestId, T0, uuid), 'guest');
  assert.throws(() => joinRoom(room, 'Eve', 'forged-id', T0, uuid), (e) => e.fatal);
});

test('leaving mid-match forfeits; host leaving closes the room', () => {
  const room = started();
  leave(room, 'guest');
  assert.equal(room.winner, 'host');
  assert.equal(room.endReason, 'forfeit');
  assert.throws(() => setReady(room, 'host', true, 'easy', room.startAt), /opponent has left/);
  leave(room, 'host');
  assert.equal(room.status, 'closed');
  assert.throws(() => joinRoom(room, 'Bob', null, T0, uuid), /closed/);
});

test('avatars and reactions', async () => {
  const { sendEmoji, EMOJI_INTERVAL_MS } = await import('../src/room.js');
  const room = createRoom('EMO1', 'A', T0, uuid, 'owl');
  setConnected(room, 'host', true, T0);
  assert.throws(() => sendEmoji(room, 'host', 'fire', T0), /Nobody/);
  joinRoom(room, 'B', null, T0, uuid, 'hacker');
  assert.equal(view(room, 'guest', T0).opponent.avatar, 'owl');
  assert.equal(view(room, 'host', T0).opponent.avatar, null);
  sendEmoji(room, 'guest', 'clap', T0);
  assert.deepEqual(view(room, 'host', T0).opponent.emoji, { key: 'clap', seq: 1 });
  assert.throws(() => sendEmoji(room, 'guest', 'fire', T0 + 100), /Slow down/);
  sendEmoji(room, 'guest', 'fire', T0 + EMOJI_INTERVAL_MS);
  assert.equal(view(room, 'host', T0).opponent.emoji.seq, 2);
  assert.throws(() => sendEmoji(room, 'guest', 'bomb', T0 + 10_000), /Unknown/);
});
