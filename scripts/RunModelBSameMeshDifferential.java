import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Model B: sparse fixed-pose force decomposition on one mesh per height.
 *
 * B0 = external ball OFF, steel OFF (self/background reference)
 * B1 = external ball OFF, steel ON  (corrected steel holding force = B1-B0)
 * B2 = external ball ON,  steel ON  (corrected total = B2-B0)
 * DeltaFx_ball = B2-B1
 *
 * The ball is not resized.  The source model's verified domains are:
 * air=1, steel=2, cylinder=3, ball=4.
 * All B0/B1/B2 solves at one z reuse exactly the same geometry and mesh.
 *
 * Usage:
 *   java RunModelBSameMeshDifferential source.mph output_dir [z_mm ...]
 */
public class RunModelBSameMeshDifferential {
  static final Locale L = Locale.US;
  static final String FX = "mfnc.Forcex_force_magnet";
  static final String FY = "mfnc.Forcey_force_magnet";
  static final String FZ = "mfnc.Forcez_force_magnet";
  static final double[] DEFAULT_Z = {120.0, 140.0, 150.0};
  static final double[] PHI = {0,45,90,135,180,225,270,315,360};
  // 可选的高效模式：每个高度只求 B2 的 phi=90°，B0/B1仍各求一次。
  static double[] ACTIVE_PHI = PHI;
  // 最终候选点的高精度复核：恢复源模型原生 hauto=1 远场网格。
  static boolean H002_MODE = false;
  static final int AIR=1, STEEL=2, CYL=3, BALL=4;

  static String f(double x) { return String.format(L, "%.12g", x); }
  static String ztag(double z) { return String.format(L, "%.1f", z).replace('.', 'p'); }
  static void log(String s) { System.out.println(s); System.out.flush(); }
  static double first(double[][][] x) {
    if (x==null || x.length==0 || x[0].length==0 || x[0][0].length==0)
      throw new IllegalStateException("empty Global result");
    return x[0][0][0];
  }
  static String p(PhysicsFeature x, String k) {
    try { return x.getString(k); } catch (Throwable t) { return "<UNREADABLE>"; }
  }
  static int[] domains(Model m) {
    int n=m.component("comp1").geom("geom1").getNEntities()[3];
    int[] a=new int[n]; for(int i=0;i<n;i++)a[i]=i+1; return a;
  }
  static double[] bbox(Model m,int dim,int id) {
    GeomMeasureFinal q=m.component("comp1").geom("geom1").measureFinal();
    q.selection().geom("geom1",dim); q.selection().set(new int[]{id});
    return q.getBoundingBox();
  }

  static void verifyDomains(Model m) {
    double[] cb=bbox(m,3,CYL), sb=bbox(m,3,STEEL), bb=bbox(m,3,BALL);
    double cspan=(cb[1]-cb[0])+(cb[3]-cb[2])+(cb[5]-cb[4]);
    double sspan=(sb[1]-sb[0])+(sb[3]-sb[2])+(sb[5]-sb[4]);
    double bspan=(bb[1]-bb[0])+(bb[3]-bb[2])+(bb[5]-bb[4]);
    if(Math.abs(cspan-3.6)>0.05 || Math.abs(sspan-8.3)>0.05 || Math.abs(bspan-150)>0.2)
      throw new IllegalStateException("DOMAIN_IDENTITY_FAILED cyl="+Arrays.toString(cb)
        +" steel="+Arrays.toString(sb)+" ball="+Arrays.toString(bb));
    log("DOMAIN_IDENTITY air=1 steel=2 cylinder=3 ball=4 cylinder_bbox="+Arrays.toString(cb)
      +" steel_bbox="+Arrays.toString(sb)+" ball_bbox="+Arrays.toString(bb));
  }

