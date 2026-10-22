// Arcade Sudoku Battle server for Cloudflare Workers.
//
// GET  /ping                 health check (also verifies the password)
// GET  /room/<CODE> (WebSocket) with ?action=create|join&name=&avatar=&deviceId=&game=&playerId=&password=
// POST /register             { deviceId, token, name, avatar }  a phone's push token
// POST /invite               { from, to, code, game }           push an invitation to a partner
// POST /invite/reply         { from, to, accepted, code }       push the answer back
//
// Each room is one Durable Object, so a room's state lives in one place and every
// change is pushed to both players at once. Messages are JSON:
//   app -> server  { id, type: 'ready' | 'board' | 'fire' | 'emoji' | 'sync' | 'leave', ... }
//   server -> app  { type: 'state', re?, serverTime, ...room view }   (re = id it answers)
//                  { type: 'error', re?, error, fatal }

import { DurableObject } from 'cloudflare:workers';
import { DirectoryError, pair, prepareMessage, register } from './directory.js';
import { createPushSender, serviceAccountText } from './push.js';
import {
  GameError, checkTimers, createRoom, joinRoom, leave, nextDeadline, normalizeCode,
  fire, sendEmoji, setConnected, setReady, setShipsReady, submitBoard, view,
} from './room.js';

const API_VERSION = 1;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const password = env.SERVER_PASSWORD || '';
    if (password && url.searchParams.get('password') !== password) {
      return json({ ok: false, error: 'Wrong server password.', fatal: true }, 401);
    }

    const path = url.pathname.replace(/\/+$/, '');
    if (path === '' || path === '/ping') {
      return json({
        ok: true, serverTime: Date.now(), version: API_VERSION, kind: 'cloudflare',
        // Whether the Firebase key for invitations is set, to make setup easy to check.
        invitations: !!serviceAccountText(env),
      });
    }

    if (request.method === 'POST' && ['/register', '/invite', '/invite/reply'].includes(path)) {
      let body;
      try {
        body = await request.json();
      } catch {
        return json({ ok: false, error: 'Expected JSON.' }, 400);
      }
      const directory = env.DIRECTORY.get(env.DIRECTORY.idFromName('directory'));
      const result = path === '/register'
        ? await directory.register(body)
        : await directory.notify(path === '/invite' ? 'invite' : 'invite_reply', body);
      return json(result, result.ok ? 200 : 400);
    }

    const match = path.match(/^\/room\/([^/]+)$/);
    if (!match) return json({ ok: false, error: 'Not found' }, 404);
    if (request.headers.get('Upgrade') !== 'websocket') {
      return json({ ok: false, error: 'Expected a WebSocket connection.' }, 426);
    }
    let code;
    try {
      code = normalizeCode(decodeURIComponent(match[1]));
    } catch (err) {
      return json({ ok: false, error: err.message, fatal: true }, 400);
    }
    const room = env.ROOMS.get(env.ROOMS.idFromName(code));
    return room.fetch(request);
  },
};

