package local.pageturner;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;

final class Languages {
    static void migrate(Context context) {
        if(Build.VERSION.SDK_INT<33) return;
        android.content.SharedPreferences prefs=context.getSharedPreferences("settings",0);
        if(prefs.getBoolean("native_language_migrated",false)) return;
        LocaleManager manager=context.getSystemService(LocaleManager.class);
        String saved=LanguagePolicy.supported(prefs.getString("language","system"));
        prefs.edit().putBoolean("native_language_migrated",true).apply();
        if(saved!=null && manager.getApplicationLocales().isEmpty()) manager.setApplicationLocales(LocaleList.forLanguageTags(saved));
    }
    static String choice(Context context) {
        if(Build.VERSION.SDK_INT>=33) {
            LocaleList locales=context.getSystemService(LocaleManager.class).getApplicationLocales();
            return locales.isEmpty()?"system":locales.get(0).toLanguageTag();
        }
        return context.getSharedPreferences("settings",0).getString("language","system");
    }
    static String tag(Context context) {
        LocaleList locales=Build.VERSION.SDK_INT>=33 ? context.getSystemService(LocaleManager.class).getSystemLocales() : Resources.getSystem().getConfiguration().getLocales();
        String[] tags=new String[locales.size()]; for(int i=0;i<tags.length;i++) tags[i]=locales.get(i).toLanguageTag();
        return LanguagePolicy.resolve(choice(context),tags);
    }
    static Context wrap(Context context) {
        Configuration config=new Configuration(context.getResources().getConfiguration());
        Locale locale=Locale.forLanguageTag(tag(context));
        config.setLocales(new LocaleList(locale)); config.setLayoutDirection(locale);
        return context.createConfigurationContext(config);
    }
    static void choose(Context context,String choice) {
        if(TurnService.instance!=null) TurnService.instance.hidePanel();
        context.getSharedPreferences("settings",0).edit().putString("language",choice).apply();
        if(Build.VERSION.SDK_INT>=33) context.getSystemService(LocaleManager.class).setApplicationLocales(choice.equals("system")?LocaleList.getEmptyLocaleList():LocaleList.forLanguageTags(choice));
    }
    static String name(Context context) {
        String choice=choice(context);
        if(choice.equals("system")) return context.getString(R.string.language_system);
        String normalized=LanguagePolicy.supported(choice);
        return "zh".equals(normalized)?"简体中文":"ja".equals(normalized)?"日本語":"English";
    }
    private Languages() {}
}
