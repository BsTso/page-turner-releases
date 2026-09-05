package local.pageturner;

import android.app.Activity;
import android.content.Context;

public class LocalizedActivity extends Activity {
    private String attachedLanguage;
    @Override protected void attachBaseContext(Context base) {
        attachedLanguage=Languages.tag(base); super.attachBaseContext(Languages.wrap(base));
    }
    @Override protected void onResume() {
        super.onResume();
        if(!Languages.tag(this).equals(attachedLanguage)) recreate();
    }
    protected String s(int id,Object... values) { return values.length==0?getString(id):getString(id,values); }
    protected String quantity(int id,int count) { return getResources().getQuantityString(id,count,count); }
}
