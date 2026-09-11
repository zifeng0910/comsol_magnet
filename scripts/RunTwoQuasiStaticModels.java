import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 两个独立准静态磁力模型：
 * A = 钢片 + 圆柱 + Air（远磁球保留几何但完全空气化，仅作为兼容旧几何的后备方案）；
 * B = 钢片 + 圆柱 + 50 mm 磁球，固定 gap=0.30 mm 的稀疏姿态研究。
 *
 * 本程序不使用 Contact、不使用动力学、不使用插值替代实算。
 * 运行入口：A 或 B。建议先运行 A，审核 CSV 后再运行 B。
 */
public class RunTwoQuasiStaticModels {
  static final Locale L = Locale.US;
  static final String FX = "mfnc.Forcex_force_magnet";
  static final String FY = "mfnc.Forcey_force_magnet";
  static final String FZ = "mfnc.Forcez_force_magnet";
  static final String TX = "mfnc.Tx_force_magnet";
  static final String NTX = "mfnc.nToutx_force_magnet";
  static final int[] CYL_BND = new int[]{15,16,17,18,19,24};
  static final double[] GAP_MM = new double[]{0.25,0.26,0.27,0.28,0.29,0.30};
  static final double[] SPARSE_PHI = new double[]{0,45,90,135,180,225,270,315,360};
  static double MESH_HMAX_MM = 0.03;
  static String onConstitutive, onMurMat, onMur, onBrMat, onBr;

  static void log(String s) { System.out.println(s); }
  static String f(double x) { return String.format(L, "%.12g", x); }
  static String tag(double x) { return String.format(L, "%.3f", x).replace('.', 'p'); }

  static String prop(PropFeature p, String name) {
    try { return p.getString(name); } catch (Throwable t) { return "<UNREADABLE>"; }
  }

  static String physicsProp(PhysicsFeature p, String name) {
    try { return p.getString(name); } catch (Throwable t) { return "<UNREADABLE>"; }
  }

  static double first(double[][][] d) {
    if (d == null || d.length == 0 || d[0].length == 0 || d[0][0].length == 0)
      throw new IllegalStateException("empty numerical result");
    return d[0][0][0];
  }

  static void clearResults(Model m) {
    for (String t : m.result().numerical().tags()) try { m.result().numerical().remove(t); } catch (Throwable ignored) {}
    for (String t : m.result().dataset().tags()) try { m.result().dataset().remove(t); } catch (Throwable ignored) {}
  }

  static void clearStudiesSolutions(Model m) {
    clearResults(m);
    for (String t : m.sol().tags()) try { m.sol().remove(t); } catch (Throwable ignored) {}
    for (String t : m.study().tags()) try { m.study().remove(t); } catch (Throwable ignored) {}
  }

  static void captureOn(Model m) {
    ModelNode c=m.component("comp1");
    PhysicsFeature f=c.physics("mfnc").feature("mfc2");
    onConstitutive=physicsProp(f,"ConstitutiveRelationBH");
    onMurMat=physicsProp(f,"mur_mat"); onMur=physicsProp(f,"mur");
    onBrMat=physicsProp(f,"normBr_crel_BH_RemanentFluxDensity_mat");
    onBr=physicsProp(f,"normBr_crel_BH_RemanentFluxDensity");
    log("ON_CONFIG constitutive="+onConstitutive+" mur_mat="+onMurMat+" mur="+onMur+" Br_mat="+onBrMat+" Br="+onBr);
  }

  static void restoreOn(Model m) {
    ModelNode c=m.component("comp1");
    c.material("mat3").selection().set(new int[]{3,4});
    c.material("mat4").selection().set(new int[]{1});
    PhysicsFeature f=c.physics("mfnc").feature("mfc2");
    if (!onConstitutive.startsWith("<")) f.set("ConstitutiveRelationBH",onConstitutive);
    if (!onMurMat.startsWith("<")) f.set("mur_mat",onMurMat);
    if (!onMur.startsWith("<")) f.set("mur",onMur);
    if (!onBrMat.startsWith("<")) f.set("normBr_crel_BH_RemanentFluxDensity_mat",onBrMat);
    if (!onBr.startsWith("<")) f.set("normBr_crel_BH_RemanentFluxDensity",onBr);
    log("ON_CONFIG_RESTORED");
  }

