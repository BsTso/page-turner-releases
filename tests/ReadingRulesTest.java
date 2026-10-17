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
        check(watch.observe("same-url","page-2",3500)==ProgressWatch.CHANGED,"same URL can turn successfully after settling");
        watch.begin("url-1","page",0,10000);
        watch.observe("url-2","page",1000);
        check(watch.observe("url-2","page",2500)==ProgressWatch.CHANGED,"changed address can confirm");
        watch.begin("url","page-1",0,10000);
        watch.observe("url","loading",1000);
        check(watch.observe("url","page-1",2000)==ProgressWatch.WAIT,"loading then original is not success");
        check(watch.observe("url","page-1",20000)==ProgressWatch.MISSED,"unchanged after grace period");
        watch.begin("url",null,0,1000);
        check(watch.observe("url",null,2000)==ProgressWatch.WAIT,"short interval still waits for slow page");
        check(watch.observe("url",null,20000)==ProgressWatch.UNREADABLE,"image-only lack of evidence is explicit");
        watch.begin("url","before",0,10000);
        watch.observe("url","candidate",1000);
        watch.clear(); watch.begin("url","new-session",3000,10000);
        check(watch.observe("url","new-session",4000)==ProgressWatch.WAIT,"restart discards pending comparison");
        check(watch.observe("url","new-session",23000)==ProgressWatch.MISSED,"restart gets its own deadline");
        watch.begin("url","before",0,10000);
        watch.observe("url","A",1000); watch.observe("url","B",1500);
        check(watch.observe("url","B",2000)==ProgressWatch.WAIT,"unstable changes restart stability timer");
        check(watch.observe("url","B",3000)==ProgressWatch.CHANGED,"settled page confirms");
        check(ReadingRules.checkDelay(3600000)==30000,"long interval check does not wait an hour");
        check(ReadingRules.checkDelay(1000)==20000,"short intervals leave time for slow image loading");
        check(ReadingRules.stopAfterMisses(1),"first unconfirmed turn stops without a blind second tap");
        check(!ReadingRules.findNextButton(2),"scroll then turn honors fixed point even when next labels exist");
        check(ReadingRules.findNextButton(3),"button detection remains an explicit mode");
        check(ReadingRules.confirmTurn(2,false),"disabled optional checking cannot remove combined-mode wait");
        check(!ReadingRules.confirmTurn(0,false),"manual opt-out remains available for tap-only mode");

        // URL and title may change before an image reader has reset its old bottom.
        watch.begin("url-1","old","bottom-view",ReadingRules.BOTTOM,true,0,10000);
        watch.observe("url-2","new-title","bottom-view",ReadingRules.BOTTOM,true,false,1000);
        check(watch.observe("url-2","new-title","bottom-view",ReadingRules.BOTTOM,true,false,5000)==ProgressWatch.WAIT,"URL/title change at old bottom cannot rearm tap");
        check(watch.observe("url-2","new-title","bottom-view",ReadingRules.MORE,true,false,5500)==ProgressWatch.WAIT,"stale forward action plus changed URL cannot stand in for actual reset");
        watch.observe("url-2","new","top-view",ReadingRules.MORE,true,true,6000);
        check(watch.observe("url-2","new","top-view",ReadingRules.MORE,true,true,12000)==ProgressWatch.WAIT,"stable loading state is not a ready page");
        watch.observe("url-2","new","top-view",ReadingRules.MORE,true,false,13000);
        check(watch.observe("url-2","new","top-view",ReadingRules.MORE,true,false,14500)==ProgressWatch.CHANGED,"slow page resumes only after body and layout settle");

        // Pure image readers can expose geometry and scroll reset without labels or a new URL.
        watch.begin("same",null,"image-bottom",ReadingRules.BOTTOM,true,0,10000);
        watch.observe("same",null,"image-top",ReadingRules.MORE,true,false,1000);
        check(watch.observe("same",null,"image-top",ReadingRules.MORE,true,false,2500)==ProgressWatch.CHANGED,"same-URL image reset can confirm without text");
        watch.begin("same",null,"image-bottom",ReadingRules.BOTTOM,true,0,10000);
        watch.observe("same",null,"image-top",ReadingRules.MORE,false,false,1000);
        check(watch.observe("same",null,"image-top",ReadingRules.MORE,false,false,5000)==ProgressWatch.WAIT,"empty layout must not stand in for a loaded image");

        watch.begin("old","old","old-view",ReadingRules.BOTTOM,true,0,10000);
        watch.observe("new","new","layout-1",ReadingRules.MORE,true,false,1000);
        watch.observe("new","new","layout-2",ReadingRules.MORE,true,false,2000);
        check(watch.observe("new","new","layout-2",ReadingRules.MORE,true,false,3000)==ProgressWatch.WAIT,"image reflow restarts settling timer");
        check(watch.observe("new","new","layout-2",ReadingRules.MORE,true,false,3500)==ProgressWatch.CHANGED,"settled image reflow confirms once");
        check(watch.observe("old",null,null,ReadingRules.BOTTOM,false,true,4000)==ProgressWatch.CHANGED,"completed transaction is terminal");

        watch.begin("same","old","old-view",ReadingRules.BOTTOM,true,0,10000);
        check(watch.observe("same","old","old-view",ReadingRules.MORE,true,false,20000)==ProgressWatch.MISSED,"unchanged page expires without retry permission");
        check(watch.observe("new","new","new-view",ReadingRules.MORE,true,false,23000)==ProgressWatch.MISSED,"late content cannot revive a timed-out transaction");
        watch.clear();
        check(watch.observe("new","new","new-view",ReadingRules.MORE,true,false,30000)==ProgressWatch.WAIT,"cleared watcher ignores late observations");

        // Image readers may push the new URL before replacing or loading the image.
        watch.begin("image-1",null,"old-image",ReadingRules.BOTTOM,true,0,10000);
        watch.observe("image-2",null,"old-image",ReadingRules.MORE,true,false,1000);
        check(watch.observe("image-2",null,"old-image",ReadingRules.MORE,true,false,5000)==ProgressWatch.WAIT,"new URL with old image still waits despite semantic MORE");
        watch.observe("image-2",null,"new-image",ReadingRules.UNKNOWN,false,false,6000);
        check(watch.observe("image-2",null,"new-image",ReadingRules.UNKNOWN,false,false,9000)==ProgressWatch.WAIT,"replacement image must actually be visible");
        watch.observe("image-2",null,"new-image",ReadingRules.UNKNOWN,true,false,10000);
        check(watch.observe("image-2",null,"new-image",ReadingRules.UNKNOWN,true,false,11500)==ProgressWatch.CHANGED,"short new page with UNKNOWN scrollability can settle");

        watch.begin("image-1",null,null,ReadingRules.BOTTOM,true,0,10000);
        watch.recordScrollReset(-1,0,2000);
        watch.recordScrollReset(2000,-1,2000);
        watch.recordScrollReset(2000,20,2000);
        watch.recordScrollReset(2000,0,-1);
        watch.observe("image-2",null,null,ReadingRules.MORE,true,false,1000);
        check(watch.observe("image-2",null,null,ReadingRules.MORE,true,false,5000)==ProgressWatch.WAIT,"invalid or non-top scroll metrics do not prove reset");
        watch.recordScrollReset(2000,0,0);
        watch.observe("image-2",null,null,ReadingRules.UNKNOWN,true,false,6000);
        check(watch.observe("image-2",null,null,ReadingRules.UNKNOWN,true,false,7500)==ProgressWatch.CHANGED,"measured reset supports a short image page without geometry landmarks");

        watch.begin("old","old","bottom",ReadingRules.BOTTOM,true,0,10000);
        check(watch.hasPending(),"record pending before dispatch so pause during a click cannot lose it");
        watch.resume(5000,10000);
        check(watch.hasPending(),"pause and resume preserve original dispatched turn");
        watch.observe("new","new","top",ReadingRules.MORE,true,false,6000);
        check(watch.observe("new","new","top",ReadingRules.MORE,true,false,7500)==ProgressWatch.CHANGED,"resume observes original baseline without another click");
        watch.clear();
        check(!watch.hasPending(),"confirmed turn can explicitly clear pending state");

        watch.begin("same","same","bottom",ReadingRules.BOTTOM,true,0,10000);
        check(watch.observe("same","same","bottom",ReadingRules.MORE,true,false,20000)==ProgressWatch.MISSED,"unchanged turn reaches timeout");
        check(watch.hasPending(),"timeout preserves dispatched turn instead of authorizing retry");
        watch.resume(100000,10000);
        check(watch.observe("same","same","bottom",ReadingRules.MORE,true,false,101000)==ProgressWatch.WAIT,"late resume gets a fresh observation window without a new click");
        watch.observe("manual-next","new","top",ReadingRules.UNKNOWN,true,false,102000);
        check(watch.observe("manual-next","new","top",ReadingRules.UNKNOWN,true,false,103500)==ProgressWatch.CHANGED,"manual turn after timeout can resolve original pending state");
        System.out.println("ReadingRulesTest: "+checks+" checks passed");
    }
}
