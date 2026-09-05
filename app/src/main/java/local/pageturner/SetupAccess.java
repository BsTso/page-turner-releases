package local.pageturner;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

final class SetupAccess {
    static int state(Context context) {
        AccessibilityManager manager=(AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        ComponentName ours=new ComponentName(context,TurnService.class);
        boolean enabled=false;
        if(manager!=null) for(AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if(ours.equals(ComponentName.unflattenFromString(info.getId()))) { enabled=true; break; }
        }
        return SetupState.resolve(enabled,TurnService.instance!=null);
    }
    static void settings(Activity activity) {
        hide();
        String component=new ComponentName(activity,TurnService.class).flattenToString();
        // Public settings entry; OEMs may honor the preference highlight or show their list.
        Bundle args=new Bundle(); args.putString(":settings:fragment_args_key",component);
        Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key",component)
            .putExtra(":settings:show_fragment_args",args);
        if(!launch(activity,intent) && !launch(activity,new Intent(Settings.ACTION_SETTINGS)))
            Toast.makeText(activity,"请在手机设置中搜索「无障碍」，找到轻翻页",Toast.LENGTH_LONG).show();
    }
    static void appInfo(Activity activity) {
        hide();
        if(!launch(activity,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName()))))
            Toast.makeText(activity,"长按轻翻页图标，打开应用信息",Toast.LENGTH_LONG).show();
    }
    private static boolean launch(Activity activity,Intent intent) {
        try { activity.startActivity(intent); return true; }
        catch(ActivityNotFoundException | SecurityException e) { return false; }
    }
    static void hide() { if(TurnService.instance!=null) TurnService.instance.hidePanel(); }
}
