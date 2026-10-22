// Runs Code.gs inside a sandbox with mocked Apps Script services.
// Usage: node --test backend/test
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');

const SOURCE = fs.readFileSync(path.join(__dirname, '..', 'Code.gs'), 'utf8');

function loadBackend() {
  const store = new Map();
  const clock = { now: 1_700_000_000_000 };
  const FakeDate = class extends Date {
    static now() { return clock.now; }
  };
  const context = {
    Math, JSON, String, Array, Error, Object,
    Date: FakeDate,
    CacheService: {
      getScriptCache: () => ({
        get: (k) => (store.has(k) ? store.get(k) : null),
        put: (k, v) => { store.set(k, v); },
        remove: (k) => { store.delete(k); }
      })
    },
    LockService: { getScriptLock: () => ({ tryLock: () => true, releaseLock: () => {} }) },
    Utilities: { getUuid: () => crypto.randomUUID() },
    ContentService: {
      MimeType: { JSON: 'json' },
      createTextOutput: (text) => ({ text, setMimeType() { return this; } })
    },
    SpreadsheetApp: { getActiveSpreadsheet: () => null }
  };
  vm.createContext(context);
  vm.runInContext(SOURCE, context);
  const call = (params) => JSON.parse(context.doGet({ parameter: params }).text);
  return { call, clock, context };
}

function isValidSolution(grid) {
  for (let i = 0; i < 9; i++) {
    const row = new Set(), col = new Set(), box = new Set();
    for (let j = 0; j < 9; j++) {
      row.add(grid[i * 9 + j]);
      col.add(grid[j * 9 + i]);
      box.add(grid[(Math.floor(i / 3) * 3 + Math.floor(j / 3)) * 9 + (i % 3) * 3 + (j % 3)]);
    }
    if (row.size !== 9 || col.size !== 9 || box.size !== 9 || row.has('0')) return false;
  }
  return true;
}

function startedBattle(b, difficulty = 'easy') {
  const host = b.call({ action: 'create', code: 'abc123', name: 'Alice' });
  const guest = b.call({ action: 'join', code: 'ABC123', name: 'Bob' });
  b.call({ action: 'ready', code: 'ABC123', playerId: host.playerId, difficulty });
  const started = b.call({ action: 'ready', code: 'ABC123', playerId: guest.playerId, difficulty });
  return { host, guest, started };
}

test('generated puzzles have a valid, unique solution', () => {
  const { context } = loadBackend();
  for (const holes of [30, 46, 54, 56]) {
    const { puzzle, solution } = context.generatePuzzle_(holes);
    assert.match(puzzle, /^[0-9]{81}$/);
    assert.ok(isValidSolution(solution), 'solution is a valid sudoku');
    for (let i = 0; i < 81; i++) {
      if (puzzle[i] !== '0') assert.equal(puzzle[i], solution[i]);
    }
    const grid = puzzle.split('').map(Number);
    assert.equal(context.countSolutions_(grid, 2), 1, 'puzzle has exactly one solution');
    const empty = puzzle.split('').filter((c) => c === '0').length;
    assert.ok(empty >= holes - 4, `enough empty cells (${empty})`);
  }
});

test('ping works without a room', () => {
  const b = loadBackend();
  const res = b.call({ action: 'ping' });
  assert.equal(res.ok, true);
  assert.equal(res.serverTime, b.clock.now);
});

test('host creates, guest joins, both ready, same puzzle and synced start', () => {
  const b = loadBackend();
  const host = b.call({ action: 'create', code: 'abc-123', name: 'Alice' });
  assert.equal(host.ok, true);
  assert.equal(host.code, 'ABC123');
  assert.equal(host.status, 'waiting');
  assert.equal(host.puzzle, null);

  const guest = b.call({ action: 'join', code: 'abc123', name: 'Bob' });
  assert.equal(guest.ok, true);
  assert.equal(guest.status, 'lobby');
  assert.equal(guest.opponent.name, 'Alice');
  assert.notEqual(guest.playerId, host.playerId);

  const third = b.call({ action: 'join', code: 'ABC123', name: 'Eve' });
  assert.equal(third.ok, false);

  const r1 = b.call({ action: 'ready', code: 'ABC123', playerId: host.playerId, difficulty: 'hard' });
  assert.equal(r1.status, 'lobby');
  assert.equal(r1.me.ready, true);
  assert.equal(r1.me.difficulty, 'hard');

  const guestSees = b.call({ action: 'sync', code: 'ABC123', playerId: guest.playerId });
  assert.equal(guestSees.opponent.difficulty, 'hard', 'the opponent sees the chosen level');

  const r2 = b.call({ action: 'ready', code: 'ABC123', playerId: guest.playerId, difficulty: 'hard' });
  assert.equal(r2.status, 'countdown');
  assert.equal(r2.difficulty, 'hard');
  assert.equal(r2.startAt, b.clock.now + 5000);
  assert.equal(r2.solution, null);

  const hostView = b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId });
  assert.equal(hostView.puzzle, r2.puzzle, 'both players get the same puzzle');
  assert.equal(hostView.startAt, r2.startAt, 'both players share the start time');
});

