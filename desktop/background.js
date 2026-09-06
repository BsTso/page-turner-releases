importScripts('common.js');
const key=id=>'reading:'+id;
async function session(id) { return (await chrome.storage.session.get(key(id)))[key(id)]; }
async function stop(id) {
  await chrome.storage.session.remove(key(id));
  try { await chrome.tabs.sendMessage(id,{type:'pt:pause'}); } catch {}
}
async function inject(tabId) { await chrome.scripting.executeScript({target:{tabId},files:['common.js','content.js']}); }
chrome.runtime.onMessage.addListener((msg,sender,reply)=>{
  (async()=>{
    if(!msg || !String(msg.type).startsWith('pt:')) return {};
    const id=sender.tab?.id??msg.tabId;
    if(!Number.isInteger(id)) throw new Error('No active tab');
    if(msg.type==='pt:open') {
      if(sender.tab) throw new Error('Popup only');
      const tab=await chrome.tabs.get(id);
      if(!/^https?:\/\//.test(tab.url||'')) throw new Error('Unsupported page');
      await inject(id);
      return await chrome.tabs.sendMessage(id,{type:msg.command||'pt:status',settings:PageTurnerCore.settings(msg.settings)});
    }
    if(!sender.tab || !/^https?:\/\//.test(sender.url||'')) throw new Error('Page only');
    if(msg.type==='pt:state') {
      if(!msg.running) await chrome.storage.session.remove(key(id));
      else {
        const existing=await session(id);
        const origin=new URL(sender.url).origin;
        let expected=null; try { const target=new URL(msg.expected); if(target.origin===origin) expected=target.href; } catch {}
        const hints=Object.fromEntries(['image','next'].filter(k=>typeof msg.hints?.[k]==='string'&&msg.hints[k].length<=512).map(k=>[k,msg.hints[k]]));
        await chrome.storage.session.set({[key(id)]:{origin,settings:PageTurnerCore.settings(msg.settings),hints,expected,expires:Date.now()+20000,stopAt:existing?.stopAt||Date.now()+1800000}});
      }
    }
    return {ok:true};
  })().then(reply,error=>reply({error:error.message}));
  return true;
});
chrome.tabs.onUpdated.addListener((id,change,tab)=>{
  if(change.status!=='complete') return;
  (async()=>{
    const state=await session(id); if(!state) return;
    let url; try { url=new URL(tab.url); } catch { return stop(id); }
    if(url.origin!==state.origin || !state.expected || url.href!==state.expected || Date.now()>state.expires || Date.now()>state.stopAt || !tab.active) return stop(id);
    await inject(id);
    await chrome.tabs.sendMessage(id,{type:'pt:resume',settings:state.settings,stopAt:state.stopAt,hints:state.hints});
  })().catch(()=>stop(id));
});
chrome.tabs.onRemoved.addListener(id=>chrome.storage.session.remove(key(id)));
chrome.tabs.onActivated.addListener(({tabId})=>{
  (async()=>{ for(const name of Object.keys(await chrome.storage.session.get(null))) if(name.startsWith('reading:') && Number(name.slice(8))!==tabId) await stop(Number(name.slice(8))); })();
});
