const $=id=>document.getElementById(id),C=PageTurnerCore;
let settings=C.settings(),tabId,running=false,busy=false;
function text(){return C.words[C.language(settings.language,navigator.languages)];}
function fitNumber(){$('interval').style.width=Math.min(230,Math.max(92,String($('interval').value).length*41))+'px';}
function render(){const t=text();document.querySelectorAll('[data-i18n]').forEach(e=>e.textContent=t[e.dataset.i18n]);for(const k of ['interval','duration','kind','mode','turnMethod','language']) $(k).value=settings[k];fitNumber();$('focus').checked=settings.focus;$('durationValue').textContent=settings.duration;$('slow').hidden=settings.mode==='tap';$('turning').hidden=settings.mode==='scroll';$('turnMethod').setAttribute('aria-label',t.turnMethod);$('start').textContent=t[running?'pause':'start'];document.querySelectorAll('[data-seconds]').forEach(e=>e.classList.toggle('selected',Number(e.dataset.seconds)===settings.interval));document.documentElement.lang=C.language(settings.language,navigator.languages);}
function read(){settings=C.settings(Object.fromEntries(['interval','duration','kind','mode','turnMethod','language'].map(k=>[k,$(k).value]).concat([['focus',$('focus').checked]])));return settings;}
async function command(type){const result=await chrome.runtime.sendMessage({type:'pt:open',tabId,command:type,settings:read()});if(result?.error)throw Error(result.error);return result;}
async function save(){read();await chrome.storage.local.set({settings});render();}
(async()=>{settings=C.settings((await chrome.storage.local.get('settings')).settings);const tabs=await chrome.tabs.query({active:true,currentWindow:true});tabId=tabs[0]?.id;render();try{const result=await command('pt:status');running=!!result?.running;if(result?.settings)settings.turnMethod=result.settings.turnMethod;$('status').textContent=result?.reason||'';render();}catch{$('status').textContent=text().permission;}})();
for(const k of ['interval','duration','kind','mode','turnMethod','focus','language']) $(k).addEventListener('change',save);
$('duration').addEventListener('input',()=>{$('durationValue').textContent=$('duration').value;});
$('interval').addEventListener('input',fitNumber);
document.querySelectorAll('[data-seconds]').forEach(e=>e.onclick=()=>{$('interval').value=e.dataset.seconds;save();});
$('more').onclick=()=>{$('extras').hidden=!$('extras').hidden;};
$('start').onclick=async()=>{if(busy)return;busy=true;$('start').disabled=true;try{if(!Number.isFinite(Number($('interval').value))||Number($('interval').value)<1||Number($('interval').value)>3600)throw Error(text().invalid);await save();const result=await command(running?'pt:pause':'pt:start');if(!running&&!result.running&&result.reason){$('status').textContent=result.reason;return;}window.close();}catch(e){$('status').textContent=e.message===text().invalid?e.message:text().failed;}finally{busy=false;$('start').disabled=false;}};
for(const name of ['pickImage','pickNext']) $(name).onclick=async()=>{if(busy)return;busy=true;try{await save();await command(name==='pickImage'?'pt:pick-image':'pt:pick-next');window.close();}catch{$('status').textContent=text().failed;}finally{busy=false;}};
