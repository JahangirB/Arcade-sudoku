import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DRAW } from '../src/wordlists.js';
import { TURNS, TURN_MS, cleanStroke, hint, isRight } from '../src/draw.js';
import {
  COUNTDOWN_MS, addStroke, checkTimers, clearDrawing, createRoom, drawGuess, joinRoom, nextDeadline,
  setConnected, setDrawReady, view,
} from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const GO = T0 + COUNTDOWN_MS;
const LINE = { points: [[0.1, 0.1], [0.5, 0.5]], color: 2, width: 1 };

function match() {
  const room = createRoom('DRAW1', 'Alice', T0, uuid, null, null, 'draw');
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, ['draw']);
  setConnected(room, 'guest', true, T0);
  setDrawReady(room, 'host', true, T0, () => 0.1);
  setDrawReady(room, 'guest', true, T0, () => 0.1);
  return room;
}

test('the server draw words match the app asset', () => {
  const file = readFileSync(new URL('../../../app/src/main/assets/words/draw.txt', import.meta.url), 'utf8').trim().split('\n').join(',');
  assert.equal(DRAW, file);
  assert.equal(hint('ice cream'), '___ _____');
  assert.ok(isRight(' Castle! ', 'castle'));
});

test('only the drawer sees the word; the guesser gets the letter count', () => {
  const room = match();
  assert.equal(room.drawer, 'host');
  const host = view(room, 'host', GO).draw;
  const guest = view(room, 'guest', GO).draw;
  assert.equal(host.drawer, 'me');
  assert.equal(host.word, room.drawWord);
  assert.equal(guest.word, null);
  assert.equal(guest.hint.length, room.drawWord.length);
  assert.equal(guest.turn, 1);
});

test('strokes come from the drawer only, are checked, and clear', () => {
  const room = match();
  assert.throws(() => addStroke(room, 'host', LINE, T0), /not running/);
  assert.throws(() => addStroke(room, 'guest', LINE, GO), /not your turn/);
  assert.throws(() => addStroke(room, 'host', { points: [[2, 0]] }, GO), /Bad stroke/);
  const { index, stroke } = addStroke(room, 'host', LINE, GO);
  assert.equal(index, 0);
  assert.deepEqual(stroke, LINE);
  assert.equal(view(room, 'guest', GO).draw.strokes.length, 1);
  clearDrawing(room, 'host');
  assert.equal(view(room, 'guest', GO).draw.strokes.length, 0);
  assert.deepEqual(cleanStroke({ points: [[0.12345, 1]], color: 99, width: 7 }, 0), { points: [[0.123, 1]], color: 0, width: 1 });
});

test('a right guess scores the seconds left and swaps the drawer', () => {
  const room = match();
  const word = room.drawWord;
  drawGuess(room, 'guest', 'nope', GO + 1000);
  assert.deepEqual(view(room, 'host', GO).draw.guesses, [{ by: 'opponent', word: 'nope' }]);
  assert.throws(() => drawGuess(room, 'host', word, GO), /drawing/);
  drawGuess(room, 'guest', word.toUpperCase(), GO + 30_000);
  assert.equal(room.score, 50);
  assert.equal(room.drawer, 'guest');
  assert.equal(room.drawTurn, 2);
  const last = view(room, 'host', GO + 30_000).draw.lastTurn;
  assert.deepEqual(last, { word, guessed: true, points: 50, drewIt: true });
  assert.notEqual(room.drawWord, word);
  assert.equal(view(room, 'host', GO + 30_000).draw.strokes.length, 0);
});

test('a turn that runs out moves on, and the match ends after the last turn', () => {
  const room = match();
  let now = GO;
  for (let t = 1; t <= TURNS; t++) {
    assert.equal(nextDeadline(room), room.turnEndsAt);
    now = room.turnEndsAt;
    assert.equal(checkTimers(room, now), 'changed');
  }
  assert.equal(room.status, 'finished');
  assert.equal(room.endReason, 'done');
  assert.equal(room.winner, null);
  assert.equal(view(room, 'host', now).draw.score, 0);
  assert.equal(TURN_MS, 80_000);
});
