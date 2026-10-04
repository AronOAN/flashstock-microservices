const { test } = require('node:test');
const assert = require('node:assert/strict');


const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { runInNewContext } = require('node:vm');
const ts = require('typescript');
const source = readFileSync(join(__dirname,'../src/lib/auth-backend.ts'),'utf8');
const result = ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022},reportDiagnostics:true});

const setCookies = [
  '__Host-flashstock-issued-access=eyAAA; Path=/; Max-Age=900; Secure; HttpOnly; SameSite=Lax',
  '__Host-flashstock-issued-refresh=eyBBB; Path=/; Max-Age=604800; Secure; HttpOnly; SameSite=Lax'
];
function setup({enabled=true, cookies=[], mock=async()=>({status:200,ok:true,headers:{getSetCookie:()=>setCookies}}), secret=Buffer.alloc(32,8).toString('base64')}={}) {
  const output={};const calls=[];
  const env={FLASHSTOCK_ISSUED_TOKENS_ENABLED: enabled?'true':'false',FLASHSTOCK_BFF_SHARED_SECRET:secret,FLASHSTOCK_API_BASE_URL:'https://api.flashstock.example'};
  const context={exports:output,process:{env},URL,Buffer,Headers,AbortSignal,console,require:(id)=>{
    if(id==='server-only') return {};
    if(id==='next/headers') return {cookies:async()=>({getAll:()=>cookies})};
    if(id==='@/lib/flashstock-session') return {siteOrigin:()=> 'https://shop.flashstock.example'};
    if(id==='next/server') return {};
    throw new Error('Unexpected import: '+id);
  },fetch:async(url,params)=>{calls.push({url,params});return mock(url,params)}};
  runInNewContext(result.outputText,context);
  return {api:output,calls};
}
test('TypeScript helper transpiles',()=>{assert.equal(result.diagnostics?.filter(d=>d.category===ts.DiagnosticCategory.Error).length,0)});
test('off by default: never calls backend',async()=>{const {api,calls}=setup({enabled:false});assert.equal((await api.callOwned('refresh')).status,503);assert.equal(calls.length,0)});
test('exchange needs validated Cognito bearer, backend origin and server secret',async()=>{
 const {api,calls}=setup();const r=await api.callOwned('exchange','a'.repeat(42));assert.equal(r.ok,true);assert.equal(r.cookies.length,2);
 assert.equal(calls[0].url,'https://api.flashstock.example/api/auth/browser/exchange');
 assert.equal(calls[0].params.headers.get('Authorization'),'Bearer '+'a'.repeat(42));
 assert.equal(calls[0].params.headers.get('Origin'),'https://shop.flashstock.example');
 assert.equal(calls[0].params.method,'POST');assert.equal(calls[0].params.body,undefined);
});
test('refresh only forwards HttpOnly cookie; never takes raw browser body',async()=>{
 const {api,calls}=setup({cookies:[{name:'__Host-flashstock-issued-refresh',value:'eySecret'},{name:'other',value:'ignored'}]});
 assert.equal((await api.callOwned('refresh','c'.repeat(48))).ok,true);
 assert.equal(calls[0].params.headers.get('Authorization'),'Bearer '+'c'.repeat(48));
 assert.equal(calls[0].params.headers.get('Cookie'),'__Host-flashstock-issued-refresh=eySecret');
 assert.equal(calls[0].params.body,undefined);
});
test('authorize requires no new Set-Cookie and a 204',async()=>{
 const {api}=setup({mock:async()=>({ok:true,status:204,headers:{getSetCookie:()=>[]}})});
 assert.equal((await api.callOwned('authorize','x'.repeat(40))).ok,true);
});
test('rejects domain-injection in upstream cookie',async()=>{
 const {api}=setup({mock:async()=>({ok:true,status:200,headers:{getSetCookie:()=>[setCookies[0]+'; Domain=attacker.test',setCookies[1]]}})});
 assert.equal((await api.callOwned('exchange','x'.repeat(40))).ok,false);
});
test('missing BFF secret fails closed',async()=>{
 const {api,calls}=setup({secret:'invalid'});await assert.rejects(api.callOwned('exchange','x'.repeat(40)),/Missing BFF server secret/);
 assert.equal(calls.length,0);
});
