import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;

public class InspectFixedCycleSolver {
  static void walk(SolverFeature f, String indent) {
    System.out.println(indent + "FEATURE " + f.tag() + " TYPE=" + f.getType());
    for (String p : new String[]{"linsolver","rtol","maxiter","dampfactor","tlist","tstepsbdf","initialstepbdf","control"}) {
      try { System.out.println(indent + "  " + p + "=" + f.getString(p)); } catch (Throwable ignored) {}
    }
    try {
      for (String t : f.feature().tags()) walk(f.feature(t), indent + "  ");
    } catch (Throwable ignored) {}
  }
  public static void main(String[] a) throws Exception {
    if (a.length != 1) throw new IllegalArgumentException("Usage: configured.mph");
    ModelUtil.initStandalone(false);
    Model m = ModelUtil.load("solver_audit_" + System.nanoTime(), a[0]);
    System.out.println("SOLVERS");
    for (String s : m.sol().tags()) {
      System.out.println("SOL " + s);
      for (String t : m.sol(s).feature().tags()) walk(m.sol(s).feature(t), "  ");
    }
    ModelUtil.disconnect();
  }
}
