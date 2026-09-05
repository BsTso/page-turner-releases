import local.pageturner.Rules;

public class RulesTest {
    static int checks;
    static void check(boolean b) { checks++; if (!b) throw new AssertionError("Check " + checks); }
    static void reject(String s) { boolean rejected=false; try { Rules.intervalMillis(s); } catch (IllegalArgumentException e) { rejected=true; } check(rejected); }
    public static void main(String[] args) {
        check(Rules.intervalMillis("10")==10000);
        check(Rules.intervalMillis("1.5")==1500);
        check(Rules.intervalMillis("3600")==3600000);
        for(String s:new String[]{"0","-1","0.99","3601","NaN","Infinity","","abc"}) reject(s);
        for(String s:new String[]{"下一页"," 下一张 ","下一頁","Next Page","NEXT","next image"}) check(Rules.nextLabel(s));
        for(String s:new String[]{"下一步","付款","next payment","next page advertisement","下一页广告",">","→",""}) check(!Rules.nextLabel(s));
        check(!Rules.nextLabel(null));
        check(Rules.sameTarget("chrome","chrome",1,1,1080,2400,1080,2400));
        check(!Rules.sameTarget("chrome","settings",1,1,1080,2400,1080,2400));
        check(!Rules.sameTarget("chrome","chrome",1,2,1080,2400,1080,2400));
        check(!Rules.sameTarget("chrome","chrome",1,1,1080,2400,2400,1080));
        check(!Rules.sameTarget("chrome","chrome",1,1,1080,2400,1800,1900));
        check(!Rules.sameTarget("chrome",null,1,1,1080,2400,1080,2400));
        for(int mode:new int[]{0,3}) check(!Rules.shouldSwipe(mode,0,2));
        check(Rules.shouldSwipe(1,99,2));
        int phase=0;
        for(int cycle=0;cycle<3;cycle++) { check(Rules.shouldSwipe(2,phase,2)); phase=Rules.nextPhase(2,phase,2); check(Rules.shouldSwipe(2,phase,2)); phase=Rules.nextPhase(2,phase,2); check(!Rules.shouldSwipe(2,phase,2)); phase=Rules.nextPhase(2,phase,2); check(phase==0); }
        check(Math.abs(Rules.swipeEnd(.5f)-.3f)<.001f);
        check(Math.abs(Rules.swipeEnd(1)-.15f)<.001f);
        check(Math.abs(Rules.swipeEnd(0)-.45f)<.001f);
        check(Rules.ownInjectedTouch(-1,0,110,100,200));
        check(!Rules.ownInjectedTouch(4,1,110,100,200)); // Finger during injected swipe still pauses.
        check(!Rules.ownInjectedTouch(4,2,110,100,200)); // Stylus during injected swipe still pauses.
        check(!Rules.ownInjectedTouch(-1,0,201,100,200));
        check(!Rules.ownInjectedTouch(-1,0,99,100,200));
        check(!Rules.ownInjectedTouch(0,0,0,0,0));
        System.out.println("PASS: " + checks + " input, target, reading-cycle and touch-origin rule checks");
    }
}
