const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
function client(fetch) {
  const exports = {};
  const code = ts.transpileModule(fs.readFileSync('src/lib/api-client.ts','utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  vm.runInNewContext(code, { exports, fetch, Headers, Response, AbortSignal, Date, process:{env:{}} });
  return exports;
}
function response(data, status=200) { return new Response(JSON.stringify({message:'test',data}),{status}); }
test('catalog remains available without calling session endpoints', async()=>{
  const calls=[];
  const api=client(async(url,options)=>{calls.push({url,options});return response([]);});
  await api.apiRequest('/api/catalog');
  assert.equal(calls.length,1);assert.equal(calls[0].url,'/api/catalog');
  assert.equal(calls[0].options.headers.has('Authorization'),false);
});
test('concurrent protected requests refresh once and send Bearer without cookies',async()=>{
  const calls=[];
  const api=client(async(url,options)=>{calls.push({url,options});return response(url.includes('/refresh')?{accessToken:'verified-jwt',expiresIn:900}:{});});
  await Promise.all([api.apiRequest('/api/auth/me'),api.apiRequest('/api/cart')]);
  assert.equal(calls.filter(c=>c.url.endsWith('/refresh')).length,1);
  for(const c of calls.filter(c=>!c.url.endsWith('/refresh'))){
    assert.equal(c.options.headers.get('Authorization'),'Bearer verified-jwt');assert.equal(c.options.credentials,'omit');
  }
});
test('expired refresh is a 401 and prevents protected request',async()=>{
  const calls=[];const api=client(async url=>{calls.push(url);return response(null,401);});
  await assert.rejects(api.apiRequest('/api/auth/me'), e=>e.status===401);
  assert.deepEqual(calls,['/api/auth/session/refresh']);
});
test('upstream outage stays 503 instead of being reported as anonymous',async()=>{
  const api=client(async()=>response(null,503));
  await assert.rejects(api.apiRequest('/api/auth/me'),e=>e.status===503);
});
test('login JWT is used and a rejected JWT is refreshed once',async()=>{
  let refreshes=0,reads=0;
  const api=client(async(url,options)=>{
    if(url.endsWith('/login'))return response({accessToken:'login-jwt',expiresIn:900});
    if(url.endsWith('/refresh')){refreshes++;return response({accessToken:'new-jwt',expiresIn:900});}
    if(++reads===1)return response(null,401);
    assert.equal(options.headers.get('Authorization'),'Bearer new-jwt');return response({authenticated:true});
  });
  await api.sessionRequest('login',{email:'a@b.cl',password:'test'});
  await api.apiRequest('/api/auth/me');assert.equal(refreshes,1);assert.equal(reads,2);
});
test('client rejects external URLs and traversal before fetch',async()=>{
  const api=client(()=>assert.fail('must not fetch'));
  assert.throws(()=>api.apiUrl('https://attacker.example/api/auth/me'));
  assert.throws(()=>api.apiUrl('/api/../auth'));
});
