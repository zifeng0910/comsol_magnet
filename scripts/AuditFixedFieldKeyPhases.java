import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/**
 * 141/142 数值审计：只计算 0、180、360 度三个相位。
 * A0 保留原 solver tree；A1 实际切换 sol1/sol2 的 Direct 节点为 PARDISO；
 * A2 在 A1 基础上把稳态/瞬态相对容差设为 1e-6。
 */
public class AuditFixedFieldKeyPhases {
  static void log(String s) { System.out.println(s); }

  static void setDirect(Model m, String solTag, String parentTag) {
    SolverFeature parent = m.sol(solTag).feature(parentTag);
    parent.feature("dDef").set("linsolver", "pardiso");
    parent.feature("fc1").set("linsolver", "dDef");
    log("DIRECT_ENABLED sol=" + solTag + " parent=" + parentTag + " node=dDef linsolver=pardiso");
  }

  static void setRtol(Model m, String solTag, String parentTag, String rtol) {
    try {
      m.sol(solTag).feature(parentTag).set("rtol", rtol);
      log("RTOL_SET sol=" + solTag + " parent=" + parentTag + " rtol=" + rtol);
    } catch (Throwable e) {
      log("RTOL_SET_FAILED sol=" + solTag + " parent=" + parentTag + " message=" + e.getMessage());
    }
  }

  static void printSolver(Model m, String solTag, String parentTag) {
    SolverFeature p = m.sol(solTag).feature(parentTag);
    log("SOLVER sol=" + solTag + " parent=" + parentTag + " type=" + p.getType());
    for (String t : p.feature().tags()) {
      SolverFeature f = p.feature(t);
      log("  CHILD tag=" + t + " type=" + f.getType());
      try { log("    linsolver=" + f.getString("linsolver")); } catch (Throwable ignored) {}
      try { log("    rtol=" + f.getString("rtol")); } catch (Throwable ignored) {}
    }
  }

  public static void main(String[] a) {
    if (a.length != 4) throw new IllegalArgumentException("Usage: source.mph z_mm profile(A0|A1|A2) outdir");
    String source = a[0], z = a[1], profile = a[2];
    Path out = Paths.get(a[3]);
    try {
      Files.createDirectories(out);
      ModelUtil.initStandalone(false);
      ModelUtil.showProgress(out.resolve("progress.log").toString());
      Model m = ModelUtil.load("audit_" + System.nanoTime(), source);
      log("CONFIG source=" + source + " z=" + z + " profile=" + profile);

      for (String s : m.sol().tags()) m.sol().remove(s);
      for (String s : m.study().tags()) m.study().remove(s);
      for (String s : m.result().numerical().tags()) m.result().numerical().remove(s);
      for (String s : m.component("comp1").common().tags()) {
        if (m.component("comp1").common(s).getType().equals("RotatingDomain")) {
          m.component("comp1").common().remove(s);
          log("REMOVED_COMMON tag=" + s + " type=RotatingDomain");
        }
      }

      m.param().set("z_sphere", z + "[mm]");
      m.component("comp1").physics("mfnc").feature("mfc2")
        .set("e_crel_BH_RemanentFluxDensity", new String[]{
          "sin(alpha)", "cos(alpha)*cos(omega*t)", "-cos(alpha)*sin(omega*t)"});
      m.component("comp1").geom("geom1").run();
      MeshSequence mesh = m.component("comp1").mesh("mesh1");
      mesh.feature("size").set("hauto", 6);
      String local = "nearfieldfine";
      try { mesh.feature(local); } catch (Throwable e) { mesh.feature().create(local, "Size"); }
      mesh.feature(local).selection().geom("geom1", 3);
      mesh.feature(local).selection().set(new int[]{2, 3, 4});
      mesh.feature(local).set("hauto", 1);
      try { mesh.feature().move(local, 1); } catch (Throwable ignored) {}
      mesh.run();
      log("MESH_DONE elements=" + mesh.getNumElem());

      // 初始相位 alpha 的稳态场。
      m.component("comp1").physics("mfnc").feature("mfc2")
        .set("e_crel_BH_RemanentFluxDensity", new String[]{"sin(alpha)", "cos(alpha)", "0"});
      m.study().create("fieldinit");
      m.study("fieldinit").create("stat", "Stationary");
      m.study("fieldinit").run();
      String initSol = m.sol().tags()[0];
      log("INITIAL_FIELD_SOLVED sol=" + initSol);

      // 周期相位只取 0、180、360 度；三点足以先确认正负翻转是否数值稳定。
      m.component("comp1").physics("mfnc").feature("mfc2")
        .set("e_crel_BH_RemanentFluxDensity", new String[]{
          "sin(alpha)", "cos(alpha)*cos(omega*t)", "-cos(alpha)*sin(omega*t)"});
      m.study().create("fixedcycle");
      m.study("fixedcycle").create("time", "Transient");
      m.study("fixedcycle").feature("time").set("useinitsol", true);
      m.study("fixedcycle").feature("time").set("initmethod", "sol");
      m.study("fixedcycle").feature("time").set("initstudy", "fieldinit");
      m.study("fixedcycle").feature("time").set("tlist", "range(0,T_cycle/2,T_cycle)");
      m.study("fixedcycle").createAutoSequences("all");
      String sol = m.sol().tags()[m.sol().tags().length - 1];
      m.sol(sol).feature("v1").set("initmethod", "sol");
      m.sol(sol).feature("v1").set("initsol", initSol);

      printSolver(m, initSol, "s1");
      printSolver(m, sol, "t1");
      if (profile.equals("A1") || profile.equals("A2")) {
        setDirect(m, initSol, "s1");
        setDirect(m, sol, "t1");
      }
      if (profile.equals("A2")) {
        setRtol(m, initSol, "s1", "1e-6");
        setRtol(m, sol, "t1", "1e-6");
      }
      printSolver(m, initSol, "s1");
      printSolver(m, sol, "t1");
      m.save(out.resolve("configured.mph").toString());
      log("CONFIGURED_SAVED");
      m.sol(sol).runAll();
      log("SOLVED");

      m.result().dataset().create("auditdata", "Solution");
      m.result().dataset("auditdata").set("solution", sol);
      m.result().numerical().create("force_read", "Global");
      m.result().numerical("force_read").set("data", "auditdata");
      m.result().numerical("force_read").set("expr", new String[]{"mfnc.Forcex_force_magnet", "t"});
      m.result().numerical("force_read").set("unit", new String[]{"mN", "s"});
      double[][][] d = m.result().numerical("force_read").getData();
      double period = m.param().evaluate("T_cycle");
      try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.resolve("key_phases.csv")))) {
        w.println("z_mm,angle_deg,time_s,Fx_mN,profile");
        for (int i = 0; i < d[0].length; i++) {
          double f = d[0][i][0];
          double t = d[1][i][0];
          double angle = 360.0 * t / period;
          String line = String.format(Locale.US, "%s,%.8f,%.12g,%.12g,%s", z, angle, t, f, profile);
          w.println(line); log(line);
        }
      }
      m.save(out.resolve("solved.mph").toString());
      log("SUCCESS");
      ModelUtil.disconnect();
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