test('duplicate secret code is rejected', () => {
  const b = loadBackend();
  b.call({ action: 'create', code: 'SAME1', name: 'A' });
  const res = b.call({ action: 'create', code: 'SAME1', name: 'B' });
  assert.equal(res.ok, false);
  assert.match(res.error, /already in use/);
});

test('progress is shown for the opponent only and the first solver wins', () => {
  const b = loadBackend();
  const { host, guest, started } = startedBattle(b);
  const room = JSON.parse(b.context.CacheService.getScriptCache().get('room_ABC123'));
  const { puzzle, solution } = room;

  // Boards sent before the start time are ignored.
  b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId, board: solution });
  assert.equal(b.call({ action: 'sync', code: 'ABC123', playerId: guest.playerId }).status, 'countdown');

  b.clock.now = started.startAt + 1000;

  // Fill half of the empty cells correctly for the host.
  const empties = [];
  for (let i = 0; i < 81; i++) if (puzzle[i] === '0') empties.push(i);
  const half = puzzle.split('');
  empties.slice(0, Math.floor(empties.length / 2)).forEach((i) => { half[i] = solution[i]; });
  const hostSync = b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId, board: half.join('') });
  assert.equal(hostSync.status, 'playing');
  assert.equal(hostSync.me.progress, null, 'own progress is hidden during play');

  const guestSync = b.call({ action: 'sync', code: 'ABC123', playerId: guest.playerId });
  assert.equal(guestSync.opponent.progress, Math.floor((Math.floor(empties.length / 2) * 100) / empties.length));

  // Changing a given is rejected.
  const tampered = solution.split('');
  const givenIdx = puzzle.split('').findIndex((c) => c !== '0');
  tampered[givenIdx] = tampered[givenIdx] === '1' ? '2' : '1';
  assert.equal(b.call({ action: 'sync', code: 'ABC123', playerId: guest.playerId, board: tampered.join('') }).ok, false);

  b.clock.now += 60_000;
  const win = b.call({ action: 'sync', code: 'ABC123', playerId: guest.playerId, board: solution });
  assert.equal(win.status, 'finished');
  assert.equal(win.winner, 'me');
  assert.equal(win.endReason, 'solved');
  assert.equal(win.me.finishMs, 61_000);
  assert.equal(win.solution, solution);

  const lose = b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId, board: solution });
  assert.equal(lose.winner, 'opponent');
  assert.equal(lose.me.finishMs, null, 'boards after the match ended do not count');
});

test('rematch starts a new round once both players are ready again', () => {
  const b = loadBackend();
  const { host, guest, started } = startedBattle(b);
  b.clock.now = started.startAt + 5000;
  const room = JSON.parse(b.context.CacheService.getScriptCache().get('room_ABC123'));
  b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId, board: room.solution });

  const r1 = b.call({ action: 'ready', code: 'ABC123', playerId: host.playerId, difficulty: 'medium' });
  assert.equal(r1.status, 'finished');
  assert.equal(b.call({ action: 'sync', code: 'ABC123', playerId: guest.playerId }).opponent.ready, true);
  const r2 = b.call({ action: 'ready', code: 'ABC123', playerId: guest.playerId, difficulty: 'medium' });
  assert.equal(r2.status, 'countdown');
  assert.equal(r2.difficulty, 'medium', 'a rematch can use a different level');
  assert.equal(r2.round, 2);
  assert.equal(r2.winner, null);
});

test('leaving mid-game forfeits, leaving as host closes the room', () => {
  const b = loadBackend();
  const { host, guest, started } = startedBattle(b);
  b.clock.now = started.startAt + 1000;
  b.call({ action: 'leave', code: 'ABC123', playerId: guest.playerId });
  const view = b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId });
  assert.equal(view.status, 'finished');
  assert.equal(view.winner, 'me');
  assert.equal(view.endReason, 'forfeit');
  assert.equal(view.opponent.left, true);
  assert.equal(b.call({ action: 'ready', code: 'ABC123', playerId: host.playerId, difficulty: 'easy' }).ok, false);

  b.call({ action: 'leave', code: 'ABC123', playerId: host.playerId });
  const closed = b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId });
  assert.equal(closed.ok, false);
  assert.match(closed.error, /closed/);
});

test('guest leaving the lobby frees the slot', () => {
  const b = loadBackend();
  const host = b.call({ action: 'create', code: 'LOBBY1', name: 'A' });
  const guest = b.call({ action: 'join', code: 'LOBBY1', name: 'B' });
  b.call({ action: 'leave', code: 'LOBBY1', playerId: guest.playerId });
  assert.equal(b.call({ action: 'sync', code: 'LOBBY1', playerId: host.playerId }).status, 'waiting');
  assert.equal(b.call({ action: 'join', code: 'LOBBY1', name: 'C' }).ok, true);
});

