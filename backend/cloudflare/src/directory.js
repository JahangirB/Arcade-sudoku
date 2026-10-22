// Who can be invited: each phone's push token, and which phones have played together.
// Plain functions over a key/value store so they can be tested without Cloudflare;
// the Directory Durable Object in index.js provides the store.

import { cleanDeviceId, cleanName, cleanAvatar, GAMES, normalizeCode } from './room.js';

export class DirectoryError extends Error {
  constructor(message, reason) {
    super(message);
    this.reason = reason;
  }
}

function pairKey(a, b) {
  return 'pair:' + [a, b].sort().join(':');
}

/** Remembers a phone's push token (and name, for notification text). */
export async function register(store, { deviceId, token, name, avatar }, now) {
  const id = cleanDeviceId(deviceId);
  if (!id) throw new DirectoryError('Missing device id.', 'bad_request');
  if (typeof token !== 'string' || token.length < 20 || token.length > 4096) {
    throw new DirectoryError('Missing push token.', 'bad_request');
  }
  await store.put('device:' + id, { token, name: cleanName(name), avatar: cleanAvatar(avatar), updatedAt: now });
}

/** Two phones that played in the same room may invite each other from then on. */
export async function pair(store, a, b) {
  const x = cleanDeviceId(a);
  const y = cleanDeviceId(b);
  if (x && y && x !== y) await store.put(pairKey(x, y), true);
}

/**
 * Builds the push message for an invitation or a reply and returns { token, data }.
 * kind: 'invite' (needs code and game) or 'invite_reply' (needs accepted).
 */
export async function prepareMessage(store, kind, { from, to, code, game, accepted }) {
  const fromId = cleanDeviceId(from);
  const toId = cleanDeviceId(to);
  if (!fromId || !toId) throw new DirectoryError('Missing device id.', 'bad_request');
  if (!(await store.get(pairKey(fromId, toId)))) {
    throw new DirectoryError('You can only invite someone you have played with on this server.', 'not_partner');
  }
  const target = await store.get('device:' + toId);
  if (!target) {
    throw new DirectoryError('Your partner has not turned on invitations yet (they need the latest app).', 'not_registered');
  }
  const sender = (await store.get('device:' + fromId)) || {};
  const data = { type: kind, fromDevice: fromId, fromName: sender.name || 'Your partner', fromAvatar: sender.avatar || '' };
  if (kind === 'invite') {
    data.code = normalizeCode(code);
    data.game = GAMES.includes(game) ? game : 'sudoku';
  } else {
    data.accepted = accepted ? 'true' : 'false';
    data.code = code ? normalizeCode(code) : '';
  }
  return { token: target.token, data, targetKey: 'device:' + toId };
}