  static String oldSteelC,oldSteelMurMat,oldSteelMur,oldSteelBrMat,oldSteelBr;
  static String oldBallC,oldBallMurMat,oldBallMur,oldBallBrMat,oldBallBr;
  static void capturePhysics(Model m) {
    Physics p=m.component("comp1").physics("mfnc");
    PhysicsFeature s=p.feature("mfcs2"), b=p.feature("mfc2");
    oldSteelC=p(s,"ConstitutiveRelationBH"); oldSteelMurMat=p(s,"mur_mat"); oldSteelMur=p(s,"mur");
    oldSteelBrMat=p(s,"normBr_crel_BH_RemanentFluxDensity_mat");
    oldSteelBr=p(s,"normBr_crel_BH_RemanentFluxDensity");
    oldBallC=p(b,"ConstitutiveRelationBH"); oldBallMurMat=p(b,"mur_mat"); oldBallMur=p(b,"mur");
    oldBallBrMat=p(b,"normBr_crel_BH_RemanentFluxDensity_mat");
    oldBallBr=p(b,"normBr_crel_BH_RemanentFluxDensity");
    log("STEEL_ON_CAPTURE constitutive="+oldSteelC+" mur_mat="+oldSteelMurMat+" mur="+oldSteelMur
      +" Br_mat="+oldSteelBrMat+" Br="+oldSteelBr);
    log("BALL_ON_CAPTURE constitutive="+oldBallC+" mur_mat="+oldBallMurMat+" mur="+oldBallMur
      +" Br_mat="+oldBallBrMat+" Br="+oldBallBr);
  }

  static void normalizePhysics(Model m) {
    ModelNode c=m.component("comp1"); Physics p=c.physics("mfnc");
    c.material("mat4").selection().set(new int[]{AIR});
    c.material("mat3").selection().set(new int[]{CYL,BALL});
    c.material("mat5").selection().set(new int[]{STEEL});
    try { p.feature("mfc1").selection().set(new int[]{AIR}); } catch(Throwable ignored) {}
    p.feature("mfcs1").selection().set(new int[]{CYL});
    p.feature("mfcs2").selection().set(new int[]{STEEL});
    p.feature("mfc2").selection().set(new int[]{BALL});
    p.feature("fcal1").selection().set(new int[]{CYL});
    try { p.feature("init1").selection().set(domains(m)); } catch(Throwable ignored) {}
    log("PHYSICS_NORMALIZED mfcs1=[3] mfcs2=[2] mfc2=[4] fcal1=[3] ForceName="
      +p.feature("fcal1").getString("ForceName"));
  }
  static void ensureScalarReference(Model m) {
    PhysicsFeature z=m.component("comp1").physics("mfnc").feature("zsp1");
    int[] pts=z.selection().entities(0);
    int[] bnd=z.selection().entities(2);
    if(pts.length==0 && bnd.length==0) {
      int[] ne=m.component("comp1").geom("geom1").getNEntities();
      if(ne.length==0 || ne[0]<20) throw new IllegalStateException("REFERENCE_POINT_20_MISSING");
      z.selection().geom("geom1",0); z.selection().set(20);
      if(!Arrays.equals(z.selection().entities(0),new int[]{20}))
        throw new IllegalStateException("REFERENCE_POINT_20_NOT_APPLIED");
      log("REFERENCE_SET point=20");
    } else log("REFERENCE_EXISTING points="+Arrays.toString(pts)+" boundaries="+Arrays.toString(bnd));
  }

