package local.pageturner;

/** Short-lived hashes only; a transient loading state is not a successful turn. */
public final class ProgressWatch {
    public static final int WAIT=0, CHANGED=1, MISSED=2, UNREADABLE=3;
    private String beforeUrl,beforeContent,candidateUrl,candidateContent;
    private long deadline,candidateAt;
    private boolean candidate;
    public void begin(String url,String content,long now,long interval) {
        clear(); beforeUrl=url; beforeContent=content; deadline=now+ReadingRules.checkDelay(interval);
    }
    public int observe(String url,String content,long now) {
        if(ReadingRules.changed(beforeUrl,url,beforeContent,content)) {
            if(candidate && equal(candidateUrl,url) && equal(candidateContent,content)) {
                if(now-candidateAt>=750) return CHANGED;
            } else { candidate=true; candidateUrl=url; candidateContent=content; candidateAt=now; }
        } else { candidate=false; candidateUrl=null; candidateContent=null; }
        if(now<deadline) return WAIT;
        return beforeContent==null || content==null ? UNREADABLE : MISSED;
    }
    public void clear() { beforeUrl=null; beforeContent=null; candidateUrl=null; candidateContent=null; candidate=false; deadline=0; candidateAt=0; }
    private static boolean equal(String a,String b) { return a==null?b==null:a.equals(b); }
}
