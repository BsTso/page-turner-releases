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
        // The system's enabled-service list can contain only bound services during a cold start.
        check(SetupState.resolve(true,true,false,false)==SetupState.CONNECTING);
        check(SetupState.resolve(true,true,false,true)==SetupState.READY);
        check(SetupState.resolve(true,false,true,true)==SetupState.OFF); // Actual revocation still wins.
        check(SetupState.resolve(false,false,true,false)==SetupState.CONNECTING);
        check(SetupState.resolve(false,false,false,true)==SetupState.READY);
        check(SetupState.resolve(false,false,false,false)==SetupState.UNKNOWN); // Never invent a revoked permission.
        String pkg="local.pageturner",service="local.pageturner.TurnService";
        check(SetupState.containsService("local.pageturner/local.pageturner.TurnService",pkg,service));
        check(SetupState.containsService("other.app/.Service:local.pageturner/.TurnService:another/.Service",pkg,service));
        check(SetupState.containsService(" local.pageturner/.TurnService ",pkg,".TurnService"));
        check(!SetupState.containsService(null,pkg,service));
        check(!SetupState.containsService("",pkg,service));
        check(!SetupState.containsService("local.pageturner/.TurnService2",pkg,service));
        check(!SetupState.containsService("local.pageturner.copy/local.pageturner.TurnService",pkg,service));
        check(!SetupState.containsService("local.pageturner:/.TurnService:local.pageturner/",pkg,service));
        check(SetupState.shouldOpenSetup(false,SetupState.OFF));
        check(!SetupState.shouldOpenSetup(false,SetupState.CONNECTING)); // Existing grant survives a fresh process.
        check(!SetupState.shouldOpenSetup(false,SetupState.READY));
        check(SetupState.shouldOpenSetup(false,SetupState.UNKNOWN)); // First install can inspect settings without claiming revoked access.
        check(!SetupState.shouldOpenSetup(true,SetupState.OFF)); // Returning users stay on the home page.
        check(!SetupState.shouldOpenSetup(true,SetupState.UNKNOWN));
        check(SetupState.needsRestore(SetupState.OFF,true)); // Previously granted but genuinely disabled.
        check(!SetupState.needsRestore(SetupState.OFF,false)); // First-time setup remains distinct.
        check(!SetupState.needsRestore(SetupState.CONNECTING,true)); // Connection delay is not revoked access.
        check(!SetupState.needsRestore(SetupState.UNKNOWN,true)); // Failed reads must not claim revocation.
        check(!SetupState.needsRestore(SetupState.READY,true));
        check(!SetupState.canFinish(SetupState.UNKNOWN,true));
        // Enabled -> disconnected -> connected does not pass through OFF or require regranting.
        check(SetupState.resolve(true,true,true,true)==SetupState.READY);
        check(SetupState.resolve(true,true,false,false)==SetupState.CONNECTING);
        check(SetupState.resolve(true,true,true,true)==SetupState.READY);
        state=SetupState.resolve(false,true);
        check(!SetupState.canFinish(state,true)); // Recheck at confirmation, not a stale UI flag.
        state=SetupState.resolve(true,false);
        check(!SetupState.canFinish(state,true));
        state=SetupState.resolve(true,true);
        check(SetupState.canFinish(state,true));
        System.out.println("PASS: "+checks+" setup state checks");
    }
}
