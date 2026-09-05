package local.pageturner;

/** Permission grant and a live service connection are separate prerequisites. */
public final class SetupState {
    public static final int OFF=0, CONNECTING=1, READY=2;
    public static int resolve(boolean enabled, boolean connected) {
        return !enabled ? OFF : connected ? READY : CONNECTING;
    }
    public static boolean canFinish(int state, boolean panelAttached) {
        return state==READY && panelAttached;
    }
    private SetupState() {}
}
