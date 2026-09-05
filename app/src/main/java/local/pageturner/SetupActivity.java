package local.pageturner;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** A short, resumable setup. Never grants permission or starts gestures. */
public class SetupActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout body;
    private int lastState=-1;
    private boolean lastPanel, wasSlow;
    private long waitingSince;
    private String error="";
    private final Runnable refresh=new Runnable() { public void run() { sync(false); handler.postDelayed(this,750); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        SetupAccess.hide();
    }
    @Override public void onResume() { super.onResume(); sync(true); handler.postDelayed(refresh,750); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private boolean panelVisible() { return TurnService.instance!=null && TurnService.instance.isPanelVisible(); }
    private void sync(boolean force) {
        int state=SetupAccess.state(this); boolean panel=panelVisible();
        if(state==SetupState.CONNECTING) { if(waitingSince==0) waitingSince=SystemClock.uptimeMillis(); }
        else waitingSince=0;
        boolean slow=waitingSince!=0 && SystemClock.uptimeMillis()-waitingSince>=10000;
        if(force || state!=lastState || panel!=lastPanel || slow!=wasSlow) {
            if(state!=lastState) error="";
            lastState=state; lastPanel=panel; wasSlow=slow; render(state,panel,slow);
        }
    }
    private void render(int state,boolean panel,boolean slow) {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Ui.BG);
        body=Ui.column(this); body.setPadding(d(28),d(24),d(28),d(28)); scroll.addView(body); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i) -> { v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom()); return i; });
        TextView back=Ui.action(this,"‹  轻翻页",Ui.INK,Ui.BG); back.setGravity(Gravity.CENTER_VERTICAL); body.addView(back); back.setOnClickListener(v -> finish());
        gap(28); line(state==SetupState.READY?"02  /  02":"01  /  02",12,Ui.MUTED); gap(16);
        TextView title=line(state==SetupState.READY?"让圆点出现":"允许屏幕操作",28,Ui.INK); Ui.bold(title); gap(16);
        line(state==SetupState.READY?"已经准备好了\n接下来试试屏幕上的小圆点":"开启「轻翻页」无障碍服务即可\n不需要再开悬浮窗权限",15,Ui.MUTED);
        gap(28);
        if(state==SetupState.READY) {
            line("●",32,Ui.INK); gap(12);
            line(panel?"小圆点已经显示\n轻点开始或暂停，长按展开":"点下方按钮，再看看屏幕左上方",15,Ui.INK);
            gap(28);
            action(panel?"看到了，开始使用":"显示小圆点",true,() -> {
                int current=SetupAccess.state(this);
                if(current!=SetupState.READY) { sync(true); return; }
                if(panel && SetupState.canFinish(current,panelVisible())) {
                    getSharedPreferences("settings",0).edit().putBoolean("setup_complete",true).putBoolean("setup_seen",true).apply();
                    startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish();
                } else showPoint();
            });
            if(panel) action("没看到，重新显示",false,() -> { SetupAccess.hide(); showPoint(); });
            gap(14); line("打开网页后，轻点圆点开始\n长按可以选位置，拖动可以挪开",12,Ui.MUTED);
        } else {
            boolean oppo="OPPO".equalsIgnoreCase(Build.MANUFACTURER);
            line(state==SetupState.CONNECTING?(slow?"已开启，还没连上":"已开启，等系统连接…"):(oppo?"已下载的应用 → 轻翻页 → 开启":"在系统设置中找到「轻翻页」，打开开关"),15,Ui.INK);
            gap(24);
            action(state==SetupState.CONNECTING?"重新检查":"去开启",true,() -> { if(SetupAccess.state(this)==SetupState.CONNECTING) sync(true); else SetupAccess.settings(this); });
            if(state==SetupState.CONNECTING) {
                if(slow) { gap(10); line("回设置关掉再打开，然后回来试试",13,Ui.MUTED); }
                action("回到无障碍设置",false,() -> SetupAccess.settings(this));
            }
            action("找不到 / 开关是灰色的",false,this::troubleshoot);
            gap(20); line("用来读取页面，按你的设置点击或滑动\n网页内容不保存、不上传",12,Ui.MUTED);
            action("了解授权范围",false,this::disclosure);
        }
        if(!error.isEmpty()) { gap(14); line(error,14,Ui.INK); action("查看解决办法",false,this::troubleshoot); }
    }
    private void showPoint() {
        TurnService service=TurnService.instance;
        if(service==null || !service.showControlPoint()) error="圆点没显示出来，重新开启无障碍服务后再试试";
        else error="";
        sync(true);
    }
    private void troubleshoot() {
        new AlertDialog.Builder(this).setTitle("卡在哪一步？")
            .setItems(new String[]{"找不到轻翻页","提示受限设置 / 开关灰色","已经开启，但仍不能用","运行一会儿就失效"},(dialog,n) -> {
                if(n==0) new AlertDialog.Builder(this).setTitle("找到轻翻页")
                    .setMessage("在无障碍设置里找「已下载的应用」或「已安装的服务」，再点轻翻页\n\n不同手机的名称可能不一样")
                    .setPositiveButton("打开设置",(a,b) -> SetupAccess.settings(this)).setNegativeButton("返回",null).show();
                if(n==1) new AlertDialog.Builder(this).setTitle("开关被系统限制")
                    .setMessage("如果手机提示「受限设置」，并且你信任这个安装包\n\n1、打开下方的应用信息\n2、点右上角菜单 → 允许受限设置，按提示确认\n3、回到轻翻页，点「去开启」\n\n找不到这个选项时，按手机上的提示操作")
                    .setPositiveButton("打开应用信息",(a,b) -> SetupAccess.appInfo(this)).setNegativeButton("返回",null).show();
                if(n==2) new AlertDialog.Builder(this).setTitle("重新连接")
                    .setMessage("回到无障碍设置，把轻翻页关掉再打开，然后回来试试")
                    .setPositiveButton("打开设置",(a,b) -> SetupAccess.settings(this)).setNegativeButton("返回",null).show();
                if(n==3) new AlertDialog.Builder(this).setTitle("只在失效时调整")
                    .setMessage("先看看无障碍服务还开着没\n\n如果手机总是关掉轻翻页，可以在应用信息的电池设置里允许后台活动\n\n能正常使用就不用调整")
                    .setPositiveButton("打开应用信息",(a,b) -> SetupAccess.appInfo(this)).setNegativeButton("返回",null).show();
            }).show();
    }
    private void disclosure() { new AlertDialog.Builder(this).setTitle("这个权限用来做什么").setMessage("读取当前页面，识别下一页和页面变化，再按你的设置点击或滑动\n\n不截屏，不保存或上传网页内容\n只有检查更新时会连接 GitHub\n\n开了权限也不会自动翻页，要由你轻点圆点或点「开始」\n手动触屏就暂停，也可以随时在系统设置里关掉服务").setPositiveButton("知道了",null).show(); }
    private TextView line(String value,int size,int color) { TextView t=Ui.text(this,value,size,color); t.setLineSpacing(d(4),1); body.addView(t); return t; }
    private void action(String label,boolean primary,Runnable onClick) { TextView t=Ui.action(this,label,primary?0xffffffff:Ui.MUTED,primary?Ui.INK:Ui.BG); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,d(54)); p.topMargin=d(8); body.addView(t,p); t.setOnClickListener(v -> onClick.run()); }
    private void gap(int height) { body.addView(new View(this),new LinearLayout.LayoutParams(1,d(height))); }
    private int d(int value) { return Ui.dp(this,value); }
}
