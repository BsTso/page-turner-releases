(() => {
  const words = {
    zh: {name:'轻翻页', interval:'停顿间隔', seconds:'秒', mode:'翻页方式', tap:'点击翻页', combined:'滑完翻页', scroll:'只慢滑', whole:'一次滑到底', halves:'一半停，再滑完', duration:'每段用时', focus:'只看主体', start:'开始', pause:'暂停', close:'关闭', more:'更多', pickImage:'选主体图片', pickNext:'选下一页按钮', language:'语言', system:'跟随系统', footer:'点击或滚动即停 · Esc 退出', credit:'由JamieTso制作，感谢使用', selectImage:'点一下主体图片 · Esc 取消', selectNext:'点一下下一页按钮 · Esc 取消', selected:'已选好', noImage:'找不到主体图片，请先选主体', noNext:'找不到唯一的下一页按钮，请先选择', unchanged:'翻页后没有变化，已暂停', switched:'页面已切换，已暂停', ended:'已到底', loading:'图片还没加载完', failed:'此页面暂时无法使用，请打开普通网页', turning:'翻页中', reading:'慢滑中', waiting:'等待', limit:'已阅读 30 分钟，休息一下', invalid:'间隔需为 1–3600 秒', permission:'请在要阅读的网页上打开扩展'},
    en: {name:'PageTurner', interval:'Pause between actions', seconds:'s', mode:'Reading mode', tap:'Turn pages', combined:'Scroll, then turn', scroll:'Scroll only', whole:'All the way down', halves:'Pause halfway', duration:'Time per part', focus:'Image only', start:'Start', pause:'Pause', close:'Close', more:'More', pickImage:'Choose main image', pickNext:'Choose next button', language:'Language', system:'System default', footer:'Click or scroll to pause · Esc to exit', credit:'Made by JamieTso, thanks for using it', selectImage:'Click the main image · Esc to cancel', selectNext:'Click the next-page button · Esc to cancel', selected:'Selected', noImage:'Main image not found, choose it first', noNext:'Choose a next-page button first', unchanged:'No page change detected, paused', switched:'Page changed, paused', ended:'End of page', loading:'Image is still loading', failed:'Unavailable here, open a regular web page', turning:'Turning page', reading:'Scrolling', waiting:'Waiting', limit:'30 minutes reached, take a break', invalid:'Interval must be 1–3600 seconds', permission:'Open the extension on the page you want to read'},
    ja: {name:'ページめくり', interval:'待ち時間', seconds:'秒', mode:'めくり方', tap:'ページをめくる', combined:'スクロールしてめくる', scroll:'スクロールのみ', whole:'一番下まで一度に', halves:'半分で休む', duration:'1段階の時間', focus:'画像だけ表示', start:'開始', pause:'一時停止', close:'閉じる', more:'その他', pickImage:'メイン画像を選ぶ', pickNext:'次ページのボタンを選ぶ', language:'言語', system:'端末に合わせる', footer:'クリック・スクロールで停止 · Esc で終了', credit:'JamieTso制作、ご利用ありがとうございます', selectImage:'画像をクリック · Esc で取消', selectNext:'次ページのボタンをクリック · Esc で取消', selected:'選択しました', noImage:'画像が見つかりません、先に選んでください', noNext:'次ページのボタンを選んでください', unchanged:'ページが変わらないため停止しました', switched:'ページが切り替わったため停止しました', ended:'一番下に着きました', loading:'画像を読み込み中です', failed:'このページでは使えません、通常のWebページを開いてください', turning:'ページを読み込み中', reading:'スクロール中', waiting:'待機中', limit:'30分経ちました、少し休みましょう', invalid:'待ち時間は1〜3600秒です', permission:'読むページで拡張機能を開いてください'}
  };
  function settings(value={}) {
    const numeric=(v,lo,hi,fallback)=>Number.isFinite(Number(v))?Math.max(lo,Math.min(hi,Number(v))):fallback;
    return {interval:numeric(value.interval,1,3600,10), duration:numeric(value.duration,1,30,10), kind:value.kind==='halves'?'halves':'whole', mode:['tap','scroll','combined'].includes(value.mode)?value.mode:'combined', focus:value.focus!==false, language:['zh','en','ja'].includes(value.language)?value.language:'system'};
  }
  function language(choice,preferences=['en']) { return words[choice]?choice:preferences.map(v=>String(v).split('-')[0]).find(v=>words[v])||'en'; }
  function target(y,max,kind) { return kind==='halves' && y<max/2-2?max/2:max; }
  // Cosine velocity ramps join a steady middle section without a hard start or stop.
  function glide(progress,duration=10) {
    const p=Math.max(0,Math.min(1,progress)),ramp=Math.min(.22,2/Math.max(1,duration));
    const edge=x=>(x-ramp/Math.PI*Math.sin(Math.PI*x/ramp))/(2*(1-ramp));
    if(p<ramp)return edge(p);
    if(p>1-ramp)return 1-edge(1-p);
    return (p-ramp/2)/(1-ramp);
  }
  function nextLabel(label) { return /^(next(?:\s+(?:page|image))?|下一[页頁张張]|下页|次のページ|次ページ|次の画像|次の写真|next[›»→]?)$/i.test(String(label||'').trim()); }
  function safeNext(url,base) { try { const next=new URL(url,base),current=new URL(base); return /^https?:$/.test(next.protocol)&&next.origin===current.origin&&next.href!==current.href?next.href:null; } catch { return null; } }
  const api={words,settings,language,target,glide,nextLabel,safeNext};
  globalThis.PageTurnerCore=api;
  if(typeof module!=='undefined') module.exports=api;
})();
