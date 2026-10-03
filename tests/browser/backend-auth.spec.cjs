const {test,expect}=require('@playwright/test');
const base=process.env.KIDTRACKER_TEST_BASE_URL;
test.skip(!base,'Requires explicitly started isolated synthetic backend');
test('real backend login, CSRF, admin creation and authorization negatives',async({page,browser})=>{
  const username=process.env.KIDTRACKER_TEST_ADMIN||'synthetic-admin';
  const password=process.env.KIDTRACKER_TEST_PASSWORD||'synthetic-test-only-password';
  await page.goto(`${base}/login`);
  await page.locator('input[name=username]').fill(username);
  await page.locator('input[name=password]').fill(password);
  await Promise.all([page.waitForURL(`${base}/`),page.locator('button[type=submit]').click()]);
  const connected=await page.evaluate(async()=>{
    const csrf=await fetch('/api/csrf').then(r=>r.json());
    return new Promise(resolve=>{
      const socket=new WebSocket(`${location.protocol==='https:'?'wss:':'ws:'}//${location.host}/device/websocket`);
      const timer=setTimeout(()=>{socket.close();resolve('timeout');},5000);
      socket.onopen=()=>socket.send(`CONNECT\naccept-version:1.2\nhost:${location.host}\n${csrf.headerName}:${csrf.token}\n\n\0`);
      socket.onmessage=event=>{clearTimeout(timer);socket.close();resolve(event.data.split('\n')[0]);};
      socket.onerror=()=>{clearTimeout(timer);resolve('socket-error');};
    });
  });
  expect(connected).toBe('CONNECTED');
  const result=await page.evaluate(async()=>{
    const csrf=await fetch('/api/csrf').then(r=>r.json());
    const user=await fetch('/api/user/info').then(r=>r.json());
    const without=await fetch('/api/user/info',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({...user,name:'must-not-save'})});
    const withToken=await fetch('/api/user/info',{method:'PUT',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify({...user,phone:user.phone||'+37060000001',name:'Synthetic admin'})});
    const legacy=await fetch('/api/user/token/not-a-token');
    const created=await fetch('/api/admin/user',{method:'POST',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify({credentials:{username:'synthetic-parent',password:'synthetic-parent-password'},name:'Synthetic parent',phone:'+37060000002',admin:false})});
    return {without:without.status,withToken:withToken.status,legacy:legacy.status,created:created.status};
  });
  expect(result.without).toBe(403);expect(result.withToken).toBe(204);expect(result.legacy).toBe(405);expect([200,201,204]).toContain(result.created);
  const other=await browser.newContext();const parent=await other.newPage();
  await parent.goto(`${base}/login`);await parent.locator('input[name=username]').fill('synthetic-parent');await parent.locator('input[name=password]').fill('synthetic-parent-password');await Promise.all([parent.waitForURL(`${base}/`),parent.locator('button[type=submit]').click()]);
  const denied=await parent.evaluate(async()=>{const c=await fetch('/api/csrf').then(r=>r.json());return {admin:(await fetch('/api/admin/user',{method:'POST',headers:{'Content-Type':'application/json',[c.headerName]:c.token},body:'{}'})).status,device:(await fetch('/api/device/unowned/config')).status};});
  expect(denied.admin).toBe(403);expect(denied.device).toBe(403);await other.close();
});