  static void setSteelOff(Model m) {
    ModelNode c=m.component("comp1"); PhysicsFeature s=c.physics("mfnc").feature("mfcs2");
    c.material("mat4").selection().set(new int[]{AIR,STEEL});
    c.material("mat5").selection().set(new int[0]);
    s.selection().set(new int[]{STEEL});
    s.set("ConstitutiveRelationBH","RelativePermeability"); s.set("mur_mat","userdef"); s.set("mur","1");
    if(s.hasProperty("normBr_crel_BH_RemanentFluxDensity_mat"))s.set("normBr_crel_BH_RemanentFluxDensity_mat","userdef");
    if(s.hasProperty("normBr_crel_BH_RemanentFluxDensity"))s.set("normBr_crel_BH_RemanentFluxDensity","0[T]");
    log("STEEL_OFF active=RelativePermeability mur=1 Br=0 domains=[2]");
  }
  static void setSteelOn(Model m) {
    ModelNode c=m.component("comp1"); PhysicsFeature s=c.physics("mfnc").feature("mfcs2");
    c.material("mat4").selection().set(new int[]{AIR}); c.material("mat5").selection().set(new int[]{STEEL});
    s.selection().set(new int[]{STEEL});
    if(!oldSteelC.startsWith("<"))s.set("ConstitutiveRelationBH",oldSteelC);
    if(!oldSteelMurMat.startsWith("<"))s.set("mur_mat",oldSteelMurMat);
    if(!oldSteelMur.startsWith("<"))s.set("mur",oldSteelMur);
    if(!oldSteelBrMat.startsWith("<"))s.set("normBr_crel_BH_RemanentFluxDensity_mat",oldSteelBrMat);
    if(!oldSteelBr.startsWith("<"))s.set("normBr_crel_BH_RemanentFluxDensity",oldSteelBr);
    log("STEEL_ON restored material=mat5 domains=[2] constitutive="+oldSteelC+" mur_mat="+oldSteelMurMat+" Br_mat="+oldSteelBrMat);
  }
  static void setBallOff(Model m) {
    ModelNode c=m.component("comp1"); PhysicsFeature b=c.physics("mfnc").feature("mfc2");
    c.material("mat3").selection().set(new int[]{CYL}); c.material("mat4").selection().set(new int[]{AIR,BALL});
    b.selection().set(new int[]{BALL}); b.set("ConstitutiveRelationBH","RelativePermeability"); b.set("mur_mat","userdef"); b.set("mur","1");
    if(b.hasProperty("normBr_crel_BH_RemanentFluxDensity_mat"))b.set("normBr_crel_BH_RemanentFluxDensity_mat","userdef");
    if(b.hasProperty("normBr_crel_BH_RemanentFluxDensity"))b.set("normBr_crel_BH_RemanentFluxDensity","0[T]");
    log("BALL_OFF active=RelativePermeability mur=1 Br=0 domains=[4]");
  }
  static void setBallOn(Model m) {
    ModelNode c=m.component("comp1"); PhysicsFeature b=c.physics("mfnc").feature("mfc2");
    c.material("mat3").selection().set(new int[]{CYL,BALL}); c.material("mat4").selection().set(new int[]{AIR});
    b.selection().set(new int[]{BALL});
    if(!oldBallC.startsWith("<"))b.set("ConstitutiveRelationBH",oldBallC);
    if(!oldBallMurMat.startsWith("<"))b.set("mur_mat",oldBallMurMat);
    if(!oldBallMur.startsWith("<"))b.set("mur",oldBallMur);
    if(!oldBallBrMat.startsWith("<"))b.set("normBr_crel_BH_RemanentFluxDensity_mat",oldBallBrMat);
    if(!oldBallBr.startsWith("<"))b.set("normBr_crel_BH_RemanentFluxDensity",oldBallBr);
    log("BALL_ON restored material=mat3 domains=[3,4] constitutive="+oldBallC+" mur_mat="+oldBallMurMat+" Br_mat="+oldBallBrMat);
  }

  static void setPose(Model m,double phi) {
    m.param().set("phi",f(phi)+"[deg]");
    m.component("comp1").physics("mfnc").feature("mfc2").set(
      "e_crel_BH_RemanentFluxDensity",
      new String[]{"sin(alpha)","cos(alpha)*cos(phi)","-cos(alpha)*sin(phi)"});
    log("POSE phi_deg="+f(phi)+" expected_e=[sin(alpha),cos(alpha)*cos(phi),-cos(alpha)*sin(phi)]");
  }

