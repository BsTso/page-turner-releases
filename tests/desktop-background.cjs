// Run with Node; this exercises the shipped service worker without browser data.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const assert=require('node:assert/strict'),crypto=require('node:crypto');
const source=fs.readFileSync(path.join(__dirname,'../desktop/background.js'),'utf8');
const core=require('../desktop/common.js');
let checks=0;
const check=(actual,expected,label)=>{assert.deepEqual(actual,expected,label);checks++;};

function fixture() {
  const storage={},listeners={},sent=[];
  let tab={id:1,active:true,url:'https://reader.test/p1'},documentId='d1',sendHook=null;
  const chrome={
    storage:{session:{
      get:async name=>structuredClone(name==null?storage:{[name]:storage[name]}),
      set:async values=>Object.assign(storage,structuredClone(values)),
      remove:async name=>{delete storage[name];}
    }},
    runtime:{onMessage:{addListener:listener=>{listeners.message=listener;}}},
    tabs:{
      get:async()=>({...tab}),
      sendMessage:async(id,message,options)=>{
        sent.push({id,message,options});
        return sendHook?sendHook(message):{ok:true};
      },
      onUpdated:{addListener:listener=>{listeners.updated=listener;}},
      onRemoved:{addListener:listener=>{listeners.removed=listener;}},
      onActivated:{addListener:listener=>{listeners.activated=listener;}}
    },
    scripting:{executeScript:async()=>[{frameId:0,documentId}]}
  };
  vm.runInNewContext(source,{chrome,PageTurnerCore:core,importScripts:()=>{},URL,crypto,Date});
  return {
    storage,sent,
    message(value,fromDocument=documentId,sessionId='s1') {
      return new Promise(resolve=>listeners.message({
        type:'pt:state',running:true,settings:{turnMethod:'point'},
        hints:{point:{x:.9,y:.5}},sessionId,...value
      },{tab:{id:1},url:tab.url,frameId:0,documentId:fromDocument,documentLifecycle:'active'},resolve));
    },
    async flush(){for(let index=0;index<12;index++)await new Promise(resolve=>setImmediate(resolve));},
    complete:()=>listeners.updated(1,{status:'complete'}),
    navigate(id,url){documentId=id;tab.url=url;},
    setSendHook(hook){sendHook=hook;},
    state:()=>storage['reading:1'],
    resumes:()=>sent.filter(item=>item.message.type==='pt:resume')
  };
}

(async()=>{
  const reader=fixture();
  await reader.message({begin:true});
  check(reader.state().sessionId,'s1','a user start creates its own session');
  check(reader.state().hints.point,{x:.9,y:.5},'the saved click point survives serialization');
  await reader.message({pendingTurn:true,turnId:'t1',expected:null});
  reader.complete();reader.complete();await reader.flush();
  check(reader.sent.length,0,'History API completion does not restart the existing document');

  reader.navigate('d2','https://reader.test/p2');
  reader.complete();reader.complete();await reader.flush();
  check(reader.resumes().length,1,'concurrent completion callbacks resume only once');
  check(reader.resumes()[0].options.documentId,'d2','resume targets the intended new document');
  check(reader.state().pendingTurn,false,'the navigation authorization is consumed');
  await reader.message({running:false},'d1');
  check(reader.state().documentId,'d2','a late old-document pause cannot remove the new reader');
  await reader.message({pendingTurn:true,turnId:'old'},'d1');
  check(reader.state().pendingTurn,false,'an old-document update cannot recreate navigation permission');
  await reader.message({begin:true},'d1','stale-start');
  check(reader.state().sessionId,'s1','an old-document start cannot reclaim the current tab');

  await reader.message({begin:true},'d2','s2');
  await reader.message({running:false},'d2','s1');
  check(reader.state().sessionId,'s2','a late old-session pause cannot stop a restart');
  await reader.message({running:false},'d2','s2');
  await reader.message({running:true},'d2','s2');
  check(reader.state(),undefined,'a delayed ordinary state cannot revive a stopped reader');

  const mismatch=fixture();
  await mismatch.message({begin:true});
  await mismatch.message({pendingTurn:true,turnId:'t1',expected:'https://reader.test/p2'});
  mismatch.navigate('d2','https://reader.test/elsewhere');mismatch.complete();await mismatch.flush();
  check(mismatch.resumes().length,0,'an explicit link requires an exact destination match');
  check(mismatch.state(),undefined,'an unexpected destination ends the session');

  const manual=fixture();
  await manual.message({begin:true});
  manual.navigate('d2','https://reader.test/p2');manual.complete();await manual.flush();
  check(manual.resumes().length,0,'unrequested navigation does not resume reading');

  const invalidPoint=fixture();
  await invalidPoint.message({begin:true,hints:{point:{x:2,y:NaN}}});
  check(invalidPoint.state().hints.point,undefined,'out-of-viewport and nonfinite points are discarded');

  const lateReply=fixture();let rejectResume;
  lateReply.setSendHook(message=>message.type==='pt:resume'?new Promise((resolve,reject)=>{rejectResume=reject;}):{ok:true});
  await lateReply.message({begin:true});
  await lateReply.message({pendingTurn:true,turnId:'t1',expected:null});
  lateReply.navigate('d2','https://reader.test/p2');lateReply.complete();await lateReply.flush();
  check(lateReply.resumes().length,1,'the in-flight resume was dispatched');
  await lateReply.message({begin:true},'d2','s2');
  rejectResume(new Error('old document reply failed'));await lateReply.flush();
  check(lateReply.state().sessionId,'s2','a failed old resume reply cannot stop a newer user session');
  check(lateReply.sent.filter(item=>item.message.type==='pt:pause').length,0,'a stale failure does not send a pause to the new reader');

  console.log(`Desktop background: ${checks} checks passed`);
})().catch(error=>{console.error(error);process.exitCode=1;});