export class Room extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    // undefined = not loaded yet; null = no room stored.
    this.room = undefined;
  }

  async load() {
    if (this.room === undefined) this.room = (await this.ctx.storage.get('room')) ?? null;
    return this.room;
  }

  async save() {
    await this.ctx.storage.put('room', this.room);
    const deadline = nextDeadline(this.room);
    if (deadline) await this.ctx.storage.setAlarm(deadline);
    else await this.ctx.storage.deleteAlarm();
  }

  async fetch(request) {
    const params = new URL(request.url).searchParams;
    const [client, server] = Object.values(new WebSocketPair());
    // Hibernation-friendly accept: the object can sleep between messages.
    this.ctx.acceptWebSocket(server);
    const now = Date.now();

    try {
      await this.load();
      const code = normalizeCode(new URL(request.url).pathname.split('/').pop());
      let role;
      if (params.get('action') === 'create') {
        if (this.room && this.room.status !== 'closed') {
          throw new GameError('That secret code is already in use. Pick another one.', true, 'code_taken');
        }
        this.room = createRoom(
          code, params.get('name'), now, () => crypto.randomUUID(),
          params.get('avatar'), params.get('deviceId'), params.get('game'),
        );
        role = 'host';
      } else {
        if (!this.room) throw new GameError('No battle found for that secret code.', true, 'not_found');
        role = joinRoom(
          this.room, params.get('name'), params.get('playerId'), now, () => crypto.randomUUID(),
          params.get('avatar'), params.get('deviceId'),
        );
      }

      // A reconnecting player replaces their old connection.
      for (const ws of this.ctx.getWebSockets()) {
        if (ws !== server && roleOf(ws) === role) {
          ws.serializeAttachment({ role: null });
          safeClose(ws, 4000, 'Replaced by a new connection');
        }
      }
      server.serializeAttachment({ role });
      setConnected(this.room, role, true, now);
      await this.save();
      await this.rememberPartners();
      this.send(server, role, { re: 'open', playerId: this.room.players[role].id });
      this.broadcast(server);
    } catch (err) {
      server.send(JSON.stringify({ type: 'error', re: 'open', error: err.message, fatal: true, reason: err.reason || null }));
      safeClose(server, 4001, 'Could not open the battle');
    }
    return new Response(null, { status: 101, webSocket: client });
  }

  /** Tells the directory that these two phones played together, so they may invite each other. */
  async rememberPartners() {
    const host = this.room.players.host?.deviceId;
    const guest = this.room.players.guest?.deviceId;
    if (!host || !guest || !this.env.DIRECTORY) return;
    try {
      await this.env.DIRECTORY.get(this.env.DIRECTORY.idFromName('directory')).pair(host, guest);
    } catch {
      // Invitations are a convenience; never let them break a battle.
    }
  }

  async webSocketMessage(ws, raw) {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch {
      return;
    }
    const role = roleOf(ws);
    const now = Date.now();
    try {
      await this.load();
      if (!this.room || !role || this.room.status === 'closed') {
        throw new GameError('The host closed this battle.', true, 'closed');
      }
      switch (msg.type) {
        case 'ready':
          if (this.room.game === 'ships') setShipsReady(this.room, role, msg.ready !== false, msg.fleet, now);
          else setReady(this.room, role, msg.ready !== false, msg.difficulty, now);
          break;
        case 'fire':
          fire(this.room, role, msg.x, msg.y, now);
          break;
        case 'board':
          submitBoard(this.room, role, msg.board, now);
          break;
        case 'emoji':
          sendEmoji(this.room, role, msg.emoji, now);
          break;
        case 'sync':
          break;
        case 'leave':
          leave(this.room, role);
          ws.serializeAttachment({ role: null });
          await this.save();
          ws.send(JSON.stringify({ type: 'left', re: msg.id }));
          safeClose(ws, 1000, 'Left');
          this.broadcast(ws);
          return;
        default:
          throw new GameError('Unknown message: ' + msg.type);
      }
      checkTimers(this.room, now);
      await this.save();
      this.send(ws, role, { re: msg.id });
      this.broadcast(ws);
    } catch (err) {
      ws.send(JSON.stringify({ type: 'error', re: msg.id, error: err.message, fatal: !!err.fatal, reason: err.reason || null }));
    }
  }

  async webSocketClose(ws) {
    await this.disconnected(ws);
  }

  async webSocketError(ws) {
    await this.disconnected(ws);
  }

  async disconnected(ws) {
    const role = roleOf(ws);
    safeClose(ws, 1000, 'Closed');
    if (!role) return;
    // Ignore the close of a connection that was already replaced.
    if (this.ctx.getWebSockets().some((other) => other !== ws && roleOf(other) === role)) return;
    await this.load();
    if (!this.room || this.room.status === 'closed') return;
    setConnected(this.room, role, false, Date.now());
    await this.save();
    this.broadcast(ws);
  }

  async alarm() {
    await this.load();
    if (!this.room) return;
    const result = checkTimers(this.room, Date.now());
    if (result === 'expired') {
      for (const ws of this.ctx.getWebSockets()) safeClose(ws, 4002, 'Battle expired');
      await this.ctx.storage.deleteAll();
      this.room = null;
      return;
    }
    await this.save();
    if (result === 'changed') this.broadcast(null);
  }

  /** Sends the room as `role` sees it. */
  send(ws, role, extra = {}) {
    try {
      ws.send(JSON.stringify({ type: 'state', ok: true, ...extra, serverTime: Date.now(), ...view(this.room, role, Date.now()) }));
    } catch {
      // The connection is going away; its close handler will run.
    }
  }

  /** Pushes the current room to every connected player except `skip`. */
  broadcast(skip) {
    for (const ws of this.ctx.getWebSockets()) {
      const role = roleOf(ws);
      if (ws === skip || !role) continue;
      if (this.room.status === 'closed') {
        try {
          ws.send(JSON.stringify({ type: 'error', error: 'The host closed this battle.', fatal: true, reason: 'closed' }));
        } catch {
          // Already gone.
        }
        ws.serializeAttachment({ role: null });
        safeClose(ws, 4003, 'Battle closed');
      } else {
        this.send(ws, role);
      }
    }
  }
}

/** One object for the whole server: push tokens and who may invite whom. */
export class Directory extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sender = null;
  }

  get store() {
    return this.ctx.storage;
  }

  async register(body) {
    try {
      await register(this.store, body || {}, Date.now());
      return { ok: true, invitations: !!serviceAccountText(this.env) };
    } catch (err) {
      return { ok: false, error: err.message, reason: err.reason || null };
    }
  }

  async pair(a, b) {
    await pair(this.store, a, b);
  }

  /** Sends an invitation or a reply to the partner's phone. */
  async notify(kind, body) {
    try {
      if (!serviceAccountText(this.env)) {
        throw new DirectoryError(
          'Invitations are not set up on this server: add the Firebase key as a Secret named ' +
            'FIREBASE_SERVICE_ACCOUNT under the Worker\'s Settings → Variables and Secrets.',
          'push_not_configured',
        );
      }
      const message = await prepareMessage(this.store, kind, body || {});
      this.sender ??= createPushSender(JSON.parse(serviceAccountText(this.env)));
      const sent = await this.sender.send(message.token, message.data);
      if (sent.unregistered) {
        await this.store.delete(message.targetKey);
        throw new DirectoryError('Your partner needs to open the app once to receive invitations again.', 'not_registered');
      }
      if (!sent.ok) throw new DirectoryError('Could not send the notification: ' + sent.error, 'push_failed');
      return { ok: true };
    } catch (err) {
      return { ok: false, error: err.message, reason: err.reason || 'push_failed' };
    }
  }
}

function roleOf(ws) {
  return ws.deserializeAttachment()?.role || null;
}

function safeClose(ws, code, reason) {
  try {
    ws.close(code, reason);
  } catch {
    // Already closed.
  }
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}
