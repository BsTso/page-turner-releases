importScripts('common.js');

const key=id=>'reading:'+id;
const queues=new Map();
const session=id=>chrome.storage.session.get(key(id)).then(values=>values[key(id)]);
const validId=value=>typeof value==='string'&&value.length>0&&value.length<=128;

// Storage reads and writes for one tab must finish in order. Do not await a
// content-script reply here: starting/resuming sends its own state message.
function serial(id,action) {
  const result=(queues.get(id)||Promise.resolve()).catch(()=>{}).then(action);
  const settled=result.catch(()=>{});
  queues.set(id,settled);
  settled.then(()=>{if(queues.get(id)===settled)queues.delete(id);});
  return result;
}
function send(id,message,documentId) {
  return chrome.tabs.sendMessage(id,message,documentId?{documentId}:undefined);
}
function sameSession(a,b) {
  return a&&b&&a.sessionId===b.sessionId&&a.documentId===b.documentId;
}
async function stopStored(id,state) {
  await chrome.storage.session.remove(key(id));
  // A delayed automatic stop must not stop a more recent user-started session.
  try { send(id,{type:'pt:pause',sessionId:state.sessionId},state.documentId).catch(()=>{}); } catch {}
}
async function stop(id,expected) {
  return serial(id,async()=>{
    const state=await session(id);
    if(state&&(!expected||sameSession(state,expected)))await stopStored(id,state);
  });
}
async function inject(tabId) {
  return chrome.scripting.executeScript({target:{tabId},files:['common.js','content.js']});
}
function cleanHints(value) {
  const hints=Object.fromEntries(['image','autoImage','next']
    .filter(name=>typeof value?.[name]==='string'&&value[name].length<=512)
    .map(name=>[name,value[name]]));
  const point=value?.point;
  if(point&&typeof point.x==='number'&&typeof point.y==='number'&&
    Number.isFinite(point.x)&&Number.isFinite(point.y)&&point.x>=0&&point.x<=1&&point.y>=0&&point.y<=1)
    hints.point={x:point.x,y:point.y};
  return hints;
}
async function updateState(id,msg,sender) {
  return serial(id,async()=>{
    const existing=await session(id);
    if(!validId(msg.sessionId)||!sender.documentId)return {ok:false};
    if(!msg.running) {
      if(sameSession(existing,{sessionId:msg.sessionId,documentId:sender.documentId}))
        await chrome.storage.session.remove(key(id));
      return {ok:true};
    }
    const origin=new URL(sender.url).origin;
    if(msg.begin===true) {
      // A new user-started reader may replace an old session, but a delayed
      // message from an old document must never reclaim the current tab.
      const frames=await chrome.scripting.executeScript({target:{tabId:id},func:()=>location.href});
      if(!frames.some(frame=>frame.frameId===0&&frame.documentId===sender.documentId))return {ok:false};
      const tab=await chrome.tabs.get(id);
      if(!tab.active||new URL(tab.url).origin!==origin)return {ok:false};
    } else if(!sameSession(existing,{sessionId:msg.sessionId,documentId:sender.documentId})) {
      return {ok:false};
    }
    let expected=null;
    if(msg.expected!=null) {
      try {
        const target=new URL(msg.expected);
        if(target.origin!==origin||!/^https?:$/.test(target.protocol))return {ok:false};
        // Older callers used the current URL for a script-driven page turn.
        if(target.href!==sender.url)expected=target.href;
      } catch {return {ok:false};}
    }
    const pendingTurn=msg.pendingTurn===true&&validId(msg.turnId);
    const continuing=msg.begin!==true;
    const state={
      origin,documentId:sender.documentId,sessionId:msg.sessionId,
      settings:PageTurnerCore.settings(msg.settings),hints:cleanHints(msg.hints),
      expected:pendingTurn?expected:null,pendingTurn,turnId:pendingTurn?msg.turnId:null,
      expires:pendingTurn?(continuing&&existing.pendingTurn&&existing.turnId===msg.turnId?existing.expires:Date.now()+20000):0,
      stopAt:continuing?existing.stopAt:Date.now()+1800000,
      resumeId:continuing?existing.resumeId:null
    };
    await chrome.storage.session.set({[key(id)]:state});
    return {ok:true};
  });
}

