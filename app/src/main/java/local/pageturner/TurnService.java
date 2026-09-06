package local.pageturner;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;

public class TurnService extends AccessibilityService {
    public static TurnService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private LinearLayout panel;
    private TextView status;
    private TextView toggle;
    private LinearLayout panelHeader, panelRow;
    private Bubble bubble;
    private FocusMask focusMask;
    private boolean immersive;
    private final Runnable focusTicker=new Runnable() {
        public void run() {
            if(!running || !immersive || !guard()) return;
            AccessibilityNodeInfo root=getRootInActiveWindow(); Rect image;
            try { image=PageProbe.imageBounds(root,screenWidth,screenHeight); } finally { if(root!=null) root.recycle(); }
            if(image==null) { if(focusMask!=null) focusMask.setVisibility(View.GONE); }
            else {
                if(focusMask==null) {
                    focusMask=new FocusMask();
                    try { manager.addView(focusMask,overlay(-1,-1,true)); }
                    catch(RuntimeException e) { focusMask=null; immersive=false; return; }
                }
                focusMask.image.set(image); focusMask.setVisibility(View.VISIBLE); focusMask.invalidate();
            }
            handler.postDelayed(this,busy?120:500);
        }
    };
    private boolean collapsed;
    private View picker, marker;
    private WindowManager.LayoutParams panelParams;
    private boolean running, busy, smart;
    private int mode, phase, steps=2;
    private long swipeDuration=10000, busyUntil;
    private int scrollKind=ScrollPlan.PAGE, passSwipes;
    private ScrollPlan scrollPlan;
    private final ScrollWatch scrollWatch=new ScrollWatch();
    private float metricScale=1;
    private int metricBefore=-1;
    private String target, homePackage = "";
    private int targetWindow, screenWidth, screenHeight, generation, count, rotation;
    private float pointX = .8f, pointY = .5f;
    private long interval, due, stopAt, busyAt;
    private long ownStart, ownEnd;
    private boolean autoBottom,checkProgress,watching;
    private final ProgressWatch progressWatch=new ProgressWatch();
    private int missedTurns;
    private String ownScrollKey;
    private long nextProbe;
    private String reason = "";
    private String panelLanguage;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { pause(s(R.string.screen_off)); }
    };
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            if (!guard()) return;
            if (now >= stopAt) { pause(s(R.string.timer_ended)); return; }
            if (busy && now > busyUntil) { pause(s(R.string.action_timeout)); return; }
            if(watching && now>=nextProbe) checkTurn(now);
            if (!running) return;
            if (!busy && !watching && now >= due) performTurn();
            if (!running) return;
            status.setText(busy?s(R.string.slow_scrolling):watching?s(R.string.waiting_page):s(R.string.action_countdown,s(mode==1 || mode==2 && scrollWatch.bottom(ownScrollKey,ReadingRules.UNKNOWN)!=ReadingRules.BOTTOM?R.string.action_scroll:smart?R.string.action_find:R.string.action_turn),Math.max(0,(due-now+999)/1000)));
            if (bubble!=null) bubble.invalidate();
            handler.postDelayed(this, 500);
        }
    };
    @Override protected void onServiceConnected() {
        manager = (WindowManager)getSystemService(WINDOW_SERVICE);
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(home, 0);
        if (info != null && info.activityInfo != null) homePackage = info.activityInfo.packageName;
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        instance = this;
        // Connecting or reconnecting never starts a session.
    }
    public boolean isPanelVisible() { return panel!=null && panel.isAttachedToWindow(); }
    public boolean showControlPoint() {
        showPanel();
        // addView schedules attachment; do not treat the first layout delay as denial.
        if(panel==null) return false;
        collapsePanel(); return true;
    }
    public void showPanel() {
        if(manager==null) return;
        panelLanguage=Languages.tag(this);
        pause(s(R.string.open_page_start));
        if (panel != null) { expandPanel(); return; }
        panel = new LinearLayout(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if(event.getActionMasked()==MotionEvent.ACTION_OUTSIDE) {
                    if(running && !Rules.ownInjectedTouch(event.getDeviceId(),event.getToolType(0),event.getEventTime(),ownStart,ownEnd)) pause(s(R.string.manual_paused));
                    return true;
                }
                return super.dispatchTouchEvent(event);
            }
        }; panel.setOrientation(LinearLayout.VERTICAL);
        panelHeader=new LinearLayout(this); panelHeader.setGravity(Gravity.CENTER_VERTICAL); panel.addView(panelHeader);
        status=Ui.text(this,"",12,0xffc1c2c9); status.setMaxLines(2); status.setPadding(dp(8),dp(6),0,dp(6)); panelHeader.addView(status,new LinearLayout.LayoutParams(0,dp(40),1));
        TextView fold=Ui.action(this,"−",Color.WHITE,Color.TRANSPARENT); fold.setContentDescription(s(R.string.collapse_panel)); panelHeader.addView(fold,new LinearLayout.LayoutParams(dp(44),dp(40))); fold.setOnClickListener(v -> { if(running) pause(s(R.string.manual_paused)); collapsePanel(); });
        panelRow=new LinearLayout(this); panel.addView(panelRow);
        toggle=smallButton(panelRow,s(R.string.start),v -> { if(running) pause(s(R.string.paused)); else startSession(); });
        smallButton(panelRow,s(R.string.pick),v -> pickPoint());
        smallButton(panelRow,s(R.string.settings),v -> { pause(s(R.string.paused)); startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); });
        smallButton(panelRow,"×",v -> hidePanel()).setContentDescription(s(R.string.stop_hide));
        bubble=new Bubble(); panel.addView(bubble,new LinearLayout.LayoutParams(dp(44),dp(44))); bubble.setVisibility(View.GONE);
        bubble.setContentDescription(s(R.string.dot_description));
        panelParams=overlay(dp(244),-2,false); panelParams.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL; panelParams.x=dp(8); panelParams.y=dp(100);
        attachDrag(status,false); attachDrag(bubble,true);
        panel.setPadding(dp(8),dp(4),dp(8),dp(6)); panel.setBackground(Ui.shape(this,0xf51f2025,22));
        try { manager.addView(panel,panelParams); }
        catch(WindowManager.BadTokenException | WindowManager.InvalidDisplayException | SecurityException e) {
            panel=null; status=null; toggle=null; bubble=null; panelHeader=null; panelRow=null;
            Toast.makeText(this,s(R.string.panel_failed),Toast.LENGTH_LONG).show(); return;
        }
        collapsed=false; refreshPaused();
    }
    public void hidePanel() { pause(s(R.string.stopped)); phase=0; removePicker(); removeMarker(); if (panel != null) { manager.removeView(panel); panel=null; status=null; toggle=null; bubble=null; } }
    public void pause(String message) {
        running=false; busy=false; generation++; handler.removeCallbacks(ticker); reason=message;
        handler.removeCallbacks(focusTicker);
        if(focusMask!=null) { manager.removeView(focusMask); focusMask=null; }
        watching=false; progressWatch.clear(); missedTurns=0; clearScroll();
        if (panel != null) { panelParams.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON; manager.updateViewLayout(panel,panelParams); }
        refreshPaused();
    }
    private void refreshPaused() { if(status!=null) status.setText(reason); if(toggle!=null) toggle.setText(s(R.string.start)); updateAppearance(); }
    public void updateAppearance() { if(bubble!=null) { bubble.setAlpha(running?Math.max(.1f,Math.min(1,getSharedPreferences("settings",0).getInt("opacity",35)/100f)):.85f); bubble.invalidate(); } }
    private void collapsePanel() {
        if(panel==null || picker!=null) return;
        collapsed=true; panelHeader.setVisibility(View.GONE); panelRow.setVisibility(View.GONE); bubble.setVisibility(View.VISIBLE);
        panel.setPadding(0,0,0,0); panel.setBackgroundColor(Color.TRANSPARENT); panelParams.width=dp(44); panelParams.height=dp(44);
        manager.updateViewLayout(panel,panelParams); updateAppearance(); clampPanel();
    }
    private void expandPanel() {
        if(panel==null) return;
        collapsed=false; bubble.setVisibility(View.GONE); panelHeader.setVisibility(View.VISIBLE); panelRow.setVisibility(View.VISIBLE);
        panel.setPadding(dp(8),dp(4),dp(8),dp(6)); panel.setBackground(Ui.shape(this,0xf51f2025,22)); panelParams.width=dp(244); panelParams.height=-2;
        manager.updateViewLayout(panel,panelParams); clampPanel();
    }
    private void clampPanel() { if(panel!=null) panel.post(() -> { if(panel==null) return; Point s=size(); panelParams.x=Math.max(0,Math.min(s.x-panel.getWidth(),panelParams.x)); panelParams.y=Math.max(dp(28),Math.min(s.y-panel.getHeight()-dp(28),panelParams.y)); manager.updateViewLayout(panel,panelParams); }); }
    private void attachDrag(View view,boolean isBubble) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float x,y; int px,py; boolean moved,held,wasRunning;
            final Runnable hold=() -> { held=true; if(isBubble) expandPanel(); };
            public boolean onTouch(View v,MotionEvent e) {
                if(panel==null) return true;
                if(e.getAction()==MotionEvent.ACTION_DOWN) { wasRunning=running; if(running) pause(s(R.string.manual_paused)); x=e.getRawX(); y=e.getRawY(); px=panelParams.x; py=panelParams.y; moved=false; held=false; if(isBubble) handler.postDelayed(hold,650); return true; }
                if(e.getAction()==MotionEvent.ACTION_MOVE) { if(Math.hypot(e.getRawX()-x,e.getRawY()-y)>dp(7)) { moved=true; handler.removeCallbacks(hold); } if(moved) { Point s=size(); panelParams.x=Math.max(0,Math.min(s.x-panel.getWidth(),px+(int)(e.getRawX()-x))); panelParams.y=Math.max(dp(28),Math.min(s.y-panel.getHeight()-dp(28),py+(int)(e.getRawY()-y))); manager.updateViewLayout(panel,panelParams); } return true; }
                if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) { handler.removeCallbacks(hold); if(isBubble && ReadingRules.bubbleRelease(wasRunning,moved,held,e.getAction()==MotionEvent.ACTION_CANCEL)==ReadingRules.START) startSession(true); return true; }
                return true;
            }
        });
    }
    private class Bubble extends View {
        final Paint paint=new Paint(3);
        Bubble() { super(TurnService.this); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
        protected void onDraw(Canvas c) {
            float cx=getWidth()/2f,cy=getHeight()/2f,r=dp(15); paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.INK); c.drawCircle(cx,cy,r,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0xffd0d1d6); c.drawCircle(cx,cy,r,paint);
            if(running) { paint.setStrokeWidth(dp(2)); paint.setColor(Color.WHITE); float fraction=Math.max(0,Math.min(1,(due-SystemClock.uptimeMillis())/(float)Math.max(3000,interval))); c.drawArc(cx-r,cy-r,cx+r,cy+r,-90,360*fraction,false,paint); }
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            if(running) { c.drawRoundRect(cx-dp(4),cy-dp(5),cx-dp(1),cy+dp(5),dp(1),dp(1),paint); c.drawRoundRect(cx+dp(1),cy-dp(5),cx+dp(4),cy+dp(5),dp(1),dp(1),paint); }
            else c.drawCircle(cx,cy,dp(2),paint);
        }
    }
    private class FocusMask extends View {
        final Rect image=new Rect(); final Paint paint=new Paint();
        FocusMask() { super(TurnService.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); paint.setColor(Color.BLACK); }
        @Override protected void onDraw(Canvas canvas) {
            int save=canvas.save(); int[] origin=new int[2]; getLocationOnScreen(origin);
            canvas.clipOutRect(image.left-origin[0],image.top-origin[1],image.right-origin[0],image.bottom-origin[1]);
            if(panel!=null) { int[] p=new int[2]; panel.getLocationOnScreen(p); canvas.clipOutRect(p[0]-origin[0]-dp(2),p[1]-origin[1]-dp(2),p[0]-origin[0]+panel.getWidth()+dp(2),p[1]-origin[1]+panel.getHeight()+dp(2)); }
            canvas.drawPaint(paint); canvas.restoreToCount(save);
        }
    }
    private void startSession() {
        startSession(false);
    }
    private void startSession(boolean quick) {
        if(panel==null) return;
        removePicker(); removeMarker();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) { if(root!=null) root.recycle(); pause(s(R.string.page_unreadable)); expandPanel(); return; }
        String pkg=root.getPackageName().toString();
        if(quick && target!=null && !target.equals(pkg)) { root.recycle(); pause(s(R.string.app_changed_confirm)); expandPanel(); return; }
        if (pkg.equals(homePackage) || pkg.equals("com.android.systemui") || pkg.equals("com.android.settings") || pkg.equals("android")
            || (pkg.equals(getPackageName()) && !PracticeActivity.visible)) { root.recycle(); pause(s(R.string.open_page_first)); expandPanel(); return; }
        SharedPreferences p=getSharedPreferences("settings",0);
        Point s=size(); int nextMode=p.getInt("mode",p.getBoolean("smart",false)?3:0);
        int nextSteps=Math.max(1,Math.min(6,p.getInt("steps",2)));
        if(!Rules.sameTarget(target,pkg,targetWindow,root.getWindowId(),screenWidth,screenHeight,s.x,s.y) || mode!=nextMode || steps!=nextSteps) phase=0;
        target=pkg; targetWindow=root.getWindowId(); root.recycle();
        screenWidth=s.x; screenHeight=s.y; rotation=manager.getDefaultDisplay().getRotation();
        String profile=ScreenProfiles.key(manager);
        mode=nextMode; steps=nextSteps;
        scrollKind=p.getInt("scroll_kind",ScrollPlan.PAGE)==ScrollPlan.HALVES?ScrollPlan.HALVES:ScrollPlan.PAGE;
        swipeDuration=Rules.swipeDuration(p.getLong("swipe_duration",10000));
        pointX=ScreenProfiles.get(p,profile,"x",.8f); pointY=ScreenProfiles.get(p,profile,"y",.5f);
        autoBottom=true; checkProgress=p.getBoolean("check_progress",true);
        watching=false; missedTurns=0; progressWatch.clear(); clearScroll();
        interval=p.getLong("interval",10000); smart=mode==3;
        immersive=p.getBoolean("immersive",false);
        count=0; busy=false; running=true; generation++;
        due=SystemClock.uptimeMillis()+Math.max(3000,interval);
        stopAt=SystemClock.uptimeMillis()+p.getInt("minutes",30)*60000L;
        if (!guard()) return;
        panelParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON; manager.updateViewLayout(panel,panelParams);
        toggle.setText(s(R.string.pause)); updateAppearance(); handler.removeCallbacks(ticker); handler.post(ticker);
        handler.removeCallbacks(focusTicker); if(immersive) handler.post(focusTicker);
        final int token=generation; handler.postDelayed(() -> { if(running && token==generation) collapsePanel(); },700);
    }
    private boolean guard() {
        if (!running) return false;
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager key=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if (!power.isInteractive() || key.isKeyguardLocked()) { pause(s(R.string.locked_paused)); return false; }
        Point s=size();
        AccessibilityNodeInfo root=getRootInActiveWindow();
        String pkg=root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
        boolean valid=root != null && Rules.sameTarget(target,pkg,targetWindow,root.getWindowId(),screenWidth,screenHeight,s.x,s.y)
            && rotation == manager.getDefaultDisplay().getRotation();
        if (root != null) root.recycle();
        if (!valid) { pause(s(R.string.window_changed)); return false; }
        List<AccessibilityWindowInfo> windows=getWindows();
        boolean obstructed=false;
        for (AccessibilityWindowInfo w:windows) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                ((w.isActive() || w.isFocused()) && w.getType() != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && w.getId() != targetWindow)) obstructed=true;
            w.recycle();
        }
        if (obstructed) { pause(s(R.string.window_obstructed)); return false; }
        return true;
    }
    private void performTurn() {
        if (!guard()) return;
        PageProbe.Snapshot before=snapshot();
        if (smart) { clickNextNode(before,false); return; }
        int bottom=scrollWatch.bottom(before.scrollKey,before.bottom);
        if(mode==1 && bottom==ReadingRules.BOTTOM) { pause(s(R.string.reached_bottom)); expandPanel(); return; }
        boolean swipe=(mode==1 || mode==2) && bottom!=ReadingRules.BOTTOM;
        if(swipe) {
            scrollPlan=new ScrollPlan(scrollKind,swipeDuration); passSwipes=0;
            scrollPlan.locate(scrollWatch.position(),scrollWatch.maximum(),SystemClock.uptimeMillis());
            scrollPart(before); return;
        }
        if(mode==2 && clickNextNode(before,true)) return;
        float x=screenWidth*pointX, y=screenHeight*pointY;
        float endY=y;
        if (pathHitsPanel(x,y,endY)) { pause(s(R.string.move_dot)); return; }
        Path path=new Path(); path.moveTo(x,y);
        long duration=70;
        GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
        if(!guard()) return;
        ownScrollKey=null;
        ownStart=SystemClock.uptimeMillis(); ownEnd=ownStart+duration+250;
        busy=true; busyAt=SystemClock.uptimeMillis(); busyUntil=busyAt+duration+3000; final int token=generation;
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { if (running && token==generation) completed(swipe,before); }
            @Override public void onCancelled(GestureDescription g) { if (token==generation) pause(s(R.string.gesture_interrupted)); }
        },handler);
        if (!accepted) pause(s(R.string.gesture_rejected));
    }
    private void scrollPart(PageProbe.Snapshot before) {
        if(!guard() || scrollPlan==null) return;
        if(++passSwipes>60) { pause(s(R.string.bottom_not_found)); expandPanel(); return; }
        Rect area=before.scrollBounds==null?new Rect(0,dp(32),screenWidth,screenHeight-dp(32)):new Rect(before.scrollBounds);
        if(!area.intersect(dp(12),dp(24),screenWidth-dp(12),screenHeight-dp(24)) || area.height()<dp(80)) { pause(s(R.string.page_unreadable)); return; }
        int yPosition=scrollWatch.position(),maximum=scrollWatch.maximum(); long now=SystemClock.uptimeMillis();
        scrollPlan.locate(yPosition,maximum,now);
        if(scrollPlan.reached(yPosition,maximum)) { finishPass(); return; }
        // A short first movement obtains the browser's actual scroll range.
        float intended=scrollPlan.located()?scrollPlan.distance(yPosition,maximum,area.height(),metricScale):passSwipes==1?ViewConfiguration.get(this).getScaledTouchSlop()*2f+2:area.height()*.85f;
        float distance=Math.min(area.height()*.85f,Math.max(ViewConfiguration.get(this).getScaledTouchSlop()*2f+2,intended));
        long duration=scrollPlan.located()?scrollPlan.duration(yPosition,maximum,distance,metricScale,now):passSwipes==1?180:swipeDuration;
        if(distance<1) { finishPass(); return; }
        float x=area.exactCenterX(),from=area.bottom-area.height()*.06f,to=from-distance;
        if(pathHitsPanel(x,from,to)) { pause(s(R.string.move_dot)); return; }
        ownScrollKey=before.scrollKey; scrollWatch.begin(ownScrollKey,before.viewport); metricBefore=scrollWatch.position();
        ownStart=now; ownEnd=now+duration+1000; busy=true; busyAt=now; busyUntil=now+duration+5000;
        final int token=generation; final ScrollPlan plan=scrollPlan;
        // Continued short strokes let pause end a slow drag without injecting a new tap.
        dragSegment(null,x,from,to,from,duration,duration,token,() -> {
            if(!running || token!=generation || plan!=scrollPlan) return;
            ownEnd=SystemClock.uptimeMillis()+250;
            busyUntil=SystemClock.uptimeMillis()+3000;
            handler.postDelayed(() -> {
                if(!running || token!=generation || plan!=scrollPlan || !guard()) return;
                PageProbe.Snapshot after=snapshot();
                int end=scrollWatch.finish(after.scrollKey,after.viewport,after.bottom);
                int position=scrollWatch.position(),max=scrollWatch.maximum();
                if(metricBefore>=0 && position-metricBefore>dp(8)) metricScale=Math.max(.25f,Math.min(4,distance/(position-metricBefore)));
                scrollPlan.locateAfterProbe(position,max,distance/metricScale,SystemClock.uptimeMillis());
                if(end==ReadingRules.BOTTOM || scrollPlan.reached(position,max)) { finishPass(); return; }
                if(!scrollPlan.located() && scrollKind==ScrollPlan.HALVES) { pause(s(R.string.half_unavailable)); expandPanel(); return; }
                if(end==ReadingRules.UNKNOWN && passSwipes>=2) { pause(s(R.string.scroll_unreadable)); expandPanel(); return; }
                scrollPart(after);
            },350);
        });
    }
    private void dragSegment(GestureDescription.StrokeDescription previous,float x,float from,float to,float current,long remaining,long total,int token,Runnable done) {
        long slice=Math.min(250,remaining);
        float next=current+(to-current)*slice/remaining;
        // Cross touch slop promptly so very slow image drags do not become long presses.
        if(previous==null && remaining>250) {
            slice=Math.min(100,remaining);
            float kick=Math.min(from-to,ViewConfiguration.get(this).getScaledTouchSlop()*2f+1);
            next=from-kick;
        }
        boolean more=remaining>slice && next>to+.5f;
        if(!more) next=to;
        Path path=new Path(); path.moveTo(x,current); path.lineTo(x,next);
        final float end=next; final long rest=remaining-slice;
        GestureDescription.StrokeDescription stroke=previous==null?new GestureDescription.StrokeDescription(path,0,slice,more):previous.continueStroke(path,0,slice,more);
        boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                if(!running || token!=generation || !guard()) {
                    if(stroke.willContinue()) { Path up=new Path(); up.moveTo(x,end); dispatchGesture(new GestureDescription.Builder().addStroke(stroke.continueStroke(up,0,1,false)).build(),null,handler); }
                    return;
                }
                if(more) { ownEnd=SystemClock.uptimeMillis()+rest+1000; busyUntil=SystemClock.uptimeMillis()+rest+5000; dragSegment(stroke,x,from,to,end,rest,total,token,done); } else done.run();
            }
            @Override public void onCancelled(GestureDescription g) { if(running && token==generation) pause(s(R.string.gesture_interrupted)); }
        },handler);
        if(!accepted) pause(s(R.string.gesture_rejected));
    }
    private void finishPass() {
        busy=false; count++; phase++; scrollPlan=null;
        due=SystemClock.uptimeMillis()+interval;
    }
    private void completed(boolean swipe,PageProbe.Snapshot before) {
        busy=false; count++; phase=swipe?phase+1:0; long now=SystemClock.uptimeMillis(); due=now+interval;
        if(!swipe) { clearScroll(); if(checkProgress) { progressWatch.begin(before.url,before.content,now,interval); watching=true; nextProbe=now+750; } }
    }
    private PageProbe.Snapshot snapshot() {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        try { return PageProbe.capture(root,screenWidth,screenHeight); } finally { if(root!=null) root.recycle(); }
    }
    private void checkTurn(long now) {
        PageProbe.Snapshot current=snapshot(); nextProbe=now+750;
        int result=progressWatch.observe(current.url,current.content,now);
        if(result==ProgressWatch.WAIT) return;
        watching=false; progressWatch.clear();
        if(result==ProgressWatch.CHANGED) { missedTurns=0; due=Math.max(due,now+interval); }
        else if(ReadingRules.stopAfterMisses(++missedTurns)) {
            pause(result==ProgressWatch.UNREADABLE?s(R.string.progress_unreadable):s(R.string.progress_unchanged)); expandPanel();
        }
    }
    private void clearScroll() { ownScrollKey=null; scrollWatch.clear(); scrollPlan=null; metricScale=1; }
    private boolean clickNextNode(PageProbe.Snapshot before,boolean optional) {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if (root == null) { pause(s(R.string.page_lost)); return true; }
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>(); queue.add(root);
        LinkedHashMap<String,AccessibilityNodeInfo> matches=new LinkedHashMap<>(); int visited=0;
        while (!queue.isEmpty() && visited++ < 2000) {
            AccessibilityNodeInfo node=queue.remove();
            if (node.isVisibleToUser() && node.isEnabled() && !node.isPassword() && !node.isEditable()
                && (Rules.nextLabel(node.getText()) || Rules.nextLabel(node.getContentDescription()))) {
                AccessibilityNodeInfo candidate=AccessibilityNodeInfo.obtain(node);
                for (int level=0; level<3 && candidate != null && !candidate.isClickable(); level++) {
                    AccessibilityNodeInfo parent=candidate.getParent(); candidate.recycle(); candidate=parent;
                }
                if (candidate != null) {
                    Rect rect=new Rect(); candidate.getBoundsInScreen(rect);
                    if (candidate.isClickable() && candidate.isEnabled() && candidate.isVisibleToUser() && !rect.isEmpty()
                        && rect.left>=0 && rect.top>=0 && rect.right<=screenWidth && rect.bottom<=screenHeight
                        && rect.width()*1L*rect.height()<screenWidth*1L*screenHeight*.85) {
                        String id=candidate.getWindowId()+":"+rect.toShortString();
                        if (!matches.containsKey(id)) matches.put(id,candidate); else candidate.recycle();
                    } else candidate.recycle();
                }
            }
            for (int i=0; i<node.getChildCount(); i++) { AccessibilityNodeInfo child=node.getChild(i); if (child != null) queue.add(child); }
            node.recycle();
        }
        boolean truncated=!queue.isEmpty(); while(!queue.isEmpty()) queue.remove().recycle();
        if (truncated || matches.size()!=1) {
            for(AccessibilityNodeInfo n:matches.values()) n.recycle();
            if(optional && !truncated && matches.isEmpty()) return false;
            pause(truncated ? s(R.string.page_complex) : matches.isEmpty() ? s(R.string.next_missing) : s(R.string.next_ambiguous)); return true;
        }
        AccessibilityNodeInfo n=matches.values().iterator().next();
        if (!guard()) { n.recycle(); return true; }
        boolean ok=n.refresh() && n.isEnabled() && n.isVisibleToUser() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK); n.recycle();
        if (ok) completed(false,before); else pause(s(R.string.button_failed));
        return true;
    }
    private void pickPoint() {
        pause(s(R.string.pick_tip)); removePicker(); removeMarker();
        FrameLayout surface=new FrameLayout(this); surface.setBackgroundColor(0x22000000);
        TextView tip=Ui.action(this,s(R.string.pick_title),Color.WHITE,0xe0202126); tip.setClickable(false);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL); tp.topMargin=dp(160); surface.addView(tip,tp);
        TextView cancel=Ui.action(this,s(R.string.cancel),Color.WHITE,0xe0202126); FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.RIGHT); cp.topMargin=dp(32); cp.rightMargin=dp(12); surface.addView(cancel,cp);
        cancel.setOnClickListener(v -> { removePicker(); pause(s(R.string.pick_cancelled)); });
        surface.setOnTouchListener((v,event) -> {
            if (event.getAction()==MotionEvent.ACTION_UP) {
                Point s=size(); float x=event.getRawX(), y=event.getRawY();
                if (x<dp(24) || x>s.x-dp(24) || y<dp(28) || y>s.y-dp(28)) { Toast.makeText(this,s(R.string.pick_edge),Toast.LENGTH_SHORT).show(); return true; }
                pointX=x/s.x; pointY=y/s.y;
                SharedPreferences settings=getSharedPreferences("settings",0);
                int selected=settings.getInt("mode",settings.getBoolean("smart",false)?3:0);
                String profile=ScreenProfiles.key(manager); ScreenProfiles.put(settings,profile,"x",pointX); ScreenProfiles.put(settings,profile,"y",pointY);
                settings.edit().putBoolean("smart",false).putInt("mode",selected==2?2:0).apply(); phase=0;
                removePicker(); showMarker(x,y); pause(s(R.string.pick_saved)); return true;
            } return true;
        });
        picker=surface; manager.addView(picker,overlay(-1,-1,false));
    }
    private void showMarker(float x,float y) {
        TextView cross=new TextView(this); cross.setText("⊕"); cross.setTextSize(30); cross.setGravity(Gravity.CENTER); cross.setTextColor(Ui.INK); cross.setBackground(Ui.shape(this,0xccffffff,20));
        WindowManager.LayoutParams p=overlay(dp(40),dp(40),true); p.x=(int)x-dp(20); p.y=(int)y-dp(20);
        marker=cross; manager.addView(marker,p);
    }
    private boolean insidePanel(float x,float y) { if(panel==null) return false; int[] pos=new int[2]; panel.getLocationOnScreen(pos); return x>=pos[0] && x<=pos[0]+panel.getWidth() && y>=pos[1] && y<=pos[1]+panel.getHeight(); }
    private boolean pathHitsPanel(float x,float y,float endY) { if(panel==null) return false; int[] pos=new int[2]; panel.getLocationOnScreen(pos); return x>=pos[0] && x<=pos[0]+panel.getWidth() && Math.max(y,endY)>=pos[1] && Math.min(y,endY)<=pos[1]+panel.getHeight(); }
    private void removePicker() { if(picker!=null) { manager.removeView(picker); picker=null; } }
    private void removeMarker() { if(marker!=null) { manager.removeView(marker); marker=null; } }
    private Point size() { Point p=new Point(); manager.getDefaultDisplay().getRealSize(p); return p; }
    private WindowManager.LayoutParams overlay(int w,int h,boolean pass) {
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if(pass) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;
        if(Build.VERSION.SDK_INT>=28) p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }
    private TextView smallButton(LinearLayout row,String text,View.OnClickListener action) { TextView b=Ui.action(this,text,Color.WHITE,Color.TRANSPARENT); b.setTextSize(13); b.setPadding(0,0,0,0); row.addView(b,new LinearLayout.LayoutParams(0,dp(44),1)); b.setOnClickListener(action); return b; }
    private String s(int id,Object... values) { Context context=Languages.wrap(this); return values.length==0?context.getString(id):context.getString(id,values); }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if(!running || !guard()) return;
        if(e.getEventType()==AccessibilityEvent.TYPE_VIEW_SCROLLED && e.getWindowId()==targetWindow && e.getPackageName()!=null && target.equals(e.getPackageName().toString())) {
            // Accept metrics from the main scroller only, during our own swipe.
            long now=SystemClock.uptimeMillis();
            if(now<ownStart || now>ownEnd+500) return;
            AccessibilityNodeInfo source=e.getSource(); if(source==null) return;
            String key=PageProbe.nodeKey(source); source.recycle();
            if(key.equals(ownScrollKey)) {
                scrollWatch.metric(key,e.getScrollY(),e.getMaxScrollY());
            }
        }
    }
    @Override public void onInterrupt() { pause(s(R.string.service_interrupted)); }
    @Override public void onConfigurationChanged(Configuration c) {
        if(panel!=null && !Languages.tag(this).equals(panelLanguage)) { super.onConfigurationChanged(c); hidePanel(); return; }
        super.onConfigurationChanged(c); pause(s(R.string.layout_changed)); removePicker(); removeMarker();
        if(panel!=null) { panelParams.x=dp(10); panelParams.y=dp(48); manager.updateViewLayout(panel,panelParams); }
    }
    @Override public void onDestroy() {
        hidePanel(); handler.removeCallbacksAndMessages(null); try { unregisterReceiver(screenReceiver); } catch(IllegalArgumentException ignored) {}
        instance=null; super.onDestroy();
    }
}
