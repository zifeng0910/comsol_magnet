import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Model A: six-gap same-mesh differential force validation.
 *
 * For every gap this program performs exactly one geom.run() and one mesh.run(),
 * then solves two magnetic configurations on that unchanged mesh:
 *   OFF: steel domain is assigned air-like RelativePermeability, mur=1, Br=0;
 *   ON : original steel material and mfnc configuration are restored.
 * The corrected holding force is ON-OFF, not the raw Force Calculation value.
 *
 * Usage:
 *   java RunModelASameMeshDifferential source.mph output.csv audit.txt level append [gaps...]
 * where level is M_h003 or M_h002 and append is true/false.
 */
public class RunModelASameMeshDifferential {
  static final Locale L = Locale.US;
  static final String FX = "mfnc.Forcex_force_magnet";
  static final String FY = "mfnc.Forcey_force_magnet";
  static final String FZ = "mfnc.Forcez_force_magnet";
  static final String[] DEFAULT_GAPS = {"0.25","0.26","0.27","0.28","0.29","0.30"};

  static String f(double x) { return String.format(L, "%.12g", x); }
  static String tag(double x) { return String.format(L, "%.3f", x).replace('.', 'p'); }
  static void log(String s) { System.out.println(s); System.out.flush(); }
  static String safe(PhysicsFeature p, String k) {
    try { return p.getString(k); } catch (Throwable t) { return "<UNREADABLE>"; }
  }
  static String firstString(String[] a) {
    return (a == null || a.length == 0) ? "<EMPTY>" : a[0];
  }
  static double first(double[][][] x) {
    if (x == null || x.length == 0 || x[0].length == 0 || x[0][0].length == 0)
      throw new IllegalStateException("empty Global numerical result");
    return x[0][0][0];
  }
  static int[] allDomains(Model m) {
    int n = m.component("comp1").geom("geom1").getNEntities()[3];
    int[] a = new int[n];
    for (int i=0; i<n; i++) a[i] = i+1;
    return a;
  }
  static double[] bbox(Model m, int dim, int id) {
    GeomMeasureFinal q = m.component("comp1").geom("geom1").measureFinal();
    q.selection().geom("geom1", dim);
    q.selection().set(new int[]{id});
    return q.getBoundingBox();
  }
  static int findDomain(Model m, double lx, double ly, double lz) {
    int hit = -1; double best = Double.POSITIVE_INFINITY;
    for (int d : allDomains(m)) {
      double[] b = bbox(m, 3, d);
      double err = Math.abs((b[1]-b[0])-lx)
                 + Math.abs((b[3]-b[2])-ly)
                 + Math.abs((b[5]-b[4])-lz);
      if (err < best) { best = err; hit = d; }
    }
    if (hit < 0 || best > 0.05)
      throw new IllegalStateException("DOMAIN_IDENTITY_FAILED target="
        + lx + "," + ly + "," + lz + " best_error=" + best);
    return hit;
  }
  static int cylinder(Model m) { return findDomain(m, 2.0, 0.8, 0.8); }
  static int steel(Model m) { return findDomain(m, 0.3, 4.0, 4.0); }

  static String oldConstitutive, oldMurMat, oldMur, oldBrMat, oldBr;
  static void captureOn(Model m) {
    PhysicsFeature p = m.component("comp1").physics("mfnc").feature("mfcs2");
    oldConstitutive = safe(p, "ConstitutiveRelationBH");
    oldMurMat = safe(p, "mur_mat");
    oldMur = safe(p, "mur");
    oldBrMat = safe(p, "normBr_crel_BH_RemanentFluxDensity_mat");
    oldBr = safe(p, "normBr_crel_BH_RemanentFluxDensity");
    log("STEEL_ON_CAPTURE constitutive=" + oldConstitutive
      + " mur_mat=" + oldMurMat + " mur=" + oldMur
      + " Br_mat=" + oldBrMat + " Br=" + oldBr);
  }

