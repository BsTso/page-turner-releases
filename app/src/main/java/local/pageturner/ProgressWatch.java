package local.pageturner;

/** One dispatched turn owns this wait; expiry never authorizes another click. */
public final class ProgressWatch {
    public static final int WAIT = 0, CHANGED = 1, MISSED = 2, UNREADABLE = 3;
    private String beforeUrl, beforeContent, beforeView, beforeImage;
    private String candidateUrl, candidateContent, candidateView, candidateImage;
    private long deadline, candidateAt, started;
    private boolean candidate, requireReset, active, resetObserved, expectImage;
    private int beforeBottom, candidateBottom, result = WAIT;
    public void begin(String url, String content, long now, long interval) {
        begin(url, content, null, ReadingRules.UNKNOWN, false, now, interval, false, null);
    }
    public void begin(String url, String content, String view, int bottom, boolean reset, long now, long interval) {
        begin(url, content, view, bottom, reset, now, interval, false, null);
    }
    public void begin(String url, String content, String view, int bottom, boolean reset, long now, long interval, boolean hasImage, String imageSignature) {
        clear();
        beforeUrl = url; beforeContent = content; beforeView = view; beforeImage = imageSignature;
        beforeBottom = bottom; requireReset = reset; expectImage = hasImage;
        active = true; started = now; deadline = now + ReadingRules.checkDelay(interval);
    }
    public int observe(String url, String content, long now) {
        return observe(url, content, null, ReadingRules.UNKNOWN, content != null, false, now, false, null);
    }
    public int observe(String url, String content, String view, int bottom, boolean bodyVisible, boolean loading, long now) {
        return observe(url, content, view, bottom, bodyVisible, loading, now, false, null);
    }
    public int observe(String url, String content, String view, int bottom, boolean bodyVisible, boolean loading, long now, boolean hasImage, String imageSignature) {
        if (!active || result != WAIT) return result;
        boolean reset = beforeBottom == ReadingRules.BOTTOM && bottom == ReadingRules.MORE;
        boolean urlChanged = beforeUrl != null && url != null && !beforeUrl.equals(url);
        boolean imageChanged = beforeImage != null && imageSignature != null && !beforeImage.equals(imageSignature);
        boolean viewChanged = (beforeView != null && view != null && !beforeView.equals(view)) || imageChanged;
        boolean changed = ReadingRules.changed(beforeUrl, url, beforeContent, content) || reset && viewChanged || resetObserved || imageChanged;
        // On image/comic pages, header text loads before the comic image. Wait until the new image actually loads and settles.
        boolean contentReady = expectImage ? hasImage : bodyVisible;
        boolean ready = contentReady && !loading
            && (!requireReset || (viewChanged || resetObserved) && (bottom != ReadingRules.BOTTOM || resetObserved || (urlChanged || imageChanged) && viewChanged));
        if (changed && ready) {
            boolean stable;
            if (expectImage && imageSignature != null) {
                // Ignore animated ad text/DOM tweaks once the main comic image itself has settled.
                stable = candidate && equal(candidateUrl, url) && equal(candidateImage, imageSignature) && candidateBottom == bottom;
            } else {
                stable = candidate && equal(candidateUrl, url) && equal(candidateContent, content) && equal(candidateView, view) && candidateBottom == bottom;
            }
            if (stable) {
                long settleMs = expectImage && imageSignature != null ? 1200 : 1500;
                long minElapsed = expectImage && imageSignature != null ? 1800 : 2000;
                if (now - candidateAt >= settleMs && now - started >= minElapsed) return result = CHANGED;
            } else {
                candidate = true; candidateUrl = url; candidateContent = content; candidateView = view; candidateImage = imageSignature; candidateBottom = bottom; candidateAt = now;
            }
        } else {
            candidate = false; candidateUrl = null; candidateContent = null; candidateView = null; candidateImage = null;
        }
        if (now < deadline) return WAIT;
        return result = !contentReady || loading || beforeContent == null && beforeView == null && beforeImage == null || content == null && view == null && imageSignature == null ? UNREADABLE : MISSED;
    }
    public boolean hasPending() { return active; }
    public void recordScrollReset(int previousPosition, int position, int maximum) {
        if (active && requireReset && beforeBottom == ReadingRules.BOTTOM && previousPosition > 2 && position >= 0 && position <= 2 && maximum >= 0) resetObserved = true;
    }
    /** Resuming re-observes the original click; it never grants another click. */
    public void resume(long now, long interval) {
        if (!active) return;
        candidate = false; candidateUrl = null; candidateContent = null; candidateView = null; candidateImage = null;
        result = WAIT; started = now; candidateAt = 0; deadline = now + ReadingRules.checkDelay(interval);
    }
    public void clear() {
        beforeUrl = null; beforeContent = null; beforeView = null; beforeImage = null;
        candidateUrl = null; candidateContent = null; candidateView = null; candidateImage = null;
        candidate = false; requireReset = false; active = false; resetObserved = false; expectImage = false;
        deadline = 0; candidateAt = 0; started = 0;
        beforeBottom = ReadingRules.UNKNOWN; candidateBottom = ReadingRules.UNKNOWN; result = WAIT;
    }
    private static boolean equal(String a, String b) { return a == null ? b == null : a.equals(b); }
}
