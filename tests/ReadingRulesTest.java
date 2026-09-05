import local.pageturner.ReadingRules;
import local.pageturner.ProgressWatch;

public class ReadingRulesTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        // A long page must not turn early just because the old two-swipe count elapsed.
        check(ReadingRules.swipeNext(2,true,ReadingRules.MORE,7,2),"long page keeps scrolling");
        check(!ReadingRules.swipeNext(2,true,ReadingRules.BOTTOM,1,2),"short page turns at bottom");
        check(ReadingRules.swipeNext(2,true,ReadingRules.UNKNOWN,1,2),"unknown uses fallback before count");
        check(!ReadingRules.swipeNext(2,true,ReadingRules.UNKNOWN,2,2),"unknown turns at fallback count");
        check(ReadingRules.swipeNext(2,false,ReadingRules.BOTTOM,1,2),"fixed count stays fixed");
        check(!ReadingRules.swipeNext(2,false,ReadingRules.MORE,2,2),"fixed count turns despite more content");
        check(!ReadingRules.swipeNext(1,true,ReadingRules.BOTTOM,0,2),"scroll-only stops at bottom");
        check(ReadingRules.swipeNext(1,true,ReadingRules.UNKNOWN,100,2),"unknown is not proof of bottom");
        check(!ReadingRules.swipeNext(0,true,ReadingRules.MORE,0,2),"tap mode never scrolls");
        check(ReadingRules.scrollPosition(0,0)==ReadingRules.UNKNOWN,"zero range is not proof");
        check(ReadingRules.scrollPosition(-1,100)==ReadingRules.UNKNOWN,"missing position");
        check(ReadingRules.scrollPosition(105,100)==ReadingRules.UNKNOWN,"invalid stale range");
        check(ReadingRules.scrollPosition(40,100)==ReadingRules.MORE,"middle of page");
        check(ReadingRules.scrollPosition(99,100)==ReadingRules.BOTTOM,"rounding near bottom");
        String folded=ReadingRules.profileKey(1080,2120),unfolded=ReadingRules.profileKey(1792,1920);
        check(!folded.equals(unfolded),"separate screen profiles");
        check(!folded.equals(ReadingRules.profileKey(2120,1080)),"separate orientation");
        check(folded.equals(ReadingRules.profileKey(1080,2120)),"same size restores same key");
        check(ReadingRules.bubbleRelease(false,false,false,false)==ReadingRules.START,"paused tap resumes");
        check(ReadingRules.bubbleRelease(true,false,false,false)==ReadingRules.PAUSE,"running tap never resumes on release");
        check(ReadingRules.bubbleRelease(false,true,false,false)==ReadingRules.NO_ACTION,"drag does not start");
        check(ReadingRules.bubbleRelease(false,false,true,false)==ReadingRules.NO_ACTION,"long press does not start");
        check(ReadingRules.bubbleRelease(false,false,false,true)==ReadingRules.NO_ACTION,"cancel does not start");
        ProgressWatch watch=new ProgressWatch();
        watch.begin("same-url","page-1",1000,10000);
        check(watch.observe("same-url","page-2",2000)==ProgressWatch.WAIT,"same URL content needs stability");
        check(watch.observe("same-url","page-2",2800)==ProgressWatch.CHANGED,"same URL can turn successfully");
        watch.begin("url-1","page",0,10000);
        watch.observe("url-2","page",1000);
        check(watch.observe("url-2","page",1800)==ProgressWatch.CHANGED,"changed address can confirm");
        watch.begin("url","page-1",0,10000);
        watch.observe("url","loading",1000);
        check(watch.observe("url","page-1",2000)==ProgressWatch.WAIT,"loading then original is not success");
        check(watch.observe("url","page-1",10000)==ProgressWatch.MISSED,"unchanged after grace period");
        watch.begin("url",null,0,1000);
        check(watch.observe("url",null,2000)==ProgressWatch.WAIT,"short interval still waits for slow page");
        check(watch.observe("url",null,8000)==ProgressWatch.UNREADABLE,"image-only lack of evidence is explicit");
        watch.begin("url","before",0,10000);
        watch.observe("url","candidate",1000);
        watch.clear(); watch.begin("url","new-session",3000,10000);
        check(watch.observe("url","new-session",4000)==ProgressWatch.WAIT,"restart discards pending comparison");
        check(watch.observe("url","new-session",13000)==ProgressWatch.MISSED,"restart gets its own deadline");
        watch.begin("url","before",0,10000);
        watch.observe("url","A",1000); watch.observe("url","B",1500);
        check(watch.observe("url","B",2000)==ProgressWatch.WAIT,"unstable changes restart stability timer");
        check(watch.observe("url","B",2300)==ProgressWatch.CHANGED,"settled page confirms");
        check(ReadingRules.checkDelay(3600000)==15000,"long interval check does not wait an hour");
        int misses=0;
        check(!ReadingRules.stopAfterMisses(++misses),"one failed turn allows retry");
        check(ReadingRules.stopAfterMisses(++misses),"second failed turn stops");
        System.out.println("ReadingRulesTest: "+checks+" checks passed");
    }
}