  static void addFineBallMesh(Model m) {
    MeshSequence mesh=m.component("comp1").mesh("mesh1");
    mesh.feature("size").set("hauto",H002_MODE ? 1 : 6);
    try { mesh.feature().remove("nearfieldfine_b"); } catch(Throwable ignored) {}
    // hauto=1 已经覆盖全域最细网格；不再叠加局部 Size，避免部分边界
    // 在细网格重建后未被 Force Calculation 的积分分区登记。
    if(H002_MODE) {
      log("MESH_POLICY far_field_hauto=1 solids_2_3_4=1 duplicate_local_size=off");
      return;
    }
    MeshFeature nf=mesh.feature().create("nearfieldfine_b","Size");
    nf.selection().geom("geom1",3); nf.selection().set(new int[]{STEEL,CYL,BALL}); nf.set("hauto",1);
    try { mesh.feature().move("nearfieldfine_b",1); } catch(Throwable ignored) {}
    log("MESH_POLICY far_field_hauto="+(H002_MODE ? "1" : "6")+" solids_2_3_4=1");
  }
  static String makeStationary(Model m,String suffix) {
    String st="b_stat_"+suffix+"_"+System.nanoTime(); m.study().create(st); m.study(st).create("stat","Stationary"); m.study(st).createAutoSequences("all");
    String[] ss=m.sol().tags(); if(ss.length==0)throw new IllegalStateException("NO_SOLVER_SEQUENCE"); String sol=ss[ss.length-1];
    SolverSequence q=m.sol(sol); SolverFeature s1=q.feature("s1"), d=s1.feature("dDef"), fc=s1.feature("fc1");
    d.set("linsolver","pardiso"); fc.set("linsolver","dDef"); s1.set("control","stat");
    StudyFeature stat=m.study(st).feature("stat");
    if(!stat.hasProperty("usestol")||!stat.hasProperty("stol"))throw new IllegalStateException("STOL_PROPERTY_MISSING");
    stat.set("usestol","on"); stat.set("stol","1e-6");
    if(!"pardiso".equals(d.getString("linsolver"))||!"dDef".equals(fc.getString("linsolver"))||!"1e-6".equals(stat.getString("stol")))throw new IllegalStateException("SOLVER_NOT_ACTIVE");
    log("SOLVER_CONFIG study="+st+" sol="+sol+" direct=pardiso fullyCoupled=dDef stol=1e-6"); return sol;
  }
  static double[] solveRead(Model m,String label)throws Exception {
    // 每个状态/姿态使用独立的 Stationary 求解序列，但不重新生成几何或网格，
    // 避免本构切换后复用旧求解器内部状态导致初值不一致。
    String sol=makeStationary(m,label.replace(' ','_'));
    log("SOLVE_START "+label+" sol="+sol); m.sol(sol).runAll(); log("SOLVE_DONE "+label+" sol="+sol);
    return new double[]{read(m,sol,FX),read(m,sol,FY),read(m,sol,FZ)};
  }
  static double read(Model m,String sol,String expr) {
    String ds="ds_b_"+System.nanoTime(), n="g_b_"+System.nanoTime();
    try { m.result().dataset().create(ds,"Solution"); m.result().dataset(ds).set("solution",sol); m.result().numerical().create(n,"Global"); NumericalFeature q=m.result().numerical(n); q.set("data",ds); q.set("expr",new String[]{expr}); q.set("unit",new String[]{"N"}); return first(q.getData())*1000.0; }
    finally { try{m.result().numerical().remove(n);}catch(Throwable ignored){} try{m.result().dataset().remove(ds);}catch(Throwable ignored){} }
  }
  static int elems(Model m){return m.component("comp1").mesh("mesh1").getNumElem();}
  static double quality(Model m){return m.component("comp1").mesh("mesh1").getMinQuality();}
  static int dofs(Model m,String sol){int[] s=m.sol(sol).getSize();return s.length==0?-1:s[0];}
  static String row(String z,String mode,String phi,double[] raw,double[] b0,double[] b1,int ne,int dof,double q,String status){
    double sx=b0==null?Double.NaN:b0[0], hx=b1==null?Double.NaN:b1[0];
    double self=mode.equals("B0")?raw[0]-raw[0]:sx;
    double hold=(b1==null?Double.NaN:hx-sx);
    double total=(mode.equals("B2")?raw[0]-sx:Double.NaN);
    double ball=(mode.equals("B2")?raw[0]-hx:Double.NaN);
    String[] v={z,mode,phi,f(raw[0]),f(raw[1]),f(raw[2]),f(self),f(hold),f(total),f(ball),Integer.toString(ne),Integer.toString(dof),f(q),status};
    for(int i=0;i<v.length;i++)if(v[i].equals("NaN"))v[i]=""; return String.join(",",v);
  }
  static void header(PrintWriter w){w.println("z_sphere_mm,mode,phi_deg,Fx_raw_mN,Fy_raw_mN,Fz_raw_mN,Fx_self_corr_mN,Fx_hold_corr_mN,Fx_total_corr_mN,DeltaFx_ball_mN,elements,DOF,min_quality,status");}

