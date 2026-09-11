import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 固定姿态 Stationary 磁力验证。
 * 参数格式：source.mph outdir z:phi [z:phi ...]
 * 例如：source.mph out 141:0 142:0 141:180 142:180 141:360 142:360
 * 每个 z 只重建一次几何/网格；同一 z 的不同 phi 复用该网格，
 * 但每个姿态都新建独立 Stationary study/solution 并重新求解。
 */
public class StaticForceValidation141142 {
  static final String FORCE_EXPR = "mfnc.Forcex_force_magnet";
  static final Locale LOCALE = Locale.US;

  static void log(String s) { System.out.println(s); }

  static double first(double[][][] data) {
    if (data == null || data.length == 0 || data[0].length == 0 || data[0][0].length == 0)
      throw new IllegalStateException("Global evaluation returned no scalar data");
    return data[0][0][0];
  }

  static void requireProperty(PropFeature e, String property, String value) {
    if (!e.hasProperty(property)) throw new IllegalStateException("Required property missing: " + property);
    e.set(property, value);
  }

  static void configureStationarySolver(Model m, String solTag, String studyTag, String linearSolver, String stol) {
    SolverFeature s1 = m.sol(solTag).feature("s1");
    SolverFeature dDef = s1.feature("dDef");
    SolverFeature fc1 = s1.feature("fc1");
    if (!"Direct".equals(dDef.getType()) || !"FullyCoupled".equals(fc1.getType()))
      throw new IllegalStateException("Unexpected stationary solver tree for " + solTag);

    // 先配置实际被 Fully Coupled 调用的 Direct 节点，再运行求解。
    requireProperty(dDef, "linsolver", linearSolver);
    requireProperty(fc1, "linsolver", "dDef");
    if (!linearSolver.equals(dDef.getString("linsolver"))) throw new IllegalStateException("Requested direct solver was not enabled");
    if (!"dDef".equals(fc1.getString("linsolver"))) throw new IllegalStateException("FullyCoupled is not using dDef");

    // 本模型的原生控制层级在 Study 的 Stationary step，而不是 solver s1：
    // stat.usestol=on + stat.stol，solver s1.control=stat。
    StudyFeature stat = m.study(studyTag).feature("stat");
    if (!stat.hasProperty("usestol") || !stat.hasProperty("stol"))
      throw new IllegalStateException("Stationary study step has no usestol/stol properties");
    stat.set("usestol", "on");
    stat.set("stol", stol);
    if (!s1.hasProperty("control")) throw new IllegalStateException("Stationary solver has no control property");
    s1.set("control", "stat");
    if (!"stat".equals(s1.getString("control"))) throw new IllegalStateException("Stationary solver is not controlled by study step");
    log("STATIONARY_TOL study=" + studyTag + ".stat usestol=" + safeString(stat, "usestol") + " stol=" + safeString(stat, "stol"));
    log("SOLVER_CONFIG sol=" + solTag + " s1.control=" + safeString(s1, "control")
      + " fc1.linsolver=" + fc1.getString("linsolver")
      + " dDef.linsolver=" + dDef.getString("linsolver"));
  }

  static String safeString(PropFeature e, String property) {
    try { return e.getString(property); } catch (Throwable t) { return "<not-readable>"; }
  }

  static double eval(Model m, String expression) {
    try { return m.param().evaluate(expression); } catch (Throwable t) { return Double.NaN; }
  }

  static void printGeometryFeature(Model m, ModelNode comp, String tag) {
    try {
      GeomFeature f = comp.geom("geom1").feature(tag);
      String pos = safeString(f, "pos");
      String r = safeString(f, "r");
      String h = safeString(f, "h");
      log("GEOM_FEATURE tag=" + tag + " type=" + f.getType() + " pos=" + pos + " r=" + r + " h=" + h + " model_length_unit=mm");
      if (!"<not-readable>".equals(pos)) {
        try {
          String[] p = f.getStringArray("pos");
          StringBuilder b = new StringBuilder("GEOM_FEATURE_PARAM_EVAL_SI tag=" + tag + " pos=[");
          for (int i=0; i<p.length; i++) { if (i > 0) b.append(","); b.append(eval(m,p[i])); }
          b.append("]"); log(b.toString());
        } catch (Throwable ignored) {}
      }
      // 几何特征的无单位常数按模型长度单位 mm 解释；有单位参数则由最终域包围盒核验。
    } catch (Throwable e) { log("GEOM_FEATURE_CHECK_FAILED tag=" + tag + " message=" + e.getMessage()); }
  }

