package local.pageturner;

/** Scroll actions are capabilities, not evidence that the page actually moved. */
public final class ScrollWatch {
    private String key, beforeView;
    private int y=-1, maximum=-1, beforeY=-1, beforeMax=-1, still;
    private boolean event;
    private int bottom=ReadingRules.UNKNOWN;
    public void clear() { key=null; beforeView=null; y=maximum=beforeY=beforeMax=-1; still=0; event=false; bottom=ReadingRules.UNKNOWN; }
    public void begin(String nextKey,String view) {
        if(nextKey==null || !nextKey.equals(key)) { clear(); key=nextKey; }
        beforeView=view; beforeY=y; beforeMax=maximum; event=false;
    }
    public void metric(String source,int position,int max) {
        if(key==null || !key.equals(source) || ReadingRules.scrollPosition(position,max)==ReadingRules.UNKNOWN) return;
        y=position; maximum=max; event=true;
    }
    public int finish(String nextKey,String view,int semanticBottom) {
        if(key==null || !key.equals(nextKey)) { clear(); return bottom; }
        boolean measured=event && y>=0 && maximum>0;
        boolean moved=measured && (beforeY<0 || Math.abs(y-beforeY)>2 || maximum!=beforeMax);
        boolean comparable=beforeView!=null && view!=null;
        boolean viewMoved=comparable && !beforeView.equals(view);
        if(semanticBottom==ReadingRules.BOTTOM || measured && ReadingRules.scrollPosition(y,maximum)==ReadingRules.BOTTOM) {
            bottom=ReadingRules.BOTTOM; still=0;
        } else if(moved || viewMoved) { bottom=ReadingRules.MORE; still=0; }
        else if(comparable || measured && beforeY>=0) {
            bottom=++still>=2?ReadingRules.BOTTOM:ReadingRules.MORE;
        } else { bottom=ReadingRules.UNKNOWN; still=0; }
        return bottom;
    }
    public int bottom(String source,int semanticBottom) {
        if(semanticBottom==ReadingRules.BOTTOM) return ReadingRules.BOTTOM;
        return source!=null && source.equals(key)?bottom:ReadingRules.UNKNOWN;
    }
    public int position() { return y; }
    public int maximum() { return maximum; }
}
