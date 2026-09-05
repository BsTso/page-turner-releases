package local.pageturner;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** A short, resumable setup. Never grants permission or starts gestures. */
public class SetupActivity extends LocalizedActivity {
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
        TextView back=Ui.action(this,s(R.string.setup_back),Ui.INK,Ui.BG); back.setGravity(Gravity.CENTER_VERTICAL); body.addView(back); back.setOnClickListener(v -> finish());
        gap(28); line(state==SetupState.READY?"02  /  02":"01  /  02",12,Ui.MUTED); gap(16);
        TextView title=line(state==SetupState.READY?s(R.string.setup_show_title):s(R.string.setup_access_title),28,Ui.INK); Ui.bold(title); gap(16);
        line(state==SetupState.READY?s(R.string.setup_ready_intro):s(R.string.setup_access_intro,SetupAccess.label(this)),15,Ui.MUTED);
        gap(28);
        if(state==SetupState.READY) {
            line("●",32,Ui.INK); gap(12);
            line(panel?s(R.string.setup_dot_visible):s(R.string.setup_find_dot),15,Ui.INK);
            gap(28);
            action(panel?s(R.string.setup_finish):s(R.string.setup_show_dot),true,() -> {
                int current=SetupAccess.state(this);
                if(current!=SetupState.READY) { sync(true); return; }
                if(panel && SetupState.canFinish(current,panelVisible())) {
                    getSharedPreferences("settings",0).edit().putBoolean("setup_complete",true).putBoolean("setup_seen",true).apply();
                    startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish();
                } else showPoint();
            });
            if(panel) action(s(R.string.setup_retry_dot),false,() -> { SetupAccess.hide(); showPoint(); });
            gap(14); line(s(R.string.setup_dot_tip),12,Ui.MUTED);
        } else {
            boolean oppo="OPPO".equalsIgnoreCase(Build.MANUFACTURER);
            line(state==SetupState.CONNECTING?(slow?s(R.string.connecting_slow):s(R.string.connecting)):(oppo?s(R.string.setup_oppo_path,SetupAccess.label(this)):s(R.string.setup_system_path,SetupAccess.label(this))),15,Ui.INK);
            gap(24);
            action(state==SetupState.CONNECTING?s(R.string.recheck):s(R.string.enable_access),true,() -> { if(SetupAccess.state(this)==SetupState.CONNECTING) sync(true); else SetupAccess.settings(this); });
            if(state==SetupState.CONNECTING) {
                if(slow) { gap(10); line(s(R.string.reconnect_hint),13,Ui.MUTED); }
                action(s(R.string.access_settings),false,() -> SetupAccess.settings(this));
            }
            action(s(R.string.setup_stuck),false,this::troubleshoot);
            gap(20); line(s(R.string.setup_disclosure_short),12,Ui.MUTED);
            action(s(R.string.permission_details),false,this::disclosure);
        }
        if(!error.isEmpty()) { gap(14); line(error,14,Ui.INK); action(s(R.string.see_solutions),false,this::troubleshoot); }
    }
    private void showPoint() {
        TurnService service=TurnService.instance;
        if(service==null || !service.showControlPoint()) error=s(R.string.dot_failed);
        else error="";
        sync(true);
    }
    private void troubleshoot() {
        new AlertDialog.Builder(this).setTitle(s(R.string.troubleshoot_title))
            .setItems(new String[]{s(R.string.missing_app,SetupAccess.label(this)),s(R.string.restricted_switch),s(R.string.enabled_not_working),s(R.string.stops_working)},(dialog,n) -> {
                if(n==0) new AlertDialog.Builder(this).setTitle(s(R.string.find_app_title,SetupAccess.label(this)))
                    .setMessage(s(R.string.find_app_help,SetupAccess.label(this)))
                    .setPositiveButton(s(R.string.open_settings),(a,b) -> SetupAccess.settings(this)).setNegativeButton(s(R.string.back),null).show();
                if(n==1) new AlertDialog.Builder(this).setTitle(s(R.string.restricted_title))
                    .setMessage(s(R.string.restricted_help))
                    .setPositiveButton(s(R.string.open_app_info),(a,b) -> SetupAccess.appInfo(this)).setNegativeButton(s(R.string.back),null).show();
                if(n==2) new AlertDialog.Builder(this).setTitle(s(R.string.reconnect_title))
                    .setMessage(s(R.string.reconnect_help,SetupAccess.label(this)))
                    .setPositiveButton(s(R.string.open_settings),(a,b) -> SetupAccess.settings(this)).setNegativeButton(s(R.string.back),null).show();
                if(n==3) new AlertDialog.Builder(this).setTitle(s(R.string.battery_title))
                    .setMessage(s(R.string.battery_help))
                    .setPositiveButton(s(R.string.open_app_info),(a,b) -> SetupAccess.appInfo(this)).setNegativeButton(s(R.string.back),null).show();
            }).show();
    }
    private void disclosure() { new AlertDialog.Builder(this).setTitle(s(R.string.disclosure_title)).setMessage(s(R.string.disclosure_full)).setPositiveButton(s(R.string.ok),null).show(); }
    private TextView line(String value,int size,int color) { TextView t=Ui.text(this,value,size,color); t.setLineSpacing(d(4),1); body.addView(t); return t; }
    private void action(String label,boolean primary,Runnable onClick) { TextView t=Ui.action(this,label,primary?0xffffffff:Ui.MUTED,primary?Ui.INK:Ui.BG); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,d(54)); p.topMargin=d(8); body.addView(t,p); t.setOnClickListener(v -> onClick.run()); }
    private void gap(int height) { body.addView(new View(this),new LinearLayout.LayoutParams(1,d(height))); }
    private int d(int value) { return Ui.dp(this,value); }
}
