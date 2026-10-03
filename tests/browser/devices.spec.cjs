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
  await page.addScriptTag({path:require.resolve('jquery/dist/jquery.js')});
  await page.addScriptTag({path:require.resolve('bootstrap/dist/js/bootstrap.js')});
  await page.evaluate(({locale,config})=>{
    Object.defineProperty(navigator,'language',{value:locale});
    document.querySelectorAll('.modal').forEach(e=>e.classList.remove('fade'));
    $.blockUI=()=>{}; $.unblockUI=()=>{};
    window.requests=[];
    window.fetch=async(url,options)=>{
      requests.push({url,method:options&&options.method});
      let body=[];let status=200;
      if(url.endsWith('/config'))body=config;
      else if(url==='/api/user/kid'){status=202;body=null;}
      else if(url.includes('/token/')){status=url.endsWith('/1234')?204:403;body=status===403?{message:'Incorrect token'}:null;}
      return {ok:status<400,status,redirected:false,text:async()=>body===null?'':JSON.stringify(body)};
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
  await page.fill('#device-deviceid','fixture-only');await page.fill('#device-name','Test child');
  await page.click('#edit-device-add');await expect(page.locator('#input-token')).toBeVisible();
  await page.fill('#input-token-input','wrong');await page.click('#input-token-execute');
  await expect(page.locator('#show-error')).toBeVisible();await page.click('#error-close');
  await expect(page.locator('#input-token')).toBeVisible();await expect(page.locator('#edit-device')).toBeVisible();
  await page.fill('#input-token-input','1234');await page.click('#input-token-execute');
  await expect(page.locator('#input-token')).not.toBeVisible();await expect(page.locator('#edit-device')).not.toBeVisible();
  expect(errors).toEqual([]);
});
test('missing public endpoint does not fabricate LAN SMS instructions',async({page})=>{
  await setup(page,'en-US',{});await expect(page.locator('#edit-device .alert-info')).toContainText('not configured');await expect(page.locator('#edit-device .alert-info')).not.toContainText('pw,');
});
test('cancelled token keeps add dialog open without success',async({page})=>{
  await setup(page,'en-US');await page.fill('#device-deviceid','fixture');await page.fill('#device-name','Kid');await page.click('#edit-device-add');await expect(page.locator('#input-token')).toBeVisible();await page.click('#input-token-close');await expect(page.locator('#edit-device')).toBeVisible();
});
