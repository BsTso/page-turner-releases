package local.pageturner;

import java.util.Locale;

/** Locale preferences, never location or download tracking. */
public final class LanguagePolicy {
    public static String supported(String tag) {
        if(tag==null) return null;
        String language=Locale.forLanguageTag(tag.replace('_','-')).getLanguage();
        return language.equals("zh") || language.equals("en") || language.equals("ja") ? language : null;
    }
    public static String resolve(String choice,String... systemTags) {
        String explicit=supported(choice); if(explicit!=null) return explicit;
        for(String tag:systemTags) { String language=supported(tag); if(language!=null) return language; }
        return "en";
    }
    private LanguagePolicy() {}
}