  static void applyAirBall(Model m) {
    ModelNode c=m.component("comp1");
    // Model A 优先采用真正删除远磁球：sph1 不再进入 mov1 的几何链，
    // 这样最终几何中不存在球域，也不存在只作用于球域的 mfc2 方程。
    GeomFeature mov1=c.geom("geom1").feature("mov1");
    String[] input=mov1.getStringArray("input");
    ArrayList<String> keep=new ArrayList<String>();
    for(String s:input) if(!"sph1".equals(s)) keep.add(s);
    if(keep.size()==input.length) throw new IllegalStateException("Model A could not find sph1 in mov1 input="+Arrays.toString(input));
    mov1.selection("input").set(keep.toArray(new String[0]));
    try { c.geom("geom1").feature().remove("sph1"); }
    catch (Throwable e) { throw new IllegalStateException("Model A failed to remove sph1 geometry feature",e); }
    c.physics("mfnc").feature().remove("mfc2");
    log("MODEL_A_BALL=GEOMETRY_REMOVED sph1_feature_removed_from_geom1 mfc2_removed");
  }

  static boolean hasPhysicsFeature(Model m,String tag){
    try { m.component("comp1").physics("mfnc").feature(tag); return true; }
    catch(Throwable t){ return false; }
  }

  static void normalizeModelADomains(Model m){
    ModelNode c=m.component("comp1");
    // sph1 已删除后最终几何应为：1=Air、2=steel、3=cylinder。
    c.material("mat4").selection().set(new int[]{1});
    c.material("mat3").selection().set(new int[]{3});
    c.material("mat5").selection().set(new int[]{2});
    Physics p=c.physics("mfnc");
    try { p.feature("mfc1").selection().set(new int[]{1}); } catch(Throwable ignored) {}
    try { p.feature("mfcs1").selection().set(new int[]{3}); } catch(Throwable ignored) {}
    try { p.feature("mfcs2").selection().set(new int[]{2}); } catch(Throwable ignored) {}
    try { p.feature("fcal1").selection().set(new int[]{3}); } catch(Throwable ignored) {}
    try { p.feature("init1").selection().set(new int[]{1,2,3}); } catch(Throwable ignored) {}
    log("MODEL_A_DOMAIN_NORMALIZED air=[1] steel=[2] cylinder=[3] mfc2_present="+hasPhysicsFeature(m,"mfc2"));
  }

  static void ensureScalarReference(Model m){
    ModelNode c=m.component("comp1"); PhysicsFeature zsp;
    try { zsp=c.physics("mfnc").feature("zsp1"); }
    catch(Throwable e){ throw new IllegalStateException("Missing zsp1 magnetic scalar reference feature",e); }
    int[] point=zsp.selection().entities(0); int[] bnd=zsp.selection().entities(2);
    if(point.length==0 && bnd.length==0){
      int[] ne=c.geom("geom1").getNEntities();
      if(ne.length==0 || ne[0]<20) throw new IllegalStateException("No stable point 20 for scalar reference");
      double[][] v=c.geom("geom1").getVertexCoord(); double[] xyz=new double[v.length];
      for(int i=0;i<v.length;i++) xyz[i]=v[i][19];
      zsp.selection().geom("geom1",0); zsp.selection().set(20);
      int[] after=zsp.selection().entities(0);
      if(!Arrays.equals(after,new int[]{20})) throw new IllegalStateException("Failed to set zsp1 point 20");
      log("ZSP_REFERENCE_SET point=20 coord_model_unit="+Arrays.toString(xyz));
    } else log("ZSP_REFERENCE_EXISTING point="+Arrays.toString(point)+" boundary="+Arrays.toString(bnd));
  }

