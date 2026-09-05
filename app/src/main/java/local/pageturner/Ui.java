package local.pageturner;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
final class Ui {
    static final int INK=0xff202126, MUTED=0xff84858d, BG=0xfffafafa, LINE=0xffe9e9ec;
    static int dp(Context c,float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c,int color,float radius) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c,radius)); return g; }
    static TextView text(Context c,String s,float size,int color) { TextView t=new TextView(c); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(false); return t; }
    static TextView action(Context c,String label,int color,int background) {
        TextView t=text(c,label,15,color); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(c,48)); t.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22888888),shape(c,background,16),null)); t.setClickable(true); t.setFocusable(true); return t;
    }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static void bold(TextView t) { t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); }
    private Ui() {}
}
