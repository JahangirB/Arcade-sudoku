import test from 'node:test';
import assert from 'node:assert/strict';
import {
  FLEET_CELLS, createRoom, fire, joinRoom, setConnected, setShipsReady, shipCells, validateFleet, view, checkTimers,
  INACTIVE_FORFEIT_MS,
} from '../src/room.js';

let ids = 0;
const uuid = () => `p-${++ids}`;
const T0 = 1_700_000_000_000;

// Ships along the left of the sea: rows 0, 2, 4, 6, 8.
const FLEET_A = [
  { x: 0, y: 0, len: 5, horizontal: true },
  { x: 0, y: 2, len: 4, horizontal: true },
  { x: 0, y: 4, len: 3, horizontal: true },
  { x: 0, y: 6, len: 3, horizontal: true },
  { x: 0, y: 8, len: 2, horizontal: true },
];
// Columns 9, 7, 5, 3, 1 from the top.
const FLEET_B = [
  { x: 9, y: 0, len: 5, horizontal: false },
  { x: 7, y: 0, len: 4, horizontal: false },
  { x: 5, y: 0, len: 3, horizontal: false },
  { x: 3, y: 0, len: 3, horizontal: false },
  { x: 1, y: 0, len: 2, horizontal: false },
];

function battle(firstTurn = 0.1) {
  const room = createRoom('SEA1', 'Alice', T0, uuid, null, null, 'ships');
  setConnected(room, 'host', true, T0);
  joinRoom(room, 'Bob', null, T0, uuid);
  setConnected(room, 'guest', true, T0);
  setShipsReady(room, 'host', true, FLEET_A, T0);
  assert.equal(room.status, 'lobby');
  setShipsReady(room, 'guest', true, FLEET_B, T0, () => firstTurn);
  return room;
}

test('fleets must be the classic five, inside the sea and apart', () => {
  assert.equal(validateFleet(FLEET_A).length, 5);
  assert.throws(() => validateFleet(FLEET_A.slice(1)), /all five/);
  assert.throws(() => validateFleet([...FLEET_A.slice(0, 4), { x: 0, y: 8, len: 3, horizontal: true }]), /5, 4, 3, 3 and 2/);
  assert.throws(() => validateFleet([{ ...FLEET_A[0], x: 6 }, ...FLEET_A.slice(1)]), /outside/);
  assert.throws(() => validateFleet([FLEET_A[0], { ...FLEET_A[1], y: 0 }, ...FLEET_A.slice(2)]), /overlap/);
  assert.deepEqual(shipCells({ x: 2, y: 3, len: 3, horizontal: false }), [32, 42, 52]);
});

test('the battle starts when both fleets are in; nobody sees the enemy fleet', () => {
  const room = battle(0.1);
  assert.equal(room.status, 'started');
  const host = view(room, 'host', T0);
  assert.equal(host.game, 'ships');
  assert.equal(host.status, 'playing');
  assert.equal(host.ships.turn, 'me', 'random() < 0.5 means the host starts');
  assert.equal(view(room, 'guest', T0).ships.turn, 'opponent');
  assert.equal(host.ships.myFleet.length, 5);
  assert.deepEqual(host.ships.sunkEnemy, []);
  assert.equal(host.ships.enemyFleet, null);
  assert.equal(host.puzzle, null);
});

test('a hit fires again, a miss passes the turn, sunk ships are revealed', () => {
  const room = battle(0.1);
  assert.throws(() => fire(room, 'guest', 0, 0, T0), /not your turn/);
  assert.deepEqual(fire(room, 'host', 1, 0, T0), { hit: true, sunk: false });
  assert.deepEqual(fire(room, 'host', 1, 1, T0), { hit: true, sunk: true }, 'the 2-ship at column 1 is sunk');
  assert.throws(() => fire(room, 'host', 1, 1, T0), /already fired/);
  let host = view(room, 'host', T0);
  assert.deepEqual(host.ships.sunkEnemy, [{ x: 1, y: 0, len: 2, horizontal: false }]);
  assert.equal(host.ships.turn, 'me');
  assert.equal(host.me.progress, Math.floor((2 * 100) / FLEET_CELLS));

  assert.deepEqual(fire(room, 'host', 0, 9, T0), { hit: false, sunk: false });
  assert.equal(view(room, 'guest', T0).ships.turn, 'me');
  assert.deepEqual(view(room, 'guest', T0).ships.theirShots.map((s) => s.hit), [true, true, false]);
  assert.deepEqual(view(room, 'guest', T0).ships.lastShot, { by: 'opponent', x: 0, y: 9, hit: false, sunk: false });
});

test('sinking the whole fleet wins and reveals everything', () => {
  const room = battle(0.9); // the guest starts
  for (const ship of FLEET_A) {
    for (const cell of shipCells(ship)) fire(room, 'guest', cell % 10, Math.floor(cell / 10), T0 + 1000);
  }
  const guest = view(room, 'guest', T0 + 1000);
  assert.equal(guest.status, 'finished');
  assert.equal(guest.winner, 'me');
  assert.equal(guest.endReason, 'sunk');
  assert.equal(guest.me.progress, 100);
  assert.equal(view(room, 'host', T0 + 1000).ships.enemyFleet.length, 5);
});

test('a rematch needs both fleets again; leaving and timeouts work like Sudoku', () => {
  const room = battle(0.1);
  setConnected(room, 'guest', false, T0);
  assert.equal(checkTimers(room, T0 + INACTIVE_FORFEIT_MS), 'changed');
  assert.equal(room.winner, 'host');
  setConnected(room, 'guest', true, T0 + INACTIVE_FORFEIT_MS);
  setShipsReady(room, 'host', true, FLEET_B, T0);
  assert.equal(room.status, 'finished');
  setShipsReady(room, 'guest', true, FLEET_A, T0, () => 0.1);
  assert.equal(room.status, 'started');
  assert.equal(room.round, 2);
  assert.deepEqual(view(room, 'host', T0).ships.myShots, []);
});
