const {test,expect}=require('@playwright/test');
const fs=require('fs');
const browserify=require('browserify');
const path=require('path');
let bundle;
test.beforeAll(async()=>{bundle=await new Promise((resolve,reject)=>browserify().require(path.resolve('src/main/js/device.js'),{expose:'devices'}).bundle((e,b)=>e?reject(e):resolve(b.toString())));});
async function setup(page,locale,config={publicHost:'watch.example.test',publicPort:9001}) {
  await page.route('**/*',route=>route.abort()); // Hermetic: never contact production, CDNs, or a watch.
  await page.setContent(fs.readFileSync('src/main/resources/static/index.html','utf8').replace(/<script[\s\S]*?<\/script>/g,'').replace(/<link[^>]*>/g,''));
  await page.addStyleTag({path:require.resolve('bootstrap/dist/css/bootstrap.min.css')});
  await page.addStyleTag({path:'src/main/resources/static/css/index.css'});
  await page.addScriptTag({path:require.resolve('jquery/dist/jquery.js')});
  await page.addScriptTag({path:require.resolve('bootstrap/dist/js/bootstrap.js')});
  await page.evaluate(({locale,config})=>{
    Object.defineProperty(navigator,'language',{value:locale});
    document.querySelectorAll('.modal').forEach(e=>e.classList.remove('fade'));
    $.blockUI=()=>{}; $.unblockUI=()=>{};
    window.requests=[]; window.assigned=false; window.scenario="normal";
    window.fetch=async(url,options)=>{
      requests.push({url,method:options&&options.method});
      if(url==='/api/csrf') return {ok:true,json:async()=>({headerName:'X-CSRF-TOKEN',token:'fixture-csrf'})};
      if(options && options.method==='POST' && options.headers['X-CSRF-TOKEN']!=='fixture-csrf') throw Error('Missing CSRF');
      let body=[];let status=200;
      if(window.scenario==='delayed' && url==='/api/user/kid') await new Promise(r=>setTimeout(r,750));
      if(url.endsWith('/config'))body=config;
      else if(url==='/api/user/kid'){status=window.scenario==='offline'?400:202;body=null;}
      else if(url==='/api/user/kids/info')body=window.assigned?[{deviceId:'7909371454',name:'Test child',users:[]}]:[];
      else if(url.includes('/token/')){status=window.scenario==='limited'?429:window.scenario==='expired'?400:(url.endsWith('/1234')||url.endsWith('/123456'))?204:403;body=status>=400?{message:'Incorrect or expired token'}:null;if(status===204)window.assigned=true;}
      return {ok:status<400,status,headers:{get:()=>status===429?'2':null},redirected:false,text:async()=>body===null?'':JSON.stringify(body)};
    };
  },{locale,config});
  await page.addScriptTag({content:bundle});
  await page.evaluate(()=>{require('devices')(null);});
  await expect(page.locator('#show-user-devices')).toBeVisible();
  await page.click('#user-devices-add');
  await expect(page.locator('#edit-device')).toBeVisible();
}
for(const locale of ['en-US','lt-LT','ru-RU'])test(`My kids + and ownership token retry in ${locale}`,async({page})=>{
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await setup(page,locale);
  await expect(page.locator('#edit-device .alert-info')).toContainText('pw,123456,ip,watch.example.test,9001#');
  await page.fill('#device-deviceid','7909371454');await page.fill('#device-name','Test child');
  await page.click('#edit-device-add');await expect(page.locator('#input-token')).toBeVisible();
  await page.fill('#input-token-input','999999');await page.click('#input-token-execute');
  await expect(page.locator('#input-token-input')).toHaveAttribute('aria-invalid','true');
  await expect(page.locator('#input-token')).toBeVisible();await expect(page.locator('#edit-device')).toBeVisible();
  await page.fill('#input-token-input','1234');await page.click('#input-token-execute');
  await expect(page.locator('#input-token')).not.toBeVisible();await expect(page.locator('#edit-device')).not.toBeVisible();
  expect(errors).toEqual([]);
});
test('missing public endpoint does not fabricate LAN SMS instructions',async({page})=>{
  await setup(page,'en-US',{});await expect(page.locator('#edit-device .alert-info')).toContainText('not configured');await expect(page.locator('#edit-device .alert-info')).not.toContainText('pw,');
});
test('cancelled token keeps add dialog open without success',async({page})=>{
  await setup(page,'en-US');await page.fill('#device-deviceid','7909371454');await page.fill('#device-name','Kid');await page.click('#edit-device-add');await expect(page.locator('#input-token')).toBeVisible();await page.click('#input-token-close');await expect(page.locator('#edit-device')).toBeVisible();
  await expect(page.locator('#device-deviceid')).toHaveValue('7909371454'); await expect(page.locator('#device-name')).toHaveValue('Kid');
});