  /** 按最终几何包围盒识别圆柱域 3 的 -x 端面、+x 端面、侧壁和全部外边界。 */
  static int[][] findCylinderBoundaryGroups(Model m){
    ModelNode c=m.component("comp1"); GeomSequence g=c.geom("geom1");
    int nb=g.getNEntities()[2]; ArrayList<Integer> minus=new ArrayList<Integer>(), plus=new ArrayList<Integer>(), side=new ArrayList<Integer>(), all=new ArrayList<Integer>();
    for(int b=1;b<=nb;b++){
      boolean adj=false; try{for(int d:g.getAdjExt(2,3,b)) if(d==3) adj=true;}catch(Throwable ignored){}
      if(!adj) continue;
      try{
        GeomMeasureFinal gm=c.measure(); gm.selection().geom("geom1",2); gm.selection().set(new int[]{b});
        double[] q=gm.getBoundingBox(); all.add(b);
        if(Math.abs(q[1]-q[0])<1e-7){ if((q[0]+q[1])/2.0<0) minus.add(b); else plus.add(b); }
        else side.add(b);
      }catch(Throwable e){ log("CYL_BOUNDARY_MEASURE_FAILED id="+b+" "+e.getMessage()); }
    }
    int[][] out=new int[][]{toInt(minus),toInt(plus),toInt(side),toInt(all)};
    log("CYL_BOUNDARY_GROUPS minus="+Arrays.toString(out[0])+" plus="+Arrays.toString(out[1])+" side="+Arrays.toString(out[2])+" all="+Arrays.toString(out[3]));
    if(out[0].length==0||out[1].length==0||out[2].length==0) throw new IllegalStateException("Cylinder boundary classification incomplete");
    return out;
  }
  static int[] toInt(ArrayList<Integer> x){int[] a=new int[x.size()];for(int i=0;i<a.length;i++)a[i]=x.get(i);return a;}

  static double[][] bbox(ModelNode c, int dim, int entity) {
    GeomMeasureFinal gm=c.measure();
    gm.selection().geom("geom1",dim); gm.selection().set(new int[]{entity});
    return new double[][]{{gm.getVolume(),gm.getArea()},gm.getBoundingBox()};
  }

  static String geometryRow(Model m) {
    ModelNode c=m.component("comp1");
    GeomMeasureFinal g1=c.measure(); g1.selection().geom("geom1",3); g1.selection().set(new int[]{3});
    double[] cb=g1.getBoundingBox();
    GeomMeasureFinal g2=c.measure(); g2.selection().geom("geom1",3); g2.selection().set(new int[]{2});
    double[] sb=g2.getBoundingBox();
    double gap=cb[0]-sb[1];
    return f(cb[0])+","+f(cb[1])+","+f(sb[1])+","+f(gap);
  }

  static void setGap(Model m,double gap) { m.param().set("x_gap",f(gap)+"[mm]"); }

  static int buildMesh(Model m, double gap, Path logFile) throws Exception {
    setGap(m,gap);
    m.component("comp1").geom("geom1").run();
    if(!hasPhysicsFeature(m,"mfc2")) normalizeModelADomains(m);
    ensureScalarReference(m);
    MeshSequence mesh=m.component("comp1").mesh("mesh1");
    // 远场保持原有 hauto=6；近场固定绝对尺寸，按最小 gap=0.25 mm 设计。
    mesh.feature("size").set("hauto",6);
    String local="qa_gap_fine";
    try { mesh.feature().remove(local); } catch (Throwable ignored) {}
    mesh.feature().create(local,"Size");
    mesh.feature(local).selection().geom("geom1",2);
    mesh.feature(local).selection().set(CYL_BND);
    mesh.feature(local).set("custom","on");
    mesh.feature(local).set("hmax",f(MESH_HMAX_MM)+"[mm]");
    mesh.feature(local).set("hmin",f(MESH_HMAX_MM/3.0)+"[mm]");
    mesh.feature(local).set("hgrad","1.3");
    mesh.feature(local).set("hnarrow","1");
    try { mesh.feature().move(local,1); } catch (Throwable ignored) {}
    mesh.run();
    double minq=mesh.getMinQuality();
    int ne=mesh.getNumElem();
    log("MESH_DONE gap="+f(gap)+" elements="+ne+" min_quality="+f(minq)+" hmax="+f(MESH_HMAX_MM)+"mm target_layers="+Math.round(gap/MESH_HMAX_MM));
    return ne;
  }

