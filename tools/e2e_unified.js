// Unified login through the real page and the real server (memory driver): one box, Manage / My work, owner, no overflow.
const SC='/tmp/claude-0/-home-claude-attendance/c5ef49d5-bebf-535f-b8a1-989bbdbd5897/scratchpad/firebase/scaffold/functions';
const {chromium}=require('/opt/node-tools/node_modules/playwright');
const assert=require('assert'), fs=require('fs'), crypto=require('crypto');
const {MemoryDriver}=require(SC+'/src/drivers/memory'); const {GasBridge}=require(SC+'/src/gasBridge');
const APP='file://'+(process.env.APP||'/home/claude/attendance/index.html');
const H=p=>crypto.createHash('sha256').update('att##sys77!'+p).digest('hex');
const D='2026-11-02'; let clock=Date.parse(D+'T10:00:00+05:30');
const b=new GasBridge({driver:new MemoryDriver(),source:fs.readFileSync(SC+'/src/backend.gs','utf8'),secret:'CHANGE_THIS_SECRET',tokenKey:'k'.repeat(32),now:()=>new Date(clock),mailer:{send:async()=>{}},pusher:{send:async()=>{}}});
const call=x=>b.handle(Object.assign({secret:'CHANGE_THIS_SECRET'},x));
let n=0; const ok=m=>{console.log('ok  ',m);n++;};
const setEmp=async(id,u)=>{ const r=(await b.exec('rows','Employees')).find(x=>x.id===id); await b.exec('setCells','Employees',r._row,u); };
(async()=>{
  await call({action:'saveAdmin',creds:{username:'boss',passHash:H('Pass1234')}});
  const TK=(await call({action:'login',kind:'manager',username:'boss',passHash:H('Pass1234')})).data.token;
  for(const [id,nm,dep] of [['E1','Asha','Sales'],['E2','Bela','Sales'],['E3','Chet','Ops']]){ assert((await call({action:'addEmployee',token:TK,emp:{id,name:nm,passHash:H(id),department:dep}})).ok); await call({action:'acceptTracking',id}); }
  await b.exec('addRow','Managers',{id:'SA9',name:'The Owner',username:'owner',passHash:H('Owner@1234'),role:'super',departments:'',active:'1'});
  await setEmp('E1',{email:'asha@x.com',roles:'dept_admin',mgrDepts:'Sales'}); await setEmp('E2',{email:'bela@x.com'});
  const br=await chromium.launch();
  const mk=async(w,h)=>{ const ctx=await br.newContext({viewport:{width:w,height:h},isMobile:w<600,hasTouch:w<600,geolocation:{latitude:26.9,longitude:75.7,accuracy:10},permissions:['geolocation'],locale:'en-IN',timezoneId:'Asia/Kolkata'});
    await ctx.route(/cloudfunctions\.net/,async rt=>{ if(rt.request().method()==='OPTIONS') return rt.fulfill({status:204,headers:{'access-control-allow-origin':'*','access-control-allow-headers':'*'}}); const res=await b.handle(JSON.parse(rt.request().postData())); await rt.fulfill({status:200,contentType:'application/json',headers:{'access-control-allow-origin':'*'},body:JSON.stringify(res)}); });
    await ctx.route(/nominatim|openstreetmap|bigdatacloud|cdnjs|gstatic/,rt=>rt.abort()); await ctx.clock.install({time:new Date(clock)}); await ctx.clock.resume();
    const p=await ctx.newPage(); p.on('pageerror',e=>{console.log('PAGEERR',e.message); process.exitCode=1;}); p.on('dialog',d=>d.dismiss()); await p.goto(APP); await p.waitForTimeout(1200); return p; };
  const noScroll=async(p,m)=>{ const o=await p.evaluate(()=>[document.documentElement.scrollWidth,innerWidth]); assert(o[0]<=o[1]+1,m+' overflow '+o); };
  const signIn=async(p,who,pw)=>{ await p.fill('#loginId',who); await p.fill('#loginPass',pw); await p.click('#loginEmployee >> text=Sign In'); await p.waitForTimeout(2500); };
  const active=p=>p.evaluate(()=>document.querySelector('.screen.active').id);

  const p=await mk(320,700);
  assert(await p.isVisible('#loginId')&&!(await p.isVisible('#oldRoles'))); ok('sign-in shows one box; the old role buttons are tucked away');
  await noScroll(p,'login 320'); await p.screenshot({path:'u_login320.png'}); ok('sign-in: no horizontal scroll at 320 px');
  await signIn(p,'asha@x.com','wrong'); assert(/Incorrect/.test(await p.locator('#loginErr').innerText())&&(await active(p))==='loginScreen'); ok('a wrong password shows one error');
  await signIn(p,' ASHA@x.com ','E1'); assert((await active(p))==='homeScreen'); assert(await p.isVisible('#hmManage')); ok('dept admin signs in by email (any case) and lands on My work with a Manage button');
  await noScroll(p,'home 320');
  await p.click('#hmManage'); await p.waitForTimeout(2500); assert((await active(p))==='adminScreen'&&await p.isVisible('#adminMyWork')); ok('Manage opens the admin panel with My work');
  await noScroll(p,'admin 320'); await p.screenshot({path:'u_admin320.png'});
  await p.click('#adminMyWork'); await p.waitForTimeout(2500); assert((await active(p))==='homeScreen'); ok('My work returns to the employee home');
  await p.reload(); await p.waitForTimeout(2000); assert((await active(p))==='homeScreen'&&await p.isVisible('#hmManage')); ok('after a reload Manage is still offered');
  await p.click('#hmManage'); await p.waitForTimeout(500); await p.fill('#mgrPw','E1'); await p.click('#fmSheet button.btn-primary'); await p.waitForTimeout(2500); console.log(await active(p), await p.evaluate(()=>[typeof currentEmp, !!document.getElementById('fmSheet'), (document.getElementById('mgrErr')||{}).textContent, !!_mgr, !!_tok])); assert((await active(p))==='adminScreen'); ok('the password prompt brings the manage panel back');
  await p.evaluate(()=>{ leaveManage(); }); await p.waitForTimeout(2000);
  await p.evaluate(()=>logout()); await signIn(p,'bela@x.com','E2'); assert((await active(p))==='homeScreen'&&!(await p.isVisible('#hmManage'))); ok('a plain staff member signs in by email and sees no Manage');
  await p.evaluate(()=>logout()); await signIn(p,'owner','Owner@1234'); assert((await active(p))==='adminScreen'); ok("the owner's username lands directly in the admin panel");
  await p.evaluate(()=>{ endManagerSession=()=>{}; logout(); });
  const d=await mk(1280,800); assert(await d.isVisible('.lg-side')); await noScroll(d,'login 1280'); await d.screenshot({path:'u_login1280.png'}); ok('desktop: welcome panel beside the card, no horizontal scroll');
  console.log(n+' checks'); await br.close();
})().catch(e=>{console.error('FAIL',e.message);process.exit(1);});
