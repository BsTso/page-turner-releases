package local.pageturner;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;

/** Reads existing accessibility data only; never takes screenshots or retains page text. */
final class PageProbe {
    static final class Snapshot {
        String content, url, scrollKey;
        int bottom=ReadingRules.UNKNOWN;
    }
    static String nodeKey(AccessibilityNodeInfo node) {
        Rect r=new Rect(); node.getBoundsInScreen(r);
        return node.getWindowId()+":"+node.getViewIdResourceName()+":"+node.getClassName()+":"+r.toShortString();
    }
    static Snapshot capture(AccessibilityNodeInfo root,int width,int height) {
        Snapshot result=new Snapshot(); if(root==null) return result;
        AccessibilityNodeInfo contentRoot=null, scrollRoot=null;
        long largestWeb=0,largestScroll=0; int visited=0;
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>(); queue.add(AccessibilityNodeInfo.obtain(root));
        while(!queue.isEmpty() && visited++<1500) {
            AccessibilityNodeInfo node=queue.remove(); Rect r=new Rect(); node.getBoundsInScreen(r);
            long area=Math.max(0,Math.min(width,r.right)-Math.max(0,r.left))*1L*Math.max(0,Math.min(height,r.bottom)-Math.max(0,r.top));
            if(node.isVisibleToUser() && !node.isPassword()) {
                String id=node.getViewIdResourceName(),cls=String.valueOf(node.getClassName());
                // Only browser address-bar IDs; never inspect general editable fields.
                if(id!=null && (id.endsWith(":id/url_bar") || id.endsWith(":id/url") || id.endsWith(":id/address_bar"))) {
                    CharSequence value=node.getText();
                    if(value!=null && !value.toString().contains(" ") && (value.toString().contains(".") || value.toString().startsWith("http"))) result.url=hash(value.toString());
                }
                if(cls.contains("WebView") && area>largestWeb) {
                    if(contentRoot!=null) contentRoot.recycle(); contentRoot=AccessibilityNodeInfo.obtain(node); largestWeb=area;
                }
                boolean vertical=has(node,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()) || has(node,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId());
                boolean horizontal=has(node,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId()) || has(node,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId());
                if(node.isScrollable() && area>width*1L*height/5 && area>largestScroll && (vertical || !horizontal)) {
                    if(scrollRoot!=null) scrollRoot.recycle(); scrollRoot=AccessibilityNodeInfo.obtain(node); largestScroll=area;
                }
            }
            addChildren(node,queue,visited); node.recycle();
        }
        boolean truncated=!queue.isEmpty(); while(!queue.isEmpty()) queue.remove().recycle();
        if(scrollRoot!=null) {
            result.scrollKey=nodeKey(scrollRoot);
            boolean forward=has(scrollRoot,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()) || has(scrollRoot,AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            boolean backward=has(scrollRoot,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId()) || has(scrollRoot,AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            if(forward) result.bottom=ReadingRules.MORE;
            else if(backward) result.bottom=ReadingRules.BOTTOM;
            scrollRoot.recycle();
        }
        if(contentRoot==null) contentRoot=AccessibilityNodeInfo.obtain(root);
        if(!truncated) result.content=fingerprint(contentRoot);
        contentRoot.recycle(); return result;
    }
    private static String fingerprint(AccessibilityNodeInfo root) {
        StringBuilder text=new StringBuilder(); int visited=0,words=0;
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>(); queue.add(AccessibilityNodeInfo.obtain(root));
        while(!queue.isEmpty() && visited++<1500 && text.length()<64000) {
            AccessibilityNodeInfo node=queue.remove();
            if(node.isVisibleToUser() && !node.isPassword() && !node.isEditable()) {
                String cls=String.valueOf(node.getClassName());
                if(!cls.contains("ProgressBar")) {
                    CharSequence label=node.getText(),description=node.getContentDescription();
                    if(label!=null && label.length()>0) { append(text,label); words++; }
                    if(description!=null && description.length()>0) { append(text,description); words++; }
                }
            }
            addChildren(node,queue,visited); node.recycle();
        }
        boolean truncated=!queue.isEmpty(); while(!queue.isEmpty()) queue.remove().recycle();
        return !truncated && words>0 ? hash(text.toString()) : null;
    }
    private static void append(StringBuilder out,CharSequence value) { String s=value.toString(); if(s.length()>2000) s=s.substring(0,2000); out.append(s.length()).append(':').append(s).append('\n'); }
    private static void addChildren(AccessibilityNodeInfo node,ArrayDeque<AccessibilityNodeInfo> queue,int visited) {
        for(int i=0;i<node.getChildCount() && queue.size()+visited<1600;i++) { AccessibilityNodeInfo child=node.getChild(i); if(child!=null) queue.add(child); }
    }
    private static boolean has(AccessibilityNodeInfo node,int action) { for(AccessibilityNodeInfo.AccessibilityAction a:node.getActionList()) if(a.getId()==action) return true; return false; }
    private static String hash(String value) {
        try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return out.toString(); }
        catch(java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private PageProbe() {}
}