  static void setSteelOff(Model m, int st) {
    ModelNode c = m.component("comp1");
    c.material("mat4").selection().set(new int[]{1, st});
    c.material("mat5").selection().set(new int[0]);
    PhysicsFeature p = c.physics("mfnc").feature("mfcs2");
    p.selection().set(new int[]{st});
    p.set("ConstitutiveRelationBH", "RelativePermeability");
    p.set("mur_mat", "userdef");
    p.set("mur", "1");
    if (p.hasProperty("normBr_crel_BH_RemanentFluxDensity_mat"))
      p.set("normBr_crel_BH_RemanentFluxDensity_mat", "userdef");
    if (p.hasProperty("normBr_crel_BH_RemanentFluxDensity"))
      p.set("normBr_crel_BH_RemanentFluxDensity", "0[T]");
    log("STEEL_OFF_CONFIG air_domains=[1," + st + "] mfcs2_domains=[" + st
      + "] active_constitutive=RelativePermeability mur=1 remanence=OFF");
  }

  static void restoreSteelOn(Model m, int st) {
    ModelNode c = m.component("comp1");
    c.material("mat4").selection().set(new int[]{1});
    c.material("mat5").selection().set(new int[]{st});
    PhysicsFeature p = c.physics("mfnc").feature("mfcs2");
    p.selection().set(new int[]{st});
    if (!oldConstitutive.startsWith("<")) p.set("ConstitutiveRelationBH", oldConstitutive);
    if (!oldMurMat.startsWith("<")) p.set("mur_mat", oldMurMat);
    if (!oldMur.startsWith("<")) p.set("mur", oldMur);
    if (!oldBrMat.startsWith("<")) p.set("normBr_crel_BH_RemanentFluxDensity_mat", oldBrMat);
    if (!oldBr.startsWith("<")) p.set("normBr_crel_BH_RemanentFluxDensity", oldBr);
    log("STEEL_ON_CONFIG material=mat5 domains=[" + st + "] restored_constitutive="
      + oldConstitutive + " mur_mat=" + oldMurMat + " mur=" + oldMur
      + " Br_mat=" + oldBrMat + " Br=" + oldBr);
  }

  static void ensureReference(Model m) {
    PhysicsFeature z = m.component("comp1").physics("mfnc").feature("zsp1");
    int[] pts = z.selection().entities(0);
    if (pts.length == 0) {
      z.selection().geom("geom1", 0);
      z.selection().set(20);
      log("REFERENCE_SET point=20");
    } else {
      log("REFERENCE_EXISTING points=" + Arrays.toString(pts));
    }
  }

  static String solverProp(PropFeature p, String k) {
    try { return p.getString(k); } catch (Throwable t) { return "<UNREADABLE>"; }
  }
  static String createStationary(Model m, String suffix) {
    String st = "same_stat_" + suffix + "_" + System.nanoTime();
    m.study().create(st);
    m.study(st).create("stat", "Stationary");
    m.study(st).createAutoSequences("all");
    String[] sols = m.sol().tags();
    if (sols.length == 0) throw new IllegalStateException("NO_SOLVER_SEQUENCE_AFTER_CREATE");
    String sol = sols[sols.length-1];
    SolverSequence sq = m.sol(sol);
    SolverFeature s1 = sq.feature("s1");
    SolverFeature direct = s1.feature("dDef");
    SolverFeature fc = s1.feature("fc1");
    if (!"Direct".equals(direct.getType()) || !"FullyCoupled".equals(fc.getType()))
      throw new IllegalStateException("UNEXPECTED_SOLVER_TREE direct=" + direct.getType()
        + " fullyCoupled=" + fc.getType());
    direct.set("linsolver", "pardiso");
    fc.set("linsolver", "dDef");
    StudyFeature stat = m.study(st).feature("stat");
    if (!stat.hasProperty("usestol") || !stat.hasProperty("stol"))
      throw new IllegalStateException("STATIONARY_TOLERANCE_PROPERTY_MISSING");
    stat.set("usestol", "on");
    stat.set("stol", "1e-6");
    s1.set("control", "stat");
    if (!"pardiso".equals(solverProp(direct, "linsolver"))
        || !"dDef".equals(solverProp(fc, "linsolver"))
        || !"1e-6".equals(solverProp(stat, "stol")))
      throw new IllegalStateException("SOLVER_CONFIGURATION_NOT_ACTIVE");
    log("SOLVER_CONFIG study=" + st + " sol=" + sol
      + " direct=" + solverProp(direct, "linsolver")
      + " fullyCoupled=" + solverProp(fc, "linsolver")
      + " stol=" + solverProp(stat, "stol"));
    return sol;
  }

