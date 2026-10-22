// Draw & Guess: one player draws a secret word while the other watches and guesses. You play
// as a team: a right guess scores the seconds left on the clock. Four turns, taking turns to draw.
// Strokes are short polylines in 0-1 coordinates so both screens scale them to their own size.

import { DRAW } from './wordlists.js';

export const TURNS = 4;
export const TURN_MS = 80_000;
export const COLORS = 6;
const MAX_POINTS = 120;
const MAX_STROKES = 2000;
export const words = DRAW.split(',');

/** A word not used yet this match. */
export function pickWord(used, random = Math.random) {
  const fresh = words.filter((w) => !used.includes(w));
  const pool = fresh.length ? fresh : words;
  return pool[Math.floor(random() * pool.length)];
}

/** The word as the guesser sees it: one underscore per letter. */
export function hint(word) {
  return word.replace(/[a-z]/g, '_');
}

export function isRight(guess, word) {
  return String(guess || '').toLowerCase().replace(/[^a-z]/g, '') === word;
}

/** Checks a stroke from the app; throws a plain Error the caller reports. */
export function cleanStroke(stroke, count) {
  if (count >= MAX_STROKES) throw new Error('The drawing is full. Clear it to keep drawing.');
  const points = Array.isArray(stroke?.points) ? stroke.points : [];
  if (points.length < 1 || points.length > MAX_POINTS) throw new Error('Bad stroke.');
  const clean = points.map((pt) => {
    const [x, y] = Array.isArray(pt) ? pt.map(Number) : [NaN, NaN];
    if (!(x >= 0 && x <= 1 && y >= 0 && y <= 1)) throw new Error('Bad stroke.');
    return [Math.round(x * 1000) / 1000, Math.round(y * 1000) / 1000];
  });
  const color = Number(stroke.color);
  const width = Number(stroke.width);
  return { points: clean, color: Number.isInteger(color) && color >= 0 && color < COLORS ? color : 0, width: width === 2 ? 2 : 1 };
}
