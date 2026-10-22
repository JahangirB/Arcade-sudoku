import test from 'node:test';
import assert from 'node:assert/strict';
import { COLS, drop, emptyBoard, winLine } from '../src/connect4.js';
import { createRoom, joinRoom, makeMove, setConnected, setTurnReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

function game(first = 0.1) {
  const room = createRoom('FOUR1', 'Alice', T0, uuid, null, null, 'connect4');
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, ['sudoku', 'ships', 'connect4']);
  setConnected(room, 'guest', true, T0);
  setTurnReady(room, 'host', true, T0);
  assert.equal(room.status, 'lobby');
  setTurnReady(room, 'guest', true, T0, () => first);
  return room;
}

function play(room, cols) {
  for (const col of cols) makeMove(room, room.turn, { col }, T0);
}

test('discs fall to the lowest free row and full columns are refused', () => {
  let board = emptyBoard();
  const first = drop(board, 3, 'h');
  assert.equal(first.cell, 5 * COLS + 3);
  board = first.board;
  assert.equal(drop(board, 3, 'g').cell, 4 * COLS + 3);
  for (let i = 0; i < 6; i++) board = drop(board, 0, 'h').board;
  assert.throws(() => drop(board, 0, 'g'), /full/);
  assert.throws(() => drop(board, 7, 'g'), /column/);
});

test('four in a row is found across, down and on both diagonals', () => {
  const at = (cells, mark = 'h') => {
    const b = [...emptyBoard()];
    for (const c of cells) b[c] = mark;
    return b.join('');
  };
  const across = [35, 36, 37, 38];
  assert.deepEqual(winLine(at(across), 37), across);
  const down = [3, 10, 17, 24];
  assert.deepEqual(winLine(at(down), 3), down);
  const diag = [14, 22, 30, 38];
  assert.deepEqual(winLine(at(diag), 22), diag);
  const anti = [20, 26, 32, 38];
  assert.deepEqual(winLine(at(anti), 26), anti);
  assert.equal(winLine(at([35, 36, 37]), 36), null);
});

test('players take turns and four in a column wins', () => {
  const room = game(0.1);
  assert.equal(room.turn, 'host');
  assert.equal(view(room, 'host', T0).turns.turn, 'me');
  assert.throws(() => makeMove(room, 'guest', { col: 0 }, T0), /not your turn/);
  play(room, [0, 1, 0, 1, 0, 1, 0]);
  assert.equal(room.status, 'finished');
  const host = view(room, 'host', T0);
  assert.equal(host.winner, 'me');
  assert.equal(host.endReason, 'four');
  assert.deepEqual(host.turns.winLine, [14, 21, 28, 35]);
  // Each player sees their own discs as 'm'.
  assert.equal(host.turns.board[35], 'm');
  assert.equal(view(room, 'guest', T0).turns.board[35], 'o');
});

test('a full board without four in a row is a draw', () => {
  const room = game(0.1);
  // A recorded game that fills the board without anyone lining up four.
  play(room, [6, 4, 0, 1, 5, 5, 5, 5, 2, 0, 1, 4, 4, 3, 4, 1, 2, 0, 6, 6, 0, 2, 2, 5, 6, 0, 1, 0, 5, 2, 3, 6, 2, 6, 4, 4, 3, 3, 1, 3, 3, 1]);
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, null);
  assert.equal(room.endReason, 'draw');
});

test('the next round starts with the other player', () => {
  const room = game(0.1);
  play(room, [0, 1, 0, 1, 0, 1, 0]);
  setTurnReady(room, 'host', true, T0);
  setTurnReady(room, 'guest', true, T0);
  assert.equal(room.round, 2);
  assert.equal(room.turn, 'guest');
  assert.equal(view(room, 'guest', T0).turns.board, '.'.repeat(42));
});

test('an app that does not know the game is asked to update', () => {
  const room = createRoom('FOUR2', 'Alice', T0, uuid, null, null, 'connect4');
  assert.throws(() => joinRoom(room, 'Bob', null, T0, uuid), (err) => err.reason === 'update_needed' && err.fatal);
  assert.throws(
    () => joinRoom(room, 'Bob', null, T0, uuid, null, null, ['sudoku', 'ships']),
    (err) => err.reason === 'update_needed',
  );
});
