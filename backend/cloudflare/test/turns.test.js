import test from 'node:test';
import assert from 'node:assert/strict';
import * as dots from '../src/dots.js';
import * as memory from '../src/memory.js';
import { prepareMessage } from '../src/directory.js';
import {
  INACTIVE_FORFEIT_MS, LOBBY_ABANDON_MS, checkTimers, createRoom, joinRoom, makeMove, nextDeadline,
  setConnected, setTurnReady, view,
} from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const ALL = ['sudoku', 'ships', 'connect4', 'dots', 'memory'];

function game(kind, random = () => 0.1) {
  const room = createRoom('TURN1', 'Alice', T0, uuid, null, null, kind);
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, ALL);
  setConnected(room, 'guest', true, T0);
  setTurnReady(room, 'host', true, T0, random);
  setTurnReady(room, 'guest', true, T0, random);
  assert.equal(room.turn, 'host');
  return room;
}

test('every dots line borders one or two boxes, and every box has four lines', () => {
  for (let line = 0; line < dots.LINES; line++) {
    const boxes = dots.lineBoxes(line);
    assert.ok(boxes.length >= 1 && boxes.length <= 2);
    for (const box of boxes) assert.ok(dots.boxLines(box).includes(line));
  }
  assert.deepEqual(dots.boxLines(0), [0, 4, 20, 21]);
  assert.deepEqual(dots.boxLines(15), [15, 19, 38, 39]);
});

test('closing a box claims it and the same player goes again', () => {
  const room = game('dots');
  // Host draws three sides of box 0, guest draws elsewhere in between.
  makeMove(room, 'host', { line: 0 }, T0);
  makeMove(room, 'guest', { line: 19 }, T0);
  makeMove(room, 'host', { line: 4 }, T0);
  makeMove(room, 'guest', { line: 39 }, T0);
  makeMove(room, 'host', { line: 20 }, T0);
  // Guest closes box 0 with its last side and keeps the turn.
  makeMove(room, 'guest', { line: 21 }, T0);
  assert.equal(room.turn, 'guest');
  const v = view(room, 'guest', T0).turns;
  assert.equal(v.boxes[0], 'm');
  assert.equal(v.lines[21], 'm');
  assert.deepEqual(v.lastMove, { by: 'me', line: 21 });
  assert.throws(() => makeMove(room, 'guest', { line: 21 }, T0), /already drawn/);
});

test('dots ends when every box is claimed, the most boxes winning', () => {
  const room = game('dots');
  // Draw every line in order; whoever closes boxes keeps going.
  for (let line = 0; line < dots.LINES; line++) makeMove(room, room.turn, { line }, T0);
  assert.equal(room.status, 'finished');
  const boxes = view(room, 'host', T0).turns.boxes;
  const mine = [...boxes].filter((c) => c === 'm').length;
  const expected = mine > 8 ? 'me' : mine < 8 ? 'opponent' : null;
  assert.equal(view(room, 'host', T0).winner, expected);
});

test('memory hides faces until flipped and keeps a missed pair up until the next flip', () => {
  const room = game('memory');
  const deck = room.turnState.deck;
  assert.equal(deck.length, 16);
  assert.ok(view(room, 'guest', T0).turns.faces.every((f) => f === null));
  const a = 0;
  const miss = deck.findIndex((f, i) => i !== a && f !== deck[a]);
  makeMove(room, 'host', { card: a }, T0);
  assert.equal(room.turn, 'host');
  makeMove(room, 'host', { card: miss }, T0);
  assert.equal(room.turn, 'guest');
  const shown = view(room, 'guest', T0).turns.faces;
  assert.equal(shown[a], deck[a]);
  assert.equal(shown[miss], deck[miss]);
  // The guest's first flip turns the missed pair face down again.
  const pair = deck.findIndex((f, i) => i !== a && f === deck[a]);
  makeMove(room, 'guest', { card: a }, T0);
  makeMove(room, 'guest', { card: pair }, T0);
  const after = view(room, 'guest', T0).turns;
  assert.equal(after.owners[a], 'm');
  assert.equal(after.owners[pair], 'm');
  assert.equal(after.faces[miss], null);
  assert.equal(room.turn, 'guest');
});

test('memory ends when all pairs are found', () => {
  const room = game('memory');
  const deck = room.turnState.deck;
  const done = new Set();
  for (let i = 0; i < 16; i++) {
    if (done.has(i)) continue;
    const j = deck.findIndex((f, k) => k !== i && f === deck[i]);
    makeMove(room, room.turn, { card: i }, T0);
    makeMove(room, room.turn, { card: j }, T0);
    done.add(i).add(j);
  }
  assert.equal(room.status, 'finished');
  assert.equal(view(room, 'host', T0).winner, 'me');
  assert.equal(room.endReason, 'most');
});

test('turn games wait for a player who closed the app instead of forfeiting', () => {
  const room = game('connect4');
  setConnected(room, 'guest', false, T0);
  assert.equal(nextDeadline(room), null);
  assert.equal(checkTimers(room, T0 + INACTIVE_FORFEIT_MS + LOBBY_ABANDON_MS), null);
  assert.equal(room.status, 'started');
  // Only when nobody has been around for a week does the room go.
  setConnected(room, 'host', false, T0);
  assert.equal(checkTimers(room, T0 + 7 * 24 * 60 * 60_000), 'expired');
});

test('a "your turn" push names the game and the room', async () => {
  const store = new Map();
  const kv = { get: async (k) => store.get(k), put: async (k, v) => store.set(k, v) };
  store.set('pair:aaaaaaaa:bbbbbbbb', true);
  store.set('device:bbbbbbbb', { token: 'x'.repeat(40) });
  store.set('device:aaaaaaaa', { name: 'Alice', avatar: 'fox' });
  const msg = await prepareMessage(kv, 'your_turn', { from: 'aaaaaaaa', to: 'bbbbbbbb', code: 'TURN1', game: 'dots' });
  assert.equal(msg.data.type, 'your_turn');
  assert.equal(msg.data.game, 'dots');
  assert.equal(msg.data.code, 'TURN1');
  assert.equal(msg.data.fromName, 'Alice');
});
