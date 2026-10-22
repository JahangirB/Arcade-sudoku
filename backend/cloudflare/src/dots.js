// Dots & Boxes on a 5 × 5 grid of dots (4 × 4 boxes). Players take turns drawing a line
// between two neighbouring dots; closing a box claims it and draws again. When every box is
// claimed, the most boxes wins (8 each is a draw).
//
// Lines are numbered: horizontal ones 0-19 (row r 0-4, column c 0-3: r * 4 + c), vertical
// ones 20-39 (row r 0-3, column c 0-4: 20 + r * 5 + c). Boxes are r * 4 + c.

export const BOXES = 4;
export const LINES = 40;

/** The four lines around box (r, c). */
export function boxLines(box) {
  const r = Math.floor(box / BOXES);
  const c = box % BOXES;
  return [r * 4 + c, (r + 1) * 4 + c, 20 + r * 5 + c, 20 + r * 5 + c + 1];
}

/** The one or two boxes next to a line. */
export function lineBoxes(line) {
  if (line < 20) {
    const r = Math.floor(line / 4);
    const c = line % 4;
    return [r - 1, r].filter((row) => row >= 0 && row < BOXES).map((row) => row * BOXES + c);
  }
  const r = Math.floor((line - 20) / 5);
  const c = (line - 20) % 5;
  return [c - 1, c].filter((col) => col >= 0 && col < BOXES).map((col) => r * BOXES + col);
}

export function start() {
  return { lines: '.'.repeat(LINES), boxes: '.'.repeat(BOXES * BOXES), last: null };
}

/** Draws { line } for `mark`. Returns { again, over, winner, reason }. */
export function play(state, mark, move) {
  const line = Number(move?.line);
  if (!Number.isInteger(line) || line < 0 || line >= LINES) throw new Error('Pick a line between two dots.');
  if (state.lines[line] !== '.') throw new Error('That line is already drawn.');
  state.lines = state.lines.slice(0, line) + mark + state.lines.slice(line + 1);
  state.last = { mark, line };
  let closed = 0;
  for (const box of lineBoxes(line)) {
    if (state.boxes[box] === '.' && boxLines(box).every((l) => state.lines[l] !== '.')) {
      state.boxes = state.boxes.slice(0, box) + mark + state.boxes.slice(box + 1);
      closed++;
    }
  }
  if (state.boxes.includes('.')) return { again: closed > 0, over: false, winner: null, reason: null };
  return { again: false, over: true, ...winnerOf(state.boxes), reason: 'most' };
}

/** The mark with more claimed cells, or null on a tie. */
export function winnerOf(owners) {
  const counts = {};
  for (const m of owners) counts[m] = (counts[m] || 0) + 1;
  const [a, b] = ['h', 'g'].map((m) => counts[m] || 0);
  return { winner: a === b ? null : a > b ? 'h' : 'g' };
}

const side = (s, mark) => [...s].map((c) => (c === '.' ? '.' : c === mark ? 'm' : 'o')).join('');

export function view(state, mark) {
  return {
    lines: side(state.lines, mark),
    boxes: side(state.boxes, mark),
    lastMove: state.last ? { by: state.last.mark === mark ? 'me' : 'opponent', line: state.last.line } : null,
  };
}
