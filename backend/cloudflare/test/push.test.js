import test from 'node:test';
import assert from 'node:assert/strict';
import { createPushSender, serviceAccountText, signJwt } from '../src/push.js';
import { DirectoryError, pair, prepareMessage, register } from '../src/directory.js';

async function serviceAccount() {
  const { privateKey, publicKey } = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify'],
  );
  const der = Buffer.from(await crypto.subtle.exportKey('pkcs8', privateKey)).toString('base64');
  const pem = `-----BEGIN PRIVATE KEY-----\n${der.match(/.{1,64}/g).join('\n')}\n-----END PRIVATE KEY-----\n`;
  return { publicKey, account: { client_email: 'bot@demo.iam.gserviceaccount.com', private_key: pem, project_id: 'demo-project' } };
}

function decode(part) {
  return JSON.parse(Buffer.from(part, 'base64url').toString());
}

test('JWTs are signed with the service account key', async () => {
  const { publicKey, account } = await serviceAccount();
  const jwt = await signJwt({ iss: account.client_email, iat: 1 }, account.private_key);
  const [header, claims, signature] = jwt.split('.');
  assert.deepEqual(decode(header), { alg: 'RS256', typ: 'JWT' });
  assert.equal(decode(claims).iss, account.client_email);
  const valid = await crypto.subtle.verify(
    'RSASSA-PKCS1-v1_5', publicKey, Buffer.from(signature, 'base64url'), new TextEncoder().encode(`${header}.${claims}`),
  );
  assert.ok(valid);
});

test('sends FCM v1 data messages and reuses the access token', async () => {
  const { account } = await serviceAccount();
  const calls = [];
  const fakeFetch = async (url, init) => {
    calls.push({ url, init });
    if (url.includes('oauth2')) return Response.json({ access_token: 'tok-1', expires_in: 3600 });
    return Response.json({ name: 'projects/demo-project/messages/1' });
  };
  let clock = 1_000_000;
  const sender = createPushSender(account, fakeFetch, () => clock);

  assert.deepEqual(await sender.send('device-token', { type: 'invite', code: 'ABC123', n: 5 }), { ok: true });
  clock += 10 * 60_000;
  await sender.send('device-token', { type: 'invite_reply' });

  assert.equal(calls.filter((c) => c.url.includes('oauth2')).length, 1, 'access token is cached');
  const tokenRequest = new URLSearchParams(calls[0].init.body);
  assert.equal(tokenRequest.get('grant_type'), 'urn:ietf:params:oauth:grant-type:jwt-bearer');
  assert.equal(decode(tokenRequest.get('assertion').split('.')[1]).scope, 'https://www.googleapis.com/auth/firebase.messaging');

  const send = calls[1];
  assert.equal(send.url, 'https://fcm.googleapis.com/v1/projects/demo-project/messages:send');
  assert.equal(send.init.headers.authorization, 'Bearer tok-1');
  const message = JSON.parse(send.init.body).message;
  assert.equal(message.token, 'device-token');
  assert.deepEqual(message.data, { type: 'invite', code: 'ABC123', n: '5' }, 'data values are strings');
  assert.equal(message.android.priority, 'high');
});

test('reports tokens Firebase no longer knows', async () => {
  const { account } = await serviceAccount();
  const fakeFetch = async (url) => url.includes('oauth2')
    ? Response.json({ access_token: 't', expires_in: 3600 })
    : Response.json({ error: { status: 'NOT_FOUND', message: 'gone', details: [{ errorCode: 'UNREGISTERED' }] } }, { status: 404 });
  const result = await createPushSender(account, fakeFetch).send('old-token', {});
  assert.equal(result.ok, false);
  assert.equal(result.unregistered, true);
});

function memoryStore() {
  const map = new Map();
  return { get: async (k) => map.get(k), put: async (k, v) => void map.set(k, v), delete: async (k) => void map.delete(k) };
}

const A = 'aaaaaaaa-1111-2222-3333-444444444444';
const B = 'bbbbbbbb-1111-2222-3333-444444444444';
const C = 'cccccccc-1111-2222-3333-444444444444';
const TOKEN = 'x'.repeat(40);

test('only partners can invite each other, and only registered phones', async () => {
  const store = memoryStore();
  await register(store, { deviceId: A, token: TOKEN + 'a', name: 'Alice', avatar: 'fox' }, 1);
  await register(store, { deviceId: B, token: TOKEN + 'b', name: 'Bob' }, 1);
  await assert.rejects(prepareMessage(store, 'invite', { from: A, to: B, code: 'ABC123' }), (e) => e.reason === 'not_partner');

  await pair(store, A, B);
  const invite = await prepareMessage(store, 'invite', { from: A, to: B, code: 'abc-123', game: 'ships' });
  assert.equal(invite.token, TOKEN + 'b');
  assert.deepEqual(invite.data, { type: 'invite', fromDevice: A, fromName: 'Alice', fromAvatar: 'fox', code: 'ABC123', game: 'ships' });

  const reply = await prepareMessage(store, 'invite_reply', { from: B, to: A, accepted: false, code: 'ABC123' });
  assert.equal(reply.token, TOKEN + 'a');
  assert.equal(reply.data.accepted, 'false');
  assert.equal(reply.data.fromName, 'Bob');

  await pair(store, A, C);
  await assert.rejects(prepareMessage(store, 'invite', { from: A, to: C, code: 'ABC123' }), (e) => e.reason === 'not_registered');
  await assert.rejects(register(store, { deviceId: 'bad id', token: TOKEN }, 1), DirectoryError);
});

test('the service account is found despite small naming slips', () => {
  assert.equal(serviceAccountText({ FIREBASE_SERVICE_ACCOUNT: ' {"a":1}\n' }), '{"a":1}');
  assert.equal(serviceAccountText({ firebase_service_account: '{}' }), '{}');
  assert.equal(serviceAccountText({ 'FIREBASE SERVICE ACCOUNT': '{}' }), '{}');
  assert.equal(serviceAccountText({ ROOMS: {} }), '');
  assert.equal(serviceAccountText({ FIREBASE_SERVICE_ACCOUNT: {} }), '');
});
