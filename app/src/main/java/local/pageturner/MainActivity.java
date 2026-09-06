package local.pageturner;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public class MainActivity extends LocalizedActivity {
    private SharedPreferences prefs;
    private LinearLayout body;
    private UpdateChecker updateChecker;
    private final android.os.Handler statusHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private int lastAccess=-1;
    private final Runnable checkAccess=new Runnable() { public void run() { int state=SetupAccess.state(MainActivity.this); if(state!=lastAccess) render(); statusHandler.postDelayed(this,750); } };
    
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); Languages.migrate(this); prefs=getSharedPreferences("settings",0);
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
        String[] MODES={s(R.string.mode_tap),s(R.string.mode_scroll),s(R.string.mode_combined),s(R.string.mode_smart)};
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Ui.BG);
        body=Ui.column(this); body.setPadding(d(28),d(22),d(28),d(24)); scroll.addView(body); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i) -> { v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom()); return i; });
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); body.addView(header);
        TextView title=Ui.text(this,s(R.string.app_name),22,Ui.INK); Ui.bold(title); header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=Ui.action(this,"·",Ui.MUTED,Ui.BG); more.setTextSize(26); more.setPadding(0,0,0,0); more.setSingleLine(); more.setContentDescription(s(R.string.more_settings)); header.addView(more,new LinearLayout.LayoutParams(d(48),d(48))); more.setOnClickListener(v -> more());
        gap(28); body.addView(Ui.text(this,s(mode()==1 || mode()==2?R.string.rest_interval:R.string.interval_label),12,Ui.MUTED));
        LinearLayout digits=new LinearLayout(this); digits.setGravity(Gravity.BOTTOM); body.addView(digits);
        TextView number=Ui.text(this,seconds(),88,Ui.INK); number.setTypeface(android.graphics.Typeface.create("sans-serif-light",0)); number.setSingleLine();
        number.setContentDescription(s(R.string.interval_accessibility,seconds())); digits.addView(number);
        TextView unit=Ui.text(this,s(R.string.seconds_unit),16,Ui.MUTED); unit.setPadding(d(12),0,0,d(14)); digits.addView(unit);
        digits.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Paint measure=new android.graphics.Paint(number.getPaint());
            float scale=getResources().getDisplayMetrics().scaledDensity; measure.setTextSize(88*scale);
            float available=digits.getWidth()-unit.getPaint().measureText(s(R.string.seconds_unit))-unit.getPaddingLeft()-unit.getPaddingRight()-d(2);
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
        gap(28); section(s(R.string.turn_mode)); int mode=mode();
        LinearLayout card=Ui.column(this); card.setBackground(Ui.shape(this,0xffffffff,20)); body.addView(card);
        for(int i=0;i<MODES.length;i++) {
            final int choice=i; LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(d(18),0,d(18),0); card.addView(row,new LinearLayout.LayoutParams(-1,d(55)));
            TextView label=Ui.text(this,MODES[i],15,mode==i?Ui.INK:Ui.MUTED); if(mode==i) Ui.bold(label); row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(Ui.text(this,mode==i?"●":"○",17,mode==i?Ui.INK:0xffc8c8ce));
            row.setContentDescription(mode==i?s(R.string.selected_mode,MODES[i]):MODES[i]); row.setClickable(true); row.setOnClickListener(v -> { prefs.edit().putInt("mode",choice).putBoolean("smart",choice==3).apply(); changed(); });
        }
        if(mode==1 || mode==2) {
            gap(14); detail(s(R.string.slow_scroll),s(R.string.scroll_summary,s(prefs.getInt("scroll_kind",ScrollPlan.PAGE)==ScrollPlan.HALVES?R.string.scroll_halves_short:R.string.scroll_page_short),Rules.swipeDuration(prefs.getLong("swipe_duration",10000))/1000)).setOnClickListener(v -> editScroll());
        }
        gap(24);
        if(lastAccess!=SetupState.READY) {
            TextView permission=Ui.action(this,lastAccess==SetupState.CONNECTING?s(R.string.finish_connecting):s(R.string.finish_setup),0xffffffff,Ui.INK); body.addView(permission,new LinearLayout.LayoutParams(-1,d(54))); permission.setOnClickListener(v -> permission());
        } else {
            TextView show=Ui.action(this,s(R.string.show_dot),0xffffffff,Ui.INK); body.addView(show,new LinearLayout.LayoutParams(-1,d(54))); show.setOnClickListener(v -> {
                TurnService service=TurnService.instance;
                if(service==null || !service.showControlPoint()) permission();
            });
        }
        gap(12); TextView footer=Ui.text(this,s(R.string.footer),11,Ui.MUTED); footer.setGravity(Gravity.CENTER); body.addView(footer);
    }
    private int mode() { return Math.max(0,Math.min(3,prefs.getInt("mode",prefs.getBoolean("smart",false)?3:0))); }
    private String seconds() { double s=prefs.getLong("interval",10000)/1000.0; return s==(long)s?""+(long)s:""+s; }
    private void changed() { if(TurnService.instance!=null) TurnService.instance.pause(s(R.string.settings_updated)); render(); }
    private void editInterval() {
        EditText e=input(seconds(),true); AlertDialog dlg=new AlertDialog.Builder(this).setTitle(s(R.string.interval_title)).setView(e).setNegativeButton(s(R.string.cancel),null).setPositiveButton(s(R.string.save),null).create();
        dlg.setOnShowListener(v -> dlg.getButton(-1).setOnClickListener(w -> { try { long ms=Rules.intervalMillis(e.getText().toString()); prefs.edit().putLong("interval",ms).apply(); dlg.dismiss(); changed(); } catch(Exception ex) { e.setError(s(R.string.interval_error)); } })); dlg.show();
    }
    private void editScroll() {
        LinearLayout box=Ui.column(this); box.setPadding(d(24),d(8),d(24),d(12));
        RadioGroup choices=new RadioGroup(this); box.addView(choices);
        for(int kind:new int[]{ScrollPlan.PAGE,ScrollPlan.HALVES}) {
            RadioButton option=new RadioButton(this); option.setId(kind); option.setText(s(kind==ScrollPlan.PAGE?R.string.scroll_page:R.string.scroll_halves)); option.setTextSize(15); option.setTextColor(Ui.INK);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.INK)); choices.addView(option,new RadioGroup.LayoutParams(-1,d(52)));
        }
        choices.check(prefs.getInt("scroll_kind",ScrollPlan.PAGE)==ScrollPlan.HALVES?ScrollPlan.HALVES:ScrollPlan.PAGE);
        TextView label=Ui.text(this,"",16,Ui.INK); label.setPadding(0,d(24),0,d(8)); box.addView(label);
        SeekBar duration=new SeekBar(this); duration.setMax(29); duration.setProgress((int)(Rules.swipeDuration(prefs.getLong("swipe_duration",10000))/1000)-1);
        duration.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.INK)); duration.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.INK)); box.addView(duration);
        label.setText(s(R.string.scroll_duration,duration.getProgress()+1));
        duration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar view,int value,boolean user) { label.setText(s(R.string.scroll_duration,value+1)); } public void onStartTrackingTouch(SeekBar view) {} public void onStopTrackingTouch(SeekBar view) {} });
        ScrollView scroll=new ScrollView(this); scroll.addView(box);
        new AlertDialog.Builder(this).setTitle(s(R.string.slow_scroll)).setView(scroll).setNegativeButton(s(R.string.cancel),null).setPositiveButton(s(R.string.save),(a,b) -> {
            prefs.edit().putInt("scroll_kind",choices.getCheckedRadioButtonId()).putLong("swipe_duration",(duration.getProgress()+1)*1000L).apply(); changed();
        }).show();
    }
    private void more() {
        LinearLayout box=Ui.column(this); box.setPadding(d(24),d(12),d(24),d(12));
        View duration=settingRow(s(R.string.auto_stop),s(R.string.minutes_value,prefs.getInt("minutes",30)));
        View visible=settingRow(s(R.string.opacity),prefs.getInt("opacity",35)+"%");
        box.addView(duration,new LinearLayout.LayoutParams(-1,d(54)));
        LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,d(54)); row.topMargin=d(10); box.addView(visible,row);
        View check=settingRow(s(R.string.check_progress),prefs.getBoolean("check_progress",true)?s(R.string.on):s(R.string.off));
        LinearLayout.LayoutParams checkRow=new LinearLayout.LayoutParams(-1,d(54)); checkRow.topMargin=d(10); box.addView(check,checkRow);
        View language=settingRow(s(R.string.language_label),Languages.name(this));
        LinearLayout.LayoutParams languageRow=new LinearLayout.LayoutParams(-1,d(54)); languageRow.topMargin=d(10); box.addView(language,languageRow);
        LinearLayout links=new LinearLayout(this); LinearLayout.LayoutParams linksLp=new LinearLayout.LayoutParams(-1,d(54)); linksLp.topMargin=d(18); box.addView(links,linksLp);
        TextView help=Ui.action(this,s(R.string.help),Ui.MUTED,0x00000000),update=Ui.action(this,s(R.string.check_updates),Ui.MUTED,0x00000000);
        links.addView(help,new LinearLayout.LayoutParams(0,-1,1)); links.addView(update,new LinearLayout.LayoutParams(0,-1,1));
        TextView version=Ui.text(this,s(R.string.version_label,"1.3"),11,Ui.MUTED); version.setGravity(Gravity.CENTER); box.addView(version);
        TextView heading=Ui.text(this,s(R.string.settings),20,Ui.INK); Ui.bold(heading); heading.setPadding(d(24),d(24),d(24),d(8));
        ScrollView settingsScroll=new ScrollView(this); settingsScroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(heading).setView(settingsScroll).create();
        duration.setOnClickListener(v -> { dialog.dismiss(); editDuration(); });
        visible.setOnClickListener(v -> { dialog.dismiss(); opacity(); });
        language.setOnClickListener(v -> { dialog.dismiss(); chooseLanguage(); });
        check.setOnClickListener(v -> { dialog.dismiss(); new AlertDialog.Builder(this).setTitle(s(R.string.check_progress)).setMessage(s(R.string.progress_explanation)).setNegativeButton(s(R.string.disable_check),(a,b) -> { prefs.edit().putBoolean("check_progress",false).apply(); changed(); }).setPositiveButton(s(R.string.enable_check),(a,b) -> { prefs.edit().putBoolean("check_progress",true).apply(); changed(); }).show(); });
        help.setOnClickListener(v -> { dialog.dismiss(); help(); });
        update.setOnClickListener(v -> { dialog.dismiss(); if(updateChecker!=null) updateChecker.cancel(); updateChecker=new UpdateChecker(this); updateChecker.start(); });
        dialog.show();
    }
    private View settingRow(String name,String value) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(d(18),0,d(18),0);
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22888888),Ui.shape(this,0xfff0f0f2,16),null)); row.setFocusable(true); row.setClickable(true);
        TextView label=Ui.text(this,name,14,Ui.INK); label.setPadding(0,0,d(10),0); row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView number=Ui.text(this,value,16,Ui.INK); number.setMaxWidth(d(130)); Ui.bold(number); row.addView(number);
        row.setContentDescription(s(R.string.settings_accessibility,name,value)); return row;
    }
    private void editDuration() {
        EditText e=input(""+prefs.getInt("minutes",30),false); AlertDialog dlg=new AlertDialog.Builder(this).setTitle(s(R.string.duration_title)).setView(e).setNegativeButton(s(R.string.cancel),null).setPositiveButton(s(R.string.save),null).create();
        dlg.setOnShowListener(v -> dlg.getButton(-1).setOnClickListener(w -> { try { int n=Integer.parseInt(e.getText().toString()); if(n<1 || n>180) throw new IllegalArgumentException(); prefs.edit().putInt("minutes",n).apply(); dlg.dismiss(); changed(); } catch(Exception ex) { e.setError(s(R.string.duration_error)); } })); dlg.show();
    }
    private void opacity() {
        LinearLayout box=Ui.column(this); box.setPadding(d(24),d(12),d(24),d(10)); TextView label=Ui.text(this,"",16,Ui.INK); box.addView(label);
        SeekBar seek=new SeekBar(this); seek.setMax(90); seek.setProgress(prefs.getInt("opacity",35)-10); box.addView(seek); label.setText(s(R.string.opacity_value,seek.getProgress()+10));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s,int p,boolean u) { label.setText(s(R.string.opacity_value,p+10)); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} });
        new AlertDialog.Builder(this).setTitle(s(R.string.dot_opacity_title)).setView(box).setNegativeButton(s(R.string.cancel),null).setPositiveButton(s(R.string.save),(a,b) -> { prefs.edit().putInt("opacity",seek.getProgress()+10).apply(); if(TurnService.instance!=null) TurnService.instance.updateAppearance(); }).show();
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle(s(R.string.app_name)).setIcon(getApplicationInfo().icon)
            .setMessage(s(R.string.author_thanks)).setPositiveButton(s(R.string.close),null).show();
    }
    private void chooseLanguage() {
        String[] tags={"system","zh","en","ja"};
        String current=Languages.choice(this),normalized=LanguagePolicy.supported(current);
        int selected=0; for(int i=1;i<tags.length;i++) if(tags[i].equals(normalized)) selected=i;
        new AlertDialog.Builder(this).setTitle(s(R.string.language_title))
            .setSingleChoiceItems(new String[]{s(R.string.language_system),"简体中文","English","日本語"},selected,(dialog,index) -> {
                dialog.dismiss(); if(updateChecker!=null) updateChecker.cancel(); Languages.choose(this,tags[index]); recreate();
            }).setNegativeButton(s(R.string.cancel),null).show();
    }
    private void permission() { startActivity(new Intent(this,SetupActivity.class)); }
    private EditText input(String value,boolean decimal) { EditText e=new EditText(this); e.setInputType(InputType.TYPE_CLASS_NUMBER|(decimal?InputType.TYPE_NUMBER_FLAG_DECIMAL:0)); e.setSingleLine(); e.setText(value); e.selectAll(); e.setPadding(d(24),d(12),d(24),d(12)); return e; }
    private TextView detail(String key,String value) { TextView t=Ui.action(this,key+"    "+value,Ui.INK,0xfff0f0f2); t.setMinHeight(d(48)); body.addView(t,new LinearLayout.LayoutParams(-1,-2)); return t; }
    private void section(String text) { body.addView(Ui.text(this,text,12,Ui.MUTED)); gap(12); }
    private void gap(int h) { body.addView(new View(this),new LinearLayout.LayoutParams(1,d(h))); }
    private int d(int n) { return Ui.dp(this,n); }
}
