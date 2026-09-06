package local.pageturner;

/** A page pass ends at half of the scrollable range or at its bottom. */
public final class ScrollPlan {
    public static final int PAGE=1, HALVES=2;
    private final int kind;
    private final long duration;
    private boolean located,half;
    private long started=-1;
    public ScrollPlan(int kind,long duration) { this.kind=kind; this.duration=Rules.swipeDuration(duration); }
    public void locate(int y,int max,long now) {
        if(!located && y>=0 && max>0) { half=kind==HALVES && y<max/2.0-2; located=true; started=now; }
    }
    public void locateAfterProbe(int y,int max,float advance,long now) {
        if(!located && y>=0 && max>0) { half=kind==HALVES && Math.max(0,y-advance)<max/2.0-2; located=true; started=now; }
    }
    public boolean located() { return located; }
    public int target(int max) { return half?Math.round(max/2f):max; }
    public boolean reached(int y,int max) { return located && y>=0 && max>0 && y>=target(max)-2; }
    public float distance(int y,int max,float viewport,float scale) {
        float cap=viewport*.85f;
        return located && max>0?Math.max(0,Math.min(cap,(target(max)-y)*scale)):cap;
    }
    public long duration(int y,int max,float pixels,float scale,long now) {
        if(!located || max<=0) return duration;
        double remaining=Math.max(1,(target(max)-y)*scale);
        long budget=Math.max(250,duration-Math.max(0,now-started));
        return Math.max(180,Math.min(30000,Math.round(budget*Math.min(1,pixels/remaining))));
    }
}
