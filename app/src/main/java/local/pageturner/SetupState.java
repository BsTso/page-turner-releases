package local.pageturner;

/** Permission grant and a live service connection are separate prerequisites. */
public final class SetupState {
    public static final int OFF=0, CONNECTING=1, READY=2, UNKNOWN=3;
    public static int resolve(boolean enabled, boolean connected) {
        return !enabled ? OFF : connected ? READY : CONNECTING;
    }
    public static int resolve(boolean settingsReadable,boolean configured,boolean listed,boolean connected) {
        if(settingsReadable) return resolve(configured,connected);
        // An unavailable settings read must not be presented as a revoked grant.
        if(connected) return READY;
        return listed?CONNECTING:UNKNOWN;
    }
    public static boolean containsService(String enabledServices,String packageName,String className) {
        if(enabledServices==null || packageName==null || className==null) return false;
        String expected=className.startsWith(".")?packageName+className:className;
        for(String item:enabledServices.split(":")) {
            String value=item.trim(); int slash=value.indexOf('/');
            if(slash<=0 || slash==value.length()-1 || !packageName.equals(value.substring(0,slash))) continue;
            String service=value.substring(slash+1);
            if(service.startsWith(".")) service=packageName+service;
            if(expected.equals(service)) return true;
        }
        return false;
    }
    public static boolean shouldOpenSetup(boolean seen,int state) { return !seen && (state==OFF || state==UNKNOWN); }
    public static boolean needsRestore(int state,boolean previouslyGranted) { return state==OFF && previouslyGranted; }
    public static boolean canFinish(int state, boolean panelAttached) {
        return state==READY && panelAttached;
    }
    private SetupState() {}
}
