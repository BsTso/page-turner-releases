import local.pageturner.SetupState;

public class SetupStateTest {
    private static int checks;
    private static void check(boolean result) { checks++; if(!result) throw new AssertionError("Check "+checks); }
    public static void main(String[] args) {
        check(SetupState.resolve(false,false)==SetupState.OFF);
        check(SetupState.resolve(true,false)==SetupState.CONNECTING); // Grant before binding.
        check(SetupState.resolve(true,true)==SetupState.READY);
        check(SetupState.resolve(false,true)==SetupState.OFF); // Revocation before disconnect.
        check(!SetupState.canFinish(SetupState.OFF,true));
        check(!SetupState.canFinish(SetupState.CONNECTING,true));
        check(!SetupState.canFinish(SetupState.READY,false)); // Window creation failure / hidden point.
        check(SetupState.canFinish(SetupState.READY,true));
        int state=SetupState.resolve(true,true);
        check(SetupState.canFinish(state,true));
        state=SetupState.resolve(false,true);
        check(!SetupState.canFinish(state,true)); // Recheck at confirmation, not a stale UI flag.
        state=SetupState.resolve(true,false);
        check(!SetupState.canFinish(state,true));
        state=SetupState.resolve(true,true);
        check(SetupState.canFinish(state,true));
        System.out.println("PASS: "+checks+" setup state checks");
    }
}