  static void removeCommonRotatingDomain(Model m) {
    ModelNode comp = m.component("comp1");
    for (String tag : comp.common().tags()) {
      String type = comp.common(tag).getType();
      if ("RotatingDomain".equals(type)) {
        comp.common().remove(tag);
        log("REMOVED_COMMON tag=" + tag + " type=RotatingDomain reason=static_fixed_geometry_branch");
      }
    }
  }

  static void removeStudiesSolutionsNumericals(Model m) {
    for (String tag : m.sol().tags()) m.sol().remove(tag);
    for (String tag : m.study().tags()) m.study().remove(tag);
    for (String tag : m.result().numerical().tags()) m.result().numerical().remove(tag);
    for (String tag : m.result().dataset().tags()) m.result().dataset().remove(tag);
  }

  static void printSelections(Model m) {
    Physics mfnc = m.component("comp1").physics("mfnc");
    ModelNode comp = m.component("comp1");
    int[] ball = mfnc.feature("mfc2").selection().entities(3);
    int[] force = mfnc.feature("fcal1").selection().entities(3);
    int[] steel = comp.material("mat5").selection().entities(3);
    int[] n52 = comp.material("mat3").selection().entities(3);
    int[] air = comp.material("mat4").selection().entities(3);
    log("MFC2_DOMAIN_SELECTION=" + Arrays.toString(ball));
    log("FORCECAL_DOMAIN_SELECTION=" + Arrays.toString(force));
    log("STEEL_DOMAIN_SELECTION=" + Arrays.toString(steel));
    log("N52_DOMAIN_SELECTION=" + Arrays.toString(n52));
    log("AIR_DOMAIN_SELECTION=" + Arrays.toString(air));
    log("MATERIALS=" + Arrays.toString(comp.material().tags()));
    try {
      // 6.3 中最终组件测量接口必须显式指定 geom1 和三维域维度。
      GeomMeasureFinal gm = comp.measure();
      gm.selection().geom("geom1", 3); gm.selection().set(ball);
      double v = gm.getVolume(); double[] b = gm.getBoundingBox();
      double[] ballBox = b;
      // COMSOL 6.3 的 getBoundingBox() 顺序为 [xmin,xmax,ymin,ymax,zmin,zmax]，单位跟随几何长度单位 mm。
      log(String.format(LOCALE, "MFC2_GEOMETRY volume_mm3=%.12g bbox_mm=[xmin=%.9g,xmax=%.9g,ymin=%.9g,ymax=%.9g,zmin=%.9g,zmax=%.9g] center_mm=[%.9g,%.9g,%.9g] radius_from_bbox_mm=%.9g",
        v, b[0],b[1],b[2],b[3],b[4],b[5],
        (b[0]+b[1])/2,(b[2]+b[3])/2,(b[4]+b[5])/2,
        Math.max(b[1]-b[0],Math.max(b[3]-b[2],b[5]-b[4]))/2));
      gm.selection().clear(); gm.selection().geom("geom1", 3); gm.selection().set(force);
      v = gm.getVolume(); b = gm.getBoundingBox(); double[] forceBox = b;
      log(String.format(LOCALE, "FORCECAL_GEOMETRY volume_mm3=%.12g bbox_mm=[xmin=%.9g,xmax=%.9g,ymin=%.9g,ymax=%.9g,zmin=%.9g,zmax=%.9g] center_mm=[%.9g,%.9g,%.9g]",
        v, b[0],b[1],b[2],b[3],b[4],b[5],
        (b[0]+b[1])/2,(b[2]+b[3])/2,(b[4]+b[5])/2));
      gm.selection().clear(); gm.selection().geom("geom1", 3); gm.selection().set(steel);
      v = gm.getVolume(); b = gm.getBoundingBox(); double[] steelBox = b;
      log(String.format(LOCALE, "STEEL_GEOMETRY volume_mm3=%.12g bbox_mm=[xmin=%.9g,xmax=%.9g,ymin=%.9g,ymax=%.9g,zmin=%.9g,zmax=%.9g] center_mm=[%.9g,%.9g,%.9g]",
        v, b[0],b[1],b[2],b[3],b[4],b[5],
        (b[0]+b[1])/2,(b[2]+b[3])/2,(b[4]+b[5])/2));
      gm.selection().clear(); gm.selection().geom("geom1", 3); gm.selection().set(air);
      v = gm.getVolume(); b = gm.getBoundingBox();
      log(String.format(LOCALE, "AIR_GEOMETRY volume_mm3=%.12g bbox_mm=[xmin=%.9g,xmax=%.9g,ymin=%.9g,ymax=%.9g,zmin=%.9g,zmax=%.9g]",
        v, b[0],b[1],b[2],b[3],b[4],b[5]));
      log(String.format(LOCALE, "X_GAP_STEEL_TO_CYLINDER_MM=%.12g", forceBox[0]-steelBox[1]));
      log(String.format(LOCALE, "SPHERE_CENTER_PARAM_MM=[%.12g,0,%.12g] radius_param_mm=25", eval(m,"x_sphere")*1000, eval(m,"z_sphere")*1000));
      gm.selection().clear();
      for (String tag : new String[]{"sph1","cyl1","cyl2","cyl3","sph_air_domain"}) printGeometryFeature(m, comp, tag);
      log("GEOMETRY_CHECK=PASS");
    } catch (Throwable e) {
      log("GEOMETRY_CHECK_FAILED=" + e.getMessage());
      throw new IllegalStateException("GEOMETRY_CHECK_FAILED", e);
    }
  }

