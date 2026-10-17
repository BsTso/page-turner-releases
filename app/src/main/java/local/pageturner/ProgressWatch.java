package local.pageturner;

/** One dispatched turn owns this wait; expiry never authorizes another click. */
public final class ProgressWatch {
    public static final int WAIT=0, CHANGED=1, MISSED=2, UNREADABLE=3;
    private String beforeUrl,beforeContent,beforeView,candidateUrl,candidateContent,candidateView;
    private long deadline,candidateAt,started;
    private boolean candidate,requireReset,active,resetObserved;
    private int beforeBottom,candidateBottom,result=WAIT;
    public void begin(String url,String content,long now,long interval) {
        begin(url,content,null,ReadingRules.UNKNOWN,false,now,interval);
    }
    public void begin(String url,String content,String view,int bottom,boolean reset,long now,long interval) {
        clear(); beforeUrl=url; beforeContent=content; beforeView=view; beforeBottom=bottom;
        requireReset=reset; active=true; started=now; deadline=now+ReadingRules.checkDelay(interval);
    }
    public int observe(String url,String content,long now) {
        return observe(url,content,null,ReadingRules.UNKNOWN,content!=null,false,now);
    }
    public int observe(String url,String content,String view,int bottom,boolean bodyVisible,boolean loading,long now) {
        if(!active || result!=WAIT) return result;
        boolean reset=beforeBottom==ReadingRules.BOTTOM && bottom==ReadingRules.MORE;
        boolean viewChanged=beforeView!=null && view!=null && !beforeView.equals(view);
        boolean changed=ReadingRules.changed(beforeUrl,url,beforeContent,content) || reset && viewChanged || resetObserved;
        // A browser can update the address/title while still showing the previous page's bottom.
        // Its forward-scroll action may also remain advertised at the bottom.
        boolean ready=bodyVisible && !loading && (!requireReset || (viewChanged || resetObserved) && (bottom!=ReadingRules.BOTTOM || resetObserved));
        if(changed && ready) {
            if(candidate && equal(candidateUrl,url) && equal(candidateContent,content) && equal(candidateView,view) && candidateBottom==bottom) {
                if(now-candidateAt>=1500 && now-started>=2000) return result=CHANGED;
            } else { candidate=true; candidateUrl=url; candidateContent=content; candidateView=view; candidateBottom=bottom; candidateAt=now; }
        } else { candidate=false; candidateUrl=null; candidateContent=null; candidateView=null; }
        if(now<deadline) return WAIT;
        return result=!bodyVisible || loading || beforeContent==null && beforeView==null || content==null && view==null ? UNREADABLE : MISSED;
    }
    public boolean hasPending() { return active; }
    public void recordScrollReset(int previousPosition,int position,int maximum) {
        if(active && requireReset && beforeBottom==ReadingRules.BOTTOM && previousPosition>2 && position>=0 && position<=2 && maximum>=0) resetObserved=true;
    }
    /** Resuming re-observes the original click; it never grants another click. */
    public void resume(long now,long interval) {
        if(!active) return;
        candidate=false; candidateUrl=null; candidateContent=null; candidateView=null;
        result=WAIT; started=now; candidateAt=0; deadline=now+ReadingRules.checkDelay(interval);
    }
    public void clear() {
        beforeUrl=null; beforeContent=null; beforeView=null; candidateUrl=null; candidateContent=null; candidateView=null;
        candidate=false; requireReset=false; active=false; resetObserved=false; deadline=0; candidateAt=0; started=0;
        beforeBottom=ReadingRules.UNKNOWN; candidateBottom=ReadingRules.UNKNOWN; result=WAIT;
    }
    private static boolean equal(String a,String b) { return a==null?b==null:a.equals(b); }
}
