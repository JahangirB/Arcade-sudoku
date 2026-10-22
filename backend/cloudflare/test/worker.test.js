// End-to-end test: starts the real Worker locally with `wrangler dev` and plays a battle
// over two WebSockets. Skipped unless RUN_WORKER_TESTS=1 (it needs wrangler installed).
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';

const PORT = 8799;
const BASE = `http://127.0.0.1:${PORT}`;
const enabled = process.env.RUN_WORKER_TESTS === '1';
const HOST_DEVICE = 'aaaaaaaa-0000-4000-8000-000000000001';
const GUEST_DEVICE = 'bbbbbbbb-0000-4000-8000-000000000002';

async function startServer() {
  const child = spawn('npx', ['wrangler', 'dev', '--port', String(PORT), '--var', 'SERVER_PASSWORD:pw1', '--persist-to', '.wrangler/test-state'], {
    cwd: new URL('..', import.meta.url).pathname,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, WRANGLER_SEND_METRICS: 'false' },
    // Own process group, so stopping it also stops wrangler's runtime process.
    detached: true,
  });
  let log = '';
  child.stdout.on('data', (d) => { log += d; });
  child.stderr.on('data', (d) => { log += d; });
  for (let i = 0; i < 120; i++) {
    try {
      const res = await fetch(`${BASE}/ping?password=pw1`);
      if (res.ok) return child;
    } catch {
      // Not up yet.
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  stop(child);
  throw new Error('wrangler dev did not start:\n' + log);
}

function stop(child) {
  try {
    process.kill(-child.pid, 'SIGTERM');
  } catch {
    // Already stopped.
  }
}

/** A WebSocket with a queue of received messages and request/response helpers. */
function connect(params) {
  const ws = new WebSocket(`ws://127.0.0.1:${PORT}/room/${params.code}?${new URLSearchParams({ password: 'pw1', ...params })}`);
  const queue = [];
  const waiters = [];
  ws.addEventListener('message', (e) => {
    const msg = JSON.parse(e.data);
    const i = waiters.findIndex((w) => w.match(msg));
    if (i >= 0) waiters.splice(i, 1)[0].resolve(msg);
    else queue.push(msg);
  });
  const next = (match, timeout = 5000, label = String(match)) => {
    const i = queue.findIndex(match);
    if (i >= 0) return Promise.resolve(queue.splice(i, 1)[0]);
    return new Promise((resolve, reject) => {
      const waiter = { match, resolve };
      waiters.push(waiter);
      setTimeout(() => reject(new Error('timed out waiting for: ' + label)), timeout).unref();
    });
  };
  let id = 0;
  const request = (type, fields = {}) => {
    const msgId = `m${++id}`;
    ws.send(JSON.stringify({ id: msgId, type, ...fields }));
    return next((m) => m.re === msgId);
  };
  return { ws, next, request };
}

test('a full battle over WebSockets', { skip: !enabled, timeout: 90_000 }, async (t) => {
  const server = await startServer();
  t.after(() => stop(server));

  const ping = await (await fetch(`${BASE}/ping?password=pw1`)).json();
  assert.equal(ping.ok, true);
  assert.equal(ping.kind, 'cloudflare');
  assert.equal(ping.invitations, false, 'no Firebase key in the test server');
  assert.equal((await fetch(`${BASE}/ping?password=wrong`)).status, 401);

  const code = `T${Date.now().toString(36).toUpperCase()}`.slice(0, 12);
  const host = connect({ code, action: 'create', name: 'Alice', avatar: 'fox', deviceId: HOST_DEVICE });
  const hostOpen = await host.next((m) => m.re === 'open');
  assert.equal(hostOpen.type, 'state');
  assert.equal(hostOpen.status, 'waiting');
  assert.ok(hostOpen.playerId);

  const guest = connect({ code, action: 'join', name: 'Bob', deviceId: GUEST_DEVICE });
  const guestOpen = await guest.next((m) => m.re === 'open');
  assert.equal(guestOpen.status, 'lobby');
  assert.equal(guestOpen.opponent.name, 'Alice');
  assert.equal(guestOpen.opponent.avatar, 'fox');
  assert.equal(guestOpen.opponent.deviceId, HOST_DEVICE);
  assert.equal(guestOpen.game, 'sudoku');
  // The host is told about the guest without asking.
  const pushed = await host.next((m) => m.type === 'state' && !m.re && m.opponent);
  assert.equal(pushed.opponent.name, 'Bob');

  // A third player is refused.
  const eve = connect({ code, action: 'join', name: 'Eve' });
  const refused = await eve.next((m) => m.re === 'open');
  assert.equal(refused.type, 'error');
  assert.match(refused.error, /two players/);

  // Different levels: nothing starts.
  await host.request('ready', { difficulty: 'easy' });
  const mismatch = await guest.request('ready', { difficulty: 'hard' });
  assert.equal(mismatch.status, 'lobby');
  // Same level: countdown for both, same puzzle.
  const start = await guest.request('ready', { difficulty: 'easy' });
  assert.equal(start.status, 'countdown');
  const hostStart = await host.next((m) => m.type === 'state' && m.status === 'countdown');
  assert.equal(hostStart.puzzle, start.puzzle);
  assert.equal(hostStart.startAt, start.startAt);

  // Wait for the start, then the guest makes progress and the host sees it pushed.
  await new Promise((r) => setTimeout(r, Math.max(0, start.startAt - start.serverTime) + 300));
  const board = start.puzzle.split('');
  const empty = board.indexOf('0');
  board[empty] = '1';
  await guest.request('board', { board: board.join('') });
  const progress = await host.next((m) => m.type === 'state' && m.status === 'playing' && m.opponent.progress !== null);
  assert.equal(typeof progress.opponent.progress, 'number');

  // Reactions are pushed to the opponent.
  await guest.request('emoji', { emoji: 'fire' });
  const reaction = await host.next((m) => m.type === 'state' && m.opponent?.emoji?.key === 'fire');
  assert.equal(reaction.opponent.emoji.seq, 1);

  // The guest drops and reconnects with its player id.
  guest.ws.close();
  const away = await host.next((m) => m.type === 'state' && m.opponent.online === false, 8000);
  assert.ok(away.opponent.idleMs >= 0);
  const again = connect({ code, action: 'join', name: 'Bob', playerId: guestOpen.playerId });
  const back = await again.next((m) => m.re === 'open');
  assert.equal(back.role, 'guest');
  assert.equal(back.status, 'playing');

  // The host leaves mid-match: forfeit, and the guest is told the room is closed.
  const left = await host.request('leave');
  assert.equal(left.type, 'left');
  const closed = await again.next((m) => m.type === 'error' && m.fatal);
  assert.match(closed.error, /closed/);

  for (const c of [host, guest, again, eve]) c.ws.close();

  // Invitations: phones register their push token; without the Firebase secret the
  // server says so clearly. Strangers can't invite each other.
  const post = (path, body) => fetch(`${BASE}${path}?password=pw1`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body),
  }).then((r) => r.json());
  const registered = await post('/register', { deviceId: HOST_DEVICE, token: 'x'.repeat(40), name: 'Alice' });
  assert.equal(registered.ok, true);
  assert.equal(registered.invitations, false);
  const invite = await post('/invite', { from: HOST_DEVICE, to: GUEST_DEVICE, code, game: 'sudoku' });
  assert.equal(invite.ok, false);
  assert.equal(invite.reason, 'push_not_configured');
  assert.equal((await post('/register', { deviceId: 'nope', token: 'short' })).ok, false);

  // Ship Battle over the same server: fleets in, then turns.
  const sea = `S${Date.now().toString(36).toUpperCase()}`.slice(0, 12);
  const row = (y) => [5, 4, 3, 3, 2].map((len, i) => ({ x: 0, y: y + i * 2, len, horizontal: true }));
  const captain = connect({ code: sea, action: 'create', name: 'Ann', game: 'ships' });
  assert.equal((await captain.next((m) => m.re === 'open')).game, 'ships');
  const rival = connect({ code: sea, action: 'join', name: 'Ben' });
  await rival.next((m) => m.re === 'open');
  await captain.request('ready', { fleet: row(0) });
  const started = await rival.request('ready', { fleet: row(0) });
  assert.equal(started.status, 'playing');
  const shooter = started.ships.turn === 'me' ? rival : captain;
  const shot = await shooter.request('fire', { x: 0, y: 0 });
  assert.equal(shot.ships.lastShot.hit, true);
  assert.equal(shot.ships.turn, 'me', 'a hit fires again');
  const other = shooter === rival ? captain : rival;
  const seen = await other.next((m) => m.type === 'state' && m.ships?.theirShots?.length === 1);
  assert.equal(seen.ships.theirShots[0].hit, true);
  for (const c of [captain, rival]) c.ws.close();
});