  static String solverValue(PropFeature p,String q){try{return p.getString(q);}catch(Throwable t){return "<NA>";}}

  static String createStationary(Model m,String suffix) {
    String st="qa_stat_"+suffix+"_"+System.nanoTime();
    m.study().create(st); m.study(st).create("stat","Stationary"); m.study(st).createAutoSequences("all");
    String[] ss=m.sol().tags();
    if(ss.length==0) throw new IllegalStateException("No solver sequence created");
    String sol=ss[ss.length-1];
    SolverFeature s1=m.sol(sol).feature("s1");
    SolverFeature direct=s1.feature("dDef"); SolverFeature fc=s1.feature("fc1");
    if(!"Direct".equals(direct.getType())||!"FullyCoupled".equals(fc.getType())) throw new IllegalStateException("Unexpected solver tree");
    if(!direct.hasProperty("linsolver")||!fc.hasProperty("linsolver")||!s1.hasProperty("control")) throw new IllegalStateException("Required solver property missing");
    direct.set("linsolver","pardiso"); fc.set("linsolver","dDef");
    StudyFeature stat=m.study(st).feature("stat");
    if(!stat.hasProperty("usestol")||!stat.hasProperty("stol")) throw new IllegalStateException("Stationary stol property missing");
    stat.set("usestol","on"); stat.set("stol","1e-6"); s1.set("control","stat");
    if(!"pardiso".equals(direct.getString("linsolver"))||!"dDef".equals(fc.getString("linsolver"))) throw new IllegalStateException("PARDISO not active");
    if(!"1e-6".equals(stat.getString("stol"))) throw new IllegalStateException("Stationary stol not active");
    log("SOLVER_CONFIG study="+st+" sol="+sol+" direct="+solverValue(direct,"linsolver")+" fullycoupled="+solverValue(fc,"linsolver")+" stol="+solverValue(stat,"stol"));
    return sol;
  }

  static double readGlobal(Model m,String sol,String expr,String unit) throws Exception {
    String ds="ds_"+System.nanoTime(), n="ev_"+System.nanoTime();
    try {
      m.result().dataset().create(ds,"Solution"); m.result().dataset(ds).set("solution",sol);
      m.result().numerical().create(n,"Global"); NumericalFeature q=m.result().numerical(n);
      q.set("data",ds); q.set("expr",new String[]{expr}); q.set("unit",new String[]{unit});
      return first(q.getData());
    } finally { try{m.result().numerical().remove(n);}catch(Throwable ignored){} try{m.result().dataset().remove(ds);}catch(Throwable ignored){} }
  }

  static double readSurface(Model m,String sol,String expr,int[] bnd) throws Exception {
    String ds="ds_s_"+System.nanoTime(), n="is_"+System.nanoTime();
    try {
      m.result().dataset().create(ds,"Solution"); m.result().dataset(ds).set("solution",sol);
      m.result().numerical().create(n,"IntSurface"); NumericalFeature q=m.result().numerical(n);
      q.set("data",ds); q.selection().geom("geom1",2); q.selection().set(bnd);
      q.set("expr",new String[]{expr}); q.set("unit",new String[]{"N"});
      // COMSOL 6.3 的 IntSurface 数值特征不支持 getData()；其原生读取接口是 getReal().
      double[][] value=q.getReal();
      if(value==null || value.length==0 || value[0].length==0) throw new IllegalStateException("empty IntSurface result");
      // COMSOL 输出为 N；统一转换为 CSV 使用的 mN。
      return 1000.0*value[0][0];
    } finally { try{m.result().numerical().remove(n);}catch(Throwable ignored){} try{m.result().dataset().remove(ds);}catch(Throwable ignored){} }
  }

  static int dofs(Model m,String sol){try{int[] s=m.sol(sol).getSize();return s.length>0?s[0]:-1;}catch(Throwable t){return -1;}}

