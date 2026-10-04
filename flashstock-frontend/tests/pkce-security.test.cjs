const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { randomBytes } = require('node:crypto');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');



// Run the actual Next.js helper without reaching Cognito or exposing credentials.
const source = readFileSync(require('node:path').join(__dirname, '../src/lib/cognito-oauth.ts'), 'utf8');
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS,
  target: ts.ScriptTarget.ES2022 } }).outputText;

function loadHelper(fetchMock = async () => { throw new Error('Unexpected token exchange'); }) {
  const exports = {};
  const environment = { ...process.env, FLASHSTOCK_SESSION_KEY: randomBytes(32).toString('base64'),
    COGNITO_DOMAIN_URL: 'https://flashstock-dev-aron.auth.us-east-1.amazoncognito.com' };
  const context = { exports, Buffer, URL, URLSearchParams, Date, process: { env: environment },
    fetch: fetchMock, AbortSignal, require: id => {
      if (id === '@/lib/cognito-password') return { cognitoConfig: () => ({
        clientId: 'testclient', endpoint: 'https://cognito-idp.us-east-1.amazonaws.com/' }) };
      if (id === '@/lib/flashstock-session') return { siteOrigin: () => 'https://flashstock.example',
        oauthCookieName: () => '__Host-flashstock-oauth', cookieOptions: () => ({}) };
      return require(id);
    } };
  runInNewContext(js, context, { filename: 'cognito-oauth.ts' });
  return { helper: exports, environment };
}

test('S256 challenge matches the published RFC 7636 example', () => {
  const { helper } = loadHelper();
  assert.equal(helper.challengeFor('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'),
    'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
});

test('PKCE state is random, sealed, time-limited, and checked exactly', () => {
  const { helper } = loadHelper();
  const first = helper.newPkceAttempt();
  assert.notEqual(first.state, helper.newPkceAttempt().state);
  assert.equal(first.verifier.length, 43);
  const cookie = helper.sealPkce(first);
  assert.equal(JSON.stringify(helper.openPkce(cookie)), JSON.stringify(first));
  assert.equal(helper.matchesState(first.state, first.state), true);
  assert.equal(helper.matchesState(first.state, helper.newPkceAttempt().state), false);
  const index = Math.floor(cookie.length / 2);
  const changed = cookie[index] === 'A' ? 'B' : 'A';
  assert.equal(helper.openPkce(cookie.slice(0, index) + changed + cookie.slice(index + 1)), null);
  assert.equal(helper.openPkce(helper.sealPkce({ ...first, expiresAt: Date.now() - 1 })), null);
});

test('rejects a Cognito domain in another region or with a malicious path', () => {
  const { helper, environment } = loadHelper();
  assert.equal(helper.oauthConfig().redirectUri, 'https://flashstock.example/auth/callback');
  environment.COGNITO_DOMAIN_URL = 'https://flashstock-dev-aron.auth.eu-west-1.amazoncognito.com';
  assert.throws(() => helper.oauthConfig());
  environment.COGNITO_DOMAIN_URL = 'https://flashstock-dev-aron.auth.us-east-1.amazoncognito.com.attacker.test';
  assert.throws(() => helper.oauthConfig());
});

test('exchanges the code with its verifier only over Cognito HTTPS POST', async () => {
  let call;
  const { helper } = loadHelper(async (url, options) => {
    call = { url, options };
    return { ok: true, json: async () => ({ token_type: 'Bearer', access_token: 'test-access',
      refresh_token: 'test-refresh', expires_in: 900 }) };
  });
  const result = await helper.exchangeCode('single-use-code', 'my-verifier');
  assert.equal(result.AuthenticationResult.AccessToken, 'test-access');
  assert.equal(call.url, 'https://flashstock-dev-aron.auth.us-east-1.amazoncognito.com/oauth2/token');
  assert.equal(call.options.method, 'POST');
  assert.equal(call.options.body.get('grant_type'), 'authorization_code');
  assert.equal(call.options.body.get('code_verifier'), 'my-verifier');
  assert.equal(call.options.body.get('redirect_uri'), 'https://flashstock.example/auth/callback');
});
