import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { ALLOWED, ANSWERS } from '../src/wordlists.js';
import { answers, isWord, score } from '../src/words.js';
import { COUNTDOWN_MS, createRoom, guessWord, joinRoom, setConnected, setWordsReady, view } from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;
const GO = T0 + COUNTDOWN_MS;

function race(answer = 'crane') {
  const room = createRoom('WORD1', 'Alice', T0, uuid, null, null, 'words');
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid, null, null, ['words']);
  setConnected(room, 'guest', true, T0);
  setWordsReady(room, 'host', true, T0);
  setWordsReady(room, 'guest', true, T0);
  room.answer = answer;
  return room;
}

test('the server word lists match the app assets', () => {
  const file = (name) => readFileSync(new URL(`../../../app/src/main/assets/words/${name}.txt`, import.meta.url), 'utf8').trim().split('\n').join('');
  assert.equal(ANSWERS, file('answers'));
  assert.equal(ALLOWED, file('allowed'));
  assert.ok(answers.length > 1500);
  assert.ok(answers.every(isWord));
});

test('scoring marks greens first and counts repeated letters once per letter in the answer', () => {
  assert.equal(score('crane', 'crane'), 'ggggg');
  assert.equal(score('react', 'crane'), 'yygy.');
  assert.equal(score('speed', 'abide'), '..y.y');
  // Two e's guessed, one e left after the green: only one counts.
  assert.equal(score('eerie', 'there'), 'y.y.g');
  assert.equal(score('geese', 'those'), '...gg');
  assert.equal(score('llama', 'hello'), 'yy...');
});

test('guesses wait for the countdown and must be real words', () => {
  const room = race();
  assert.throws(() => guessWord(room, 'host', 'slate', T0), /countdown/);
  assert.throws(() => guessWord(room, 'host', 'zzzzz', GO), /word list/);
  assert.throws(() => guessWord(room, 'host', 'abc', GO), /five-letter/);
  guessWord(room, 'host', 'slate', GO);
  const guest = view(room, 'guest', GO).words;
  assert.deepEqual(guest.theirs, [{ score: score('slate', 'crane') }]);
  assert.equal(guest.answer, null);
  assert.equal(view(room, 'host', GO).words.mine[0].word, 'slate');
});

test('solving first ends the round when the other can no longer do better', () => {
  const room = race();
  guessWord(room, 'guest', 'slate', GO);
  guessWord(room, 'host', 'slate', GO);
  guessWord(room, 'host', 'crane', GO + 1000);
  // The guest has 1 guess; solving next would also take 2 tries but later, so the host wins now.
  assert.equal(room.status, 'finished');
  assert.equal(view(room, 'host', GO).winner, 'me');
  assert.equal(view(room, 'guest', GO).words.answer, 'crane');
  assert.equal(view(room, 'guest', GO).words.theirs[1].word, 'crane');
});

test('a slower solve in fewer tries still wins', () => {
  const room = race();
  guessWord(room, 'host', 'slate', GO);
  guessWord(room, 'host', 'react', GO + 100);
  guessWord(room, 'host', 'crane', GO + 200);
  assert.equal(room.status, 'started');
  guessWord(room, 'guest', 'crane', GO + 9000);
  assert.equal(view(room, 'guest', GO).winner, 'me');
});

test('nobody solving is a draw', () => {
  const room = race();
  for (const role of ['host', 'guest']) for (let i = 0; i < 6; i++) guessWord(room, role, 'slate', GO + i);
  assert.equal(room.status, 'finished');
  assert.equal(room.winner, null);
  assert.equal(room.endReason, 'draw');
});