  static void writeAHeader(PrintWriter w){
    w.println("gap_mm,measured_gap_mm,cylinder_xmin_mm,steel_near_face_x_mm,Fx_hold_mN,Fy_hold_mN,Fz_hold_mN,F_lateral_hold_mN,Fx_face15_mN,Fx_face24_mN,Fx_side_mN,Fx_surface_total_mN,cancellation_ratio,h_gap_mm,gap_element_layers_target,mesh_elements,DOF,min_mesh_quality,solver_status");
  }

  static void runModelA(Model m,Path out,double[] gaps) throws Exception {
    applyAirBall(m);
    Path csv=out.resolve("holding_force_vs_gap_025_030_step001.csv");
    try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){
      writeAHeader(w);
      for(double gap:gaps){
        clearStudiesSolutions(m);
        try{
          int ne=buildMesh(m,gap,out.resolve("modelA_progress.log"));
          String geo=geometryRow(m); String[] g=geo.split(",");
          String sol=createStationary(m,"A_gap"+tag(gap));
          m.sol(sol).runAll();
          double fx=readGlobal(m,sol,FX,"mN"), fy=readGlobal(m,sol,FY,"mN"), fz=readGlobal(m,sol,FZ,"mN");
          int[][] bg=findCylinderBoundaryGroups(m);
          double f15=readSurface(m,sol,NTX,bg[0]);
          double f24=readSurface(m,sol,NTX,bg[1]);
          double fs=readSurface(m,sol,NTX,bg[2]);
          double fall=readSurface(m,sol,NTX,bg[3]);
          double ratio=(Math.abs(f15)+Math.abs(f24)+Math.abs(fs))/Math.abs(fall);
          int dof=dofs(m,sol); double minq=m.component("comp1").mesh("mesh1").getMinQuality();
          String row=String.join(",",f(gap),g[3],g[0],g[2],f(fx),f(fy),f(fz),f(Math.sqrt(fy*fy+fz*fz)),f(f15),f(f24),f(fs),f(fall),f(ratio),f(MESH_HMAX_MM),Long.toString(Math.round(gap/MESH_HMAX_MM)),Integer.toString(ne),Integer.toString(dof),f(minq),"SUCCESS");
          w.println(row); w.flush(); log("MODEL_A_RESULT "+row);
          m.save(out.resolve("modelA_gap"+tag(gap)+"_solved.mph").toString());
        }catch(Throwable e){
          String row=String.join(",",f(gap),"","","","","","","","","","","","",f(MESH_HMAX_MM),Long.toString(Math.round(gap/MESH_HMAX_MM)),"","","","ERROR:"+e.getClass().getSimpleName());
          w.println(row); w.flush(); log("MODEL_A_ERROR gap="+f(gap)+" message="+e.getMessage()); e.printStackTrace();
        }
      }
    }
    clearStudiesSolutions(m); m.save(out.resolve("modelA_final.mph").toString());
    log("MODEL_A_FINISH csv="+csv);
  }

  static void setPose(Model m,double phi){
    m.param().set("phi",f(phi)+"[deg]");
    m.component("comp1").physics("mfnc").feature("mfc2").set("e_crel_BH_RemanentFluxDensity",new String[]{"sin(alpha)","cos(alpha)*cos(phi)","-cos(alpha)*sin(phi)"});
  }

  static void writeBHeader(PrintWriter w){
    w.println("gap_mm,z_sphere_mm,phi_deg,Fx_total_mN,Fy_total_mN,Fz_total_mN,F_lateral_mN,Fx_hold_030_mN,DeltaFx_drive_mN,release_margin_x_mN,release_candidate,Fx_face15_mN,Fx_face24_mN,Fx_side_mN,Fx_surface_total_mN,cancellation_ratio,air_radius_mm,mesh_elements,DOF,solver_status,closure_error_mN,numerical_warning");
  }

  static void runModelB(Model m,Path out,double hold030) throws Exception {
    restoreOn(m); m.param().set("x_gap","0.30[mm]");
    double[] zs=new double[]{120,140,150};
    try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out.resolve("driven_force_vs_z_phi_gap030.csv")))){
      writeBHeader(w);
      for(double z:zs){
        clearStudiesSolutions(m); m.param().set("z_sphere",f(z)+"[mm]");
        m.component("comp1").geom("geom1").run();
        int ne=buildMesh(m,0.30,out.resolve("modelB_progress.log"));
        int[][] bg=findCylinderBoundaryGroups(m);
        for(double phi:SPARSE_PHI){
          try{
            setPose(m,phi); String sol=createStationary(m,"B_z"+tag(z)+"_p"+tag(phi)); m.sol(sol).runAll();
            double fx=readGlobal(m,sol,FX,"mN"), fy=readGlobal(m,sol,FY,"mN"), fz=readGlobal(m,sol,FZ,"mN");
            double f15=readSurface(m,sol,NTX,bg[0]), f24=readSurface(m,sol,NTX,bg[1]), fs=readSurface(m,sol,NTX,bg[2]), fall=readSurface(m,sol,NTX,bg[3]);
            double ratio=(Math.abs(f15)+Math.abs(f24)+Math.abs(fs))/Math.abs(fall), delta=fx-hold030;
            int dof=dofs(m,sol); String candidate=fx>0?"MAGNETIC_RELEASE_CANDIDATE":"HELD_BY_MAGNETIC_FORCE";
            String row=String.join(",","0.30",f(z),f(phi),f(fx),f(fy),f(fz),f(Math.sqrt(fy*fy+fz*fz)),f(hold030),f(delta),f(fx),candidate,f(f15),f(f24),f(fs),f(fall),f(ratio),"400",Integer.toString(ne),Integer.toString(dof),"SUCCESS","0"," ");
            w.println(row); w.flush(); log("MODEL_B_RESULT "+row);
          }catch(Throwable e){
            w.println(String.join(",","0.30",f(z),f(phi),"","","","",f(hold030),"","","UNKNOWN","","","","","","400",Integer.toString(ne),"","ERROR","","ERROR")); w.flush(); log("MODEL_B_ERROR z="+f(z)+" phi="+f(phi)+" "+e.getMessage());
          }
          clearStudiesSolutions(m);
        }
        m.save(out.resolve("modelB_z"+tag(z)+"_final.mph").toString());
      }
    }
    m.save(out.resolve("modelB_final.mph").toString()); log("MODEL_B_FINISH");
  }

  public static void main(String[] args){
    if(args.length<3)throw new IllegalArgumentException("Usage: source.mph outdir A|B [hold030_mN]");
    String source=args[0]; Path out=Paths.get(args[1]); String mode=args[2].toUpperCase(L);
    try{
      Files.createDirectories(out); ModelUtil.initStandalone(false); ModelUtil.showProgress(out.resolve("progress.log").toString());
      String meshH=System.getProperty("qa.meshHmaxMm");
      if(meshH!=null && !meshH.isEmpty()) MESH_HMAX_MM=Double.parseDouble(meshH);
      if(!(MESH_HMAX_MM>0.0)) throw new IllegalArgumentException("qa.meshHmaxMm must be positive");
      log("MESH_POLICY hmax_mm="+f(MESH_HMAX_MM)+" hmin_mm="+f(MESH_HMAX_MM/3.0));
      Model m=ModelUtil.load("two_quasi_static_"+System.nanoTime(),source); ModelNode c=m.component("comp1");
      captureOn(m); log("SOURCE="+source+" PHYSICS="+Arrays.toString(c.physics().tags()));
      for(String common:c.common().tags()) if("RotatingDomain".equals(c.common(common).getType())){c.common().remove(common);log("REMOVED_ROTATING_DOMAIN="+common);}
      if("A".equals(mode)) {
        double[] gaps;
        if (args.length > 3) {
          gaps=new double[args.length-3];
          for(int i=0;i<gaps.length;i++) gaps[i]=Double.parseDouble(args[i+3]);
        } else gaps=GAP_MM;
        runModelA(m,out,gaps);
      }
      else if("B".equals(mode)){double hold=Double.parseDouble(args[3]);runModelB(m,out,hold);}
      else throw new IllegalArgumentException("mode must be A or B");
      ModelUtil.disconnect(); System.exit(0);
    }catch(Throwable e){e.printStackTrace();try{ModelUtil.disconnect();}catch(Throwable ignored){}System.exit(1);}
  }
}
