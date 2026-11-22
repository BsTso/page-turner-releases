package local.pageturner;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;

/** Reads existing accessibility data only; never takes screenshots or retains page text. */
final class PageProbe {
    static final class ImageInfo {
        final Rect clipped, raw;
        final String signature;
        final boolean extendsBelow, fitsInViewport;
        ImageInfo(Rect clipped, Rect raw, String signature, boolean extendsBelow, boolean fitsInViewport) {
            this.clipped = clipped; this.raw = raw; this.signature = signature;
            this.extendsBelow = extendsBelow; this.fitsInViewport = fitsInViewport;
        }
    }
    static final class Snapshot {
        String content, url, scrollKey, viewport, imageSignature;
        Rect scrollBounds, imageBounds;
        int bottom = ReadingRules.UNKNOWN;
        boolean bodyVisible, loading, hasImage, extendsBelow, fitsInViewport, readable;
    }
    static String nodeKey(AccessibilityNodeInfo node) {
        // Stable across browser toolbar resizing, DOM reflows and scroll event sources.
        return node.getWindowId() + ":" + node.getViewIdResourceName() + ":" + node.getClassName();
    }
    static Rect imageBounds(AccessibilityNodeInfo root, int width, int height) {
        ImageInfo info = inspectImages(root, width, height, null);
        return info == null ? null : info.clipped;
    }
    static Rect imageBounds(AccessibilityNodeInfo root, int width, int height, Rect clamp) {
        ImageInfo info = inspectImages(root, width, height, clamp);
        return info == null ? null : info.clipped;
    }
    static ImageInfo inspectImages(AccessibilityNodeInfo root, int width, int height, Rect clamp) {
        if (root == null || width <= 0 || height <= 0) return null;
        Rect clip = clamp == null ? new Rect(0, 0, width, height) : new Rect(clamp);
        if (!clip.intersect(0, 0, width, height) || clip.isEmpty()) clip.set(0, 0, width, height);
        ArrayList<Rect> clippedList = new ArrayList<>();
        ArrayList<Rect> rawList = new ArrayList<>();
        ArrayList<String> descList = new ArrayList<>();
        int bestIdx = -1; long largest = 0; int visited = 0;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(AccessibilityNodeInfo.obtain(root));
        while (!queue.isEmpty() && visited++ < 1200) {
            AccessibilityNodeInfo node = queue.remove(); String cls = String.valueOf(node.getClassName());
            if (node.isVisibleToUser() && !node.isPassword() && cls.contains("Image")) {
                Rect raw = new Rect(); node.getBoundsInScreen(raw);
                Rect r = new Rect(raw);
                if (r.intersect(clip) && r.width() >= width * .32f && r.height() >= height * .16f) {
                    long area = r.width() * 1L * r.height();
                    if (area > width * 1L * height * .09f) {
                        CharSequence desc = node.getContentDescription();
                        clippedList.add(r);
                        rawList.add(raw);
                        descList.add(desc == null ? "" : desc.toString());
                        if (area > largest) { largest = area; bestIdx = clippedList.size() - 1; }
                    }
                }
            }
            addChildren(node, queue, visited, 1300); node.recycle();
        }
        while (!queue.isEmpty()) queue.remove().recycle();
        if (bestIdx < 0) return null;
        Rect best = new Rect(clippedList.get(bestIdx));
        Rect bestRaw = new Rect(rawList.get(bestIdx));
        StringBuilder sig = new StringBuilder();
        appendImageSig(sig, rawList.get(bestIdx), clippedList.get(bestIdx), descList.get(bestIdx));
        clippedList.remove(bestIdx); rawList.remove(bestIdx); descList.remove(bestIdx);
        // Include vertically adjacent same-column comic images so multi-image pages do not mask the next panel.
        int alignTol = Math.max(10, Math.round(width * .10f)), gapTol = Math.max(16, Math.round(height * .08f));
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = clippedList.size() - 1; i >= 0; i--) {
                Rect r = clippedList.get(i), raw = rawList.get(i);
                if (Math.abs(r.left - best.left) <= alignTol && Math.abs(r.right - best.right) <= alignTol
                    && r.top <= best.bottom + gapTol && r.bottom >= best.top - gapTol) {
                    if (!best.contains(r)) { best.union(r); merged = true; }
                    bestRaw.union(raw);
                    appendImageSig(sig, raw, r, descList.get(i));
                    clippedList.remove(i); rawList.remove(i); descList.remove(i);
                }
            }
        }
        boolean extendsBelow = bestRaw.bottom > clip.bottom + 40;
        boolean fits = bestRaw.top >= clip.top - 16 && bestRaw.bottom <= clip.bottom + 16;
        return new ImageInfo(best, bestRaw, hash(sig.toString()), extendsBelow, fits);
    }
    private static void appendImageSig(StringBuilder out, Rect raw, Rect clipped, String desc) {
        out.append(raw.left / 6).append(':').append(raw.top / 6).append(':')
           .append(raw.right / 6).append(':').append(raw.bottom / 6).append('|')
           .append(clipped.width() / 8).append('x').append(clipped.height() / 8).append('|')
           .append(desc.length() > 120 ? desc.substring(0, 120) : desc).append(';');
    }
    static Snapshot capture(AccessibilityNodeInfo root, int width, int height) {
        Snapshot result = new Snapshot(); if (root == null) return result;
        result.readable = true;
        AccessibilityNodeInfo contentRoot = null, scrollRoot = null;
        long largestWeb = 0, largestScroll = 0; int visited = 0;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(AccessibilityNodeInfo.obtain(root));
        while (!queue.isEmpty() && visited++ < 1500) {
            AccessibilityNodeInfo node = queue.remove(); Rect r = new Rect(); node.getBoundsInScreen(r);
            long area = Math.max(0, Math.min(width, r.right) - Math.max(0, r.left)) * 1L * Math.max(0, Math.min(height, r.bottom) - Math.max(0, r.top));
            if (node.isVisibleToUser() && !node.isPassword()) {
                String id = node.getViewIdResourceName(), cls = String.valueOf(node.getClassName());
                if (cls.contains("ProgressBar") && area > 0) result.loading = true;
                // Only browser address-bar IDs; never inspect general editable fields.
                if (isUrlBarId(id)) {
                    CharSequence value = node.getText();
                    if (value != null && !value.toString().contains(" ") && (value.toString().contains(".") || value.toString().startsWith("http"))) result.url = hash(value.toString());
                }
                if (cls.contains("WebView") && area > largestWeb) {
                    if (contentRoot != null) contentRoot.recycle(); contentRoot = AccessibilityNodeInfo.obtain(node); largestWeb = area;
                }
                boolean vertical = has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()) || has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId());
                boolean horizontal = has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId()) || has(node, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId());
                if ((node.isScrollable() || cls.contains("WebView")) && r.left <= width / 2 && r.right >= width / 2 && area > width * 1L * height / 5 && area > largestScroll && (vertical || !horizontal)) {
                    if (scrollRoot != null) scrollRoot.recycle(); scrollRoot = AccessibilityNodeInfo.obtain(node); largestScroll = area;
                }
            }
            addChildren(node, queue, visited, 1600); node.recycle();
        }
        while (!queue.isEmpty()) queue.remove().recycle();
        if (scrollRoot != null) {
            result.scrollKey = nodeKey(scrollRoot);
            result.scrollBounds = new Rect(); scrollRoot.getBoundsInScreen(result.scrollBounds);
            result.viewport = viewportFingerprint(scrollRoot, width, height);
            boolean forward = has(scrollRoot, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()) || has(scrollRoot, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            boolean backward = has(scrollRoot, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId()) || has(scrollRoot, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            if (forward) result.bottom = ReadingRules.MORE;
            else if (backward) result.bottom = ReadingRules.BOTTOM;
            scrollRoot.recycle();
        }
        if (contentRoot == null) contentRoot = AccessibilityNodeInfo.obtain(root);
        if (result.viewport == null) result.viewport = viewportFingerprint(contentRoot, width, height);
        result.content = fingerprint(contentRoot);
        ImageInfo img = inspectImages(contentRoot, width, height, result.scrollBounds);
        if (img != null) {
            result.imageBounds = img.clipped;
            result.imageSignature = img.signature;
            result.hasImage = true;
            result.extendsBelow = img.extendsBelow;
            result.fitsInViewport = img.fitsInViewport;
            if (img.extendsBelow) result.bottom = ReadingRules.MORE;
        }
        result.bodyVisible = result.content != null || result.hasImage;
        contentRoot.recycle(); return result;
    }
    private static boolean isUrlBarId(String id) {
        return id != null && (id.endsWith(":id/url_bar") || id.endsWith(":id/url") || id.endsWith(":id/address_bar")
            || id.endsWith(":id/mozac_browser_toolbar_url_view") || id.endsWith(":id/location_bar_edit_text")
            || id.endsWith(":id/url_text") || id.endsWith(":id/url_input"));
    }
    private static String viewportFingerprint(AccessibilityNodeInfo root, int width, int height) {
        StringBuilder geometry = new StringBuilder(); int visited = 0, landmarks = 0; boolean hasMainImage = false;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); addChildren(root, queue, 0, 1600);
        while (!queue.isEmpty() && visited++ < 1500) {
            AccessibilityNodeInfo node = queue.remove(); Rect r = new Rect(); node.getBoundsInScreen(r);
            String cls = String.valueOf(node.getClassName());
            boolean isImage = cls.contains("Image");
            boolean intersects = r.right > 0 && r.left < width && r.bottom > 0 && r.top < height;
            // Include tall images whose top/bottom extend beyond the screen; their raw bounds still move on scroll.
            boolean edge = r.top > 0 && r.top < height || r.bottom > 0 && r.bottom < height;
            if (node.isVisibleToUser() && !node.isPassword() && !node.isEditable() && !cls.contains("ProgressBar")
                && intersects && r.width() >= 24 && r.height() >= 24 && (isImage || edge && node.getChildCount() == 0)) {
                geometry.append(cls).append(':').append(r.left / 4).append(':').append(r.top / 4).append(':').append(r.right / 4).append(':').append(r.bottom / 4).append(';');
                landmarks++;
                if (isImage && r.width() >= width * .32f && r.height() >= height * .16f) hasMainImage = true;
            }
            addChildren(node, queue, visited, 1600); node.recycle();
        }
        boolean truncated = !queue.isEmpty(); while (!queue.isEmpty()) queue.remove().recycle();
        return (hasMainImage && landmarks >= 1) || (!truncated && landmarks >= 2) ? hash(geometry.toString()) : null;
    }
    private static String fingerprint(AccessibilityNodeInfo root) {
        StringBuilder text = new StringBuilder(); int visited = 0, words = 0;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(AccessibilityNodeInfo.obtain(root));
        while (!queue.isEmpty() && visited++ < 1500 && text.length() < 64000) {
            AccessibilityNodeInfo node = queue.remove();
            if (node.isVisibleToUser() && !node.isPassword() && !node.isEditable()) {
                String cls = String.valueOf(node.getClassName());
                if (!cls.contains("ProgressBar")) {
                    CharSequence label = node.getText(), description = node.getContentDescription();
                    if (label != null && label.length() > 0) { append(text, label); words++; }
                    if (description != null && description.length() > 0) { append(text, description); words++; }
                }
            }
            addChildren(node, queue, visited, 1600); node.recycle();
        }
        boolean truncated = !queue.isEmpty(); while (!queue.isEmpty()) queue.remove().recycle();
        return !truncated && words > 0 ? hash(text.toString()) : null;
    }
    private static void append(StringBuilder out, CharSequence value) { String s = value.toString(); if (s.length() > 2000) s = s.substring(0, 2000); out.append(s.length()).append(':').append(s).append('\n'); }
    private static void addChildren(AccessibilityNodeInfo node, ArrayDeque<AccessibilityNodeInfo> queue, int visited, int cap) {
        for (int i = 0; i < node.getChildCount() && queue.size() + visited < cap; i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.add(child); }
    }
    private static boolean has(AccessibilityNodeInfo node, int action) { for (AccessibilityNodeInfo.AccessibilityAction a : node.getActionList()) if (a.getId() == action) return true; return false; }
    private static String hash(String value) {
        try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return out.toString(); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private PageProbe() {}
}
