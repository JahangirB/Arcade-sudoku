// The repository root has its own wrangler.jsonc so Cloudflare can deploy from there too.
// It must describe the same Worker as the one in this folder.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

function readJsonc(url) {
  const text = readFileSync(url, 'utf8')
    .replace(/^\s*\/\/.*$/gm, '')
    .replace(/,(\s*[}\]])/g, '$1');
  const config = JSON.parse(text);
  delete config.$schema;
  return config;
}

test('the root wrangler.jsonc matches this folder\'s', () => {
  const here = readJsonc(new URL('../wrangler.jsonc', import.meta.url));
  const root = readJsonc(new URL('../../../wrangler.jsonc', import.meta.url));
  assert.equal(root.main, 'backend/cloudflare/' + here.main);
  delete here.main;
  delete root.main;
  assert.deepEqual(root, here);
});