  static double readGlobal(Model m, String sol, String expr) {
    String ds = "ds_" + System.nanoTime();
    String n = "g_" + System.nanoTime();
    try {
      m.result().dataset().create(ds, "Solution");
      m.result().dataset(ds).set("solution", sol);
      m.result().numerical().create(n, "Global");
      NumericalFeature q = m.result().numerical(n);
      q.set("data", ds);
      q.set("expr", new String[]{expr});
      q.set("unit", new String[]{"N"});
      return first(q.getData()) * 1000.0; // N -> mN
    } finally {
      try { m.result().numerical().remove(n); } catch (Throwable ignored) {}
      try { m.result().dataset().remove(ds); } catch (Throwable ignored) {}
    }
  }

  static double[] solveAndRead(Model m, String sol, String mode) throws Exception {
    log("SOLVE_START mode=" + mode + " sol=" + sol);
    m.sol(sol).runAll();
    log("SOLVE_DONE mode=" + mode + " sol=" + sol);
    return new double[]{readGlobal(m, sol, FX), readGlobal(m, sol, FY), readGlobal(m, sol, FZ)};
  }

  static int meshElements(Model m) { return m.component("comp1").mesh("mesh1").getNumElem(); }
  static double minQuality(Model m) { return m.component("comp1").mesh("mesh1").getMinQuality(); }
  static int dofs(Model m, String sol) {
    int[] s = m.sol(sol).getSize();
    return (s.length == 0) ? -1 : s[0];
  }

  static String errorRow(String level, double gap, int ne, double minq, String status) {
    // Keep missing values empty; never convert a failed or undefined force to zero.
    return String.join(",", level, f(gap), "", "", "", "", "", "", "", "", "",
      Integer.toString(ne), "", f(minq), "false", status);
  }

