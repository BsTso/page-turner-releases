package local.pageturner;

/** Decisions shared by the Android service and host-side regression checks. */
public final class ReadingRules {
    public static final int UNKNOWN=-1, MORE=0, BOTTOM=1;
    public static final int NO_ACTION=0, START=1, PAUSE=2;
    public static String profileKey(int width,int height) {
        if(width<=0 || height<=0) throw new IllegalArgumentException("Invalid screen size");
        return "screen_"+width+"x"+height+"_";
    }
    public static boolean swipeNext(int mode,boolean autoBottom,int bottom,int swipes,int fallbackSteps) {
        if(mode==1) return bottom!=BOTTOM;
        if(mode!=2) return false;
        if(autoBottom && bottom!=UNKNOWN) return bottom==MORE;
        return swipes<Math.max(1,Math.min(6,fallbackSteps));
    }
    public static int scrollPosition(int y,int maximum) {
        if(y<0 || maximum<=0 || y>maximum+2) return UNKNOWN;
        return maximum-y<=2 ? BOTTOM : MORE;
    }
    public static int bubbleRelease(boolean wasRunning,boolean moved,boolean held,boolean cancelled) {
        return moved || held || cancelled ? NO_ACTION : wasRunning ? PAUSE : START;
    }
    public static boolean changed(String oldUrl,String newUrl,String oldContent,String newContent) {
        if(oldUrl!=null && newUrl!=null && !oldUrl.equals(newUrl)) return true;
        return oldContent!=null && newContent!=null && !oldContent.equals(newContent);
    }
    public static long checkDelay(long interval) { return Math.max(8000,Math.min(15000,interval)); }
    public static boolean stopAfterMisses(int count) { return count>=2; }
    private ReadingRules() {}
}
