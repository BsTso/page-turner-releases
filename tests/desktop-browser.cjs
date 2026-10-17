// Integration fixture only: a copied extension gets access to localhost, never to real browsing data.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const words=require('../desktop/common.js').words.en;
const root=path.resolve(__dirname,'..'),work=path.join(root,'verification','desktop-browser-'+Date.now());
fs.mkdirSync(work,{recursive:true});const extension=path.join(work,'extension');fs.cpSync(path.join(root,'desktop'),extension,{recursive:true});
const manifest=JSON.parse(fs.readFileSync(path.join(extension,'manifest.json')));manifest.host_permissions=['http://127.0.0.1/*'];fs.writeFileSync(path.join(extension,'manifest.json'),JSON.stringify(manifest));
let checks=0;const check=(value,reason)=>{assert.ok(value,reason);checks++;};
const server=http.createServer((req,res)=>{
  if(req.url==='/blank.svg'){res.writeHead(200,{'Content-Type':'image/svg+xml'});res.end('<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/>');return;}
  if(req.url==='/wallpaper.svg'){res.writeHead(200,{'Content-Type':'image/svg+xml'});res.end('<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="4000"><rect width="1600" height="4000" fill="#1e2b3c"/></svg>');return;}
  if(req.url.startsWith('/identity')){
    const second=req.url.includes('page=2'),failed=req.url.includes('fail=1');
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});
    res.end(`<!doctype html><style>body{margin:0}main{width:800px;margin:auto}#img{width:800px;height:2400px}#wallpaper{width:100%;position:absolute;top:0;z-index:-1}</style>${second?'<img id="wallpaper" src="/wallpaper.svg">':''}<main><img id="img" src="${second?'/blank.svg':'/comic1.svg'}"><a href="/identity?page=2${failed?'&fail=1':''}">Next page</a></main>${second&&!failed?'<script>setTimeout(()=>document.querySelector("#img").src="/comic2.svg",1200)</script>':''}`);return;
  }
  if(req.url.startsWith('/point-image')||req.url.startsWith('/point-staged')){
    const unchanged=req.url.includes('unchanged=1'),staged=req.url.startsWith('/point-staged');
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});
    const update=unchanged?'':staged?"const turn=window.turns;document.querySelector('#img').src='/comic2.svg?turn='+turn;setTimeout(()=>document.querySelector('#img').src='/comic3.svg?turn='+turn,2100);":"const turn=window.turns;history.replaceState({},'', '/point-image?page='+(turn+1));setTimeout(()=>document.querySelector('#img').src='/comic'+(turn+1)+'.svg',2600);";
    res.end(`<!doctype html><title>Image click fixture</title><style>body{margin:0}main{width:800px;margin:auto}#img{display:block;width:800px}</style><main><img id="img" src="/comic1.svg"></main><script>window.turns=0;window.turnEvents=[];document.querySelector('#img').onclick=e=>{window.turns++;window.turnEvents.push({type:e.type,x:e.clientX,y:e.clientY,target:e.target.tagName});${update}};</script>`);return;
  }
  if(req.url.startsWith('/point-navigation')){
    const page=Number(new URL(req.url,'http://localhost').searchParams.get('page')||1);
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});
    res.end(`<!doctype html><title>Div click navigation fixture</title><style>body{margin:0}main{width:800px;margin:auto}#img{width:800px;display:block}#zone{position:fixed;left:25%;top:25%;width:25%;height:30%}</style><main><img id="img" src="/comic${page}.svg"></main><div id="zone"></div><script>window.turns=0;document.querySelector('#zone').onclick=()=>{window.turns++;location.href='/point-navigation?page=${page+1}';};</script>`);return;
  }
  if(req.url.startsWith('/reader-site')){
    const page=Number(new URL(req.url,'http://localhost').searchParams.get('page')||1);
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});
    // Public reader behavior, recreated with local SVGs: history first, replace the
    // whole linked image after a response, then occasionally use full navigation.
    res.end(`<!doctype html><title>Linked image replacement fixture</title><style>body{margin:0}main{width:800px;margin:auto}#img{width:800px;display:block}</style><main id="i3"><a href="/reader-site?page=${page+1}" onclick="return advance(${page+1})"><img id="img" src="/comic${page}.svg"></a></main><script>window.siteTurns=Number(sessionStorage.siteTurns||0);if(${page}===1){sessionStorage.siteTurns=0;window.siteTurns=0;}function advance(next){sessionStorage.siteTurns=++window.siteTurns;if(next>=3){location.href='/reader-site?page='+next;return false;}history.pushState({},'', '/reader-site?page='+next);setTimeout(()=>{scrollTo(0,0);document.querySelector('#i3').innerHTML='<a href="/reader-site?page=3" onclick="return advance(3)"><img id="img" src="/slow.svg?site=2"></a>';},800);return false;}</script>`);return;
  }
  if(req.url.startsWith('/slow.svg')){setTimeout(()=>{res.writeHead(200,{'Content-Type':'image/svg+xml'});res.end('<svg xmlns="http://www.w3.org/2000/svg" width="800" height="2600"><rect width="800" height="2600" fill="#c4d0d9"/></svg>');},1400);return;}
  if(req.url.startsWith('/comic')){const n=req.url.includes('2')?2:1;res.writeHead(200,{'Content-Type':'image/svg+xml'});res.end(`<svg xmlns="http://www.w3.org/2000/svg" width="800" height="2400"><rect width="800" height="2400" fill="${n===1?'#dad5cc':'#c4d0d9'}"/><path d="M0 800H800M0 1600H800" stroke="#333" stroke-width="8"/><text x="100" y="160" font-size="80">PAGE ${n}</text><text x="100" y="1200" font-size="80">HALFWAY</text><text x="100" y="2300" font-size="80">END</text></svg>`);return;}
  res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});
  res.end(`<!doctype html><html><head><meta charset="utf-8"><title>Comic reading fixture</title><style>body{margin:0;background:#ddd;font:22px sans-serif}.ad{height:220px;background:#ff8866}main{width:800px;margin:auto}#img{width:800px;display:block}a{display:block;padding:30px}aside{position:fixed;right:0;top:200px;background:#f80;padding:25px}</style></head><body><div class="ad">ADVERTISING</div><aside>SIDEBAR</aside><main><img id="img" src="/comic1.svg"><a id="next" href="/reader?page=2">Next page</a></main><div class="ad">FOOTER</div><script>window.turns=0;if(location.pathname==='/delayed')document.querySelector('#next').href='/delayed?page=1';document.querySelector('#next').onclick=e=>{if(location.pathname==='/delayed'){e.preventDefault();window.turns++;history.pushState({},'', '/delayed?page='+window.turns);setTimeout(()=>document.querySelector('#img').src='/slow.svg?n='+window.turns,800);return;}if(!location.search&&window.turns===0){e.preventDefault();window.turns++;document.querySelector('#img').src='/comic2.svg';document.querySelector('#next').href='/reader?page=3';}};if(location.search)window.addEventListener('load',()=>scrollTo(0,1500));</script></body></html>`);
});
(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base=`http://127.0.0.1:${server.address().port}`;
  const context=await chromium.launchPersistentContext(path.join(work,'profile'),{channel:'chromium',headless:true,viewport:{width:1100,height:800},args:[`--disable-extensions-except=${extension}`,`--load-extension=${extension}`]});
  try{
    let worker=context.serviceWorkers()[0]||await context.waitForEvent('serviceworker');
    const extensionId=new URL(worker.url()).host;
    const page=await context.newPage();await page.goto(base+'/reader');await page.bringToFront();
    if(process.env.PAGETURNER_LAYOUT_ONLY!=='1'){
    const tabId=await worker.evaluate(async url=>(await chrome.tabs.query({url:url+'/*'}))[0].id,base);
    const command=async(type,settings)=>worker.evaluate(async({tabId,type,settings})=>{await chrome.scripting.executeScript({target:{tabId},files:['common.js','content.js']});return chrome.tabs.sendMessage(tabId,{type,settings});},{tabId,type,settings});
    const status=()=>page.locator('#page-turner-layer').evaluate(host=>{const r=host.shadowRoot.querySelector('.reader');return {focus:!!r,y:r?.scrollTop||0,max:r?r.scrollHeight-r.clientHeight:0,note:host.shadowRoot.querySelector('.note').textContent};});
    const options={interval:1,duration:1,kind:'halves',mode:'combined',focus:true,language:'en',turnMethod:'button'};
    await command('pt:start',options);check((await status()).focus,'focused reading layer exists');check(await page.locator('#page-turner-layer').evaluate(host=>host.shadowRoot.querySelectorAll('.reader img').length)===1,'only main comic is copied');
    await page.waitForTimeout(2200);let mid=await status();check(Math.abs(mid.y-mid.max/2)<5,'stops at half of the actual content range');await page.waitForTimeout(400);check(Math.abs((await status()).y-mid.y)<5,'halfway pause is maintained');
    await page.screenshot({path:path.join(work,'reader.png')});
    await page.waitForFunction(()=>window.turns===1,null,{timeout:7000});await page.waitForFunction(()=>document.querySelector('#page-turner-layer')?.shadowRoot.querySelector('.reader img')?.src.includes('/comic2.svg'),null,{timeout:7000});check((await status()).focus,'AJAX page turn retains clean reader');check((await status()).y<10,'new page starts at top');
    await page.mouse.wheel(0,200);await page.waitForTimeout(150);check(!(await status()).focus,'manual wheel restores original page');check(await page.evaluate(()=>document.documentElement.style.overflow)==='','original overflow restored');
    await page.reload();await command('pt:start',{...options,kind:'whole',duration:3});await page.waitForTimeout(1400);await page.mouse.click(400,350);await page.waitForTimeout(100);check(!(await status()).focus,'manual click pauses a running animation');const y=await page.evaluate(()=>scrollY);await page.waitForTimeout(400);check(Math.abs(await page.evaluate(()=>scrollY)-y)<3,'no ghost scrolling after pause');check(await page.evaluate(()=>window.turns)===0,'pause click does not accidentally turn page');
    await page.reload();await command('pt:start',{...options,kind:'whole',mode:'tap'});await page.waitForFunction(()=>window.turns===1);await page.waitForURL('**/reader?page=3',{timeout:10000});await page.waitForSelector('#page-turner-layer',{timeout:10000});await page.waitForTimeout(400);check((await status()).focus,'same-origin full navigation resumes the authorized session');check((await status()).y===0,'full navigation ignores the site restoring an old scroll position');
    await command('pt:pause',options);await command('pt:pick-image',options);await page.locator('#img').click({position:{x:100,y:100}});check((await status()).note===words.selected,'manual image picker selects without navigating');
    await page.keyboard.press('Escape');await command('pt:pause',options);
    await command('pt:start',options);const other=await context.newPage();await other.goto('about:blank');await page.waitForTimeout(200);check(!(await status()).focus,'switching tabs pauses and restores the page');await page.bringToFront();await page.waitForTimeout(150);check(!(await status()).focus,'returning to a tab does not start without consent');await other.close();
    await page.goto(base+'/reader');await command('pt:start',{...options,interval:30});
    await page.locator('#page-turner-layer').evaluate(host=>host.shadowRoot.querySelector('.reader').scrollTop=1100);
    await command('pt:pause');
    const originalY=await page.evaluate(()=>scrollY);
    check(Math.abs(originalY-1020)<5,'pausing maps the enlarged image back to the same content in the original page');
    await command('pt:start',{...options,interval:30});check(Math.abs((await status()).y-1100)<5,'resuming the same image preserves the reading position');
    const coreStyle=()=>page.locator('#page-turner-layer').evaluate(host=>getComputedStyle(host.shadowRoot.querySelector('.core')).transform);
    const scaleA=await coreStyle();await page.waitForTimeout(700);check(await coreStyle()!==scaleA,'the dot itself breathes while waiting');
    check((await status()).note.includes('s'),'hover status includes the countdown unit');
    await page.locator('#page-turner-layer .dot').hover();await page.screenshot({path:path.join(work,'running-dot.png')});
    await page.emulateMedia({reducedMotion:'reduce'});await page.waitForTimeout(100);check(await coreStyle()==='none','reduced motion stops breathing without stopping reading');await page.emulateMedia({reducedMotion:'no-preference'});
    await command('pt:pause');const pausedStyle=await coreStyle();await page.waitForTimeout(150);check(await coreStyle()===pausedStyle&&pausedStyle==='none','paused dot stays still');
    await page.goto(base+'/delayed');await command('pt:start',{...options,mode:'tap'});
    await page.waitForFunction(()=>window.turns===1);await page.waitForTimeout(1000);check((await status()).focus&&(await status()).note===words.turning,'slow image loading retains the reader and reports turning: '+JSON.stringify(await status()));
    await page.waitForFunction(()=>document.querySelector('#page-turner-layer')?.shadowRoot.querySelector('.reader img')?.src.includes('/slow.svg'),null,{timeout:7000});
    check((await status()).y===0,'delayed AJAX image starts at the top after it loads');await command('pt:pause');
    await page.goto(base+'/reader');await command('pt:start',{...options,focus:false,mode:'tap'});await page.evaluate(()=>scrollTo(0,1000));await page.waitForFunction(()=>window.turns===1);await page.waitForFunction(()=>scrollY===0,null,{timeout:7000});check(await page.evaluate(()=>scrollY)===0,'original-page mode also resets after AJAX turns');await command('pt:pause');
    await page.goto(base+'/identity');await command('pt:start',{...options,mode:'tap'});
    await page.waitForURL('**/identity?page=2');await page.waitForSelector('#page-turner-layer .reader img');
    check(await page.locator('#page-turner-layer .reader img').getAttribute('src')===base+'/comic2.svg','full navigation waits for the known main image instead of showing a large wallpaper or placeholder');
    check((await status()).y===0,'delayed main image starts from the top');await command('pt:pause');
    await page.goto(base+'/reader');await command('pt:start',{...options,mode:'scroll',kind:'whole',duration:3});
    const motion=await page.evaluate(()=>new Promise((resolve,reject)=>{
      const timeout=setTimeout(()=>reject(new Error('Scrolling did not start and finish within 15 seconds')),15000);
      const rows=[];let start;
      function sample(now){const layer=document.querySelector('#page-turner-layer')?.shadowRoot,r=layer?.querySelector('.reader'),note=layer?.querySelector('.note')?.textContent||'';
        if(note.startsWith('Scrolling')){start??=now;rows.push({ms:now-start,y:r.scrollTop});}
        else if(start!==undefined){clearTimeout(timeout);resolve({rows,ms:now-start,y:r.scrollTop,max:r.scrollHeight-r.clientHeight});return;}
        requestAnimationFrame(sample);
      }requestAnimationFrame(sample);
    }));
    const speed=(lo,hi)=>{const rows=motion.rows.filter(r=>r.ms>=lo&&r.ms<=hi),a=rows[0],b=rows.at(-1);return (b.y-a.y)/(b.ms-a.ms);};
    check(speed(100,350)<speed(1200,1700)*.65,'scroll starts gradually rather than jumping to cruising speed');
    check(speed(2650,2890)<speed(1200,1700)*.65,'scroll slows down before the end rather than stopping at cruising speed');
    check(motion.y===motion.max&&Math.abs(motion.ms-3000)<250,'easing reaches the intended end within the chosen duration');
    fs.writeFileSync(path.join(work,'motion.json'),JSON.stringify(motion));await command('pt:pause');
    await page.goto(base+'/identity?fail=1');await command('pt:start',{...options,mode:'tap'});
    await page.waitForURL('**/identity?page=2&fail=1');
    await page.waitForFunction(text=>document.querySelector('#page-turner-layer')?.shadowRoot.querySelector('.note')?.textContent===text,words.noImage,{timeout:18000});
    check(!(await status()).focus,'missing main image pauses and restores the original page instead of scrolling a wallpaper');
    console.log(`Desktop browser: ${checks} existing behavior checks passed`);
    // A saved page coordinate may be on any image or div: no semantic next button is required.
    const pointOptions={...options,kind:'whole',turnMethod:'point'};
    await page.goto(base+'/point-image');await command('pt:pick-next',pointOptions);
    const imagePoint={x:825,y:400};await page.mouse.click(imagePoint.x,imagePoint.y);
    check((await status()).note===words.selected,'an ordinary image can be picked as the page-turn location');
    check(await page.evaluate(()=>window.turns)===0,'picking an image location does not activate the page');
    await command('pt:start',pointOptions);
    await page.waitForFunction(()=>window.turns===1,null,{timeout:8000});
    const firstTurn=await page.evaluate(()=>window.turnEvents[0]);
    check(firstTurn.target==='IMG'&&Math.abs(firstTurn.x-imagePoint.x)<=1&&Math.abs(firstTurn.y-imagePoint.y)<=1,'saved point clicks the original image through the clean reader');
    await page.waitForTimeout(1700);
    check(await page.evaluate(()=>window.turns)===1,'a URL change while the next image loads cannot cause a second click');
    check((await status()).focus&&(await status()).note===words.turning,'the old reader stays visible while the next image loads');
    await page.waitForFunction(()=>document.querySelector('#page-turner-layer')?.shadowRoot.querySelector('.reader img')?.src.includes('/comic2.svg'),null,{timeout:7000});
    check((await status()).y===0,'the image reached by a picked point starts at the top');await command('pt:pause');
    await page.goto(base+'/point-image?fresh=1');await command('pt:start',{...pointOptions,mode:'tap'});
    await page.waitForFunction(()=>window.turns===1,null,{timeout:5000});
    check(await page.evaluate(()=>window.turnEvents[0].target)==='IMG','a new document on the same site remembers the chosen image point');await command('pt:pause');
    await page.waitForTimeout(700);check(await page.evaluate(()=>window.turns)===1,'pausing during image loading cancels any later page turn');
    await command('pt:start',{...pointOptions,mode:'tap',interval:30});await page.waitForTimeout(1500);
    check(await page.evaluate(()=>window.turns)===1,'resuming an unconfirmed turn waits for the original click instead of clicking again');
    await page.waitForFunction(()=>document.querySelector('#page-turner-layer')?.shadowRoot.querySelector('.reader img')?.src.includes('/comic2.svg'),null,{timeout:7000});
    check((await status()).y===0,'resuming an in-flight turn waits for the new image before restoring the reader');await command('pt:pause');
    await page.goto(base+'/point-image?unchanged=1');await command('pt:start',{...pointOptions,mode:'tap'});
    await page.waitForFunction(()=>window.turns===1,null,{timeout:5000});
    await page.waitForFunction(text=>document.querySelector('#page-turner-layer')?.shadowRoot.querySelector('.note')?.textContent===text,words.unchanged,{timeout:18000});
    check(await page.evaluate(()=>window.turns)===1,'an unchanged page receives one click only, including after the loading timeout');
    check(!(await status()).focus,'an unchanged page pauses and restores the original page');
    await command('pt:start',{...pointOptions,mode:'tap',interval:30});await page.waitForTimeout(1500);
    check(await page.evaluate(()=>window.turns)===1,'resuming after the timeout never retries the unconfirmed click');await command('pt:pause');
    await page.evaluate(()=>{document.querySelector('#img').src='/comic2.svg';});
    await page.waitForFunction(()=>document.querySelector('#img').complete&&document.querySelector('#img').naturalWidth>240);
    await command('pt:start',{...pointOptions,mode:'tap',interval:30});
    await page.waitForFunction(()=>document.querySelector('#page-turner-layer')?.shadowRoot.querySelector('.reader img')?.src.includes('/comic2.svg'),null,{timeout:7000});
    check((await status()).y===0&&await page.evaluate(()=>window.turns)===1,'a manual page change resolves the pending turn without an extra click');await command('pt:pause');
    await page.goto(base+'/point-staged');await command('pt:start',{...pointOptions,duration:3});
    await page.waitForFunction(()=>window.turns===1,null,{timeout:8000});
    await page.waitForFunction(()=>document.querySelector('#page-turner-layer')?.shadowRoot.querySelector('.reader img')?.src.includes('/comic3.svg'),null,{timeout:8000});
    check((await status()).y<5&&await page.evaluate(()=>window.turns)===1,'a final image arriving after an intermediate image restarts reading at the top before another click');
    await page.waitForTimeout(2000);check(await page.evaluate(()=>window.turns)===1,'the final replacement image receives reading time before the next page turn');await command('pt:pause');
    await page.goto(base+'/point-navigation');await command('pt:pick-next',pointOptions);
    await page.mouse.click(385,320);check((await status()).note===words.selected,'a div without button semantics can be picked');
    check(await page.evaluate(()=>window.turns)===0,'picking a div does not follow its navigation handler');
    await command('pt:start',{...pointOptions,mode:'tap'});await page.waitForURL('**/point-navigation?page=2',{timeout:7000});
    await page.waitForSelector('#page-turner-layer .reader img',{timeout:7000});
    check((await status()).focus&&(await status()).y===0,'a coordinate-triggered same-origin navigation resumes at the top');
    await page.waitForURL('**/point-navigation?page=3',{timeout:7000});await page.waitForSelector('#page-turner-layer .reader img',{timeout:7000});
    check((await status()).focus,'the saved div point survives full navigation for the next page');await command('pt:pause');
    console.log(`Desktop browser: ${checks} checks passed including saved-position regressions`);
    await page.goto(base+'/reader-site');await command('pt:pick-next',pointOptions);await page.mouse.click(825,400);
    check(await page.evaluate(()=>window.siteTurns)===0,'picking a linked image does not activate its AJAX handler');
    await command('pt:start',pointOptions);await page.waitForFunction(()=>window.siteTurns===1,null,{timeout:8000});
    await page.waitForTimeout(1700);check(await page.evaluate(()=>window.siteTurns)===1&&(await status()).note===words.turning,'replacing the linked image node waits for its new image load without a second click');
    await page.waitForFunction(()=>document.querySelector('#page-turner-layer')?.shadowRoot.querySelector('.reader img')?.src.includes('/slow.svg?site=2'),null,{timeout:7000});
    check((await status()).y===0,'the linked image rebuilt after the response starts at the top');
    await page.waitForURL('**/reader-site?page=3',{timeout:8000});await page.waitForSelector('#page-turner-layer .reader img',{timeout:7000});
    check((await status()).focus&&(await status()).y===0&&await page.evaluate(()=>window.siteTurns)===2,'a reader that falls back from AJAX to full navigation advances exactly one more page and resumes at the top');await command('pt:pause');
    }
    // Open the real extension popup document for layout checks in all shipped languages.
    const popup=await context.newPage();await popup.goto(`chrome-extension://${extensionId}/popup.html`);await popup.setViewportSize({width:340,height:850});
    for(const language of ['zh','en','ja']){await popup.locator('#more').click();await popup.locator('#language').selectOption(language);await popup.waitForTimeout(100);await popup.locator('#more').click();await popup.screenshot({path:path.join(work,`popup-${language}.png`)});check(await popup.locator('body').evaluate(e=>e.scrollWidth<=340),'popup does not overflow horizontally in '+language);const height=await popup.locator('body').evaluate(e=>e.getBoundingClientRect().height);check(height<=600,`main popup fits browser height in ${language} (${height}px)`);}
    console.log(`Desktop browser: ${checks} checks passed\nScreenshots: ${work}`);
  } finally {await context.close();server.close();}
})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
