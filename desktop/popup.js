const $=id=>document.getElementById(id),C=PageTurnerCore;
let settings=C.settings(),tabId,running=false;
function text(){return C.words[C.language(settings.language,navigator.languages)];}
function fitNumber(){$('interval').style.width=Math.min(230,Math.max(92,String($('interval').value).length*41))+'px';}
function render(){const t=text();document.querySelectorAll('[data-i18n]').forEach(e=>e.textContent=t[e.dataset.i18n]);for(const k of ['interval','duration','kind','mode','language']) $(k).value=settings[k];fitNumber();$('focus').checked=settings.focus;$('durationValue').textContent=settings.duration;$('slow').hidden=settings.mode==='tap';$('start').textContent=t[running?'pause':'start'];document.querySelectorAll('[data-seconds]').forEach(e=>e.classList.toggle('selected',Number(e.dataset.seconds)===settings.interval));document.documentElement.lang=C.language(settings.language,navigator.languages);}
function read(){settings=C.settings(Object.fromEntries(['interval','duration','kind','mode','language'].map(k=>[k,$(k).value]).concat([['focus',$('focus').checked]])));return settings;}
async function command(type){const result=await chrome.runtime.sendMessage({type:'pt:open',tabId,command:type,settings:read()});if(result?.error)throw Error(result.error);return result;}
async function save(){read();await chrome.storage.local.set({settings});render();}
(async()=>{settings=C.settings((await chrome.storage.local.get('settings')).settings);const tabs=await chrome.tabs.query({active:true,currentWindow:true});tabId=tabs[0]?.id;render();try{running=!!(await command('pt:status'))?.running;render();}catch{$('status').textContent=text().permission;}})();
for(const k of ['interval','duration','kind','mode','focus','language']) $(k).addEventListener('change',save);
$('duration').addEventListener('input',()=>{$('durationValue').textContent=$('duration').value;});
$('interval').addEventListener('input',fitNumber);
document.querySelectorAll('[data-seconds]').forEach(e=>e.onclick=()=>{$('interval').value=e.dataset.seconds;save();});
$('more').onclick=()=>{$('extras').hidden=!$('extras').hidden;};
$('start').onclick=async()=>{try{if(!Number.isFinite(Number($('interval').value))||Number($('interval').value)<1||Number($('interval').value)>3600)throw Error(text().invalid);await save();await command(running?'pt:pause':'pt:start');window.close();}catch(e){$('status').textContent=e.message===text().invalid?e.message:text().failed;}};
for(const name of ['pickImage','pickNext']) $(name).onclick=async()=>{try{await save();await command(name==='pickImage'?'pt:pick-image':'pt:pick-next');window.close();}catch{$('status').textContent=text().failed;}};