  static String runGap(String source, String level, double gap, Path modelOut) {
    Model m = null;
    int ne = -1; double q = Double.NaN;
    try {
      m = ModelUtil.load("same_gap_" + tag(gap) + "_" + System.nanoTime(), source);
      ModelNode c = m.component("comp1");
      int cyl = cylinder(m), st = steel(m);
      log("DOMAIN_IDENTITY gap=" + f(gap) + " cylinder=" + cyl + " steel=" + st
        + " cylinder_bbox=" + Arrays.toString(bbox(m,3,cyl))
        + " steel_bbox=" + Arrays.toString(bbox(m,3,st)));
      m.param().set("x_gap", f(gap) + "[mm]");
      log("GEOMETRY_RUN_START gap=" + f(gap));
      c.geom("geom1").run();
      int cylAfter = cylinder(m), stAfter = steel(m);
      captureOn(m);
      ensureReference(m);
      log("MESH_RUN_START gap=" + f(gap));
      c.mesh("mesh1").run();
      ne = meshElements(m); q = minQuality(m);
      log("MESH_LOCKED gap=" + f(gap) + " elements=" + ne + " min_quality=" + f(q)
        + " cylinder=" + cylAfter + " steel=" + stAfter);
      String sol = createStationary(m, level + "_gap" + tag(gap));

      // The two calls below intentionally share the exact same geometry, mesh and solver sequence.
      setSteelOff(m, stAfter);
      double[] off = solveAndRead(m, sol, "STEEL_OFF");
      restoreSteelOn(m, stAfter);
      double[] on = solveAndRead(m, sol, "STEEL_ON");
      double dx = on[0]-off[0], dy = on[1]-off[1], dz = on[2]-off[2];
      int dof = dofs(m, sol);
      log("PAIR_RESULT level=" + level + " gap=" + f(gap)
        + " OFF_F=[" + f(off[0]) + "," + f(off[1]) + "," + f(off[2]) + "]"
        + " ON_F=[" + f(on[0]) + "," + f(on[1]) + "," + f(on[2]) + "]"
        + " DIFF_Fx=" + f(dx) + " elements=" + ne + " DOF=" + dof
        + " min_quality=" + f(q) + " same_mesh_verified=true");
      try {
        Files.createDirectories(modelOut.toAbsolutePath().getParent());
        m.save(modelOut.toString());
        log("MODEL_SAVED " + modelOut);
      } catch (Throwable saveError) {
        log("MODEL_SAVE_ERROR " + saveError.getMessage());
      }
      return String.join(",", level, f(gap), f(off[0]), f(off[1]), f(off[2]),
        f(on[0]), f(on[1]), f(on[2]), f(dx), f(dy), f(dz),
        Integer.toString(ne), Integer.toString(dof), f(q), "true", "SUCCESS");
    } catch (Throwable e) {
      log("GAP_ERROR level=" + level + " gap=" + f(gap) + " type="
        + e.getClass().getName() + " message=" + e.getMessage());
      e.printStackTrace(System.out);
      return errorRow(level, gap, ne, q, "ERROR_" + e.getClass().getSimpleName());
    } finally {
      if (m != null) try { ModelUtil.remove(m.name()); } catch (Throwable ignored) {}
    }
  }

  static void writeHeader(PrintWriter w) {
    w.println("mesh_level,gap_mm,Fx_off_mN,Fy_off_mN,Fz_off_mN,Fx_on_mN,Fy_on_mN,Fz_on_mN,"
      + "Fx_hold_diff_mN,Fy_hold_diff_mN,Fz_hold_diff_mN,elements,DOF,min_quality,"
      + "same_mesh_verified,status");
  }

  public static void main(String[] a) throws Exception {
    if (a.length < 5)
      throw new IllegalArgumentException("Usage: source.mph output.csv audit.txt level append [gaps...]");
    String source = a[0];
    Path csv = Paths.get(a[1]);
    Path audit = Paths.get(a[2]); // retained for a compact run manifest
    String level = a[3];
    boolean append = Boolean.parseBoolean(a[4]);
    ArrayList<Double> gaps = new ArrayList<Double>();
    if (a.length > 5) for (int i=5; i<a.length; i++) gaps.add(Double.parseDouble(a[i]));
    else for (String s : DEFAULT_GAPS) gaps.add(Double.parseDouble(s));

    Files.createDirectories(csv.toAbsolutePath().getParent());
    Files.createDirectories(audit.toAbsolutePath().getParent());
    ModelUtil.initStandalone(false);
    try {
      try { ModelUtil.showProgress(csv.toAbsolutePath().getParent().resolve(level + "_progress.log").toString()); }
      catch (Throwable ignored) {}
      log("START level=" + level + " source=" + source + " gaps=" + gaps);
      try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv,
          StandardOpenOption.CREATE, append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING))) {
        if (!append) writeHeader(w);
        for (double gap : gaps) {
          Path modelOut = csv.toAbsolutePath().getParent().resolve("model_" + level + "_gap" + tag(gap) + ".mph");
          String row = runGap(source, level, gap, modelOut);
          w.println(row); w.flush();
          log("CSV_ROW " + row);
        }
      }
      log("FINISH level=" + level + " csv=" + csv);
    } finally {
      try { ModelUtil.disconnect(); } catch (Throwable ignored) {}
    }
  }
}
