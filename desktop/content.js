(() => {
  if(globalThis.__pageTurner) return;
  const C=PageTurnerCore;
  let config=C.settings(),running=false,generation=0,stopAt=0,timer=0,stopTimer=0,animation=0,reader=null,originalOverflow='',originalPriority='',navigating=false;
  let chosenImage=null,chosenNext=null,picking=null,hints={},message='',savedReader=null,readerSources=[];
  let pulse=0,phaseEnd=0,phaseStarted=0;
  const host=document.createElement('div'); host.id='page-turner-layer';
  host.style.cssText='all:initial!important;position:fixed!important;inset:0!important;z-index:2147483647!important;pointer-events:none!important';
  const shadow=host.attachShadow({mode:'open'});
  const style=document.createElement('style'); style.textContent=`
    :host{color-scheme:dark}*{box-sizing:border-box}.reader{position:absolute;inset:0;background:#101113;overflow:auto;overscroll-behavior:contain;pointer-events:auto;scrollbar-width:none;scroll-behavior:auto!important}
    .reader img{display:block;width:100%;max-width:1200px;height:auto;margin:0 auto;pointer-events:auto}.reader::-webkit-scrollbar{display:none}
    .tools{position:absolute;right:18px;bottom:22px;display:flex;gap:8px;align-items:center;pointer-events:auto;font:13px system-ui,sans-serif}
    button{border:1px solid #72747a;background:#202124;color:#fff;border-radius:50%;width:38px;height:38px;font:18px system-ui;cursor:pointer;opacity:.75}button:hover,button:focus{opacity:1}
    .note{max-width:260px;background:#202124e8;color:#e8e8ea;border-radius:12px;padding:8px 12px;font:12px/1.5 system-ui;pointer-events:none}.note:empty{display:none}.tools.running:not(:hover):not(:focus-within) .note{opacity:0}
    .dot{display:grid;place-items:center;opacity:.6;flex:none}.core{display:block;width:8px;height:8px;border:1px solid #fff;border-radius:50%;background:transparent;pointer-events:none}.running .core{background:#fff}.tools.running .dot{opacity:.48}.tools .dot:hover,.tools .dot:focus{opacity:1}
    .tools.running:not(:hover):not(:focus-within) .close{opacity:0;pointer-events:none}
    .picker{position:absolute;top:24px;left:50%;transform:translateX(-50%);background:#202124;color:white;padding:12px 18px;border-radius:14px;font:14px system-ui;pointer-events:none}
    .frame{position:absolute;border:2px solid #fff;outline:2px solid #202124;pointer-events:none}button:focus-visible{outline:2px solid white;outline-offset:3px}`;
  const sheet=new CSSStyleSheet();sheet.replaceSync(style.textContent);shadow.adoptedStyleSheets=[sheet];
  const tools=document.createElement('div');tools.className='tools';tools.dataset.ptUi='true';
  const note=document.createElement('span');note.className='note';note.setAttribute('role','status');
  const dot=document.createElement('button');dot.className='dot';
  const core=document.createElement('span');core.className='core';core.setAttribute('aria-hidden','true');dot.append(core);
  const close=document.createElement('button');close.className='close';close.textContent='×';
  tools.append(note,dot,close);shadow.append(tools);
  function mount(){if(!host.isConnected)document.documentElement.append(host);}
  function t(){return C.words[C.language(config.language,navigator.languages)];}
  function show(key){message=key||'';tools.classList.toggle('running',running);note.textContent=t()[key]||'';dot.title=t()[running?'pause':'start'];dot.setAttribute('aria-label',dot.title);close.title=t().close;close.setAttribute('aria-label',close.title);}
  const reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');
  function breathe(now){
    if(!running)return;
    const wave=(1-Math.cos(Math.PI*(now-phaseStarted)/1000))/2;
    core.style.transform=reducedMotion.matches?'none':`scale(${.92+.16*wave})`;
    const remaining=Math.max(0,Math.ceil((phaseEnd-now)/1000));
    const label=(t()[message]||'')+(phaseEnd>now?' · '+remaining+' '+t().seconds:'');
    if(note.textContent!==label)note.textContent=label;
    dot.title=t().pause+' · '+label;
    pulse=requestAnimationFrame(breathe);
  }
  function phase(key,seconds=0){show(key);phaseStarted=performance.now();phaseEnd=phaseStarted+seconds*1000;}
  function state(extra={}){return chrome.runtime.sendMessage({type:'pt:state',running,settings:config,hints,...extra}).catch(()=>{});}
  function visible(node){const r=node.getBoundingClientRect(),s=getComputedStyle(node);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';}
  function query(selector){try{return selector?document.querySelector(selector):null;}catch{return null;}}
  function selector(node){if(node.id)return '#'+CSS.escape(node.id);const parts=[];for(let n=node;n&&n!==document.body&&parts.length<6;n=n.parentElement){const tag=n.localName;if(!tag)break;const peers=Array.from(n.parentElement?.children||[]).filter(e=>e.localName===tag);parts.unshift(tag+':nth-of-type('+(peers.indexOf(n)+1)+')');}return parts.join(' > ');}
  function imageCandidates(){
    const nodes=Array.from(document.images).filter(img=>visible(img)&&img.complete&&img.naturalWidth>=240&&img.naturalHeight>=200);
    const scored=nodes.map(node=>{const r=node.getBoundingClientRect();return {node,r,score:node.naturalWidth*node.naturalHeight*(node.id==='img'?3:node.closest('main,article')?1.5:1)};}).filter(x=>x.r.width>=180&&x.r.height>=120&&x.r.width/x.r.height<5&&getComputedStyle(x.node).position!=='fixed').sort((a,b)=>b.score-a.score);
    const manual=chosenImage?.isConnected?chosenImage:query(hints.image);
    if(manual?.tagName==='IMG'&&manual.complete&&manual.naturalWidth)return [manual];
    const best=scored[0];if(!best)return [];
    const container=best.node.closest('main,article,.reader,.reading-content')||best.node.parentElement;
    const group=container===document.body||container===document.documentElement?[best.node]:scored.filter(x=>container.contains(x.node)&&x.r.width>=best.r.width*.8&&x.r.width<=best.r.width*1.2&&x.node.naturalHeight>=300).map(x=>x.node);
    return (group.length?group:[best.node]).sort((a,b)=>a===b?0:a.compareDocumentPosition(b)&Node.DOCUMENT_POSITION_FOLLOWING?-1:1).slice(0,100);
  }
  function fingerprint(){const imgs=imageCandidates();return location.href+'|'+(imgs.length?imgs.map(i=>i.currentSrc||i.src).join('|'):(document.querySelector('main,article')?.textContent||'').slice(0,4000));}
  function sourceTop(){const image=imageCandidates()[0];return image?image.getBoundingClientRect().top+scrollY:0;}
  function originalPosition(y){
    for(let i=readerSources.length-1;i>=0;i--){const {source,copy}=readerSources[i];if(!source.isConnected||y<copy.offsetTop&&i>0)continue;const r=source.getBoundingClientRect();return Math.max(0,scrollY+r.top+(y-copy.offsetTop)*r.height/Math.max(1,copy.height));}
    return sourceTop()+y;
  }
  function removeReader(){
    if(!reader)return;
    const y=reader.scrollTop,max=Math.max(0,reader.scrollHeight-reader.clientHeight),sig=fingerprint(),pageTop=originalPosition(y);
    reader.remove();reader=null;if(originalOverflow)document.documentElement.style.setProperty('overflow',originalOverflow,originalPriority);else document.documentElement.style.removeProperty('overflow');
    readerSources=[];window.scrollTo({top:pageTop,behavior:'instant'});
    savedReader={y,max,sig,pageY:scrollY};
  }
  async function focusImages(token,reset=false){
    if(!config.focus)return true;
    const images=imageCandidates();if(!images.length){pause('noImage');return false;}
    if(!reader){originalOverflow=document.documentElement.style.getPropertyValue('overflow');originalPriority=document.documentElement.style.getPropertyPriority('overflow');document.documentElement.style.setProperty('overflow','hidden','important');reader=document.createElement('div');reader.className='reader';reader.setAttribute('aria-label',t().focus);shadow.prepend(reader);}
    const frame=reader;frame.replaceChildren();
    readerSources=[];
    const loaded=images.map(source=>new Promise(resolve=>{const image=document.createElement('img');image.alt='';image.referrerPolicy=source.referrerPolicy;let timeout;const done=ok=>{clearTimeout(timeout);resolve(ok);};image.onload=()=>done(true);image.onerror=()=>done(false);timeout=setTimeout(()=>done(false),10000);image.src=source.currentSrc||source.src;frame.append(image);readerSources.push({source,copy:image});if(image.complete)done(image.naturalWidth>0);}));
    const ok=(await Promise.all(loaded)).every(Boolean);
    if(!running||token!==generation)return false;
    if(!ok){pause('loading');return false;}
    const first=readerSources[0];
    frame.scrollTop=reset?0:savedReader&&savedReader.sig===fingerprint()&&Math.abs(scrollY-savedReader.pageY)<3?savedReader.y:Math.max(0,(scrollY-sourceTop())*first.copy.height/Math.max(1,first.source.getBoundingClientRect().height));
    return true;
  }
  function scroller(){return reader||document.scrollingElement;}
  function position(){return scroller()?.scrollTop||0;}
  function maximum(){const s=scroller();return Math.max(0,(s?.scrollHeight||0)-(reader?reader.clientHeight:innerHeight));}
  function move(y){if(reader)reader.scrollTop=y;else window.scrollTo({top:y,behavior:'instant'});}
  function pause(reason='',notify=true){running=false;navigating=false;generation++;clearTimeout(timer);clearTimeout(stopTimer);cancelAnimationFrame(animation);cancelAnimationFrame(pulse);core.style.transform='none';removeReader();show(reason);if(notify)state();}
  async function start(value=config,resumeStopAt=null,resumeHints=null,reset=false){
    pause('',false);mount();config=C.settings(value);if(resumeHints)hints=resumeHints;
    running=true;generation++;stopAt=resumeStopAt||Date.now()+1800000;const token=generation;
    if(document.hidden||Date.now()>=stopAt){pause('switched');return;}
    stopTimer=setTimeout(()=>pause('limit'),Math.max(0,stopAt-Date.now()));
    phase('loading');pulse=requestAnimationFrame(breathe);
    if(reset){savedReader=null;move(0);}
    if(reset&&config.focus){const until=Date.now()+15000;while(guard(token)&&!imageCandidates().length&&Date.now()<until)await new Promise(resolve=>setTimeout(resolve,250));if(!guard(token))return;}
    if(!await focusImages(token,reset))return;
    if(!running||token!==generation)return;show('waiting');await state();schedule(token);
  }
  function schedule(token){clearTimeout(timer);if(!running||token!==generation)return;phase('waiting',config.interval);timer=setTimeout(()=>act(token),config.interval*1000);}
  function guard(token){if(!running||token!==generation)return false;if(document.hidden){pause('switched');return false;}if(Date.now()>=stopAt){pause('limit');return false;}return true;}
  async function act(token){
    if(!guard(token))return;
    if(config.mode==='tap')return turn(token);
    const max=maximum();
    if(position()>=max-2){if(config.mode==='scroll'){pause('ended');return;}return turn(token);}
    const from=position(),target=C.target(from,max,config.kind),fraction=target<max?0.5:1,started=performance.now();phase('reading',config.duration);
    const animate=now=>{if(!guard(token))return;const ratio=Math.min(1,(now-started)/(config.duration*1000));const goal=maximum()*fraction;move(from+(goal-from)*ratio);if(ratio<1)animation=requestAnimationFrame(animate);else schedule(token);};
    animation=requestAnimationFrame(animate);
  }
  function nextButton(){
    const picked=chosenNext?.isConnected?chosenNext:query(hints.next);
    if(picked&&visible(picked)&&!picked.disabled)return picked;
    const matches=new Map();
    for(const node of document.querySelectorAll('a,button,[role="button"]')){
      if(!visible(node)||node.disabled||node.getAttribute('aria-disabled')==='true')continue;
      const label=node.getAttribute('aria-label')||node.getAttribute('title')||node.querySelector('img')?.alt||node.textContent;
      if(node.rel!=='next'&&!C.nextLabel(label))continue;
      const url=node.tagName==='A'?C.safeNext(node.href,location.href):null;
      if(node.tagName==='A'&&!url)continue;
      matches.set(url||node,node);
    }
    return matches.size===1?matches.values().next().value:null;
  }
  async function turn(token){
    const next=nextButton();if(!next){pause('noNext');return;}
    const href=next.closest('a')?.href,expected=href?C.safeNext(href,location.href):location.href;
    if(href&&!expected){pause('noNext');return;}
    const before=fingerprint(),previousImages=imageCandidates().map(i=>({node:i,src:i.currentSrc||i.src}));navigating=true;phase('turning');await state({expected});if(!guard(token))return;
    next.click();const startTime=Date.now();let candidate='',stable=0;
    const check=async()=>{
      if(!guard(token))return;
      const current=fingerprint();
      const images=imageCandidates(),pending=previousImages.some(({node})=>node.isConnected&&!node.complete);
      const changedImage=images.some(i=>!previousImages.some(p=>p.src===(i.currentSrc||i.src)));
      const ready=!pending&&(!config.focus||images.length>0&&(previousImages.length===0||changedImage));
      if(current!==before&&ready){if(candidate!==current){candidate=current;stable=Date.now();}else if(Date.now()-stable>=500){navigating=false;await state();if(!guard(token))return;savedReader=null;if(!config.focus)move(0);if(await focusImages(token,true))schedule(token);return;}}
      else {candidate='';stable=0;}
      if(Date.now()-startTime>15000){pause('unchanged');return;}
      timer=setTimeout(check,250);
    };
    timer=setTimeout(check,250);
  }
  let pickerLabel=null,pickerFrame=null;
  function cancelPick(){picking=null;pickerLabel?.remove();pickerFrame?.remove();pickerLabel=pickerFrame=null;}
  function pick(kind){pause();cancelPick();mount();picking=kind;pickerLabel=document.createElement('div');pickerLabel.className='picker';pickerLabel.textContent=t()[kind==='image'?'selectImage':'selectNext'];pickerFrame=document.createElement('div');pickerFrame.className='frame';shadow.append(pickerLabel,pickerFrame);}
  function pickTarget(event){const element=event.composedPath().find(n=>n instanceof Element&&n!==host);return picking==='image'?(element?.closest('img')):element?.closest('a,button,[role="button"]');}
  document.addEventListener('pointermove',event=>{if(!picking)return;const selected=pickTarget(event);if(!selected){pickerFrame.style.display='none';return;}const r=selected.getBoundingClientRect();pickerFrame.style.cssText=`left:${r.left}px;top:${r.top}px;width:${r.width}px;height:${r.height}px`;},true);
  document.addEventListener('click',event=>{if(!picking||event.composedPath().some(n=>n?.dataset?.ptUi))return;event.preventDefault();event.stopImmediatePropagation();const selected=pickTarget(event);if(!selected)return;if(picking==='image'){chosenImage=selected;hints.image=selector(selected);}else{chosenNext=selected;hints.next=selector(selected);}cancelPick();show('selected');},true);
  function intervention(event){
    if(!event.isTrusted)return;
    if(!running&&!picking)return;
    if(event.type==='keydown'&&event.key==='Escape'){event.preventDefault();cancelPick();pause();return;}
    if(event.composedPath().some(n=>n?.dataset?.ptUi))return;
    if(picking){if(event.type==='pointerdown'){event.preventDefault();event.stopImmediatePropagation();}return;}
    if(running){if(reader){event.preventDefault();event.stopImmediatePropagation();}pause();}
  }
  for(const type of ['pointerdown','wheel','keydown','touchstart'])document.addEventListener(type,intervention,{capture:true,passive:false});
  document.addEventListener('visibilitychange',()=>{if(document.hidden&&!navigating)pause('switched');});
  window.addEventListener('pagehide',()=>{if(!navigating)pause('switched');});
  dot.onclick=()=>running?pause():start();close.onclick=()=>{cancelPick();pause();host.remove();};
  chrome.runtime.onMessage.addListener((msg,sender,reply)=>{
    if(!msg||!String(msg.type).startsWith('pt:'))return;
    (async()=>{if(msg.type==='pt:start')await start(msg.settings);else if(msg.type==='pt:resume')await start(msg.settings,msg.stopAt,msg.hints,true);else if(msg.type==='pt:pause')pause();else if(msg.type==='pt:pick-image'||msg.type==='pt:pick-next'){config=C.settings(msg.settings);pick(msg.type==='pt:pick-image'?'image':'next');}return {running,reason:t()[message]||''};})().then(reply,error=>{pause('failed');reply({error:error.message});});return true;
  });
  globalThis.__pageTurner={pause,start,images:imageCandidates,next:nextButton,status:()=>({running,message,reader:!!reader,y:position(),max:maximum()})};
})();
