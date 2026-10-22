// Word Race: both players race to find the same secret five-letter word in six tries. Each
// guess colours its letters: 'g' right letter in the right place, 'y' in the word but
// elsewhere, '.' not in the word. Players see each other's colours, never the letters.

import { ALLOWED, ANSWERS } from './wordlists.js';

export const LENGTH = 5;
export const TRIES = 6;

const split = (joined) => joined.match(/.{5}/g);
export const answers = split(ANSWERS);
const allowed = new Set([...split(ALLOWED), ...answers]);

export function randomAnswer(random = Math.random) {
  return answers[Math.floor(random() * answers.length)];
}

export function isWord(word) {
  return allowed.has(word);
}

/** Colours for `guess` against `answer`. A repeated letter only counts as often as the answer has it. */
export function score(guess, answer) {
  const result = Array(LENGTH).fill('.');
  const left = {};
  for (let i = 0; i < LENGTH; i++) {
    if (guess[i] === answer[i]) result[i] = 'g';
    else left[answer[i]] = (left[answer[i]] || 0) + 1;
  }
  for (let i = 0; i < LENGTH; i++) {
    if (result[i] === '.' && left[guess[i]] > 0) {
      result[i] = 'y';
      left[guess[i]]--;
    }
  }
  return result.join('');
}
