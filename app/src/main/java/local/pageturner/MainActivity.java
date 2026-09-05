package local.pageturner;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout body;
    private UpdateChecker updateChecker;
    private final android.os.Handler statusHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private int lastAccess=-1;
    private final Runnable checkAccess=new Runnable() { public void run() { int state=SetupAccess.state(MainActivity.this); if(state!=lastAccess) render(); statusHandler.postDelayed(this,750); } };
    private static final String[] MODES={"点击翻页","向下浏览","滑动后翻页","识别下一页"};
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); prefs=getSharedPreferences("settings",0);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    @Override public void onResume() {
        super.onResume(); render(); statusHandler.postDelayed(checkAccess,750);
        if(!prefs.getBoolean("setup_seen",false)) {
            prefs.edit().putBoolean("setup_seen",true).apply();
            if(SetupAccess.state(this)!=SetupState.READY) permission();
        }
    }
    @Override public void onPause() { statusHandler.removeCallbacks(checkAccess); super.onPause(); }
    @Override public void onDestroy() { if(updateChecker!=null) updateChecker.cancel(); super.onDestroy(); }
    private void render() {
        lastAccess=SetupAccess.state(this);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Ui.BG);
        body=Ui.column(this); body.setPadding(d(28),d(22),d(28),d(24)); scroll.addView(body); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i) -> { v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom()); return i; });
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); body.addView(header);
        TextView title=Ui.text(this,"轻翻页",22,Ui.INK); Ui.bold(title); header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=Ui.action(this,"·",Ui.MUTED,Ui.BG); more.setTextSize(26); more.setPadding(0,0,0,0); more.setSingleLine(); more.setContentDescription("更多设置"); header.addView(more,new LinearLayout.LayoutParams(d(48),d(48))); more.setOnClickListener(v -> more());
        gap(28); body.addView(Ui.text(this,"翻页间隔",12,Ui.MUTED));
        LinearLayout digits=new LinearLayout(this); digits.setGravity(Gravity.BOTTOM); body.addView(digits);
        TextView number=Ui.text(this,seconds(),88,Ui.INK); number.setTypeface(android.graphics.Typeface.create("sans-serif-light",0)); number.setSingleLine();
        number.setContentDescription("翻页间隔 "+seconds()+" 秒，点按修改"); digits.addView(number);
        TextView unit=Ui.text(this,"秒",16,Ui.MUTED); unit.setPadding(d(12),0,0,d(14)); digits.addView(unit);
        digits.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Paint measure=new android.graphics.Paint(number.getPaint());
            float scale=getResources().getDisplayMetrics().scaledDensity; measure.setTextSize(88*scale);
            float available=digits.getWidth()-unit.getPaint().measureText("秒")-unit.getPaddingLeft()-unit.getPaddingRight()-d(2);
            if(available<=0) return;
            float sp=Math.max(20,Math.min(88,88*available/Math.max(1,measure.measureText(seconds()))));
            if(Math.abs(number.getTextSize()-sp*scale)>.5f) number.setTextSize(sp);
        });
        digits.setOnClickListener(v -> editInterval()); digits.setPadding(0,d(10),0,d(18));
        LinearLayout presets=new LinearLayout(this); body.addView(presets);
        for(int n:new int[]{5,10,15,30}) {
            boolean selected=prefs.getLong("interval",10000)==n*1000;
            TextView pill=Ui.action(this,""+n,selected?0xffffffff:Ui.MUTED,selected?Ui.INK:0xfff0f0f2);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(44),1); lp.rightMargin=n==30?0:d(8); presets.addView(pill,lp);
            pill.setOnClickListener(v -> { prefs.edit().putLong("interval",n*1000).apply(); changed(); });
        }
        gap(28); section("翻页方式"); int mode=mode();
        LinearLayout card=Ui.column(this); card.setBackground(Ui.shape(this,0xffffffff,20)); body.addView(card);
        for(int i=0;i<MODES.length;i++) {
            final int choice=i; LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(d(18),0,d(18),0); card.addView(row,new LinearLayout.LayoutParams(-1,d(55)));
            TextView label=Ui.text(this,MODES[i],15,mode==i?Ui.INK:Ui.MUTED); if(mode==i) Ui.bold(label); row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(Ui.text(this,mode==i?"●":"○",17,mode==i?Ui.INK:0xffc8c8ce));
            row.setContentDescription(MODES[i]+(mode==i?"，已选":"")); row.setClickable(true); row.setOnClickListener(v -> { prefs.edit().putInt("mode",choice).putBoolean("smart",choice==3).apply(); changed(); });
        }
        if(mode==1 || mode==2) {
            String profile=ScreenProfiles.key(getWindowManager());
            gap(14); detail("每次滑动",Math.round(ScreenProfiles.get(prefs,profile,"swipe",.5f)*100)+"% 屏幕").setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("每次滑动").setItems(new String[]{"小幅 · 35%","适中 · 50%","大幅 · 65%"},(dlg,which) -> { ScreenProfiles.put(prefs,profile,"swipe",new float[]{.35f,.5f,.65f}[which]); changed(); }).show());
            if(mode==2) detail("何时翻页",prefs.getBoolean("auto_bottom",true)?"滑到底后":"滑动 "+prefs.getInt("steps",2)+" 次后").setOnClickListener(v -> chooseSteps());
        }
        gap(12); String[] hints={"打开网页，长按圆点选择翻页位置","定时向上滑，慢慢往下看",prefs.getBoolean("auto_bottom",true)?"看不到底部时，先滑 "+prefs.getInt("steps",2)+" 次再翻页":"滑完再点击选好的位置","试验功能 · 找不到下一页就暂停"};
        TextView hint=Ui.text(this,hints[mode],12,Ui.MUTED); hint.setLineSpacing(d(3),1); body.addView(hint);
        gap(28);
        if(lastAccess!=SetupState.READY) {
            TextView permission=Ui.action(this,lastAccess==SetupState.CONNECTING?"完成连接":"完成使用准备",0xffffffff,Ui.INK); body.addView(permission,new LinearLayout.LayoutParams(-1,d(54))); permission.setOnClickListener(v -> permission());
        } else {
            TextView show=Ui.action(this,"显示控制点",0xffffffff,Ui.INK); body.addView(show,new LinearLayout.LayoutParams(-1,d(54))); show.setOnClickListener(v -> {
                TurnService service=TurnService.instance;
                if(service!=null && service.showControlPoint()) Toast.makeText(this,"打开网页，轻点圆点开始，长按选位置",Toast.LENGTH_LONG).show(); else permission();
            });
        }
        gap(14); TextView footer=Ui.text(this,"点数字改间隔 · 手动触屏即暂停",11,Ui.MUTED); footer.setGravity(Gravity.CENTER); body.addView(footer);
    }
    private int mode() { return Math.max(0,Math.min(3,prefs.getInt("mode",prefs.getBoolean("smart",false)?3:0))); }
    private String seconds() { double s=prefs.getLong("interval",10000)/1000.0; return s==(long)s?""+(long)s:""+s; }
    private void changed() { if(TurnService.instance!=null) TurnService.instance.pause("设置已更新"); render(); }
    private void editInterval() {
        EditText e=input(seconds(),true); AlertDialog dlg=new AlertDialog.Builder(this).setTitle("间隔 · 秒").setView(e).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dlg.setOnShowListener(v -> dlg.getButton(-1).setOnClickListener(w -> { try { long ms=Rules.intervalMillis(e.getText().toString()); prefs.edit().putLong("interval",ms).apply(); dlg.dismiss(); changed(); } catch(Exception ex) { e.setError("输入 1–3600 秒"); } })); dlg.show();
    }
    private void chooseSteps() {
        new AlertDialog.Builder(this).setTitle("何时翻页").setItems(new String[]{"滑到底后","滑动 1 次后","滑动 2 次后","滑动 3 次后","滑动 4 次后","滑动 5 次后","滑动 6 次后"},(dialog,which) -> {
            if(which==0) new AlertDialog.Builder(this).setTitle("看不到底部时滑几次").setItems(new String[]{"1 次","2 次","3 次","4 次","5 次","6 次"},(a,b) -> { prefs.edit().putBoolean("auto_bottom",true).putInt("steps",b+1).apply(); changed(); }).show();
            else { prefs.edit().putBoolean("auto_bottom",false).putInt("steps",which).apply(); changed(); }
        }).show();
    }
    private void more() {
        LinearLayout box=Ui.column(this); box.setPadding(d(24),d(12),d(24),d(12));
        View duration=settingRow("自动停止",prefs.getInt("minutes",30)+" 分钟");
        View visible=settingRow("圆点可见度",prefs.getInt("opacity",35)+"%");
        box.addView(duration,new LinearLayout.LayoutParams(-1,d(54)));
        LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,d(54)); row.topMargin=d(10); box.addView(visible,row);
        View check=settingRow("无变化时暂停",prefs.getBoolean("check_progress",true)?"开":"关");
        LinearLayout.LayoutParams checkRow=new LinearLayout.LayoutParams(-1,d(54)); checkRow.topMargin=d(10); box.addView(check,checkRow);
        LinearLayout links=new LinearLayout(this); LinearLayout.LayoutParams linksLp=new LinearLayout.LayoutParams(-1,d(54)); linksLp.topMargin=d(18); box.addView(links,linksLp);
        TextView help=Ui.action(this,"帮助",Ui.MUTED,0x00000000),update=Ui.action(this,"检查更新",Ui.MUTED,0x00000000);
        links.addView(help,new LinearLayout.LayoutParams(0,-1,1)); links.addView(update,new LinearLayout.LayoutParams(0,-1,1));
        TextView version=Ui.text(this,"轻翻页 1.1",11,Ui.MUTED); version.setGravity(Gravity.CENTER); box.addView(version);
        TextView heading=Ui.text(this,"设置",20,Ui.INK); Ui.bold(heading); heading.setPadding(d(24),d(24),d(24),d(8));
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(heading).setView(box).create();
        duration.setOnClickListener(v -> { dialog.dismiss(); editDuration(); });
        visible.setOnClickListener(v -> { dialog.dismiss(); opacity(); });
        check.setOnClickListener(v -> { dialog.dismiss(); new AlertDialog.Builder(this).setTitle("无变化时暂停").setMessage("连续两次没看到页面变化就暂停\n\n有些图片页面看不出变化，如果经常误停，可以关掉").setNegativeButton("关闭检查",(a,b) -> { prefs.edit().putBoolean("check_progress",false).apply(); changed(); }).setPositiveButton("开启检查",(a,b) -> { prefs.edit().putBoolean("check_progress",true).apply(); changed(); }).show(); });
        help.setOnClickListener(v -> { dialog.dismiss(); help(); });
        update.setOnClickListener(v -> { dialog.dismiss(); if(updateChecker!=null) updateChecker.cancel(); updateChecker=new UpdateChecker(this); updateChecker.start(); });
        dialog.show();
    }
    private View settingRow(String name,String value) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(d(18),0,d(18),0);
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22888888),Ui.shape(this,0xfff0f0f2,16),null)); row.setFocusable(true); row.setClickable(true);
        row.addView(Ui.text(this,name,14,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));
        TextView number=Ui.text(this,value,16,Ui.INK); Ui.bold(number); row.addView(number);
        row.setContentDescription(name+"，"+value); return row;
    }
    private void editDuration() {
        EditText e=input(""+prefs.getInt("minutes",30),false); AlertDialog dlg=new AlertDialog.Builder(this).setTitle("多少分钟后停止").setView(e).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dlg.setOnShowListener(v -> dlg.getButton(-1).setOnClickListener(w -> { try { int n=Integer.parseInt(e.getText().toString()); if(n<1 || n>180) throw new IllegalArgumentException(); prefs.edit().putInt("minutes",n).apply(); dlg.dismiss(); changed(); } catch(Exception ex) { e.setError("输入 1–180 分钟"); } })); dlg.show();
    }
    private void opacity() {
        LinearLayout box=Ui.column(this); box.setPadding(d(24),d(12),d(24),d(10)); TextView label=Ui.text(this,"",16,Ui.INK); box.addView(label);
        SeekBar seek=new SeekBar(this); seek.setMax(90); seek.setProgress(prefs.getInt("opacity",35)-10); box.addView(seek); label.setText("可见度 "+(seek.getProgress()+10)+"%");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s,int p,boolean u) { label.setText("可见度 "+(p+10)+"%"); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} });
        new AlertDialog.Builder(this).setTitle("收起后的圆点").setView(box).setNegativeButton("取消",null).setPositiveButton("保存",(a,b) -> { prefs.edit().putInt("opacity",seek.getProgress()+10).apply(); if(TurnService.instance!=null) TurnService.instance.updateAppearance(); }).show();
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("轻翻页").setIcon(getApplicationInfo().icon)
            .setMessage("由JamieTso制作，感谢使用").setPositiveButton("关闭",null).show();
    }
    private void permission() { startActivity(new Intent(this,SetupActivity.class)); }
    private EditText input(String value,boolean decimal) { EditText e=new EditText(this); e.setInputType(InputType.TYPE_CLASS_NUMBER|(decimal?InputType.TYPE_NUMBER_FLAG_DECIMAL:0)); e.setSingleLine(); e.setText(value); e.selectAll(); e.setPadding(d(24),d(12),d(24),d(12)); return e; }
    private TextView detail(String key,String value) { TextView t=Ui.action(this,key+"    "+value,Ui.INK,0xfff0f0f2); body.addView(t,new LinearLayout.LayoutParams(-1,d(48))); return t; }
    private void section(String text) { body.addView(Ui.text(this,text,12,Ui.MUTED)); gap(12); }
    private void gap(int h) { body.addView(new View(this),new LinearLayout.LayoutParams(1,d(h))); }
    private int d(int n) { return Ui.dp(this,n); }
}