test('mobile Lithuanian guide is passive and rejects private endpoint',async({page},testInfo)=>{
  await page.setViewportSize({width:390,height:844});
  await setup(page,'lt-LT',{publicHost:'192.168.15.11',publicPort:8001});
  await expect(page.locator('#edit-device .alert-info')).not.toContainText('pw,');
  await expect(page.locator('#edit-device-add')).toHaveText('Gauti patvirtinimo kodą');
  expect(await page.evaluate(()=>requests.filter(r=>r.method==='POST'))).toEqual([]);
  await page.screenshot({path:testInfo.outputPath('synthetic-lt-onboarding.png'),fullPage:true});
});
test('validation and offline request preserve child fields',async({page})=>{
  await setup(page,'en-US');
  await page.click('#edit-device-add');
  await expect(page.locator('#device-name')).toHaveAttribute('aria-invalid','true');
  await page.fill('#device-name','Test child'); await page.fill('#device-deviceid','7909371454');
  await page.evaluate(()=>window.scenario='offline'); await page.click('#edit-device-add');
  await expect(page.locator('#edit-device-status')).toContainText('Request failed');
  await expect(page.locator('#input-token')).not.toBeVisible();
  await expect(page.locator('#device-name')).toHaveValue('Test child');
});
test('delayed response does not fabricate accepted or online state',async({page})=>{
  await setup(page,'en-US'); await page.fill('#device-name','Test child'); await page.fill('#device-deviceid','7909371454');
  await page.evaluate(()=>window.scenario='delayed');await page.click('#edit-device-add');
  await expect(page.locator('#edit-device-add')).toBeDisabled();
  await expect(page.locator('#edit-device-status')).toContainText('not yet confirmed');
  await expect(page.locator('#input-token')).toBeVisible();
});
test('expired code and Retry-After preserve values; explicit resend then six digit success',async({page},testInfo)=>{
  await setup(page,'lt-LT');await page.fill('#device-name','Test child');await page.fill('#device-deviceid','7909371454');await page.click('#edit-device-add');
  await expect(page.locator('#input-token')).toBeVisible();await page.fill('#input-token-input','123456');
  await page.evaluate(()=>window.scenario='expired');await page.click('#input-token-execute');
  await expect(page.locator('#input-token-input')).toHaveValue('123456');
  await expect(page.locator('#input-token-status')).toContainText('nebegalioja');
  await page.evaluate(()=>window.scenario='limited');await page.click('#input-token-execute');
  await expect(page.locator('#input-token-execute')).toBeDisabled();await expect(page.locator('#input-token-resend')).toBeDisabled();
  await page.screenshot({path:testInfo.outputPath('synthetic-lt-retry-after.png'),fullPage:true});
  await expect(page.locator('#input-token-resend')).toBeEnabled({timeout:4000});
  await page.evaluate(()=>window.scenario='normal');await page.click('#input-token-resend');
  await expect(page.locator('#input-token-status')).toContainText('Naujo kodo');
  await expect(page.locator('#input-token-input')).toHaveValue('123456');
  await page.click('#input-token-execute');await expect(page.locator('#edit-device')).not.toBeVisible();
  expect(await page.evaluate(()=>requests.filter(r=>r.url==='/api/user/kid').length)).toBe(2);
});

test('successful token without assignment evidence does not close onboarding',async({page})=>{
  await setup(page,'en-US');
  await page.evaluate(()=>{ const original=window.fetch;window.fetch=async(url,options)=>{
    const response=await original(url,options);
    if(url.includes('/token/'))window.assigned=false;
    return response;
  };});
  await page.fill('#device-name','Test child');await page.fill('#device-deviceid','7909371454');await page.click('#edit-device-add');
  await expect(page.locator('#input-token')).toBeVisible();await page.fill('#input-token-input','123456');await page.click('#input-token-execute');
  await expect(page.locator('#edit-device')).toBeVisible();await expect(page.locator('#edit-device-status')).toContainText('Assignment is not confirmed');
});