test('POST with a JSON body is accepted', () => {
  const b = loadBackend();
  const res = JSON.parse(b.context.doPost({
    parameter: {},
    postData: { contents: JSON.stringify({ action: 'create', code: 'POST1', name: 'P' }) }
  }).text);
  assert.equal(res.ok, true);
  assert.equal(res.code, 'POST1');
});

test('an opponent who stops syncing forfeits by timeout', () => {
  const b = loadBackend();
  const { host, started } = startedBattle(b);
  b.clock.now = started.startAt + 10_000;
  assert.equal(b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId }).status, 'playing');
  b.clock.now += 30_000; // 40s into the match, guest last seen at its ready call
  const stillPlaying = b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId });
  assert.equal(stillPlaying.status, 'playing');
  assert.equal(stillPlaying.opponent.idleMs, 45_000);
  b.clock.now += 10_000; // 50s into the match
  const view = b.call({ action: 'sync', code: 'ABC123', playerId: host.playerId });
  assert.equal(view.status, 'finished');
  assert.equal(view.winner, 'me');
  assert.equal(view.endReason, 'timeout');
});

test('unknown rooms and players are fatal errors', () => {
  const b = loadBackend();
  const missing = b.call({ action: 'sync', code: 'NOPE99', playerId: 'x' });
  assert.equal(missing.ok, false);
  assert.equal(missing.fatal, true);
  b.call({ action: 'create', code: 'REAL1', name: 'A' });
  assert.equal(b.call({ action: 'sync', code: 'REAL1', playerId: 'intruder' }).fatal, true);
  assert.equal(b.call({ action: 'create', code: 'REAL1', name: 'B' }).fatal, false);
});

test('the round does not start until both players pick the same level', () => {
  const b = loadBackend();
  const host = b.call({ action: 'create', code: 'LEVEL1', name: 'A' });
  const guest = b.call({ action: 'join', code: 'LEVEL1', name: 'B' });

  b.call({ action: 'ready', code: 'LEVEL1', playerId: host.playerId, difficulty: 'hard' });
  const mismatch = b.call({ action: 'ready', code: 'LEVEL1', playerId: guest.playerId, difficulty: 'easy' });
  assert.equal(mismatch.status, 'lobby', 'different levels keep the lobby open');
  assert.equal(mismatch.me.difficulty, 'easy');
  assert.equal(mismatch.opponent.difficulty, 'hard');
  assert.equal(mismatch.puzzle, null);

  // Switching to the host's level starts the round.
  const match = b.call({ action: 'ready', code: 'LEVEL1', playerId: guest.playerId, difficulty: 'hard' });
  assert.equal(match.status, 'countdown');
  assert.equal(match.difficulty, 'hard');
  const empty = match.puzzle.split('').filter((c) => c === '0').length;
  assert.ok(empty >= 46, `hard puzzle has many empty cells (${empty})`);
});

test('a player can cancel being ready and must choose a valid level', () => {
  const b = loadBackend();
  const host = b.call({ action: 'create', code: 'LEVEL2', name: 'A' });
  const guest = b.call({ action: 'join', code: 'LEVEL2', name: 'B' });

  assert.equal(b.call({ action: 'ready', code: 'LEVEL2', playerId: host.playerId }).ok, false, 'level is required');
  assert.equal(b.call({ action: 'ready', code: 'LEVEL2', playerId: host.playerId, difficulty: 'nightmare' }).ok, false);

  b.call({ action: 'ready', code: 'LEVEL2', playerId: host.playerId, difficulty: 'easy' });
  const cancelled = b.call({ action: 'ready', code: 'LEVEL2', playerId: host.playerId, ready: 'false' });
  assert.equal(cancelled.me.ready, false);
  assert.equal(cancelled.me.difficulty, null);

  const guestReady = b.call({ action: 'ready', code: 'LEVEL2', playerId: guest.playerId, difficulty: 'easy' });
  assert.equal(guestReady.status, 'lobby', 'the host cancelled, so nothing starts');
});

test('every level generates a puzzle', () => {
  const b = loadBackend();
  for (const level of ['beginner', 'easy', 'medium', 'hard', 'expert', 'master']) {
    const host = b.call({ action: 'create', code: `LV${level}`.slice(0, 12), name: 'A' });
    const guest = b.call({ action: 'join', code: `LV${level}`.slice(0, 12), name: 'B' });
    b.call({ action: 'ready', code: `LV${level}`.slice(0, 12), playerId: host.playerId, difficulty: level });
    const res = b.call({ action: 'ready', code: `LV${level}`.slice(0, 12), playerId: guest.playerId, difficulty: level });
    assert.equal(res.difficulty, level);
    assert.match(res.puzzle, /^[0-9]{81}$/);
  }
});
