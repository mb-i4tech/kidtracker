// DOM integration of actual HTML, jQuery, Bootstrap and localization; no site navigation.
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const {JSDOM}=require('jsdom');
(async()=>{for(const language of ['lt-LT','en-US','ru-RU']){
const d=new JSDOM(fs.readFileSync('src/main/resources/static/index.html','utf8'),{runScripts:'outside-only',url:'http://fixture.invalid/'}),w=d.window;
Object.defineProperty(w.navigator,'language',{value:language});
w.eval(fs.readFileSync(require.resolve('jquery/dist/jquery.js'),'utf8'));
w.eval(fs.readFileSync(require.resolve('bootstrap/dist/js/bootstrap.js'),'utf8'));
w.document.querySelectorAll('.modal').forEach(x=>x.classList.remove('fade'));
const c=d.getInternalVMContext();c.module={exports:{}};vm.runInContext('(function(){'+fs.readFileSync('src/main/js/i18n.js','utf8')+'})()',c);const i18n=c.module.exports;
c.module={exports:{}};c.$=w.jQuery;c.require=n=>n.includes('i18n')?i18n:n.includes('util')?{fetchWithRedirect:async u=>u.includes('/config')?{publicHost:'watch.example.test',publicPort:9001}:[]} :n.includes('moment')?()=>({format:()=>'',fromNow:()=>''}):{};
vm.runInContext(fs.readFileSync('src/main/js/device.js','utf8'),c);
c.module.exports({subscribe:()=>({unsubscribe(){}}),send(){},userId:'fixture'});
await new Promise(r=>setTimeout(r,30));assert(w.document.querySelector('#show-user-devices').classList.contains('show'));
w.jQuery('#user-devices-add').trigger('click');await new Promise(r=>setTimeout(r,30));
assert(w.document.querySelector('#edit-device').classList.contains('show'),'Add dialog visible '+language);assert(!w.document.querySelector('#device-deviceid').disabled);assert(w.document.querySelector('#edit-device .alert-info').textContent.includes('123456'));
assert(w.document.querySelector('#device-deviceid').getAttribute('inputmode')==='numeric');
assert(w.document.querySelector('#input-token-input').getAttribute('maxlength')==='6');
if(language==='lt-LT') assert(w.document.querySelector('#edit-device-add').textContent==='Gauti patvirtinimo kodą');
console.log('PASS actual Bootstrap My kids → + opens editable dialog:',language);w.close();
}})().catch(e=>{console.error(e);process.exitCode=1});
