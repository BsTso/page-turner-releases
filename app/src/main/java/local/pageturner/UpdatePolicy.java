package local.pageturner;

import java.net.URI;

public final class UpdatePolicy {
    public static String version(String tag) {
        String s=tag != null && tag.startsWith("v") ? tag.substring(1) : tag;
        if(s==null || !s.matches("[0-9]{1,6}\\.[0-9]{1,6}(\\.[0-9]{1,6})?")) throw new IllegalArgumentException("无法识别发布版本");
        return s;
    }
    public static int compare(String left,String right) {
        String[] a=version(left).split("\\."),b=version(right).split("\\.");
        for(int i=0;i<3;i++) { int x=i<a.length?Integer.parseInt(a[i]):0,y=i<b.length?Integer.parseInt(b[i]):0; if(x!=y) return Integer.compare(x,y); }
        return 0;
    }
    public static boolean validRepository(String repo) { return repo!=null && repo.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9][A-Za-z0-9._-]{0,99}"); }
    public static boolean validAsset(String repo,String tag,String address) {
        try { URI uri=new URI(address); return validRepository(repo) && "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
            && uri.getPort()==-1 && uri.getUserInfo()==null && uri.getRawQuery()==null && uri.getRawFragment()==null
            && uri.getRawPath().equals("/"+repo+"/releases/download/"+tag+"/PageTurner-"+version(tag)+".apk");
        } catch(Exception e) { return false; }
    }
    private UpdatePolicy() {}
}
