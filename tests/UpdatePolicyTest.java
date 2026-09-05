import local.pageturner.UpdatePolicy;
public class UpdatePolicyTest {
    static int count;
    static void check(boolean ok) { count++; if(!ok) throw new AssertionError("Update check "+count); }
    public static void main(String[] args) {
        check(UpdatePolicy.compare("v0.3","0.2")>0); check(UpdatePolicy.compare("0.10","0.9")>0);
        check(UpdatePolicy.compare("0.3.0","0.3")==0); check(UpdatePolicy.compare("0.3","0.4")<0); check(UpdatePolicy.compare("1.0","0.99")>0);
        for(String s:new String[]{"latest","0.3-beta","v","0.3.0.1","99999999999.1","https://evil.test"}) { boolean fails=false; try { UpdatePolicy.version(s); } catch(IllegalArgumentException e) { fails=true; } check(fails); }
        check(UpdatePolicy.validRepository("Reader/page-turner")); check(!UpdatePolicy.validRepository("../secret")); check(!UpdatePolicy.validRepository("a/b/c")); check(!UpdatePolicy.validRepository(""));
        String repo="Reader/page-turner",tag="v0.3",url="https://github.com/Reader/page-turner/releases/download/v0.3/PageTurner-0.3.apk";
        check(UpdatePolicy.validAsset(repo,tag,url));
        for(String bad:new String[]{url.replace("https:","http:"),url.replace("github.com","github.com.evil.test"),url.replace("Reader","SomeoneElse"),url+"?redirect=evil",url.replace("0.3.apk","0.2.apk"),url.replace("https://","https://user@"),url.replace("github.com","github.com:444")}) check(!UpdatePolicy.validAsset(repo,tag,bad));
        System.out.println("PASS: "+count+" release-version and download-origin checks");
    }
}
