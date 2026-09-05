package local.pageturner;

import android.content.SharedPreferences;
import android.graphics.Point;
import android.view.WindowManager;

final class ScreenProfiles {
    static String key(WindowManager manager) { Point s=new Point(); manager.getDefaultDisplay().getRealSize(s); return ReadingRules.profileKey(s.x,s.y); }
    static void migrate(SharedPreferences prefs,String key) {
        if(prefs.getBoolean("screen_profiles_migrated",false)) return;
        prefs.edit().putFloat(key+"x",prefs.getFloat("x",.8f)).putFloat(key+"y",prefs.getFloat("y",.5f))
            .putFloat(key+"swipe",prefs.getFloat("swipe",.5f)).putBoolean("screen_profiles_migrated",true).apply();
    }
    static float get(SharedPreferences prefs,String key,String field,float fallback) { migrate(prefs,key); return prefs.getFloat(key+field,fallback); }
    static void put(SharedPreferences prefs,String key,String field,float value) { migrate(prefs,key); prefs.edit().putFloat(key+field,value).apply(); }
    private ScreenProfiles() {}
}