  static void printPhysicsSelections(Model m) {
    Physics p = m.component("comp1").physics("mfnc");
    for (String tag : p.feature().tags()) {
      PhysicsFeature f = p.feature(tag);
      StringBuilder s = new StringBuilder("PHYSICS_FEATURE tag=" + tag + " type=" + f.getType());
      for (int dim=3; dim>=0; dim--) {
        try { int[] e=f.selection().entities(dim); if (e.length>0) s.append(" dim").append(dim).append("=").append(Arrays.toString(e)); } catch(Throwable ignored) {}
      }
      log(s.toString());
      for (String prop : f.properties()) {
        try {
          String[] a = f.getStringArray(prop);
          if (a != null && a.length > 1) log("  " + prop + "=" + Arrays.toString(a));
          else log("  " + prop + "=" + f.getString(prop));
        } catch(Throwable ignored) {
          try { log("  " + prop + "=" + f.getString(prop)); } catch(Throwable ignored2) {}
        }
      }
      if ("ZeroMagneticScalarPotential".equals(f.getType())) {
        int[] bnd = new int[0];
        try { bnd = f.selection().entities(2); } catch(Throwable ignored) {}
        log("MAGNETIC_SCALAR_POTENTIAL_REFERENCE tag=" + tag + " boundary_selection=" + Arrays.toString(bnd)
          + (bnd.length == 0 ? " status=NO_EXPLICIT_SELECTION" : " status=EXPLICIT_SELECTION"));
      }
    }
    log("MAGNETIC_INSULATION_BOUNDARIES=" + Arrays.toString(p.feature("mi1").selection().entities(2)));
  }

  static void printMaterials(Model m) {
    ModelNode c=m.component("comp1");
    for(String tag:c.material().tags()) {
      Material mat=c.material(tag);
      log("MATERIAL tag="+tag+" label="+mat.label()+" domains="+Arrays.toString(mat.selection().entities(3)));
      for(String g:mat.propertyGroup().tags()) {
        MaterialModel mm=mat.propertyGroup(g);
        log("  GROUP="+g+" type="+mm.getType());
        for(String prop:mm.properties()) { try { log("    "+prop+"="+mm.getString(prop)); } catch(Throwable ignored) {} }
      }
    }
  }

