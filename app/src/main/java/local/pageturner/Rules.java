package local.pageturner;

import java.util.Locale;

/** Pure session rules, also exercised by desktop tests. */
public final class Rules {
    public static boolean shouldSwipe(int mode, int phase, int steps) {
        return mode == 1 || (mode == 2 && phase < Math.max(1, Math.min(6, steps)));
    }
    public static int nextPhase(int mode, int phase, int steps) {
        return mode == 2 ? (phase + 1) % (Math.max(1, Math.min(6, steps)) + 1) : 0;
    }
    public static float swipeEnd(float distance) { return .8f - Math.max(.35f, Math.min(.65f, distance)); }
    public static boolean ownInjectedTouch(int deviceId, int toolType, long eventTime, long ownStart, long ownEnd) {
        // AOSP accessibility gestures use a virtual device and TOOL_TYPE_UNKNOWN.
        // Physical finger/stylus events are never suppressed, even during our gesture.
        return deviceId <= 0 && toolType == 0 && ownStart > 0 && eventTime >= ownStart && eventTime <= ownEnd;
    }
    public static long intervalMillis(String text) {
        double seconds = Double.parseDouble(text.trim());
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 1 || seconds > 3600)
            throw new IllegalArgumentException("Interval must be between 1 and 3600 seconds");
        return Math.round(seconds * 1000);
    }
    public static boolean nextLabel(CharSequence text) {
        if (text == null) return false;
        String s = text.toString().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return s.equals("下一页") || s.equals("下一張") || s.equals("下一张") || s.equals("下页")
            || s.equals("下一頁") || s.equals("next") || s.equals("next page") || s.equals("next image")
            || s.equals("次のページ") || s.equals("次ページ") || s.equals("次の画像") || s.equals("次の写真");
    }
    public static boolean sameTarget(String expected, String actual, int window, int current,
                                     int width, int height, int nowWidth, int nowHeight) {
        return expected != null && expected.equals(actual) && window == current
            && width == nowWidth && height == nowHeight;
    }
    private Rules() {}
}
