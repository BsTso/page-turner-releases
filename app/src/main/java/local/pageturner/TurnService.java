package local.pageturner;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.util.*;

public class TurnService extends AccessibilityService {
    private static final String CHANNEL_ID = "pageturner_active";
    private static final int NOTIF_ID = 1001;
    public static TurnService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private LinearLayout panel;
    private TextView status;
    private TextView toggle;
    private LinearLayout panelHeader, panelRow;
    private Bubble bubble;
    private FocusFrame focusFrame;
    private boolean immersive, focusSuspendedForTap, keepAliveActive;
    private Rect lastScrollBounds;
    private long lastEventGuard;
    private final Runnable focusTicker = new Runnable() {
        public void run() {
            if (!running || !immersive) return;
            if (!watching && !focusSuspendedForTap) {
                try {
                    AccessibilityNodeInfo root = getRootInActiveWindow(); Rect image = null;
                    try { if (root != null) image = PageProbe.imageBounds(root, screenWidth, screenHeight, lastScrollBounds); }
                    finally { if (root != null) root.recycle(); }
                    updateFocus(image);
                } catch (RuntimeException ignored) {}
            }
            if (running && immersive) handler.postDelayed(this, busy ? 280 : 600);
        }
    };
    private boolean collapsed;
    private View picker, marker;
    private WindowManager.LayoutParams panelParams;
    private boolean running, busy, smart;
    private int mode, phase, steps = 2;
    private long swipeDuration = 10000, busyUntil;
    private int scrollKind = ScrollPlan.PAGE, passSwipes;
    private ScrollPlan scrollPlan;
    private final ScrollWatch scrollWatch = new ScrollWatch();
    private float metricScale = 1;
    private int metricBefore = -1;
    private GestureDescription.StrokeDescription activeStroke;
    private float activeStrokeX, activeStrokeY;
    private long gestureId;
    private String target, homePackage = "";
    private int targetWindow, screenWidth, screenHeight, generation, count, rotation;
    private float pointX = .8f, pointY = .5f;
    private long interval, due, stopAt, busyAt;
    private long ownStart, ownEnd;
    private boolean autoBottom, checkProgress, watching;
    private final ProgressWatch progressWatch = new ProgressWatch();
    private String turnScrollKey;
    private int turnFromY = -1;
    private String ownScrollKey;
    private long nextProbe;
    private String reason = "";
    private String panelLanguage;
    private boolean receiverRegistered;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { pause(s(R.string.screen_off)); }
    };
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try {
                long now = SystemClock.uptimeMillis();
                if (!guard()) return;
                if (now >= stopAt) { pause(s(R.string.timer_ended)); return; }
                if (busy && now > busyUntil) { pause(s(R.string.action_timeout)); return; }
                if (watching && now >= nextProbe) checkTurn(now);
                if (!running) return;
                if (!busy && !watching && now >= due) performTurn();
                if (!running) return;
                status.setText(busy ? s(R.string.slow_scrolling) : watching ? s(R.string.waiting_page) : s(R.string.action_countdown, s(mode == 1 || mode == 2 && scrollWatch.bottom(ownScrollKey, ReadingRules.UNKNOWN) != ReadingRules.BOTTOM ? R.string.action_scroll : smart ? R.string.action_find : R.string.action_turn), Math.max(0, (due - now + 999) / 1000)));
                if (bubble != null) bubble.invalidate();
            } catch (RuntimeException ignored) {}
            if (running) handler.postDelayed(this, 500);
        }
    };
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(home, 0);
        if (info != null && info.activityInfo != null) homePackage = info.activityInfo.packageName;
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, filter);
            receiverRegistered = true;
        }
        instance = this;
        SetupAccess.rememberGrant(this);
        // Connecting or reconnecting never starts a session.
    }
    public boolean isPanelVisible() { return panel != null && panel.isAttachedToWindow(); }
    public boolean showControlPoint() {
        showPanel();
        // addView schedules attachment; do not treat the first layout delay as denial.
        if (panel == null) return false;
        collapsePanel(); return panel != null;
    }
    public void showPanel() {
        if (manager == null) return;
        panelLanguage = Languages.tag(this);
        pause(s(R.string.open_page_start));
        if (panel != null) { startKeepAlive(); expandPanel(); return; }
        panel = new LinearLayout(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    if (running && !Rules.ownInjectedTouch(event.getDeviceId(), event.getToolType(0), event.getEventTime(), ownStart, ownEnd)) pause(s(R.string.manual_paused));
                    return true;
                }
                return super.dispatchTouchEvent(event);
            }
        }; panel.setOrientation(LinearLayout.VERTICAL);
        panelHeader = new LinearLayout(this); panelHeader.setGravity(Gravity.CENTER_VERTICAL); panel.addView(panelHeader);
        status = Ui.text(this, "", 12, 0xffc1c2c9); status.setMaxLines(2); status.setPadding(dp(8), dp(6), 0, dp(6)); panelHeader.addView(status, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView fold = Ui.action(this, "−", Color.WHITE, Color.TRANSPARENT); fold.setContentDescription(s(R.string.collapse_panel)); panelHeader.addView(fold, new LinearLayout.LayoutParams(dp(44), dp(40))); fold.setOnClickListener(v -> { if (running) pause(s(R.string.manual_paused)); collapsePanel(); });
        panelRow = new LinearLayout(this); panel.addView(panelRow);
        toggle = smallButton(panelRow, s(R.string.start), v -> { if (running) pause(s(R.string.paused)); else startSession(); });
        smallButton(panelRow, s(R.string.pick), v -> pickPoint());
        smallButton(panelRow, s(R.string.settings), v -> { pause(s(R.string.paused)); startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); });
        smallButton(panelRow, "×", v -> hidePanel()).setContentDescription(s(R.string.stop_hide));
        bubble = new Bubble(); panel.addView(bubble, new LinearLayout.LayoutParams(dp(44), dp(44))); bubble.setVisibility(View.GONE);
        bubble.setContentDescription(s(R.string.dot_description));
        panelParams = overlay(dp(244), -2, false); panelParams.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL; panelParams.x = dp(8); panelParams.y = dp(100);
        attachDrag(status, false); attachDrag(bubble, true);
        panel.setPadding(dp(8), dp(4), dp(8), dp(6)); panel.setBackground(Ui.shape(this, 0xf51f2025, 22));
        try { manager.addView(panel, panelParams); }
        catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException | SecurityException | IllegalArgumentException e) {
            panel = null; status = null; toggle = null; bubble = null; panelHeader = null; panelRow = null;
            Toast.makeText(this, s(R.string.panel_failed), Toast.LENGTH_LONG).show(); return;
        }
        startKeepAlive();
        collapsed = false; refreshPaused();
    }
    public void hidePanel() {
        pause(s(R.string.stopped)); phase = 0; removePicker(); removeMarker();
        if (panel != null) { removeOverlay(panel); panel = null; status = null; toggle = null; bubble = null; panelHeader = null; panelRow = null; }
        stopKeepAlive();
    }
    public void pause(String message) {
        running = false; busy = false; generation++; handler.removeCallbacks(ticker); reason = message;
        releaseDrag();
        handler.removeCallbacks(focusTicker);
        focusSuspendedForTap = false;
        if (focusFrame != null) { focusFrame.destroy(); focusFrame = null; }
        // Preserve a dispatched but unconfirmed turn across pause/resume.
        watching = false; clearScroll();
        if (panel != null) { panelParams.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON; updatePanelLayout(); }
        refreshPaused();
    }
    private void startKeepAlive() {
        if (keepAliveActive) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, s(R.string.keep_alive_channel), NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false); ch.setSound(null, null); ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), piFlags);
            Notification notif = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(s(R.string.app_name))
                .setContentText(s(R.string.keep_alive_notif))
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIF_ID, notif);
            keepAliveActive = true;
        } catch (RuntimeException ignored) { keepAliveActive = false; }
    }
    private void stopKeepAlive() {
        if (!keepAliveActive) return;
        keepAliveActive = false;
        try { stopForeground(true); } catch (RuntimeException ignored) {}
    }
    private void refreshPaused() { if (status != null) status.setText(reason); if (toggle != null) toggle.setText(s(R.string.start)); updateAppearance(); }
    public void updateAppearance() { if (bubble != null) { bubble.setAlpha(running ? Math.max(.1f, Math.min(1, getSharedPreferences("settings", 0).getInt("opacity", 35) / 100f)) : .85f); bubble.invalidate(); } }
    private void collapsePanel() {
        if (panel == null || picker != null) return;
        collapsed = true; panelHeader.setVisibility(View.GONE); panelRow.setVisibility(View.GONE); bubble.setVisibility(View.VISIBLE);
        panel.setPadding(0, 0, 0, 0); panel.setBackgroundColor(Color.TRANSPARENT); panelParams.width = dp(44); panelParams.height = dp(44);
        if (!updatePanelLayout()) return; updateAppearance(); clampPanel();
    }
    private void expandPanel() {
        if (panel == null) return;
        collapsed = false; bubble.setVisibility(View.GONE); panelHeader.setVisibility(View.VISIBLE); panelRow.setVisibility(View.VISIBLE);
        panel.setPadding(dp(8), dp(4), dp(8), dp(6)); panel.setBackground(Ui.shape(this, 0xf51f2025, 22)); panelParams.width = dp(244); panelParams.height = -2;
        if (!updatePanelLayout()) return; clampPanel();
    }
    private void clampPanel() { if (panel != null) panel.post(() -> { if (panel == null) return; Point s = size(); panelParams.x = Math.max(0, Math.min(s.x - panel.getWidth(), panelParams.x)); panelParams.y = Math.max(dp(28), Math.min(s.y - panel.getHeight() - dp(28), panelParams.y)); updatePanelLayout(); }); }
    private void attachDrag(View view, boolean isBubble) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float x, y; int px, py; boolean moved, held, wasRunning;
            final Runnable hold = () -> { held = true; if (isBubble) expandPanel(); };
            public boolean onTouch(View v, MotionEvent e) {
                if (panel == null) return true;
                if (e.getAction() == MotionEvent.ACTION_DOWN) { wasRunning = running; if (running) pause(s(R.string.manual_paused)); x = e.getRawX(); y = e.getRawY(); px = panelParams.x; py = panelParams.y; moved = false; held = false; if (isBubble) handler.postDelayed(hold, 650); return true; }
                if (e.getAction() == MotionEvent.ACTION_MOVE) { if (Math.hypot(e.getRawX() - x, e.getRawY() - y) > dp(7)) { moved = true; handler.removeCallbacks(hold); } if (moved) { Point s = size(); panelParams.x = Math.max(0, Math.min(s.x - panel.getWidth(), px + (int) (e.getRawX() - x))); panelParams.y = Math.max(dp(28), Math.min(s.y - panel.getHeight() - dp(28), py + (int) (e.getRawY() - y))); updatePanelLayout(); } return true; }
                if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { handler.removeCallbacks(hold); if (isBubble && ReadingRules.bubbleRelease(wasRunning, moved, held, e.getAction() == MotionEvent.ACTION_CANCEL) == ReadingRules.START) startSession(true); return true; }
                return true;
            }
        });
    }
    private class Bubble extends View {
        final Paint paint = new Paint(3);
        Bubble() { super(TurnService.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        protected void onDraw(Canvas c) {
            float wave = running ? (float) ((1 - Math.cos(Math.PI * (due - SystemClock.uptimeMillis()) / 1000.0)) / 2) : 0;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = dp(15) * (running ? .96f + .08f * wave : 1); paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.INK); c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0xffd0d1d6); c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            c.drawCircle(cx, cy, running ? dp(3) * (.92f + .16f * wave) : dp(2), paint);
            if (running && isShown()) postInvalidateDelayed(33);
        }
    }
    /** Four non-overlapping edge strips frame the main image without covering it with an overlay window. */
    private class FocusFrame {
        final Strip[] strips = new Strip[4];
        final WindowManager.LayoutParams[] params = new WindowManager.LayoutParams[4];
        final Rect image = new Rect();
        boolean attached, visible, suspended;
        long lastFoundAt;
        private class Strip extends View {
            final Paint paint = new Paint();
            Strip() { super(TurnService.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); paint.setColor(Color.BLACK); }
            @Override protected void onDraw(Canvas canvas) {
                int save = canvas.save();
                if (panel != null && panel.isAttachedToWindow()) {
                    int[] origin = new int[2], p = new int[2];
                    getLocationOnScreen(origin); panel.getLocationOnScreen(p);
                    canvas.clipOutRect(p[0] - origin[0] - dp(2), p[1] - origin[1] - dp(2), p[0] - origin[0] + panel.getWidth() + dp(2), p[1] - origin[1] + panel.getHeight() + dp(2));
                }
                canvas.drawPaint(paint); canvas.restoreToCount(save);
            }
        }
        boolean covers(float x, float y) {
            return attached && visible && !suspended && !image.contains(Math.round(x), Math.round(y));
        }
        void suspend(boolean on) {
            if (suspended == on) return;
            suspended = on;
            if (!attached) return;
            if (on) { for (Strip s : strips) if (s != null) s.setVisibility(View.GONE); }
            else if (visible) layoutStrips();
        }
        void apply(Rect next, long now) {
            if (next == null) {
                if (visible && now - lastFoundAt < 1500) return;
                visible = false;
                if (attached) for (Strip s : strips) if (s != null) s.setVisibility(View.GONE);
                return;
            }
            Rect clamped = new Rect(Math.max(0, next.left), Math.max(0, next.top), Math.min(screenWidth, next.right), Math.min(screenHeight, next.bottom));
            if (clamped.isEmpty()) return;
            lastFoundAt = now;
            if (attached && visible && !suspended
                && Math.abs(clamped.left - image.left) < 4 && Math.abs(clamped.top - image.top) < 4
                && Math.abs(clamped.right - image.right) < 4 && Math.abs(clamped.bottom - image.bottom) < 4) return;
            image.set(clamped); visible = true;
            if (suspended) return;
            if (!attached && !attach()) return;
            layoutStrips();
        }
        private boolean attach() {
            for (int i = 0; i < 4; i++) {
                strips[i] = new Strip();
                params[i] = overlay(0, 0, true);
                strips[i].setVisibility(View.GONE);
                try { manager.addView(strips[i], params[i]); }
                catch (RuntimeException e) { destroy(); immersive = false; return false; }
            }
            attached = true; return true;
        }
        private void layoutStrips() {
            int w = screenWidth, h = screenHeight;
            setStrip(0, 0, 0, w, image.top);
            setStrip(1, 0, image.bottom, w, h - image.bottom);
            setStrip(2, 0, image.top, image.left, image.height());
            setStrip(3, image.right, image.top, w - image.right, image.height());
        }
        private void setStrip(int i, int x, int y, int w, int h) {
            Strip s = strips[i]; WindowManager.LayoutParams lp = params[i];
            if (s == null || lp == null) return;
            if (w <= 0 || h <= 0) {
                if (s.getVisibility() != View.GONE) s.setVisibility(View.GONE);
                return;
            }
            boolean changed = lp.x != x || lp.y != y || lp.width != w || lp.height != h;
            lp.x = x; lp.y = y; lp.width = w; lp.height = h;
            if (s.getVisibility() != View.VISIBLE) s.setVisibility(View.VISIBLE);
            if (changed) {
                try { manager.updateViewLayout(s, lp); }
                catch (RuntimeException e) { destroy(); immersive = false; return; }
            }
            s.invalidate();
        }
        void destroy() {
            attached = false; visible = false; suspended = false;
            for (int i = 0; i < 4; i++) {
                if (strips[i] != null) { removeOverlay(strips[i]); strips[i] = null; }
                params[i] = null;
            }
        }
    }
    private void updateFocus(Rect image) {
        if (!running || !immersive) return;
        if (focusFrame == null) {
            if (image == null) return;
            focusFrame = new FocusFrame();
        }
        focusFrame.apply(image, SystemClock.uptimeMillis());
    }
    private void restoreFocusAfterTap() {
        if (!focusSuspendedForTap) return;
        focusSuspendedForTap = false;
        if (focusFrame != null && running && immersive) focusFrame.suspend(false);
    }
    private void startSession() {
        startSession(false);
    }
    private void startSession(boolean quick) {
        if (panel == null) return;
        removePicker(); removeMarker();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) { if (root != null) root.recycle(); pause(s(R.string.page_unreadable)); expandPanel(); return; }
        String pkg = root.getPackageName().toString();
        if (quick && target != null && !target.equals(pkg)) { root.recycle(); pause(s(R.string.app_changed_confirm)); expandPanel(); return; }
        if (pkg.equals(homePackage) || pkg.equals("com.android.systemui") || pkg.equals("com.android.settings") || pkg.equals("android")
            || (pkg.equals(getPackageName()) && !PracticeActivity.visible)) { root.recycle(); pause(s(R.string.open_page_first)); expandPanel(); return; }
        SharedPreferences p = getSharedPreferences("settings", 0);
        Point s = size(); int nextMode = p.getInt("mode", p.getBoolean("smart", false) ? 3 : 0);
        int nextSteps = Math.max(1, Math.min(6, p.getInt("steps", 2)));
        boolean samePage = Rules.sameTarget(target, pkg, targetWindow, root.getWindowId(), screenWidth, screenHeight, s.x, s.y)
            && rotation == manager.getDefaultDisplay().getRotation() && mode == nextMode;
        boolean resumeTurn = samePage && progressWatch.hasPending();
        if (!samePage || steps != nextSteps) phase = 0;
        if (!resumeTurn) clearTurn();
        target = pkg; targetWindow = root.getWindowId(); root.recycle();
        screenWidth = s.x; screenHeight = s.y; rotation = manager.getDefaultDisplay().getRotation();
        String profile = ScreenProfiles.key(manager);
        mode = nextMode; steps = nextSteps;
        scrollKind = p.getInt("scroll_kind", ScrollPlan.PAGE) == ScrollPlan.HALVES ? ScrollPlan.HALVES : ScrollPlan.PAGE;
        swipeDuration = Rules.swipeDuration(p.getLong("swipe_duration", 10000));
        pointX = ScreenProfiles.get(p, profile, "x", .8f); pointY = ScreenProfiles.get(p, profile, "y", .5f);
        autoBottom = true; checkProgress = p.getBoolean("check_progress", true);
        watching = resumeTurn; clearScroll(); lastScrollBounds = null; focusSuspendedForTap = false;
        interval = p.getLong("interval", 10000); smart = ReadingRules.findNextButton(mode);
        if (resumeTurn) { long now = SystemClock.uptimeMillis(); progressWatch.resume(now, interval); nextProbe = now + 750; }
        immersive = p.getBoolean("immersive", false);
        count = 0; busy = false; running = true; generation++;
        due = SystemClock.uptimeMillis() + Math.max(3000, interval);
        stopAt = SystemClock.uptimeMillis() + p.getInt("minutes", 30) * 60000L;
        if (!guard()) return;
        panelParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON; if (!updatePanelLayout()) return;
        toggle.setText(s(R.string.pause)); updateAppearance(); handler.removeCallbacks(ticker); handler.post(ticker);
        handler.removeCallbacks(focusTicker); if (immersive) handler.post(focusTicker);
        final int token = generation; handler.postDelayed(() -> { if (running && token == generation) collapsePanel(); }, 700);
    }
    private boolean guard() {
        if (!running) return false;
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager key = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (!power.isInteractive() || key.isKeyguardLocked()) { pause(s(R.string.locked_paused)); return false; }
        Point s = size();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = root == null || root.getPackageName() == null ? null : root.getPackageName().toString();
        boolean valid = root != null && Rules.sameTarget(target, pkg, targetWindow, root.getWindowId(), screenWidth, screenHeight, s.x, s.y)
            && rotation == manager.getDefaultDisplay().getRotation();
        if (root != null) root.recycle();
        if (!valid) { pause(s(R.string.window_changed)); return false; }
        List<AccessibilityWindowInfo> windows = getWindows();
        boolean obstructed = false;
        for (AccessibilityWindowInfo w : windows) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                ((w.isActive() || w.isFocused()) && w.getType() != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && w.getId() != targetWindow)) obstructed = true;
            w.recycle();
        }
        if (obstructed) { pause(s(R.string.window_obstructed)); return false; }
        return true;
    }
    private void performTurn() {
        if (busy || watching || !guard()) return;
        PageProbe.Snapshot before = snapshot();
        if (smart) { clickNextNode(before); return; }
        int bottom = scrollWatch.bottom(before.scrollKey, before.bottom);
        before.bottom = bottom;
        if (mode == 1 && bottom == ReadingRules.BOTTOM) { pause(s(R.string.reached_bottom)); expandPanel(); return; }
        boolean swipe = (mode == 1 || mode == 2) && bottom != ReadingRules.BOTTOM;
        if (swipe) {
            scrollPlan = new ScrollPlan(scrollKind, swipeDuration); passSwipes = 0;
            scrollPlan.locate(scrollWatch.position(), scrollWatch.maximum(), SystemClock.uptimeMillis());
            scrollPart(before); return;
        }
        // Scroll-then-turn always uses the saved point (right side by default).
        // Button detection is a separate, explicitly selected mode.
        float x = screenWidth * pointX, y = screenHeight * pointY;
        float endY = y;
        if (pathHitsPanel(x, y, endY)) { pause(s(R.string.move_dot)); return; }
        Path path = new Path(); path.moveTo(x, y);
        long duration = 70;
        GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, duration)).build();
        if (!guard()) return;
        if (focusFrame != null && focusFrame.covers(x, y)) { focusSuspendedForTap = true; focusFrame.suspend(true); }
        ownScrollKey = null;
        ownStart = SystemClock.uptimeMillis(); ownEnd = ownStart + duration + 250;
        busy = true; busyAt = SystemClock.uptimeMillis(); busyUntil = busyAt + duration + 3000;
        final int token = generation; final long action = ++gestureId;
        prepareTurn(before);
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { if (currentGesture(token, action) && busy) { restoreFocusAfterTap(); completed(swipe, before); } }
            @Override public void onCancelled(GestureDescription g) { if (currentGesture(token, action)) { restoreFocusAfterTap(); pause(s(R.string.gesture_interrupted)); } }
        }, handler);
        if (!accepted && currentGesture(token, action)) { restoreFocusAfterTap(); clearTurn(); pause(s(R.string.gesture_rejected)); }
    }
    private void scrollPart(PageProbe.Snapshot before) {
        if (!guard() || scrollPlan == null) return;
        if (++passSwipes > 60) { pause(s(R.string.bottom_not_found)); expandPanel(); return; }
        Rect area = before.scrollBounds == null ? new Rect(0, dp(32), screenWidth, screenHeight - dp(32)) : new Rect(before.scrollBounds);
        if (!area.intersect(dp(12), dp(24), screenWidth - dp(12), screenHeight - dp(24)) || area.height() < dp(80)) { pause(s(R.string.page_unreadable)); return; }
        int yPosition = scrollWatch.position(), maximum = scrollWatch.maximum(); long now = SystemClock.uptimeMillis();
        scrollPlan.locate(yPosition, maximum, now);
        if (scrollPlan.reached(yPosition, maximum)) { finishPass(); return; }
        // A short first movement obtains the browser's actual scroll range.
        // If swipe 1 did not move at all, use a short confirming swipe instead of a full slow drag.
        boolean confirmStill = !scrollPlan.located() && passSwipes == 2 && scrollWatch.stillCount() >= 1;
        float intended = scrollPlan.located() ? scrollPlan.distance(yPosition, maximum, area.height(), metricScale)
            : passSwipes == 1 ? ViewConfiguration.get(this).getScaledTouchSlop() * 2f + 2
            : confirmStill ? area.height() * .35f : area.height() * .85f;
        float distance = Math.min(area.height() * .85f, Math.max(ViewConfiguration.get(this).getScaledTouchSlop() * 2f + 2, intended));
        long duration = scrollPlan.located() ? scrollPlan.duration(yPosition, maximum, distance, metricScale, now)
            : passSwipes == 1 ? 180
            : confirmStill ? Math.min(swipeDuration, 800) : swipeDuration;
        if (distance < 1) { finishPass(); return; }
        float x = area.exactCenterX(), from = area.bottom - area.height() * .06f, to = from - distance;
        if (pathHitsPanel(x, from, to)) { pause(s(R.string.move_dot)); return; }
        ownScrollKey = before.scrollKey; scrollWatch.begin(ownScrollKey, before.viewport); metricBefore = scrollWatch.position();
        ownStart = now; ownEnd = now + duration + 1000; busy = true; busyAt = now; busyUntil = now + duration + 5000;
        final int token = generation; final ScrollPlan plan = scrollPlan;
        // Continued short strokes let pause end a slow drag without injecting a new tap.
        dragSegment(null, x, from, to, from, duration, duration, token, () -> {
            if (!running || token != generation || plan != scrollPlan) return;
            ownEnd = SystemClock.uptimeMillis() + 250;
            busyUntil = SystemClock.uptimeMillis() + 3000;
            handler.postDelayed(() -> {
                if (!running || token != generation || plan != scrollPlan || !guard()) return;
                PageProbe.Snapshot after = snapshot();
                int end = scrollWatch.finish(after.scrollKey, after.viewport, after.bottom);
                int position = scrollWatch.position(), max = scrollWatch.maximum();
                if (metricBefore >= 0 && position - metricBefore > dp(8)) metricScale = Math.max(.25f, Math.min(4, distance / (position - metricBefore)));
                scrollPlan.locateAfterProbe(position, max, distance / metricScale, SystemClock.uptimeMillis());
                if (end == ReadingRules.BOTTOM || scrollPlan.reached(position, max)) { finishPass(); return; }
                if (!scrollPlan.located() && scrollKind == ScrollPlan.HALVES) { pause(s(R.string.half_unavailable)); expandPanel(); return; }
                if (end == ReadingRules.UNKNOWN && passSwipes >= 2) { pause(s(R.string.scroll_unreadable)); expandPanel(); return; }
                scrollPart(after);
            }, 350);
        });
    }
    private void dragSegment(GestureDescription.StrokeDescription previous, float x, float from, float to, float current, long remaining, long total, int token, Runnable done) {
        if (!running || token != generation || !guard()) return;
        long slice = Math.min(250, remaining);
        float next = current + (to - current) * slice / remaining;
        // Cross touch slop promptly so very slow image drags do not become long presses.
        if (previous == null && remaining > 250) {
            slice = Math.min(100, remaining);
            float kick = Math.min(from - to, ViewConfiguration.get(this).getScaledTouchSlop() * 2f + 1);
            next = from - kick;
        }
        boolean more = remaining > slice && next > to + .5f;
        if (!more) next = to;
        Path path = new Path(); path.moveTo(x, current); path.lineTo(x, next);
        final float end = next; final long rest = remaining - slice;
        GestureDescription.StrokeDescription stroke = previous == null ? new GestureDescription.StrokeDescription(path, 0, slice, more) : previous.continueStroke(path, 0, slice, more);
        activeStroke = stroke; activeStrokeX = x; activeStrokeY = end;
        final long action = ++gestureId;
        boolean accepted = dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                if (!currentGesture(token, action) || activeStroke != stroke) return;
                if (!guard()) return;
                if (activeStroke == stroke) activeStroke = null;
                if (more) { ownEnd = SystemClock.uptimeMillis() + rest + 1000; busyUntil = SystemClock.uptimeMillis() + rest + 5000; dragSegment(stroke, x, from, to, end, rest, total, token, done); } else done.run();
            }
            @Override public void onCancelled(GestureDescription g) {
                if (!currentGesture(token, action) || activeStroke != stroke) return;
                if (activeStroke == stroke) activeStroke = null;
                pause(s(R.string.gesture_interrupted));
            }
        }, handler);
        if (!accepted && currentGesture(token, action)) { activeStroke = null; pause(s(R.string.gesture_rejected)); }
    }
    private boolean currentGesture(int token, long action) { return running && token == generation && action == gestureId; }
    private void releaseDrag() {
        GestureDescription.StrokeDescription stroke = activeStroke; activeStroke = null;
        if (stroke == null || !stroke.willContinue()) return;
        // The pause action releases its own stroke; old callbacks never inject into a new session.
        Path up = new Path(); up.moveTo(activeStrokeX, activeStrokeY);
        try { dispatchGesture(new GestureDescription.Builder().addStroke(stroke.continueStroke(up, 0, 1, false)).build(), null, handler); }
        catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) { /* Android already ended the connection/stroke. */ }
    }
    private void finishPass() {
        busy = false; count++; phase++; scrollPlan = null;
        due = SystemClock.uptimeMillis() + interval;
    }
    private void completed(boolean swipe, PageProbe.Snapshot before) {
        if (!running || !busy) return;
        gestureId++;
        busy = false; count++; phase = swipe ? phase + 1 : 0; long now = SystemClock.uptimeMillis(); due = now + interval;
        if (!swipe) {
            clearScroll();
            if (progressWatch.hasPending()) {
                watching = true; nextProbe = now + 750;
            }
        }
    }
    private PageProbe.Snapshot snapshot() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            try {
                PageProbe.Snapshot snap = PageProbe.capture(root, screenWidth, screenHeight);
                if (snap.scrollBounds != null) lastScrollBounds = new Rect(snap.scrollBounds);
                if (running && immersive && !focusSuspendedForTap) updateFocus(snap.imageBounds);
                return snap;
            } finally { if (root != null) root.recycle(); }
        } catch (RuntimeException e) { return new PageProbe.Snapshot(); }
    }
    private void checkTurn(long now) {
        PageProbe.Snapshot current = snapshot(); nextProbe = now + 750;
        int result = progressWatch.observe(current.url, current.content, current.viewport, current.bottom, current.bodyVisible, current.loading, now);
        if (result == ProgressWatch.WAIT) return;
        watching = false;
        if (result == ProgressWatch.CHANGED) { clearTurn(); due = now + interval; }
        else {
            pause(result == ProgressWatch.UNREADABLE ? s(R.string.progress_unreadable) : s(R.string.progress_unchanged)); expandPanel();
        }
    }
    private void prepareTurn(PageProbe.Snapshot before) {
        if (!ReadingRules.confirmTurn(mode, checkProgress)) return;
        turnScrollKey = before.scrollKey; turnFromY = scrollWatch.position();
        progressWatch.begin(before.url, before.content, before.viewport, before.bottom, mode == 2, SystemClock.uptimeMillis(), interval);
    }
    private void clearTurn() { progressWatch.clear(); turnScrollKey = null; turnFromY = -1; }
    private void clearScroll() { ownScrollKey = null; scrollWatch.clear(); scrollPlan = null; metricScale = 1; }
    private boolean clickNextNode(PageProbe.Snapshot before) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { pause(s(R.string.page_lost)); return true; }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(root);
        LinkedHashMap<String, AccessibilityNodeInfo> matches = new LinkedHashMap<>(); int visited = 0;
        while (!queue.isEmpty() && visited++ < 2000) {
            AccessibilityNodeInfo node = queue.remove();
            if (node.isVisibleToUser() && node.isEnabled() && !node.isPassword() && !node.isEditable()
                && (Rules.nextLabel(node.getText()) || Rules.nextLabel(node.getContentDescription()))) {
                AccessibilityNodeInfo candidate = AccessibilityNodeInfo.obtain(node);
                for (int level = 0; level < 3 && candidate != null && !candidate.isClickable(); level++) {
                    AccessibilityNodeInfo parent = candidate.getParent(); candidate.recycle(); candidate = parent;
                }
                if (candidate != null) {
                    Rect rect = new Rect(); candidate.getBoundsInScreen(rect);
                    if (candidate.isClickable() && candidate.isEnabled() && candidate.isVisibleToUser() && !rect.isEmpty()
                        && rect.left >= 0 && rect.top >= 0 && rect.right <= screenWidth && rect.bottom <= screenHeight
                        && rect.width() * 1L * rect.height() < screenWidth * 1L * screenHeight * .85) {
                        String id = candidate.getWindowId() + ":" + rect.toShortString();
                        if (!matches.containsKey(id)) matches.put(id, candidate); else candidate.recycle();
                    } else candidate.recycle();
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.add(child); }
            node.recycle();
        }
        boolean truncated = !queue.isEmpty(); while (!queue.isEmpty()) queue.remove().recycle();
        if (truncated || matches.size() != 1) {
            for (AccessibilityNodeInfo n : matches.values()) n.recycle();
            pause(truncated ? s(R.string.page_complex) : matches.isEmpty() ? s(R.string.next_missing) : s(R.string.next_ambiguous)); return true;
        }
        AccessibilityNodeInfo n = matches.values().iterator().next();
        if (!guard()) { n.recycle(); return true; }
        busy = true; busyAt = SystemClock.uptimeMillis(); busyUntil = busyAt + 3000;
        final int token = generation;
        prepareTurn(before);
        boolean ok = n.refresh() && n.isEnabled() && n.isVisibleToUser() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK); n.recycle();
        if (!running || token != generation) return true;
        if (ok) completed(false, before); else { clearTurn(); pause(s(R.string.button_failed)); }
        return true;
    }
    private void pickPoint() {
        pause(s(R.string.pick_tip)); removePicker(); removeMarker();
        FrameLayout surface = new FrameLayout(this); surface.setBackgroundColor(0x22000000);
        TextView tip = Ui.action(this, s(R.string.pick_title), Color.WHITE, 0xe0202126); tip.setClickable(false);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); tp.topMargin = dp(160); surface.addView(tip, tp);
        TextView cancel = Ui.action(this, s(R.string.cancel), Color.WHITE, 0xe0202126); FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP | Gravity.RIGHT); cp.topMargin = dp(32); cp.rightMargin = dp(12); surface.addView(cancel, cp);
        cancel.setOnClickListener(v -> { removePicker(); pause(s(R.string.pick_cancelled)); });
        surface.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Point s = size(); float x = event.getRawX(), y = event.getRawY();
                if (x < dp(24) || x > s.x - dp(24) || y < dp(28) || y > s.y - dp(28)) { Toast.makeText(this, s(R.string.pick_edge), Toast.LENGTH_SHORT).show(); return true; }
                pointX = x / s.x; pointY = y / s.y;
                SharedPreferences settings = getSharedPreferences("settings", 0);
                int selected = settings.getInt("mode", settings.getBoolean("smart", false) ? 3 : 0);
                String profile = ScreenProfiles.key(manager); ScreenProfiles.put(settings, profile, "x", pointX); ScreenProfiles.put(settings, profile, "y", pointY);
                settings.edit().putBoolean("smart", false).putInt("mode", selected == 2 ? 2 : 0).apply(); phase = 0;
                removePicker(); showMarker(x, y); pause(s(R.string.pick_saved)); return true;
            } return true;
        });
        picker = surface;
        try { manager.addView(picker, overlay(-1, -1, false)); }
        catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException | SecurityException | IllegalArgumentException e) { picker = null; pause(s(R.string.panel_failed)); }
    }
    private void showMarker(float x, float y) {
        TextView cross = new TextView(this); cross.setText("⊕"); cross.setTextSize(30); cross.setGravity(Gravity.CENTER); cross.setTextColor(Ui.INK); cross.setBackground(Ui.shape(this, 0xccffffff, 20));
        WindowManager.LayoutParams p = overlay(dp(40), dp(40), true); p.x = (int) x - dp(20); p.y = (int) y - dp(20);
        marker = cross;
        try { manager.addView(marker, p); }
        catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException | SecurityException | IllegalArgumentException e) { marker = null; Toast.makeText(this, s(R.string.panel_failed), Toast.LENGTH_LONG).show(); }
    }
    private boolean insidePanel(float x, float y) { if (panel == null) return false; int[] pos = new int[2]; panel.getLocationOnScreen(pos); return x >= pos[0] && x <= pos[0] + panel.getWidth() && y >= pos[1] && y <= pos[1] + panel.getHeight(); }
    private boolean pathHitsPanel(float x, float y, float endY) { if (panel == null) return false; int[] pos = new int[2]; panel.getLocationOnScreen(pos); return x >= pos[0] && x <= pos[0] + panel.getWidth() && Math.max(y, endY) >= pos[1] && Math.min(y, endY) <= pos[1] + panel.getHeight(); }
    private void removePicker() { if (picker != null) { removeOverlay(picker); picker = null; } }
    private void removeMarker() { if (marker != null) { removeOverlay(marker); marker = null; } }
    private void removeOverlay(View view) {
        if (view != null && manager != null) try { manager.removeView(view); } catch (IllegalArgumentException | SecurityException ignored) {}
    }
    private boolean updatePanelLayout() {
        if (panel == null || manager == null) return false;
        try { manager.updateViewLayout(panel, panelParams); return true; }
        catch (WindowManager.BadTokenException | WindowManager.InvalidDisplayException | IllegalArgumentException | SecurityException e) {
            releaseDrag(); clearOverlays(); reason = s(R.string.panel_failed);
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show(); return false;
        }
    }
    private Point size() { Point p = new Point(); manager.getDefaultDisplay().getRealSize(p); return p; }
    private WindowManager.LayoutParams overlay(int w, int h, boolean pass) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (pass) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }
    private TextView smallButton(LinearLayout row, String text, View.OnClickListener action) { TextView b = Ui.action(this, text, Color.WHITE, Color.TRANSPARENT); b.setTextSize(13); b.setPadding(0, 0, 0, 0); row.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1)); b.setOnClickListener(action); return b; }
    private String s(int id, Object... values) { Context context = Languages.wrap(this); return values.length == 0 ? context.getString(id) : context.getString(id, values); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (!running || e == null) return;
        try {
            int type = e.getEventType();
            if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (e.getWindowId() != targetWindow || e.getPackageName() == null || target == null || !target.equals(e.getPackageName().toString())) return;
                long now = SystemClock.uptimeMillis();
                AccessibilityNodeInfo source = e.getSource(); if (source == null) return;
                String key; Rect bounds = new Rect();
                try { key = PageProbe.nodeKey(source); source.getBoundsInScreen(bounds); } finally { source.recycle(); }
                boolean mainSized = bounds.width() >= screenWidth / 2 && bounds.height() >= screenHeight / 3;
                // A real return to the top can confirm image-only pages without accessible labels.
                if (progressWatch.hasPending() && (key.equals(turnScrollKey) || mainSized)) {
                    progressWatch.recordScrollReset(turnFromY, e.getScrollY(), e.getMaxScrollY());
                }
                // Slow-scroll metrics still belong only to our own swipe and the main scroller.
                if (now < ownStart || now > ownEnd + 500) return;
                if (key.equals(ownScrollKey)) {
                    scrollWatch.metric(key, e.getScrollY(), e.getMaxScrollY());
                } else if (ownScrollKey != null && mainSized && (e.getMaxScrollY() > 0 || e.getScrollY() > 0)) {
                    scrollWatch.metric(ownScrollKey, e.getScrollY(), e.getMaxScrollY());
                }
                return;
            }
            CharSequence pkg = e.getPackageName();
            if (pkg != null && getPackageName().equals(pkg.toString())) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastEventGuard >= 200) { lastEventGuard = now; guard(); }
        } catch (RuntimeException ignored) {}
    }
    @Override public void onInterrupt() { pause(s(R.string.service_interrupted)); }
    @Override public boolean onUnbind(Intent intent) {
        disconnectService();
        return super.onUnbind(intent);
    }
    private void disconnectService() {
        // Clear identity first: cleanup failure must never leave a disconnected service marked ready.
        if (instance == this) instance = null;
        clearOverlays();
        stopKeepAlive();
    }
    private void clearOverlays() {
        running = false; busy = false; watching = false; generation++; activeStroke = null;
        focusSuspendedForTap = false; lastScrollBounds = null;
        handler.removeCallbacksAndMessages(null); clearScroll();
        if (focusFrame != null) { focusFrame.destroy(); focusFrame = null; }
        // Android may already have detached accessibility overlays while unbinding.
        for (View view : new View[]{picker, marker, panel}) removeOverlay(view);
        picker = null; marker = null; panel = null; status = null; toggle = null;
        bubble = null; panelHeader = null; panelRow = null;
    }
    @Override public void onConfigurationChanged(Configuration c) {
        if (panel != null && !Languages.tag(this).equals(panelLanguage)) { super.onConfigurationChanged(c); hidePanel(); return; }
        super.onConfigurationChanged(c); pause(s(R.string.layout_changed)); removePicker(); removeMarker();
        if (panel != null) { panelParams.x = dp(10); panelParams.y = dp(48); updatePanelLayout(); }
    }
    @Override public void onDestroy() {
        disconnectService();
        if (receiverRegistered) { try { unregisterReceiver(screenReceiver); } catch (IllegalArgumentException ignored) {} receiverRegistered = false; }
        if (instance == this) instance = null; super.onDestroy();
    }
}