  static void applyAirBallOverride(Model m) {
    ModelNode c = m.component("comp1");
    Material n52 = c.material("mat3");
    n52.selection().set(new int[]{3});
    // 复用模型内置 Air 材料，避免新建 Common 材料时缺少磁学 def 属性。
    // 关键是先把共享 N52 从球域移除，再把原 Air 材料覆盖到域 4；因此不会修改圆柱域 3。
    Material air = c.material("mat4");
    air.selection().set(new int[]{1,4});
    MaterialModel def = air.propertyGroup("def");
    if (!def.hasProperty("relpermeability")) throw new IllegalStateException("Air override lacks relpermeability");
    def.set("relpermeability", "1");
    if (def.hasProperty("relpermittivity")) def.set("relpermittivity", "1");
    if (def.hasProperty("electricconductivity")) def.set("electricconductivity", "0[S/m]");
    log("AIR_OVERRIDE material=mat4 domain=[1,4] relpermeability=1 Br=0(no RemanentFluxDensity group) mfc2_direction=preserved_nonzero");
  }

  static double readForce(Model m, String solTag, String datasetTag, String numTag, String unit) {
    m.result().dataset().create(datasetTag, "Solution");
    m.result().dataset(datasetTag).set("solution", solTag);
    m.result().numerical().create(numTag, "Global");
    m.result().numerical(numTag).set("data", datasetTag);
    m.result().numerical(numTag).set("expr", new String[]{FORCE_EXPR});
    m.result().numerical(numTag).set("unit", new String[]{unit});
    double value = first(m.result().numerical(numTag).getData());
    m.result().numerical().remove(numTag);
    m.result().dataset().remove(datasetTag);
    return value;
  }

  static String formatVector(Model m, double phiDeg) {
    double a = eval(m, "alpha");
    double p = Math.toRadians(phiDeg);
    return String.format(LOCALE, "[%.12g,%.12g,%.12g]", Math.sin(a), Math.cos(a)*Math.cos(p), -Math.cos(a)*Math.sin(p));
  }

  static void setStaticPose(Model m, double phiDeg) {
    m.param().set("phi", String.format(LOCALE, "%.12g[deg]", phiDeg));
    m.component("comp1").physics("mfnc").feature("mfc2")
      .set("e_crel_BH_RemanentFluxDensity", new String[]{
        "sin(alpha)", "cos(alpha)*cos(phi)", "-cos(alpha)*sin(phi)"});
    log(String.format(LOCALE, "POSE phi_deg=%.12g expected_unit_magnetization=%s model_alpha_rad=%.12g", phiDeg, formatVector(m, phiDeg), eval(m,"alpha")));
    log(String.format(LOCALE, "MODEL_MAGNETIZATION_EVAL=[%.12g,%.12g,%.12g]", eval(m,"sin(alpha)"), eval(m,"cos(alpha)*cos(phi)"), eval(m,"-cos(alpha)*sin(phi)")));
  }

  static String staticStudyTag(double z, double phi) {
    return String.format(LOCALE, "st_z%g_p%g", z, phi).replace('.', 'p').replace('-', 'm');
  }

  static void solvePose(Model m, Path out, double z, double phi, int elements, String mode, String linearSolver, String stol, PrintWriter csv) throws Exception {
    setStaticPose(m, phi);
    String studyTag = staticStudyTag(z, phi);
    m.study().create(studyTag);
    m.study(studyTag).create("stat", "Stationary");
    m.study(studyTag).createAutoSequences("all");
    String[] sols = m.sol().tags();
    String solTag = sols[sols.length - 1];
    configureStationarySolver(m, solTag, studyTag, linearSolver, stol);
    Path poseDir = out.resolve(String.format(LOCALE, "z%g_phi%g", z, phi).replace('.', 'p'));
    Files.createDirectories(poseDir);
    m.save(poseDir.resolve("configured.mph").toString());
    log("CONFIGURED_SAVED z=" + z + " phi=" + phi + " study=" + studyTag + " sol=" + solTag);
    m.sol(solTag).runAll();
    log("SOLVED z=" + z + " phi=" + phi + " sol=" + solTag);
    double fxN = readForce(m, solTag, "ds_" + studyTag, "numN_" + studyTag, "N");
    double fxmN = readForce(m, solTag, "dsm_" + studyTag, "numm_" + studyTag, "mN");
    double converted = fxN * 1000.0;
    if (Math.abs(converted - fxmN) > Math.max(1e-9, Math.abs(fxmN)*1e-6))
      throw new IllegalStateException("N/mN unit mismatch: N=" + fxN + " mN=" + fxmN);
    double a=eval(m,"alpha"), p=Math.toRadians(phi);
    String row = String.format(LOCALE, "%s,%.8f,%.8f,%.12g,%.12g,%.12g,%s,%s,stol=%s,%d,NA,%.12g,%.12g,%.12g,SUCCESS,UNKNOWN,UNKNOWN",
      mode, z, phi, Math.sin(a), Math.cos(a)*Math.cos(p), -Math.cos(a)*Math.sin(p), solTag, linearSolver, stol, elements, fxN, fxmN, converted);
    csv.println(row); csv.flush(); log("RESULT " + row);
    m.save(poseDir.resolve("solved.mph").toString());
    m.save(out.resolve("static_force_validation_latest.mph").toString());
  }

