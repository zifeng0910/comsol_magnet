import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 固定姿态 Stationary 磁力验证。
 * 参数格式：source.mph outdir mode(ON|AIR) linearSolver stol [meshMode(M0|M1)] z:phi [z:phi ...]
 * 例如：source.mph out 141:0 142:0 141:180 142:180 141:360 142:360
 * 每个 z 只重建一次几何/网格；同一 z 的不同 phi 复用该网格，
 * 但每个姿态都新建独立 Stationary study/solution 并重新求解。
 */
public class StaticForceValidation141142 {
  static final String FORCE_EXPR = "mfnc.Forcex_force_magnet";
  static final double[] SPARSE_ANGLES_DEG = new double[]{0,45,90,135,180,225,270,315,360};
  static final Locale LOCALE = Locale.US;
  static String onConstitutive, onMurMat, onMur, onBrMat, onBr;

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

  static void requirePhysicsProperty(PhysicsFeature e, String property, String value) {
    if (!e.hasProperty(property)) throw new IllegalStateException("Required physics property missing: " + property);
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
        for (int dim=3; dim>=0; dim--) {
          try { log("ZSP_SELECTION tag=" + tag + " dim=" + dim + " entities=" + Arrays.toString(f.selection().entities(dim))); }
          catch(Throwable e) { log("ZSP_SELECTION tag=" + tag + " dim=" + dim + " status=READ_ERROR message=" + e.getMessage()); }
        }
      }
    }
    log("MAGNETIC_INSULATION_BOUNDARIES=" + Arrays.toString(p.feature("mi1").selection().entities(2)));
  }

  static void printMagneticBoundaryAudit(Model m) {
    ModelNode comp = m.component("comp1");
    PhysicsFeature mi = comp.physics("mfnc").feature("mi1");
    int[] boundaries = mi.selection().entities(2);
    GeomMeasureFinal gm = comp.measure();
    log("MAGNETIC_BOUNDARY_AUDIT_BEGIN count=" + boundaries.length);
    for (int b : boundaries) {
      try {
        gm.selection().clear(); gm.selection().geom("geom1", 2); gm.selection().set(new int[]{b});
        double area = gm.getArea(); double[] box = gm.getBoundingBox();
        int[] adj = comp.geom("geom1").getAdjExt(2, 3, b);
        boolean external = (adj.length == 1) || (adj.length == 2 && (adj[0] == 0 || adj[1] == 0));
        boolean internal = adj.length == 2 && !external;
        log(String.format(LOCALE,
          "MI_BOUNDARY id=%d area_mm2=%.12g bbox_mm=[xmin=%.9g,xmax=%.9g,ymin=%.9g,ymax=%.9g,zmin=%.9g,zmax=%.9g] adjacent_domains=%s identity=%s",
          b, area, box[0],box[1],box[2],box[3],box[4],box[5], Arrays.toString(adj),
          external ? "EXTERNAL" : (internal ? "INTERNAL" : "UNRESOLVED")));
      } catch (Throwable e) {
        log("MI_BOUNDARY_AUDIT_FAILED id=" + b + " message=" + e.getMessage());
      }
    }
    gm.selection().clear();
    log("MAGNETIC_BOUNDARY_AUDIT_END");
  }

  static void ensureMagneticScalarReference(Model m) {
    ModelNode comp = m.component("comp1");
    PhysicsFeature zsp = comp.physics("mfnc").feature("zsp1");
    int[] p = zsp.selection().entities(0);
    int[] b = zsp.selection().entities(2);
    log("ZSP_REFERENCE_BEFORE point_selection=" + Arrays.toString(p) + " boundary_selection=" + Arrays.toString(b));
    if (p.length == 0 && b.length == 0) {
      int[] nentities = comp.geom("geom1").getNEntities();
      int npoints = nentities.length > 0 ? nentities[0] : 0;
      if (npoints < 20) throw new IllegalStateException("GEOMETRY_CHECK_FAILED: fixed reference point 20 does not exist; npoints=" + npoints);
      double[][] vertices = comp.geom("geom1").getVertexCoord();
      double[] xyz = new double[vertices.length];
      for (int d=0; d<vertices.length; d++) xyz[d] = vertices[d][19];
      log("ZSP_REFERENCE_CANDIDATE point=20 coord_model_unit=" + Arrays.toString(xyz) + " npoints=" + npoints);
      // 旧原生建模代码明确使用 zsp1.selection().set(20)。这里恢复同一个固定点，
      // 不随 z_sphere 移动，也不额外增加第二个参考约束。
      zsp.selection().geom("geom1", 0);
      zsp.selection().set(20);
      int[] after = zsp.selection().entities(0);
      if (!Arrays.equals(after, new int[]{20})) throw new IllegalStateException("CONTROL_CONFIG_ERROR zsp1 point reference was not applied");
      log("ZSP_REFERENCE_AFTER point_selection=" + Arrays.toString(after) + " coord_model_unit=" + Arrays.toString(xyz) + " status=EXPLICIT_FIXED_POINT");
    } else {
      log("ZSP_REFERENCE_AFTER existing_point_selection=" + Arrays.toString(p) + " existing_boundary_selection=" + Arrays.toString(b) + " status=EXISTING_REFERENCE");
    }
  }

  static String physicsString(PhysicsFeature f, String property) {
    try { return f.getString(property); } catch (Throwable e) { return "<READ_ERROR:" + e.getMessage() + ">"; }
  }

  static void validateModeConfiguration(Model m, String mode) {
    ModelNode c = m.component("comp1");
    PhysicsFeature mfc2 = c.physics("mfnc").feature("mfc2");
    String constitutive = physicsString(mfc2, "ConstitutiveRelationBH");
    String murSource = physicsString(mfc2, "mur_mat");
    String mur = physicsString(mfc2, "mur");
    String brSource = physicsString(mfc2, "normBr_crel_BH_RemanentFluxDensity_mat");
    String br = physicsString(mfc2, "normBr_crel_BH_RemanentFluxDensity");
    log("ACTIVE_MFC2_CONFIG mode=" + mode + " ConstitutiveRelationBH=" + constitutive
      + " mur_mat=" + murSource + " mur=" + mur + " normBr_source=" + brSource + " normBr=" + br
      + " domains=" + Arrays.toString(mfc2.selection().entities(3)));
    if ("AIR".equalsIgnoreCase(mode)) {
      boolean ok = "RelativePermeability".equals(constitutive)
        && "userdef".equals(murSource) && mur.trim().startsWith("1")
        && Arrays.equals(c.material("mat3").selection().entities(3), new int[]{3})
        && Arrays.equals(c.material("mat4").selection().entities(3), new int[]{1,4});
      if (!ok) throw new IllegalStateException("CONTROL_CONFIG_ERROR AIR active constitutive/material verification failed");
      log("AIR_CONTROL_CONFIG=PASS active_constitutive=RelativePermeability active_mur=1 active_remanence=OFF material_domains=mat3:[3],mat4:[1,4]");
    } else if ("AIR_BR0".equalsIgnoreCase(mode)) {
      boolean ok = "RemanentFluxDensity".equals(constitutive)
        && "userdef".equals(murSource) && mur.trim().startsWith("1")
        && br.trim().startsWith("0")
        && Arrays.equals(c.material("mat3").selection().entities(3), new int[]{3})
        && Arrays.equals(c.material("mat4").selection().entities(3), new int[]{1,4});
      if (!ok) throw new IllegalStateException("CONTROL_CONFIG_ERROR AIR_BR0 active constitutive/material verification failed");
      log("AIR_BR0_CONTROL_CONFIG=PASS active_constitutive=RemanentFluxDensity active_mur=1 active_normBr=0 material_domains=mat3:[3],mat4:[1,4]");
    } else if ("ON".equalsIgnoreCase(mode)) {
      log("ON_CONTROL_CONFIG=RECORDED active_constitutive=" + constitutive + " active_mur=" + mur + " active_normBr=" + br);
    } else {
      throw new IllegalStateException("CONTROL_CONFIG_ERROR unknown mode=" + mode);
    }
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
    // 关键修复：mfc2 自身仍保留 RemanentFluxDensity 时，单改材料不会空气化。
    // 切换活动本构为 RelativePermeability，并把相对磁导率改为明确的用户值 1。
    PhysicsFeature mfc2 = c.physics("mfnc").feature("mfc2");
    requirePhysicsProperty(mfc2, "ConstitutiveRelationBH", "RelativePermeability");
    requirePhysicsProperty(mfc2, "mur_mat", "userdef");
    requirePhysicsProperty(mfc2, "mur", "1");
    // 这些剩磁字段在 RelativePermeability 下应为非活动字段；清零只为避免后续误读，
    // 有效性由上面的活动本构回读决定，不能仅凭这个 0[T] 宣称空气化。
    if (mfc2.hasProperty("normBr_crel_BH_RemanentFluxDensity_mat")) mfc2.set("normBr_crel_BH_RemanentFluxDensity_mat", "userdef");
    if (mfc2.hasProperty("normBr_crel_BH_RemanentFluxDensity")) mfc2.set("normBr_crel_BH_RemanentFluxDensity", "0[T]");
    log("AIR_OVERRIDE material=mat4 domain=[1,4] mfc2.ConstitutiveRelationBH=RelativePermeability mfc2.mur_mat=userdef mfc2.mur=1 remanence_fields_cleared_inactive");
  }

  static void applyAirBallBrZeroOverride(Model m) {
    ModelNode c = m.component("comp1");
    c.material("mat3").selection().set(new int[]{3});
    c.material("mat4").selection().set(new int[]{1,4});
    PhysicsFeature mfc2 = c.physics("mfnc").feature("mfc2");
    requirePhysicsProperty(mfc2, "ConstitutiveRelationBH", "RemanentFluxDensity");
    requirePhysicsProperty(mfc2, "mur_mat", "userdef");
    requirePhysicsProperty(mfc2, "mur", "1");
    requirePhysicsProperty(mfc2, "normBr_crel_BH_RemanentFluxDensity_mat", "userdef");
    requirePhysicsProperty(mfc2, "normBr_crel_BH_RemanentFluxDensity", "0[T]");
    mfc2.set("e_crel_BH_RemanentFluxDensity", new String[]{"1", "0", "0"});
    log("AIR_BR0_OVERRIDE material=mat4 domain=[1,4] mfc2.ConstitutiveRelationBH=RemanentFluxDensity mfc2.mur=1 normBr=0[T] direction=[1,0,0]");
  }

  static void captureOnConfiguration(Model m) {
    PhysicsFeature mfc2 = m.component("comp1").physics("mfnc").feature("mfc2");
    onConstitutive = physicsString(mfc2, "ConstitutiveRelationBH");
    onMurMat = physicsString(mfc2, "mur_mat");
    onMur = physicsString(mfc2, "mur");
    onBrMat = physicsString(mfc2, "normBr_crel_BH_RemanentFluxDensity_mat");
    onBr = physicsString(mfc2, "normBr_crel_BH_RemanentFluxDensity");
    log("ON_CONFIGURATION_CAPTURED ConstitutiveRelationBH=" + onConstitutive + " mur_mat=" + onMurMat
      + " mur=" + onMur + " normBr_source=" + onBrMat + " normBr=" + onBr);
  }

  static void restoreOnConfiguration(Model m) {
    ModelNode c = m.component("comp1");
    c.material("mat3").selection().set(new int[]{3,4});
    c.material("mat4").selection().set(new int[]{1});
    PhysicsFeature mfc2 = c.physics("mfnc").feature("mfc2");
    if (onConstitutive != null && !onConstitutive.startsWith("<")) mfc2.set("ConstitutiveRelationBH", onConstitutive);
    if (onMurMat != null && !onMurMat.startsWith("<")) mfc2.set("mur_mat", onMurMat);
    if (onMur != null && !onMur.startsWith("<")) mfc2.set("mur", onMur);
    if (onBrMat != null && !onBrMat.startsWith("<")) mfc2.set("normBr_crel_BH_RemanentFluxDensity_mat", onBrMat);
    if (onBr != null && !onBr.startsWith("<")) mfc2.set("normBr_crel_BH_RemanentFluxDensity", onBr);
    log("ON_CONFIGURATION_RESTORED");
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

  static void auditAirFieldAtBallCenter(Model m, String solTag, String mode) {
    if (!mode.toUpperCase(Locale.ROOT).startsWith("AIR")) return;
    String ds = "ds_airfield_" + System.nanoTime();
    String num = "interp_airfield_" + System.nanoTime();
    try {
      m.result().dataset().create(ds, "Solution");
      m.result().dataset(ds).set("solution", solTag);
      m.result().numerical().create(num, "Interp");
      NumericalFeature n = m.result().numerical(num);
      n.set("data", ds);
      // 本模型的数值特征坐标按几何长度单位 mm 传入，矩阵布局为“坐标维度 × 点数”。
      double xmm = eval(m, "x_sphere")*1000.0, zmm = eval(m, "z_sphere")*1000.0;
      n.set("coord", new double[][]{{xmm},{0.0},{zmm}});
      n.set("expr", new String[]{"mfnc.Bx", "mfnc.By", "mfnc.Bz", "mu0_const*mfnc.Hx", "mu0_const*mfnc.Hy", "mu0_const*mfnc.Hz", "mfnc.normB", "mu0_const*mfnc.normH"});
      n.set("unit", new String[]{"T", "T", "T", "T", "T", "T", "T", "T"});
      double[][][] d = n.getData();
      double b = d[6][0][0], muh = d[7][0][0];
      log(String.format(LOCALE, "AIR_FIELD_AUDIT point_m=[%.12g,0,%.12g] Bvec_T=[%.12g,%.12g,%.12g] mu0Hvec_T=[%.12g,%.12g,%.12g] normB_T=%.12g mu0H_T=%.12g diff_T=%.12g",
        xmm, zmm, d[0][0][0],d[1][0][0],d[2][0][0],d[3][0][0],d[4][0][0],d[5][0][0],b,muh,b-muh));
    } catch (Throwable e) {
      throw new IllegalStateException("CONTROL_CONFIG_ERROR AIR field constitutive postcheck failed", e);
    } finally {
      try { m.result().numerical().remove(num); } catch (Throwable ignored) {}
      try { m.result().dataset().remove(ds); } catch (Throwable ignored) {}
    }
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

  static String staticStudyTag(String mode, double z, double phi) {
    return String.format(LOCALE, "st_%s_z%g_p%g", mode.toLowerCase(Locale.ROOT), z, phi).replace('.', 'p').replace('-', 'm');
  }

  static void solvePose(Model m, Path out, double z, double phi, int elements, String mode, String linearSolver, String stol, PrintWriter csv) throws Exception {
    solvePose(m, out, z, phi, elements, mode, linearSolver, stol, csv, true);
  }

  static void solvePose(Model m, Path out, double z, double phi, int elements, String mode, String linearSolver, String stol, PrintWriter csv, boolean savePoseCheckpoint) throws Exception {
    setStaticPose(m, phi);
    validateModeConfiguration(m, mode);
    String studyTag = staticStudyTag(mode, z, phi);
    m.study().create(studyTag);
    m.study(studyTag).create("stat", "Stationary");
    m.study(studyTag).createAutoSequences("all");
    String[] sols = m.sol().tags();
    String solTag = sols[sols.length - 1];
    configureStationarySolver(m, solTag, studyTag, linearSolver, stol);
    Path poseDir = out.resolve(String.format(LOCALE, "%s_z%g_phi%g", mode.toLowerCase(Locale.ROOT), z, phi).replace('.', 'p'));
    if (savePoseCheckpoint) {
      Files.createDirectories(poseDir);
      m.save(poseDir.resolve("configured.mph").toString());
    }
    log("CONFIGURED_SAVED z=" + z + " phi=" + phi + " study=" + studyTag + " sol=" + solTag);
    m.sol(solTag).runAll();
    log("SOLVED z=" + z + " phi=" + phi + " sol=" + solTag);
    auditAirFieldAtBallCenter(m, solTag, mode);
    double fxN = readForce(m, solTag, "ds_" + studyTag, "numN_" + studyTag, "N");
    double fxmN = readForce(m, solTag, "dsm_" + studyTag, "numm_" + studyTag, "mN");
    double converted = fxN * 1000.0;
    if (Math.abs(converted - fxmN) > Math.max(1e-9, Math.abs(fxmN)*1e-6))
      throw new IllegalStateException("N/mN unit mismatch: N=" + fxN + " mN=" + fxmN);
    double a=eval(m,"alpha"), p=Math.toRadians(phi);
    String row = String.format(LOCALE, "%s,%.8f,%.8f,%.12g,%.12g,%.12g,%s,%s,stol=%s,%d,NA,%.12g,%.12g,%.12g,SUCCESS,UNKNOWN,UNKNOWN",
      mode, z, phi, Math.sin(a), Math.cos(a)*Math.cos(p), -Math.cos(a)*Math.sin(p), solTag, linearSolver, stol, elements, fxN, fxmN, converted);
    csv.println(row); csv.flush(); log("RESULT " + row);
    if (savePoseCheckpoint) m.save(poseDir.resolve("solved.mph").toString());
    m.save(out.resolve("static_force_validation_latest.mph").toString());
  }

  // 每个高度只重建一次几何和网格；同一高度的全部 phi 共享这份网格。
  static int rebuildGeometryAndMesh(Model m, double z, String meshMode) {
    m.param().set("z_sphere", String.format(LOCALE, "%.12g[mm]", z));
    m.component("comp1").geom("geom1").run();
    MeshSequence mesh = m.component("comp1").mesh("mesh1");
    mesh.feature("size").set("hauto", "M1".equals(meshMode) ? 5 : ("M2".equals(meshMode) ? 4 : 6));
    String local = "nearfieldfine";
    try { mesh.feature(local); } catch (Throwable e) { mesh.feature().create(local, "Size"); }
    mesh.feature(local).selection().geom("geom1", 3);
    mesh.feature(local).selection().set(new int[]{2,3,4});
    mesh.feature(local).set("hauto", 1);
    try { mesh.feature().move(local, 1); } catch (Throwable ignored) {}
    mesh.run();
    int elements = mesh.getNumElem();
    log("GEOM_MESH_DONE z=" + z + " elements=" + elements);
    ensureMagneticScalarReference(m);
    printSelections(m);
    printPhysicsSelections(m);
    printMagneticBoundaryAudit(m);
    printMaterials(m);
    return elements;
  }

  // 探索模式：每个高度做 0:45:360 的 ON 稀疏周期采样，仅在 phi=0 做一次有效 AIR 对照。
  static void runSparseHeight(Model m, Path out, double z, int elements, String linearSolver, String stol, PrintWriter csv) throws Exception {
    log(String.format(LOCALE, "SPARSE_HEIGHT_START z=%.8g angles=0,45,90,135,180,225,270,315,360", z));
    restoreOnConfiguration(m);
    for (double phi : SPARSE_ANGLES_DEG) {
      restoreOnConfiguration(m);
      solvePose(m, out, z, phi, elements, "ON", linearSolver, stol, csv, false);
    }
    // AIR 只做一次，且使用与该高度 ON 角度完全相同的几何和网格。
    applyAirBallOverride(m);
    solvePose(m, out, z, 0.0, elements, "AIR", linearSolver, stol, csv, false);
    restoreOnConfiguration(m);
    m.save(out.resolve(String.format(LOCALE, "height_z%g_final.mph", z).replace('.', 'p')).toString());
    log(String.format(LOCALE, "SPARSE_HEIGHT_FINISH z=%.8g elements=%d", z, elements));
  }

  public static void main(String[] args) {
    if (args.length < 6) throw new IllegalArgumentException("Usage: source.mph outdir mode(ON|AIR|AIR_BR0|PAIR|SPARSE) linearSolver stol [meshMode(M0|M1|M2)] z:phi [z:phi ...]");
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
      if (!"ON".equalsIgnoreCase(mode) && !"AIR".equalsIgnoreCase(mode) && !"AIR_BR0".equalsIgnoreCase(mode) && !"PAIR".equalsIgnoreCase(mode) && !"SPARSE".equalsIgnoreCase(mode)) throw new IllegalArgumentException("mode must be ON, AIR, AIR_BR0, PAIR, or SPARSE");
      captureOnConfiguration(m);
      if ("AIR".equalsIgnoreCase(mode)) applyAirBallOverride(m);
      else if ("AIR_BR0".equalsIgnoreCase(mode)) applyAirBallBrZeroOverride(m);
      m.param().set("phi", "0[deg]");
      // 为兼容旧命令，meshMode 可省略；M0/M1/M2 对应全局空气 hauto=6/5/4，固体局部始终 hauto=1。
      String meshMode = "M0"; int poseStart = 5;
      if (args.length > 5 && ("M0".equalsIgnoreCase(args[5]) || "M1".equalsIgnoreCase(args[5]) || "M2".equalsIgnoreCase(args[5]))) { meshMode = args[5].toUpperCase(Locale.ROOT); poseStart = 6; }
      log("MESH_MODE=" + meshMode + " global_hauto=" + ("M1".equals(meshMode) ? "5" : ("M2".equals(meshMode) ? "4" : "6")) + " local_solid_hauto=1");
      PrintWriter csv = new PrintWriter(Files.newBufferedWriter(out.resolve("static_force_results.csv")));
      csv.println("mode,z_mm,phi_deg,ux,uy,uz,solution,linear_solver,tolerance,mesh_elements,dofs,Fx_N,Fx_mN,Fx_mN_from_N,status,reproducibility,mesh_validation");
      double activeZ = Double.NaN; int elements = 0;
      if ("SPARSE".equalsIgnoreCase(mode)) {
        if (args.length <= poseStart) throw new IllegalArgumentException("SPARSE requires one or more z values in mm");
        for (int i = poseStart; i < args.length; i++) {
          double z = Double.parseDouble(args[i]);
          elements = rebuildGeometryAndMesh(m, z, meshMode);
          runSparseHeight(m, out, z, elements, linearSolver, stol, csv);
        }
      } else for (int i = poseStart; i < args.length; i++) {
        String[] pair = args[i].split(":");
        if (pair.length != 2) throw new IllegalArgumentException("Bad pose: " + args[i]);
        double z = Double.parseDouble(pair[0]); double phi = Double.parseDouble(pair[1]);
        if (Double.isNaN(activeZ) || Math.abs(z-activeZ) > 1e-12) {
          elements = rebuildGeometryAndMesh(m, z, meshMode); activeZ = z;
        }
        if ("PAIR".equalsIgnoreCase(mode)) {
          restoreOnConfiguration(m);
          solvePose(m, out, z, phi, elements, "ON", linearSolver, stol, csv);
          applyAirBallOverride(m);
          solvePose(m, out, z, phi, elements, "AIR", linearSolver, stol, csv);
          restoreOnConfiguration(m);
        } else {
          solvePose(m, out, z, phi, elements, mode.toUpperCase(Locale.ROOT), linearSolver, stol, csv);
        }
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
