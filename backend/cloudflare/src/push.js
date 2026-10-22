// Sends push notifications through Firebase Cloud Messaging (FCM HTTP v1).
//
// The host stores a Firebase service account key (the JSON file from the Firebase console)
// in the Worker secret FIREBASE_SERVICE_ACCOUNT. We sign a short-lived JWT with its private
// key, trade it for an OAuth access token, and use that to send data messages.

const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';

/**
 * @param serviceAccount parsed service account JSON ({ client_email, private_key, project_id })
 * @param fetchFn fetch to use (injectable for tests)
 * @param now clock in ms (injectable for tests)
 */
export function createPushSender(serviceAccount, fetchFn = fetch, now = () => Date.now()) {
  let cached = null; // { token, expiresAt }

  async function accessToken() {
    if (cached && cached.expiresAt - 60_000 > now()) return cached.token;
    const iat = Math.floor(now() / 1000);
    const assertion = await signJwt(
      { iss: serviceAccount.client_email, scope: SCOPE, aud: TOKEN_URL, iat, exp: iat + 3600 },
      serviceAccount.private_key,
    );
    const res = await fetchFn(TOKEN_URL, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }).toString(),
    });
    const body = await res.json();
    if (!res.ok || !body.access_token) {
      throw new Error('Firebase refused the service account: ' + (body.error_description || body.error || res.status));
    }
    cached = { token: body.access_token, expiresAt: now() + (body.expires_in || 3600) * 1000 };
    return cached.token;
  }

  /**
   * Sends a high-priority data message (all values strings) to one device.
   * Returns { ok: true } or { ok: false, unregistered } when the token is no longer valid.
   */
  async function send(deviceToken, data) {
    const url = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`;
    const res = await fetchFn(url, {
      method: 'POST',
      headers: { authorization: `Bearer ${await accessToken()}`, 'content-type': 'application/json' },
      body: JSON.stringify({
        message: {
          token: deviceToken,
          data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v ?? '')])),
          // High priority wakes the phone so the invitation shows at once.
          android: { priority: 'high', ttl: '600s' },
        },
      }),
    });
    if (res.ok) return { ok: true };
    const body = await res.json().catch(() => ({}));
    const code = body?.error?.details?.find?.((d) => d.errorCode)?.errorCode || body?.error?.status;
    return { ok: false, unregistered: code === 'UNREGISTERED' || res.status === 404, error: body?.error?.message || String(res.status) };
  }

  return { send };
}

/** Signs a JWT with RS256 using a PEM "PRIVATE KEY" (PKCS#8), as Google service accounts use. */
export async function signJwt(claims, privateKeyPem) {
  const header = { alg: 'RS256', typ: 'JWT' };
  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const der = Uint8Array.from(atob(privateKeyPem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '')), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${base64url(signature)}`;
}

function base64url(input) {
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : new Uint8Array(input);
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