  public static void main(String[] args) {
    if (args.length < 6) throw new IllegalArgumentException("Usage: source.mph outdir mode(ON|AIR) linearSolver stol z:phi [z:phi ...]");
    String source = args[0]; Path out = Paths.get(args[1]); String mode = args[2]; String linearSolver = args[3]; String stol = args[4];
    try {
      Files.createDirectories(out);
      ModelUtil.initStandalone(false);
      ModelUtil.showProgress(out.resolve("progress.log").toString());
      Model m = ModelUtil.load("static_validation_" + System.nanoTime(), source);
      log("SOURCE=" + source);
      log("PHYSICS=" + Arrays.toString(m.component("comp1").physics().tags()));
      log("PARAM z_sphere=" + m.param().get("z_sphere") + " x_sphere=" + m.param().get("x_sphere") + " alpha=" + m.param().get("alpha"));
      log("PARAM omega=" + m.param().get("omega") + " T_cycle=" + m.param().get("T_cycle"));
      removeStudiesSolutionsNumericals(m);
      removeCommonRotatingDomain(m);
      if ("AIR".equalsIgnoreCase(mode)) applyAirBallOverride(m);
      else if (!"ON".equalsIgnoreCase(mode)) throw new IllegalArgumentException("mode must be ON or AIR");
      m.param().set("phi", "0[deg]");
      PrintWriter csv = new PrintWriter(Files.newBufferedWriter(out.resolve("static_force_results.csv")));
      csv.println("mode,z_mm,phi_deg,ux,uy,uz,solution,linear_solver,tolerance,mesh_elements,dofs,Fx_N,Fx_mN,Fx_mN_from_N,status,reproducibility,mesh_validation");
      double activeZ = Double.NaN; int elements = 0;
      for (int i = 5; i < args.length; i++) {
        String[] pair = args[i].split(":");
        if (pair.length != 2) throw new IllegalArgumentException("Bad pose: " + args[i]);
        double z = Double.parseDouble(pair[0]); double phi = Double.parseDouble(pair[1]);
        if (Double.isNaN(activeZ) || Math.abs(z-activeZ) > 1e-12) {
          m.param().set("z_sphere", String.format(LOCALE, "%.12g[mm]", z));
          m.component("comp1").geom("geom1").run();
          MeshSequence mesh = m.component("comp1").mesh("mesh1");
          mesh.feature("size").set("hauto", 6);
          String local = "nearfieldfine";
          try { mesh.feature(local); } catch (Throwable e) { mesh.feature().create(local, "Size"); }
          mesh.feature(local).selection().geom("geom1", 3);
          mesh.feature(local).selection().set(new int[]{2,3,4});
          mesh.feature(local).set("hauto", 1);
          try { mesh.feature().move(local, 1); } catch (Throwable ignored) {}
          mesh.run(); elements = mesh.getNumElem(); activeZ = z;
          log("GEOM_MESH_DONE z=" + z + " elements=" + elements);
          printSelections(m);
          printPhysicsSelections(m);
          printMaterials(m);
        }
        solvePose(m, out, z, phi, elements, mode.toUpperCase(Locale.ROOT), linearSolver, stol, csv);
      }
      csv.close();
      m.save(out.resolve("static_force_validation_final.mph").toString());
      log("FINISH results=" + out.resolve("static_force_results.csv"));
      ModelUtil.disconnect();
      System.exit(0);
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
