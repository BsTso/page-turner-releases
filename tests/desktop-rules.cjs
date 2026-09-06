const assert=require('node:assert/strict');
const C=require('../desktop/common.js');
let checks=0;const check=(a,b)=>{assert.deepEqual(a,b);checks++;};
check(C.target(0,4000,'halves'),2000);check(C.target(2000,4000,'halves'),4000);check(C.target(0,4000,'whole'),4000);check(C.target(2500,4000,'halves'),4000);
for(const duration of [1,10,30]){
  check([C.glide(0,duration),C.glide(.5,duration),C.glide(1,duration)],[0,.5,1]);
  const samples=Array.from({length:1001},(_,i)=>C.glide(i/1000,duration));
  check(samples.every((v,i)=>v>=0&&v<=1&&(i===0||v>=samples[i-1])),true);
  const speed=t=>(C.glide(t+.0001,duration)-C.glide(t,duration))/.0001;
  check(speed(0)<speed(.5)*.001&&speed(.9999)<speed(.5)*.001,true);
  check(Math.abs(speed(.4)-speed(.6))<.00001,true);
}
check(C.settings().duration,10);check(C.settings({duration:999,interval:-1}).duration,30);check(C.settings({interval:-1}).interval,1);check(C.settings({interval:'oops'}).interval,10);check(C.settings({mode:'pay'}).mode,'combined');
check(C.language('system',['fr','ja-JP','en']), 'ja');check(C.language('zh',['ja']), 'zh');check(C.language('system',['fr']), 'en');
for(const label of ['下一页','Next page','next','次のページ'])check(C.nextLabel(label),true);
for(const label of ['next payment','下一步','付款','広告',''])check(C.nextLabel(label),false);
check(C.safeNext('/page/2','https://example.com/page/1'),'https://example.com/page/2');check(C.safeNext('https://ads.example/next','https://example.com/page/1'),null);check(C.safeNext('javascript:alert(1)','https://example.com/page/1'),null);check(C.safeNext('/page/1','https://example.com/page/1'),null);
check(Object.keys(C.words.zh).sort(),Object.keys(C.words.en).sort());check(Object.keys(C.words.zh).sort(),Object.keys(C.words.ja).sort());
const manifest=require('../desktop/manifest.json');check(manifest.permissions,['activeTab','scripting','storage']);check(manifest.host_permissions,undefined);
console.log(`Desktop rules: ${checks} checks passed`);
