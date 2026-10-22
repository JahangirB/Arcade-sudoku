// Puzzle generator: the same algorithm as generatePuzzle_ in ../Code.gs.

/**
 * Returns { puzzle, solution } as 81-character strings ('0' = empty cell).
 * Removes up to `holes` cells, keeping only removals that leave exactly one solution.
 */
export function generatePuzzle(holes, random = Math.random) {
  const solution = new Array(81).fill(0);
  fillGrid(solution, random);
  const puzzle = solution.slice();
  let removed = 0;
  for (const idx of shuffle([...Array(81).keys()], random)) {
    if (removed >= holes) break;
    const backup = puzzle[idx];
    puzzle[idx] = 0;
    if (countSolutions(puzzle.slice(), 2) === 1) removed++;
    else puzzle[idx] = backup;
  }
  return { puzzle: puzzle.join(''), solution: solution.join('') };
}

function fillGrid(grid, random) {
  const idx = grid.indexOf(0);
  if (idx === -1) return true;
  for (const digit of shuffle([1, 2, 3, 4, 5, 6, 7, 8, 9], random)) {
    if (canPlace(grid, idx, digit)) {
      grid[idx] = digit;
      if (fillGrid(grid, random)) return true;
      grid[idx] = 0;
    }
  }
  return false;
}

function canPlace(grid, idx, digit) {
  const row = Math.floor(idx / 9);
  const col = idx % 9;
  const boxRow = row - (row % 3);
  const boxCol = col - (col % 3);
  for (let i = 0; i < 9; i++) {
    if (grid[row * 9 + i] === digit) return false;
    if (grid[i * 9 + col] === digit) return false;
    if (grid[(boxRow + Math.floor(i / 3)) * 9 + boxCol + (i % 3)] === digit) return false;
  }
  return true;
}

/** Counts solutions up to `limit`, always branching on the most constrained cell. */
export function countSolutions(grid, limit) {
  let best = -1;
  let bestCandidates = null;
  for (let idx = 0; idx < 81; idx++) {
    if (grid[idx] !== 0) continue;
    const candidates = [];
    for (let d = 1; d <= 9; d++) if (canPlace(grid, idx, d)) candidates.push(d);
    if (candidates.length === 0) return 0;
    if (best === -1 || candidates.length < bestCandidates.length) {
      best = idx;
      bestCandidates = candidates;
      if (candidates.length === 1) break;
    }
  }
  if (best === -1) return 1;
  let count = 0;
  for (let i = 0; i < bestCandidates.length && count < limit; i++) {
    grid[best] = bestCandidates[i];
    count += countSolutions(grid, limit - count);
  }
  grid[best] = 0;
  return count;
}

function shuffle(arr, random) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}