chrome.runtime.onMessage.addListener((msg,sender,reply)=>{
  (async()=>{
    if(!msg||!String(msg.type).startsWith('pt:'))return {};
    const id=sender.tab?.id??msg.tabId;
    if(!Number.isInteger(id))throw new Error('No active tab');
    if(msg.type==='pt:open') {
      if(sender.tab)throw new Error('Popup only');
      const tab=await chrome.tabs.get(id);
      if(!/^https?:\/\//.test(tab.url||''))throw new Error('Unsupported page');
      await inject(id);
      return send(id,{type:msg.command||'pt:status',settings:PageTurnerCore.settings(msg.settings)});
    }
    if(!sender.tab||sender.frameId!==0||!/^https?:\/\//.test(sender.url||'')||
      sender.documentLifecycle&&sender.documentLifecycle!=='active')throw new Error('Page only');
    if(msg.type==='pt:state')return updateState(id,msg,sender);
    return {ok:true};
  })().then(reply,error=>reply({error:error.message}));
  return true;
});

chrome.tabs.onUpdated.addListener((id,change)=>{
  if(change.status!=='complete')return;
  let observed;
  (async()=>{
    observed=await serial(id,()=>session(id));
    if(!observed)return;
    const tab=await chrome.tabs.get(id);
    let url;
    try {url=new URL(tab.url);}catch{return stop(id,observed);}
    if(url.origin!==observed.origin||Date.now()>observed.stopAt||!tab.active)return stop(id,observed);
    const frames=await inject(id),documentId=frames.find(frame=>frame.frameId===0)?.documentId;
    if(!documentId)return stop(id,observed);
    let dispatched;
    await serial(id,async()=>{
      const state=await session(id);
      if(!state)return;
      // A History API update, or another completion callback for the same
      // document, keeps the existing reader and its pending-load check.
      if(documentId===state.documentId)return;
      if(!sameSession(state,observed))return;
      const currentTab=await chrome.tabs.get(id);
      let currentUrl;
      try {currentUrl=new URL(currentTab.url);}catch{return stopStored(id,state);}
      if(currentUrl.origin!==state.origin||!currentTab.active||Date.now()>state.stopAt||
        !state.pendingTurn||!state.turnId||Date.now()>state.expires||
        state.expected&&currentUrl.href!==state.expected)return stopStored(id,state);
      // Consume this turn before dispatching. Concurrent completion callbacks
      // cannot restart the reader after its first resume has already advanced.
      const resumed={...state,documentId,pendingTurn:false,turnId:null,expected:null,expires:0,resumeId:crypto.randomUUID()};
      await chrome.storage.session.set({[key(id)]:resumed});
      const response=send(id,{
        type:'pt:resume',settings:resumed.settings,stopAt:resumed.stopAt,hints:resumed.hints,
        sessionId:resumed.sessionId,resumeId:resumed.resumeId
      },documentId);
      dispatched={state:resumed,response};
    });
    if(dispatched) {
      try {
        const result=await dispatched.response;
        if(result?.error)await stop(id,dispatched.state);
      } catch {await stop(id,dispatched.state);}
    }
  })().catch(()=>{if(observed)stop(id,observed).catch(()=>{});});
});
chrome.tabs.onRemoved.addListener(id=>{serial(id,()=>chrome.storage.session.remove(key(id))).catch(()=>{});});
chrome.tabs.onActivated.addListener(({tabId})=>{
  (async()=>{
    const stored=await chrome.storage.session.get(null);
    await Promise.all(Object.entries(stored).filter(([name])=>name.startsWith('reading:')&&Number(name.slice(8))!==tabId)
      .map(([name,state])=>serial(Number(name.slice(8)),async()=>{
        const id=Number(name.slice(8)),current=await session(id);
        if(!sameSession(current,state))return;
        let tab;
        try {tab=await chrome.tabs.get(id);}catch{return chrome.storage.session.remove(name);}
        if(!tab.active)await stopStored(id,current);
      })));
  })().catch(()=>{});
});