  static void runHeight(Model m,double z,PrintWriter w,Path out) throws Exception {
    m.param().set("x_gap","0.30[mm]"); m.param().set("z_sphere",f(z)+"[mm]");
    log("HEIGHT_START z="+f(z));
    m.component("comp1").geom("geom1").run();
    verifyDomains(m);
    // 必须先抓取源模型的真实 ON 本构；normalizePhysics 只重设选择，
    // 不得在 capturePhysics 之前覆盖球或钢片的剩磁配置。
    capturePhysics(m);
    normalizePhysics(m);
    ensureScalarReference(m);
    addFineBallMesh(m);
    m.component("comp1").mesh("mesh1").run();
    int ne=elems(m); double q=quality(m); int dof;
    // B0: both external sources off.
    setSteelOff(m); setBallOff(m); setPose(m,0); double[] b0=solveRead(m,"B0 z="+f(z)); dof=dofs(m,m.sol().tags()[m.sol().tags().length-1]); w.println(row(f(z),"B0","0",b0,b0,null,ne,dof,q,"SUCCESS")); w.flush(); log("B0_RESULT z="+f(z)+" Fx="+f(b0[0])+"mN");
    // B1: steel on, ball remains off; same mesh and same solver.
    setSteelOn(m); setBallOff(m); setPose(m,0); double[] b1=solveRead(m,"B1 z="+f(z)); dof=dofs(m,m.sol().tags()[m.sol().tags().length-1]); w.println(row(f(z),"B1","0",b1,b0,b1,ne,dof,q,"SUCCESS")); w.flush(); log("B1_RESULT z="+f(z)+" Fx="+f(b1[0])+"mN hold_corr="+f(b1[0]-b0[0])+"mN");
    // B2: both on; nine independent static poses, same geometry and mesh.
    setSteelOn(m); setBallOn(m);
    for(double phi:ACTIVE_PHI){setPose(m,phi); double[] b2=solveRead(m,"B2 z="+f(z)+" phi="+f(phi)); dof=dofs(m,m.sol().tags()[m.sol().tags().length-1]); w.println(row(f(z),"B2",f(phi),b2,b0,b1,ne,dof,q,"SUCCESS")); w.flush(); log("B2_RESULT z="+f(z)+" phi="+f(phi)+" Fx_raw="+f(b2[0])+" Fx_total_corr="+f(b2[0]-b0[0])+" DeltaFx_ball="+f(b2[0]-b1[0])+"mN");}
    try { Files.createDirectories(out); m.save(out.resolve("modelB_latest_z"+ztag(z)+".mph").toString()); log("CHECKPOINT_SAVED z="+f(z)); } catch(Throwable e){log("CHECKPOINT_SAVE_ERROR z="+f(z)+" "+e.getMessage());}
  }

  public static void main(String[] a) throws Exception {
    if(a.length<2)throw new IllegalArgumentException("Usage: source.mph output_dir [z_mm ...]");
    ArrayList<Double> zs=new ArrayList<Double>();
    int zStart=2;
    if(a.length>=3 && "PHI90_ONLY".equalsIgnoreCase(a[2])) { ACTIVE_PHI=new double[]{90.0}; zStart=3; }
    if(a.length>=3 && "H002_ONLY".equalsIgnoreCase(a[2])) { H002_MODE=true; ACTIVE_PHI=new double[]{90.0}; zStart=3; }
    if(a.length==zStart)for(double z:DEFAULT_Z)zs.add(z);else for(int i=zStart;i<a.length;i++)zs.add(Double.parseDouble(a[i]));
    Path out=Paths.get(a[1]); Files.createDirectories(out); Path csv=out.resolve("modelB_same_mesh_differential_sparse.csv");
    ModelUtil.initStandalone(false);
    try { ModelUtil.showProgress(out.resolve("progress.log").toString()); Model m=ModelUtil.load("modelB_diff_"+System.nanoTime(),a[0]);
      for(String t:m.sol().tags())try{m.sol().remove(t);}catch(Throwable ignored){} for(String t:m.study().tags())try{m.study().remove(t);}catch(Throwable ignored){}
      for(String t:m.component("comp1").common().tags())try{if("RotatingDomain".equals(m.component("comp1").common(t).getType())){m.component("comp1").common().remove(t);log("REMOVED_ROTATING_DOMAIN="+t);}}catch(Throwable ignored){}
      log("START ModelB source="+a[0]+" heights="+zs+" phis="+Arrays.toString(ACTIVE_PHI));
      try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING))){header(w); for(double z:zs){try{runHeight(m,z,w,out);}catch(Throwable e){log("HEIGHT_ERROR z="+f(z)+" "+e.getMessage());e.printStackTrace(System.out);w.println(String.join(",",f(z),"ERROR","","","","","","","","","","","","ERROR"));w.flush();}}}
      try{m.save(out.resolve("modelB_same_mesh_differential_sparse_final.mph").toString());log("FINAL_MODEL_SAVED");}catch(Throwable e){log("FINAL_MODEL_SAVE_ERROR "+e.getMessage());}
      log("FINISH csv="+csv);
    } finally {try{ModelUtil.disconnect();}catch(Throwable ignored){}}
  }
}
