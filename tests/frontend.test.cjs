const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function load(file, extras = {}) {
  const errors = []; let unblocked = 0;
  const context = {module:{exports:{}}, navigator:{language:'en-US'}, AbortController, setTimeout, clearTimeout,
    $:{blockUI(){},unblockUI(){unblocked++;}},
    require: name => name.includes('notification') ? {showError: m => errors.push(m)} : {translate:x=>x}, ...extras};
  vm.runInNewContext(fs.readFileSync(`src/main/js/${file}.js`, 'utf8'),context);
  return {api:context.module.exports,errors,unblocked:()=>unblocked};
}
test('English, Lithuanian, Russian and explicit locale formatting never produce undefined',()=>{
  const {api} = load('i18n');
  for (const lang of ['en-US','lt-LT','ru-RU']) { api.setLocale(lang); assert.equal(api.lang,lang.split('-')[0]); assert.equal(typeof api.translate('My kids'),'string'); assert.equal(api.format('hello {}',['world']),'hello world'); }
});
test('HTTP error keeps success callback untouched and releases overlay',async()=>{
  let success=false; const h=load('util',{fetch:async()=>({ok:false,status:403,text:async()=>'{"message":"Denied"}'})});
  assert.equal(await h.api.fetchWithRedirect('/test',{}, {block:true,success:()=>success=true}),undefined);
  assert.equal(success,false); assert.deepEqual(h.errors,['Denied']); assert.equal(h.unblocked(),1);
});
test('HTML error and network rejection are visible, not JSON crashes',async()=>{
  for (const fetch of [async()=>({ok:false,status:502,text:async()=>'<html>gateway</html>'}),async()=>{throw Error('offline');}]) {
    const h=load('util',{fetch}); await h.api.fetchWithRedirect('/test',{}, {block:true}); assert.equal(h.errors.length,1); assert.equal(h.unblocked(),1);
  }
});
test('bounded request aborts and releases UI',async()=>{
  const h=load('util',{fetch:(_,opts)=>new Promise((resolve,reject)=>opts.signal.addEventListener('abort',()=>reject(Object.assign(Error(),{name:'AbortError'}))))});
  await h.api.fetchWithRedirect('/test',{}, {timeout:5,block:true}); assert.match(h.errors[0],/timed out/); assert.equal(h.unblocked(),1);
});
test('204 has explicit success result without JSON parse',async()=>{
  let called=false;const h=load('util',{fetch:async()=>({ok:true,status:204})}); assert.equal(await h.api.fetchWithRedirect('/test',{}, {success:()=>called=true}),true);assert.equal(called,true);
});
