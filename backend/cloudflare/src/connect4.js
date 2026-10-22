// Connect Four: players take turns dropping a disc into one of seven columns; the first to
// line up four of their own across, down or diagonally wins. A full board is a draw.
// The board is a 42-character string, row by row from the top: '.' empty, or a player's mark.

export const COLS = 7;
export const ROWS = 6;

export function emptyBoard() {
  return '.'.repeat(COLS * ROWS);
}

/** Drops [mark] into [col]. Returns { board, cell } or throws a plain Error the caller reports. */
export function drop(board, col, mark) {
  if (!Number.isInteger(col) || col < 0 || col >= COLS) throw new Error('Pick a column on the board.');
  for (let row = ROWS - 1; row >= 0; row--) {
    const cell = row * COLS + col;
    if (board[cell] === '.') return { board: board.slice(0, cell) + mark + board.slice(cell + 1), cell };
  }
  throw new Error('That column is full.');
}

/** The cells of a line of four or more through [cell], or null. */
export function winLine(board, cell) {
  const mark = board[cell];
  const row = Math.floor(cell / COLS);
  const col = cell % COLS;
  for (const [dr, dc] of [[0, 1], [1, 0], [1, 1], [1, -1]]) {
    const line = [cell];
    for (const sign of [1, -1]) {
      let r = row + dr * sign;
      let c = col + dc * sign;
      while (r >= 0 && r < ROWS && c >= 0 && c < COLS && board[r * COLS + c] === mark) {
        line.push(r * COLS + c);
        r += dr * sign;
        c += dc * sign;
      }
    }
    if (line.length >= 4) return line.sort((a, b) => a - b);
  }
  return null;
}

export function isFull(board) {
  return !board.includes('.');
}

// The turn-game interface room.js uses: start, play and view. A mark is a player's
// one-letter id ('h' host, 'g' guest).

export function start() {
  return { board: emptyBoard(), winLine: null, last: null };
}

/** Plays { col } for `mark`. Returns { again, over, winner, reason }. */
export function play(state, mark, move) {
  const { board, cell } = drop(state.board, Number(move?.col), mark);
  state.board = board;
  state.last = { mark, cell };
  const line = winLine(board, cell);
  if (line) {
    state.winLine = line;
    return { again: false, over: true, winner: mark, reason: 'four' };
  }
  return { again: false, over: isFull(board), winner: null, reason: 'draw' };
}

/** The board from one player's side: 'm' for their discs, 'o' for the opponent's. */
export function view(state, mark) {
  return {
    board: [...state.board].map((c) => (c === '.' ? '.' : c === mark ? 'm' : 'o')).join(''),
    winLine: state.winLine,
    lastMove: state.last ? { by: state.last.mark === mark ? 'me' : 'opponent', cell: state.last.cell } : null,
  };
}
