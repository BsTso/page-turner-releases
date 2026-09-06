import local.pageturner.*;

public class ScrollTest {
    private static int checks;
    private static void check(boolean value,String reason) { checks++; if(!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        ScrollWatch watch=new ScrollWatch();
        check(watch.bottom("web",ReadingRules.MORE)==ReadingRules.UNKNOWN,"capability alone cannot cause endless swiping");
        watch.begin("web","top"); watch.metric("ad",100,100);
        check(watch.position()==-1,"ignore nested advertisement scrolls");
        watch.metric("web",300,1500);
        check(watch.finish("web","middle",ReadingRules.MORE)==ReadingRules.MORE,"actual motion keeps long pages scrolling");
        watch.begin("web","middle"); watch.metric("web",1500,1500);
        check(watch.finish("web","end",ReadingRules.MORE)==ReadingRules.BOTTOM,"real bottom wins over stale forward capability");
        watch.clear(); watch.begin("web","end");
        check(watch.finish("web","end",ReadingRules.MORE)==ReadingRules.MORE,"one unchanged swipe is not enough");
        watch.begin("web","end");
        check(watch.finish("web","end",ReadingRules.MORE)==ReadingRules.BOTTOM,"two stationary swipes confirm bottom even with forward action");
        watch.clear(); watch.begin("web",null);
        check(watch.finish("web",null,ReadingRules.MORE)==ReadingRules.UNKNOWN,"unreadable canvas is not evidence of bottom");
        watch.begin("web","one"); watch.metric("web",300,2000); watch.finish("web","two",ReadingRules.MORE);
        watch.begin("new-window","end");
        check(watch.position()==-1,"new scroller discards old position");
        watch.finish("new-window","end",ReadingRules.MORE);
        watch.begin("new-window","end");
        check(watch.finish("new-window","loaded",ReadingRules.MORE)==ReadingRules.MORE,"lazy-loaded content breaks stationary streak");
        watch.clear(); watch.begin("web",null); watch.metric("web",500,2000); watch.finish("web",null,ReadingRules.MORE);
        watch.begin("web",null); watch.metric("web",500,2000);
        check(watch.finish("web",null,ReadingRules.MORE)==ReadingRules.MORE,"one repeated metric allows a retry");
        watch.begin("web",null); watch.metric("web",500,2000);
        check(watch.finish("web",null,ReadingRules.MORE)==ReadingRules.BOTTOM,"stationary measured scroller cannot loop forever");
        watch.clear(); watch.begin("web","end"); watch.metric("web",50,100); watch.finish("web","end",ReadingRules.MORE);
        watch.begin("web","end"); watch.metric("web",50,1000);
        check(watch.finish("web","end",ReadingRules.MORE)==ReadingRules.MORE,"expanded range is more content, not bottom");
        ScrollPlan full=new ScrollPlan(ScrollPlan.PAGE,10000); full.locate(0,4000,0);
        check(full.target(4000)==4000,"full pass uses page length, not screen percentage");
        check(full.distance(0,4000,1000,1)==850,"long pages use safe successive strokes");
        check(full.duration(0,4000,850,1,0)==2125,"time is apportioned over the whole page");
        check(!full.reached(850,4000),"one screen does not finish a long page");
        check(full.reached(4000,4000),"full pass ends at actual bottom");
        ScrollPlan half=new ScrollPlan(ScrollPlan.HALVES,10000); half.locate(0,4000,0);
        check(half.target(4000)==2000,"half means half the page travel");
        check(half.reached(2000,4000),"first part pauses halfway");
        ScrollPlan second=new ScrollPlan(ScrollPlan.HALVES,10000); second.locate(2000,4000,10000);
        check(second.target(4000)==4000,"next part advances to bottom, not halfway again");
        ScrollPlan resumed=new ScrollPlan(ScrollPlan.HALVES,10000); resumed.locate(2500,4000,0);
        check(resumed.target(4000)==4000,"resume after halfway continues downward");
        ScrollPlan probe=new ScrollPlan(ScrollPlan.HALVES,10000); probe.locateAfterProbe(60,100,60,100);
        check(probe.target(100)==50 && probe.reached(60,100),"short page probe must not skip first pause");
        ScrollPlan unknown=new ScrollPlan(ScrollPlan.HALVES,10000); unknown.locate(-1,-1,0);
        check(!unknown.located() && !unknown.reached(0,0),"unreported page length is explicit");
        check(half.target(6000)==3000,"lazy images update halfway target");
        check(full.distance(3900,4000,1000,2)==200,"CSS-to-screen scale controls remaining drag");
        check(Rules.swipeDuration(60000)==30000 && Rules.swipeDuration(0)==1000,"duration bounds");
        System.out.println("ScrollTest: "+checks+" checks passed");
    }
}
