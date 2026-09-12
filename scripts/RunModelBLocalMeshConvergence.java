import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Model B: resource-feasible local mesh convergence at one fixed pose.
 *
 * Main comparison cases: alpha=0 deg and alpha=20 deg, gap=0.30 mm.
 * Each mesh level is loaded from a fresh source copy.  Within one level,
 * B0/B1/B2 reuse the exact same geometry and mesh.
 *
 * CURRENT_SOURCE_MESH means the previously validated ~60k-element policy:
 * far-field hauto=6 plus solid domains 2/3/4 hauto=1, without the new
 * near-field boundary Size feature.  It is intentionally not the native
 * source model hauto=1 mesh, which is about 1.24M elements and is recorded
 * separately as the earlier resource-failure reason.
 */
public class RunModelBLocalMeshConvergence {
  static final Locale L=Locale.US;
  static final String FX="mfnc.Forcex_force_magnet";
  static final String FY="mfnc.Forcey_force_magnet";
  static final String FZ="mfnc.Forcez_force_magnet";
  static final double Z=120.0, PHI=90.0, GAP=0.30, X_SPHERE=26.0;
  static final int AIR=1, STEEL=2, CYL=3, BALL=4;
  static final double[] SPARSE_PHI={0,45,90,135,180,225,270,315,360};

  static void log(String s){System.out.println(s);System.out.flush();}
  static String f(double x){return String.format(L,"%.12g",x);}
  static double first(double[][][] x){
    if(x==null||x.length==0||x[0].length==0||x[0][0].length==0)
      throw new IllegalStateException("empty Global result");
    return x[0][0][0];
  }
  static String get(PropFeature p,String k){try{return p.getString(k);}catch(Throwable t){return "<UNREADABLE>";}}
  static void set(PropFeature p,String k,String v){
    if(!p.hasProperty(k))throw new IllegalStateException("PROPERTY_MISSING "+k);
    p.set(k,v); if(!v.equals(p.getString(k)))throw new IllegalStateException("PROPERTY_NOT_APPLIED "+k);
  }
  static void setVec(PhysicsFeature p,String k,String[] v){
    if(!p.hasProperty(k))throw new IllegalStateException("PHYSICS_PROPERTY_MISSING "+k);
    p.set(k,v); if(p.getStringArray(k).length!=v.length)throw new IllegalStateException("VECTOR_NOT_APPLIED "+k);
  }
  static double[] bbox(Model m,int dim,int id){
    GeomMeasureFinal q=m.component("comp1").geom("geom1").measureFinal();
    q.selection().geom("geom1",dim); q.selection().set(new int[]{id}); return q.getBoundingBox();
  }
  static void verifyDomains(Model m){
    double[] c=bbox(m,3,CYL),s=bbox(m,3,STEEL),b=bbox(m,3,BALL);
    double cs=(c[1]-c[0])+(c[3]-c[2])+(c[5]-c[4]);
    double ss=(s[1]-s[0])+(s[3]-s[2])+(s[5]-s[4]);
    double bs=(b[1]-b[0])+(b[3]-b[2])+(b[5]-b[4]);
    if(Math.abs(cs-3.6)>0.05||Math.abs(ss-8.3)>0.05||Math.abs(bs-150)>0.2)
      throw new IllegalStateException("DOMAIN_IDENTITY_FAILED cyl="+Arrays.toString(c)+" steel="+Arrays.toString(s)+" ball="+Arrays.toString(b));
    double gap=(c[0]-s[1]);
    log("DOMAIN_IDENTITY cyl="+Arrays.toString(c)+" steel="+Arrays.toString(s)+" ball="+Arrays.toString(b)+" gap_x_mm="+f(gap));
    if(Math.abs(gap-0.30)>0.02)throw new IllegalStateException("GAP_CHECK_FAILED gap_x_mm="+f(gap));
  }
  static int[] allDomains(Model m){
    int n=m.component("comp1").geom("geom1").getNEntities()[3]; int[] a=new int[n];
    for(int i=0;i<n;i++)a[i]=i+1; return a;
  }
  static void cleanup(Model m){
    for(String t:m.sol().tags())try{m.sol().remove(t);}catch(Throwable ignored){}
    for(String t:m.study().tags())try{m.study().remove(t);}catch(Throwable ignored){}
    for(String t:m.result().numerical().tags())try{m.result().numerical().remove(t);}catch(Throwable ignored){}
    for(String t:m.result().dataset().tags())try{m.result().dataset().remove(t);}catch(Throwable ignored){}
  }
  static void removeRotating(Model m){
    ModelNode c=m.component("comp1");
    for(String t:c.common().tags())try{if("RotatingDomain".equals(c.common(t).getType()))c.common().remove(t);}catch(Throwable ignored){}
  }
  static String oldSC,oldSM,oldSmu,oldSBM,oldSB;
  static String oldBC,oldBM,oldBmu,oldBBM,oldBB;
  static String read(PhysicsFeature p,String k){try{return p.getString(k);}catch(Throwable t){return "<UNREADABLE>";}}
  static void capturePhysics(Model m){
    Physics p=m.component("comp1").physics("mfnc"); PhysicsFeature s=p.feature("mfcs2"),b=p.feature("mfc2");
    oldSC=read(s,"ConstitutiveRelationBH"); oldSM=read(s,"mur_mat"); oldSmu=read(s,"mur");
    oldSBM=read(s,"normBr_crel_BH_RemanentFluxDensity_mat"); oldSB=read(s,"normBr_crel_BH_RemanentFluxDensity");
    oldBC=read(b,"ConstitutiveRelationBH"); oldBM=read(b,"mur_mat"); oldBmu=read(b,"mur");
    oldBBM=read(b,"normBr_crel_BH_RemanentFluxDensity_mat"); oldBB=read(b,"normBr_crel_BH_RemanentFluxDensity");
    log("SOURCE_PHYSICS steel="+oldSC+"/"+oldSM+"/"+oldSmu+" ball="+oldBC+"/"+oldBM+"/"+oldBmu+" Br="+oldBB);
  }
  static void normalize(Model m){
    ModelNode c=m.component("comp1"); Physics p=c.physics("mfnc");
    c.material("mat4").selection().set(new int[]{AIR}); c.material("mat3").selection().set(new int[]{CYL,BALL}); c.material("mat5").selection().set(new int[]{STEEL});
    try{p.feature("mfc1").selection().set(new int[]{AIR});}catch(Throwable ignored){}
    p.feature("mfcs1").selection().set(new int[]{CYL}); p.feature("mfcs2").selection().set(new int[]{STEEL});
    p.feature("mfc2").selection().set(new int[]{BALL}); p.feature("fcal1").selection().set(new int[]{CYL});
    try{p.feature("init1").selection().set(allDomains(m));}catch(Throwable ignored){}
  }
  static void reference(Model m){
    PhysicsFeature z=m.component("comp1").physics("mfnc").feature("zsp1");
    if(z.selection().entities(0).length==0&&z.selection().entities(2).length==0){z.selection().geom("geom1",0);z.selection().set(20);}
    if(z.selection().entities(0).length==0&&z.selection().entities(2).length==0)throw new IllegalStateException("REFERENCE_POINT_MISSING");
  }
  static void steelOff(Model m){
    ModelNode c=m.component("comp1"); PhysicsFeature p=c.physics("mfnc").feature("mfcs2");
    c.material("mat4").selection().set(new int[]{AIR,STEEL}); c.material("mat5").selection().set(new int[0]);
    p.selection().set(new int[]{STEEL}); p.set("ConstitutiveRelationBH","RelativePermeability"); p.set("mur_mat","userdef"); p.set("mur","1");
    if(p.hasProperty("normBr_crel_BH_RemanentFluxDensity_mat"))p.set("normBr_crel_BH_RemanentFluxDensity_mat","userdef");
    if(p.hasProperty("normBr_crel_BH_RemanentFluxDensity"))p.set("normBr_crel_BH_RemanentFluxDensity","0[T]");
  }
  static void steelOn(Model m){
    ModelNode c=m.component("comp1"); PhysicsFeature p=c.physics("mfnc").feature("mfcs2");
    c.material("mat4").selection().set(new int[]{AIR}); c.material("mat5").selection().set(new int[]{STEEL}); p.selection().set(new int[]{STEEL});
    if(!oldSC.startsWith("<"))p.set("ConstitutiveRelationBH",oldSC); if(!oldSM.startsWith("<"))p.set("mur_mat",oldSM); if(!oldSmu.startsWith("<"))p.set("mur",oldSmu);
    if(!oldSBM.startsWith("<"))p.set("normBr_crel_BH_RemanentFluxDensity_mat",oldSBM); if(!oldSB.startsWith("<"))p.set("normBr_crel_BH_RemanentFluxDensity",oldSB);
  }
  static void ballOff(Model m){
    ModelNode c=m.component("comp1"); PhysicsFeature p=c.physics("mfnc").feature("mfc2");
    c.material("mat3").selection().set(new int[]{CYL}); c.material("mat4").selection().set(new int[]{AIR,BALL}); p.selection().set(new int[]{BALL});
    p.set("ConstitutiveRelationBH","RelativePermeability"); p.set("mur_mat","userdef"); p.set("mur","1");
    if(p.hasProperty("normBr_crel_BH_RemanentFluxDensity_mat"))p.set("normBr_crel_BH_RemanentFluxDensity_mat","userdef");
    if(p.hasProperty("normBr_crel_BH_RemanentFluxDensity"))p.set("normBr_crel_BH_RemanentFluxDensity","0[T]");
  }
  static void ballOn(Model m){
    ModelNode c=m.component("comp1"); PhysicsFeature p=c.physics("mfnc").feature("mfc2");
    c.material("mat3").selection().set(new int[]{CYL,BALL}); c.material("mat4").selection().set(new int[]{AIR}); p.selection().set(new int[]{BALL});
    if(!oldBC.startsWith("<"))p.set("ConstitutiveRelationBH",oldBC); if(!oldBM.startsWith("<"))p.set("mur_mat",oldBM); if(!oldBmu.startsWith("<"))p.set("mur",oldBmu);
    if(!oldBBM.startsWith("<"))p.set("normBr_crel_BH_RemanentFluxDensity_mat",oldBBM); if(!oldBB.startsWith("<"))p.set("normBr_crel_BH_RemanentFluxDensity",oldBB);
  }
  static void pose(Model m){
    poseAt(m,Z);
  }
  static void poseAt(Model m,double z){
    poseAt(m,z,PHI);
  }
  static void poseAt(Model m,double z,double phi){
    m.param().set("z_sphere",f(z)+"[mm]"); m.param().set("x_gap",f(GAP)+"[mm]"); m.param().set("phi",f(phi)+"[deg]");
    setVec(m.component("comp1").physics("mfnc").feature("mfc2"),"e_crel_BH_RemanentFluxDensity",new String[]{"sin(alpha)","cos(alpha)*cos(phi)","-cos(alpha)*sin(phi)"});
  }
  static void poseAtX(Model m,double x,double z,double phi){
    m.param().set("x_sphere",f(x)+"[mm]"); poseAt(m,z,phi);
  }
  static boolean boundaryContainedInDomain(double[] db,double[] bb){
    double tol=1e-6;
    return bb[0]>=db[0]-tol&&bb[1]<=db[1]+tol&&bb[2]>=db[2]-tol&&bb[3]<=db[3]+tol&&bb[4]>=db[4]-tol&&bb[5]<=db[5]+tol;
  }
  static int[] containedBoundaries(Model m,int dom){
    GeomSequence g=m.component("comp1").geom("geom1"); int nb=g.getNEntities()[2]; double[] db=bbox(m,3,dom); ArrayList<Integer> out=new ArrayList<Integer>();
    for(int b=1;b<=nb;b++)try{if(boundaryContainedInDomain(db,bbox(m,2,b)))out.add(b);}catch(Throwable ignored){}
    int[] ids=new int[out.size()]; for(int i=0;i<ids.length;i++)ids[i]=out.get(i); return ids;
  }
  static int[] nearFieldBoundaries(Model m){
    // 圆柱全部外边界；钢片只取面向圆柱的最大 x 面，以及中心孔附近的
    // 小边界。钢片远端外壁不参与局部细化，否则 0.02--0.03 mm 会扩散
    // 到整个 4 mm 级钢片表面，重新形成百万单元网格。
    TreeSet<Integer> ids=new TreeSet<Integer>(); for(int b:containedBoundaries(m,CYL))ids.add(b);
    double[] sd=bbox(m,3,STEEL); for(int b:containedBoundaries(m,STEEL)){double[] bb=bbox(m,2,b);double span=(bb[1]-bb[0])+(bb[3]-bb[2])+(bb[5]-bb[4]);boolean front=Math.abs(bb[0]-sd[1])<1e-6&&Math.abs(bb[1]-sd[1])<1e-6;boolean holeEdge=span<1.05;if(front||holeEdge)ids.add(b);}
    if(ids.isEmpty())throw new IllegalStateException("LOCAL_BOUNDARY_SELECTION_EMPTY"); int[] r=new int[ids.size()]; int i=0; for(int b:ids)r[i++]=b;
    log("LOCAL_BOUNDARIES cylinder_steel_bbox_contained count="+r.length+" ids="+Arrays.toString(r)); return r;
  }
  static void mesh(Model m,String level,double h){
    MeshSequence q=m.component("comp1").mesh("mesh1"); q.feature("size").set("hauto","6");
    try{q.feature().remove("nearfieldfine_local");}catch(Throwable ignored){}
    MeshFeature solid=q.feature().create("nearfieldfine_local","Size"); solid.selection().geom("geom1",3); solid.selection().set(new int[]{STEEL,CYL,BALL}); solid.set("hauto","1"); try{q.feature().move("nearfieldfine_local",1);}catch(Throwable ignored){}
    if(!"CURRENT_SOURCE_MESH".equals(level)){
      String st="nearfield_size_"+level.toLowerCase(Locale.ROOT); int[] ids=nearFieldBoundaries(m);
      try{q.feature().remove(st);}catch(Throwable ignored){} MeshFeature sf=q.feature().create(st,"Size"); sf.selection().geom("geom1",2); sf.selection().set(ids); set(sf,"custom","on"); set(sf,"hmax",f(h)+"[mm]"); set(sf,"hmin",f(h/3.0)+"[mm]"); set(sf,"hgrad","1.3"); if(sf.hasProperty("hnarrow"))sf.set("hnarrow","1"); try{q.feature().move(st,1);}catch(Throwable ignored){} log("LOCAL_SIZE level="+level+" hmax_mm="+f(h)+" hmin_mm="+f(h/3.0)+" boundary_count="+ids.length);
    }else log("LOCAL_SIZE level=CURRENT_SOURCE_MESH hauto6+solid_domains234_hauto1 boundary_size=off");
    q.run(); log("MESH_DONE level="+level+" elements="+q.getNumElem()+" min_quality="+f(q.getMinQuality()));
  }
  static String solver(Model m,String suffix){
    String st="local_mesh_stat_"+suffix+"_"+System.nanoTime(); m.study().create(st); m.study(st).create("stat","Stationary"); m.study(st).createAutoSequences("all"); String sol=m.sol().tags()[m.sol().tags().length-1];
    SolverFeature s=m.sol(sol).feature("s1"),d=s.feature("dDef"),fc=s.feature("fc1"); set(d,"linsolver","pardiso"); set(fc,"linsolver","dDef"); set(s,"control","stat"); StudyFeature stat=m.study(st).feature("stat"); if(!stat.hasProperty("usestol")||!stat.hasProperty("stol"))throw new IllegalStateException("STOL_PROPERTY_MISSING"); stat.set("usestol","on"); stat.set("stol","1e-6");
    log("SOLVER_CONFIG level="+suffix+" sol="+sol+" direct="+get(d,"linsolver")+" fullyCoupled="+get(fc,"linsolver")+" stol="+get(stat,"stol")); return sol;
  }
  static double[] read(Model m,String sol,String expr){
    String ds="ds_local_"+System.nanoTime(),n="g_local_"+System.nanoTime(); try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"Global");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.set("expr",new String[]{expr});q.set("unit",new String[]{"mN"});return new double[]{first(q.getData())};}finally{try{m.result().numerical().remove(n);}catch(Throwable ignored){}try{m.result().dataset().remove(ds);}catch(Throwable ignored){}}
  }
  static double[] solve(Model m,String label)throws Exception{String sol=solver(m,label.replace(' ','_'));log("SOLVE_START "+label);m.sol(sol).runAll();log("SOLVE_DONE "+label);return new double[]{read(m,sol,FX)[0],read(m,sol,FY)[0],read(m,sol,FZ)[0]};}
  static int dof(Model m,String sol){try{int[] s=m.sol(sol).getSize();return s.length==0?-1:s[0];}catch(Throwable t){return -1;}}
  static String val(double x){return Double.isNaN(x)?"":f(x);}
  static void header(PrintWriter w){w.println("mesh_level,h_local_mm,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,status");}
  static void write(PrintWriter w,String level,double h,double[] b0,double[] b1,double[] b2,int ne,int dof,double q,String status){
    double hold=b1[0]-b0[0],ball=b2[0]-b1[0],total=b2[0]-b0[0],fy=b2[1]-b0[1],fz=b2[2]-b0[2]; w.println(String.join(",",level,val(h),val(b0[0]),val(b1[0]),val(b2[0]),val(hold),val(ball),val(total),val(fy),val(fz),Integer.toString(ne),Integer.toString(dof),val(q),"true",status));w.flush();
    log("RESULT level="+level+" h="+val(h)+" Fx_hold="+f(hold)+" DeltaFx_ball="+f(ball)+" Fx_total="+f(total)+" Fy_total="+f(fy)+" Fz_total="+f(fz)+" elements="+ne+" dof="+dof+" minq="+f(q)+" status="+status);
  }
  static void errorRow(PrintWriter w,String level,double h,String status){w.println(String.join(",",level,val(h),"","","","","","","","","","","","",status));w.flush();}
  static void runLevel(String source,Path out,String level,double h,PrintWriter w)throws Exception{
    Model m=null;String name="local_mesh_"+level+"_"+System.nanoTime();try{
      m=ModelUtil.load(name,source);cleanup(m);removeRotating(m);m.param().set("x_gap",f(GAP)+"[mm]");m.param().set("z_sphere",f(Z)+"[mm]");m.component("comp1").geom("geom1").run();verifyDomains(m);capturePhysics(m);normalize(m);reference(m);pose(m);mesh(m,level,h);int ne=m.component("comp1").mesh("mesh1").getNumElem();double q=m.component("comp1").mesh("mesh1").getMinQuality();
      steelOff(m);ballOff(m);pose(m);double[] b0=solve(m,"B0 "+level);String s0=m.sol().tags()[m.sol().tags().length-1];
      steelOn(m);ballOff(m);pose(m);double[] b1=solve(m,"B1 "+level);String s1=m.sol().tags()[m.sol().tags().length-1];
      steelOn(m);ballOn(m);pose(m);double[] b2=solve(m,"B2 "+level);String s2=m.sol().tags()[m.sol().tags().length-1];
      int d=dof(m,s2);Path dir=out.resolve(level);Files.createDirectories(dir);m.save(dir.resolve("solved.mph").toString());write(w,level,h,b0,b1,b2,ne,d,q,"SUCCESS");log("LEVEL_FINISH="+level);
    }catch(OutOfMemoryError e){errorRow(w,level,h,"RESOURCE_LIMIT");log("LEVEL_ERROR="+level+" RESOURCE_LIMIT "+e);}
    catch(Throwable e){errorRow(w,level,h,"ERROR");log("LEVEL_ERROR="+level+" "+e.getClass().getSimpleName()+" "+e.getMessage());e.printStackTrace(System.out);}
    finally{try{if(m!=null)m.save(out.resolve(level+"_last_state.mph").toString());}catch(Throwable ignored){}try{ModelUtil.remove(name);}catch(Throwable ignored){}}
  }
  /** 固定 z=120、phi=90，复用一张 LOCAL_M03 网格扫描 alpha。 */
  static void runAlpha(String source,Path out)throws Exception{
    String name="alpha_coarse_"+System.nanoTime(); Model m=null;
    try{
      m=ModelUtil.load(name,source); cleanup(m); removeRotating(m);
      m.param().set("x_gap",f(GAP)+"[mm]"); m.param().set("z_sphere",f(Z)+"[mm]");
      m.param().set("alpha","30[deg]"); m.param().set("phi",f(PHI)+"[deg]");
      m.component("comp1").geom("geom1").run(); verifyDomains(m); capturePhysics(m); normalize(m); reference(m); pose(m);
      mesh(m,"LOCAL_M03",0.03); int ne=m.component("comp1").mesh("mesh1").getNumElem();
      steelOff(m); ballOff(m); pose(m); double[] b0=solve(m,"B0 alpha_scan");
      steelOn(m); ballOff(m); pose(m); double[] b1=solve(m,"B1 alpha_scan");
      log("ALPHA_SCAN_FIXED_MESH elements="+ne+" phi="+f(PHI)+" B0_B1_SOLVED_ONCE=true");
      Path csv=out.resolve("modelB_z120_phi90_alpha_coarse.csv");
      try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){
        w.println("alpha_deg,phi_deg,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,same_mesh_verified,status");
        for(double alpha:new double[]{0,10,20,30,40,50,60,70,80,90}){
          m.param().set("alpha",f(alpha)+"[deg]"); ballOn(m); pose(m);
          log("ALPHA_START alpha="+f(alpha)+" GEOM_MESH_REBUILD=false");
          double[] b2=solve(m,"B2 alpha="+f(alpha)); String[] st=m.sol().tags(); int dof=dof(m,st[st.length-1]);
          double hold=b1[0]-b0[0],ball=b2[0]-b1[0],total=b2[0]-b0[0],fy=b2[1]-b0[1],fz=b2[2]-b0[2];
          w.println(String.join(",",val(alpha),val(PHI),val(b2[0]),val(hold),val(ball),val(total),val(fy),val(fz),Integer.toString(ne),Integer.toString(dof),"true","SUCCESS")); w.flush();
          log("ALPHA_RESULT alpha="+f(alpha)+" Fx_hold="+f(hold)+" DeltaFx_ball="+f(ball)+" Fx_total="+f(total)+" Fy_total="+f(fy)+" Fz_total="+f(fz)+" elements="+ne+" dof="+dof);
        }
      }
      m.save(out.resolve("modelB_z120_phi90_alpha_coarse_final.mph").toString()); log("FINISH csv="+csv);
    } finally { try{if(m!=null)m.save(out.resolve("alpha_last_state.mph").toString());}catch(Throwable ignored){} try{ModelUtil.remove(name);}catch(Throwable ignored){} }
  }
  /** 复用既有 LOCAL_M02 z=120 检查点，仅新增 alpha=0 的 B2。 */
  static void runAlpha0M02(String checkpoint,Path out)throws Exception{
    String name="alpha0_m02_"+System.nanoTime(); Model m=null;
    try{
      m=ModelUtil.load(name,checkpoint); verifyDomains(m); capturePhysics(m); normalize(m); reference(m);
      m.param().set("alpha","0[deg]"); poseAt(m,Z); int ne=m.component("comp1").mesh("mesh1").getNumElem(); double q=m.component("comp1").mesh("mesh1").getMinQuality();
      String[] tags=m.sol().tags(); if(tags.length<2)throw new IllegalStateException("M02_CHECKPOINT_SOLUTIONS_MISSING");
      String sol0=tags[0],sol1=tags[1]; double[] b0= new double[]{read(m,sol0,FX)[0],read(m,sol0,FY)[0],read(m,sol0,FZ)[0]};
      double[] b1= new double[]{read(m,sol1,FX)[0],read(m,sol1,FY)[0],read(m,sol1,FZ)[0]};
      log("M02_CHECKPOINT_REUSED sol0="+sol0+" sol1="+sol1+" elements="+ne+" min_quality="+f(q));
      steelOn(m); ballOn(m); poseAt(m,Z); String sol2=solver(m,"B2_alpha0_M02_validation"); log("SOLVE_START B2 alpha=0 M02");m.sol(sol2).runAll();log("SOLVE_DONE B2 alpha=0 M02");
      double[] b2=new double[]{read(m,sol2,FX)[0],read(m,sol2,FY)[0],read(m,sol2,FZ)[0]}; int d=dof(m,sol2);
      double hold=b1[0]-b0[0],ball=b2[0]-b1[0],total=b2[0]-b0[0];
      Path csv=out.resolve("modelB_z120_alpha0_M02_validation.csv");try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){
        w.println("z_sphere_mm,alpha_deg,phi_deg,mesh_level,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,status");
        w.println(String.join(",",val(Z),"0","90","LOCAL_M02",val(b0[0]),val(b1[0]),val(b2[0]),val(hold),val(ball),val(total),val(b2[1]-b0[1]),val(b2[2]-b0[2]),Integer.toString(ne),Integer.toString(d),val(q),"true","SUCCESS"));
      }
      m.save(out.resolve("modelB_z120_alpha0_M02_validation.mph").toString()); log("ALPHA0_M02_RESULT Fx_hold="+f(hold)+" DeltaFx_ball="+f(ball)+" Fx_total="+f(total)+" elements="+ne+" dof="+d);
    } finally {try{if(m!=null)m.save(out.resolve("last_state.mph").toString());}catch(Throwable ignored){}try{ModelUtil.remove(name);}catch(Throwable ignored){}}
  }
  /** 最终释放候选 z=60、phi=90 的 LOCAL_M02 三状态复核。 */
  static void runCandidateM02(String source,Path out)throws Exception{
    final double z=60.0;String name="candidate_m02_z60_"+System.nanoTime();Model m=null;
    try{
      m=ModelUtil.load(name,source);cleanup(m);removeRotating(m);m.param().set("x_gap",f(GAP)+"[mm]");m.param().set("z_sphere",f(z)+"[mm]");m.param().set("alpha","0[deg]");m.param().set("phi",f(PHI)+"[deg]");m.component("comp1").geom("geom1").run();verifyDomains(m);capturePhysics(m);normalize(m);reference(m);poseAt(m,z);mesh(m,"LOCAL_M02",0.02);int ne=m.component("comp1").mesh("mesh1").getNumElem();double q=m.component("comp1").mesh("mesh1").getMinQuality();
      steelOff(m);ballOff(m);poseAt(m,z);double[] b0=solve(m,"B0 candidate z60 M02");steelOn(m);ballOff(m);poseAt(m,z);double[] b1=solve(m,"B1 candidate z60 M02");steelOn(m);ballOn(m);poseAt(m,z);double[] b2=solve(m,"B2 candidate z60 M02");String[] st=m.sol().tags();int d=dof(m,st[st.length-1]);double hold=b1[0]-b0[0],ball=b2[0]-b1[0],total=b2[0]-b0[0];
      Path csv=out.resolve("modelB_alpha0_z60_M02_validation.csv");try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){w.println("z_sphere_mm,alpha_deg,phi_deg,mesh_level,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status");w.println(String.join(",",val(z),"0","90","LOCAL_M02",val(b0[0]),val(b1[0]),val(b2[0]),val(hold),val(ball),val(total),val(b2[1]-b0[1]),val(b2[2]-b0[2]),Integer.toString(ne),Integer.toString(d),val(q),"true",total>0?"MAGNETIC_RELEASE_CANDIDATE":"MAGNETICALLY_HELD","SUCCESS"));}
      m.save(out.resolve("modelB_alpha0_z60_M02_validation.mph").toString());log("CANDIDATE_M02_RESULT z="+f(z)+" Fx_hold="+f(hold)+" DeltaFx_ball="+f(ball)+" Fx_total="+f(total)+" elements="+ne+" dof="+d+" minq="+f(q));
    }finally{try{if(m!=null)m.save(out.resolve("candidate_m02_last_state.mph").toString());}catch(Throwable ignored){}try{ModelUtil.remove(name);}catch(Throwable ignored){}}
  }
  static void runOneZ(String source,Path out,double z,PrintWriter w)throws Exception{
    String name="alpha0_z_"+f(z)+"_"+System.nanoTime(); Model m=null;
    try{
      m=ModelUtil.load(name,source); cleanup(m); removeRotating(m); m.param().set("x_gap",f(GAP)+"[mm]");m.param().set("z_sphere",f(z)+"[mm]");m.param().set("alpha","0[deg]");m.param().set("phi",f(PHI)+"[deg]");m.component("comp1").geom("geom1").run();verifyDomains(m);capturePhysics(m);normalize(m);reference(m);poseAt(m,z);mesh(m,"LOCAL_M03",0.03);
      int ne=m.component("comp1").mesh("mesh1").getNumElem();double q=m.component("comp1").mesh("mesh1").getMinQuality();
      steelOff(m);ballOff(m);poseAt(m,z);double[] b0=solve(m,"B0 alpha0 z="+f(z)); steelOn(m);ballOff(m);poseAt(m,z);double[] b1=solve(m,"B1 alpha0 z="+f(z)); steelOn(m);ballOn(m);poseAt(m,z);double[] b2=solve(m,"B2 alpha0 z="+f(z));String[] st=m.sol().tags();int d=dof(m,st[st.length-1]);
      double hold=b1[0]-b0[0],ball=b2[0]-b1[0],total=b2[0]-b0[0];
      w.println(String.join(",",val(z),val(b0[0]),val(b1[0]),val(b2[0]),val(hold),val(ball),val(total),val(b2[1]-b0[1]),val(b2[2]-b0[2]),Integer.toString(ne),Integer.toString(d),val(q),"true",total>0?"MAGNETIC_RELEASE_CANDIDATE":"MAGNETICALLY_HELD","SUCCESS"));w.flush();
      log("Z_RESULT z="+f(z)+" Fx_hold="+f(hold)+" DeltaFx_ball="+f(ball)+" Fx_total="+f(total)+" elements="+ne+" dof="+d+" minq="+f(q)+" status=SUCCESS");
    }catch(Throwable e){w.println(String.join(",",val(z),"","","","","","","","","","","","","UNKNOWN","ERROR"));w.flush();log("Z_ERROR z="+f(z)+" "+e.getClass().getSimpleName()+" "+e.getMessage());e.printStackTrace(System.out);}finally{try{ModelUtil.remove(name);}catch(Throwable ignored){}}
  }
  /** 固定 z=60、alpha=0、phi=90，向 x+ 方向扫描磁球中心位置。 */
  static void runOneX(String source,Path out,double x,PrintWriter w)throws Exception{
    String name="alpha0_x_"+f(x)+"_"+System.nanoTime(); Model m=null;
    try{
      m=ModelUtil.load(name,source); cleanup(m); removeRotating(m);
      m.param().set("x_sphere",f(x)+"[mm]"); m.param().set("x_gap",f(GAP)+"[mm]");
      m.param().set("z_sphere","60[mm]"); m.param().set("alpha","0[deg]"); m.param().set("phi","90[deg]");
      m.component("comp1").geom("geom1").run(); verifyDomains(m); capturePhysics(m); normalize(m); reference(m); poseAtX(m,x,60.0,90.0); mesh(m,"LOCAL_M03",0.03);
      int ne=m.component("comp1").mesh("mesh1").getNumElem(); double q=m.component("comp1").mesh("mesh1").getMinQuality();
      steelOff(m); ballOff(m); poseAtX(m,x,60.0,90.0); double[] b0=solve(m,"B0 alpha0 x="+f(x));
      steelOn(m); ballOff(m); poseAtX(m,x,60.0,90.0); double[] b1=solve(m,"B1 alpha0 x="+f(x));
      steelOn(m); ballOn(m); poseAtX(m,x,60.0,90.0); double[] b2=solve(m,"B2 alpha0 x="+f(x));
      String[] st=m.sol().tags(); int d=dof(m,st[st.length-1]); double hold=b1[0]-b0[0], ball=b2[0]-b1[0], total=b2[0]-b0[0];
      w.println(String.join(",",val(x),val(60),val(0),val(90),val(b0[0]),val(b1[0]),val(b2[0]),val(hold),val(ball),val(total),val(b2[1]-b0[1]),val(b2[2]-b0[2]),Integer.toString(ne),Integer.toString(d),val(q),"true",total>0?"MAGNETIC_RELEASE_CANDIDATE":"MAGNETICALLY_HELD","SUCCESS")); w.flush();
      log("X_RESULT x="+f(x)+" Fx_hold="+f(hold)+" DeltaFx_ball="+f(ball)+" Fx_total="+f(total)+" elements="+ne+" dof="+d+" minq="+f(q)+" status=SUCCESS");
    }catch(Throwable e){
      w.println(String.join(",",val(x),"60","0","90","","","","","","","","","","","","UNKNOWN","ERROR")); w.flush();
      log("X_ERROR x="+f(x)+" "+e.getClass().getSimpleName()+" "+e.getMessage()); e.printStackTrace(System.out);
    }finally{try{ModelUtil.remove(name);}catch(Throwable ignored){}}
  }
  static void runXScan(String source,Path out)throws Exception{
    // x=26 mm 为当前已验证的相切位置；只向 x+ 扫描，避免球体与圆柱穿透。
    double[] xs=new double[]{26,28,30,32,34,36}; Path csv=out.resolve("modelB_alpha0_x60_scan.csv");
    try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){
      w.println("x_sphere_mm,z_sphere_mm,alpha_deg,phi_deg,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status");
      for(double x:xs){log("X_START x="+f(x)+" z=60 alpha=0 phi=90 GEOM_MESH_REBUILD=true");runOneX(source,out,x,w);}
    }
    log("FINISH csv="+csv);
  }
  /** Stage A：固定 z、alpha=0 的完整稀疏角度扫描；B0/B1 只求一次，B2 随 phi 变化。 */
  static void runSparseZ(String source,Path out,double z,String fileName)throws Exception{
    runSparseZ(source,out,z,0.0,fileName);
  }
  /** 可复用的固定 alpha 稀疏周期扫描；alpha 只改变磁化姿态，不改变几何和网格策略。 */
  static void runSparseZ(String source,Path out,double z,double alpha,String fileName)throws Exception{
    runSparseZ(source,out,z,alpha,fileName,true);
  }
  /** Fixed-alpha sparse cycle with optional last-state persistence. */
  static void runSparseZ(String source,Path out,double z,double alpha,String fileName,boolean saveLastState)throws Exception{
    String name="alpha0_sparse_z"+f(z)+"_"+System.nanoTime(); Model m=null;
    try{
      m=ModelUtil.load(name,source); cleanup(m); removeRotating(m);
      m.param().set("x_sphere",f(X_SPHERE)+"[mm]"); m.param().set("x_gap",f(GAP)+"[mm]");
      m.param().set("z_sphere",f(z)+"[mm]"); m.param().set("alpha",f(alpha)+"[deg]"); m.param().set("phi","0[deg]");
      m.component("comp1").geom("geom1").run(); verifyDomains(m); capturePhysics(m); normalize(m); reference(m); poseAtX(m,X_SPHERE,z,0); mesh(m,"LOCAL_M03",0.03);
      int ne=m.component("comp1").mesh("mesh1").getNumElem(); double q=m.component("comp1").mesh("mesh1").getMinQuality();
      steelOff(m); ballOff(m); poseAtX(m,X_SPHERE,z,0); double[] b0=solve(m,"B0 sparse z="+f(z));
      steelOn(m); ballOff(m); poseAtX(m,X_SPHERE,z,0); double[] b1=solve(m,"B1 sparse z="+f(z));
      Path csv=out.resolve(fileName); double fmax=-Double.MAX_VALUE,fmin=Double.MAX_VALUE; double phiMax=Double.NaN;
      try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){
        w.println("z_sphere_mm,phi_deg,mesh_level,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status");
        for(double phi:SPARSE_PHI){
          m.param().set("phi",f(phi)+"[deg]"); ballOn(m); poseAtX(m,X_SPHERE,z,phi); double[] b2=solve(m,"B2 sparse z="+f(z)+" phi="+f(phi)); String[] st=m.sol().tags(); int d=dof(m,st[st.length-1]);
          double hold=b1[0]-b0[0],ball=b2[0]-b1[0],total=b2[0]-b0[0];
          w.println(String.join(",",val(z),val(phi),"LOCAL_M03",val(b0[0]),val(b1[0]),val(b2[0]),val(hold),val(ball),val(total),val(b2[1]-b0[1]),val(b2[2]-b0[2]),Integer.toString(ne),Integer.toString(d),val(q),"true",total>0?"MAGNETIC_RELEASE_CANDIDATE":"MAGNETICALLY_HELD","SUCCESS")); w.flush();
          log("SPARSE_RESULT z="+f(z)+" phi="+f(phi)+" Fx_total="+f(total)+" DeltaFx_ball="+f(ball)+" status=SUCCESS");
          if(total>fmax){fmax=total;phiMax=phi;} if(total<fmin)fmin=total;
        }
      }
      try(PrintWriter s=new PrintWriter(Files.newBufferedWriter(out.resolve("stageA_summary.txt")))){s.println("z_sphere_mm="+f(z));s.println("Fmin_mN="+f(fmin));s.println("Fmax_mN="+f(fmax));s.println("phi_at_Fmax_deg="+f(phiMax));s.println("elements="+ne);s.println("min_quality="+f(q));}
      log("SPARSE_FINISH z="+f(z)+" alpha="+f(alpha)+" Fmin="+f(fmin)+" Fmax="+f(fmax)+" phi_at_Fmax="+f(phiMax)+" csv="+csv);
    }finally{try{if(saveLastState&&m!=null)m.save(out.resolve("z"+f(z)+"_M03_last_state.mph").toString());}catch(Throwable ignored){}try{ModelUtil.remove(name);}catch(Throwable ignored){}}
  }
  /**
   * 宏观 z 扫描的单点 phi=90°版本：每个高度只构建一次几何和网格，
   * B0/B1/B2 严格复用该高度同一张 LOCAL_M03 网格。
   */
  static void runMacroPhi90Z(String source,Path out,double z,PrintWriter w)throws Exception{
    String name="macro_phi90_z"+f(z)+"_"+System.nanoTime(); Model m=null;
    try{
      m=ModelUtil.load(name,source); cleanup(m); removeRotating(m);
      m.param().set("x_sphere",f(X_SPHERE)+"[mm]"); m.param().set("x_gap",f(GAP)+"[mm]");
      m.param().set("z_sphere",f(z)+"[mm]"); m.param().set("alpha","0[deg]"); m.param().set("phi","90[deg]");
      m.component("comp1").geom("geom1").run(); verifyDomains(m); capturePhysics(m); normalize(m); reference(m); poseAtX(m,X_SPHERE,z,90.0);
      mesh(m,"LOCAL_M03",0.03); int ne=m.component("comp1").mesh("mesh1").getNumElem(); double q=m.component("comp1").mesh("mesh1").getMinQuality();
      steelOff(m); ballOff(m); poseAtX(m,X_SPHERE,z,90.0); double[] b0=solve(m,"B0 macro z="+f(z));
      steelOn(m); ballOff(m); poseAtX(m,X_SPHERE,z,90.0); double[] b1=solve(m,"B1 macro z="+f(z));
      steelOn(m); ballOn(m); poseAtX(m,X_SPHERE,z,90.0); double[] b2=solve(m,"B2 macro z="+f(z));
      String[] st=m.sol().tags(); int d=dof(m,st[st.length-1]); double hold=b1[0]-b0[0],ball=b2[0]-b1[0],total=b2[0]-b0[0];
      w.println(String.join(",",val(z),"90","LOCAL_M03",val(b0[0]),val(b1[0]),val(b2[0]),val(hold),val(ball),val(total),val(b2[1]-b0[1]),val(b2[2]-b0[2]),Integer.toString(ne),Integer.toString(d),val(q),"true",total>0?"MAGNETIC_RELEASE_CANDIDATE":"MAGNETICALLY_HELD","SUCCESS")); w.flush();
      log("MACRO_RESULT z="+f(z)+" phi=90 Fx_hold="+f(hold)+" DeltaFx_ball="+f(ball)+" Fx_total="+f(total)+" elements="+ne+" dof="+d+" minq="+f(q)+" status=SUCCESS");
      Path zd=out.resolve("z"+f(z)); Files.createDirectories(zd); m.save(zd.resolve("z"+f(z)+"_M03_last_state.mph").toString());
    }catch(Throwable e){
      w.println(String.join(",",val(z),"90","LOCAL_M03","","","","","","","","","","","","","UNKNOWN","ERROR")); w.flush();
      log("MACRO_ERROR z="+f(z)+" "+e.getClass().getSimpleName()+" "+e.getMessage()); e.printStackTrace(System.out);
    }finally{try{ModelUtil.remove(name);}catch(Throwable ignored){}}
  }
  /** 第一批宏观验证：先完成 120、140、150 mm 的 phi=90°三状态结果。 */
  static void runMacroFirst(String source,Path out)throws Exception{
    double[] zs=new double[]{120,140,150}; Path csv=out.resolve("modelB_x26_macro_z_phi90_first.csv");
    try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){
      w.println("z_sphere_mm,phi_deg,mesh_level,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status");
      for(double z:zs){log("MACRO_START z="+f(z)+" phi=90 alpha=0 GEOM_MESH_REBUILD=true");runMacroPhi90Z(source,out,z,w);}
    }
    log("MACRO_FIRST_FINISH csv="+csv);
  }
  /** 代表性高度的完整稀疏周期扫描；z=50 的已验证结果由外部汇总器复用。 */
  static void runMacroSparseReps(String source,Path out)throws Exception{
    double[] zs=new double[]{60,80,100,120,140,160};
    for(double z:zs){
      Path zd=out.resolve("sparse_z"+f(z)); Files.createDirectories(zd);
      log("MACRO_SPARSE_START z="+f(z)+" phi=0:45:360 alpha=0 GEOM_MESH_REBUILD=true");
      runSparseZ(source,zd,z,"modelB_x26_macro_phi_sparse.csv");
    }
    log("MACRO_SPARSE_FINISH out="+out);
  }
  /** 转变区补点：仅计算 105/110/115 mm，每个高度完整稀疏周期。 */
  static void runTransitionSparse(String source,Path out)throws Exception{
    double[] zs=new double[]{105,110,115};
    for(double z:zs){
      Path zd=out.resolve("transition_z"+f(z)); Files.createDirectories(zd);
      log("TRANSITION_START z="+f(z)+" phi=0:45:360 alpha=0 GEOM_MESH_REBUILD=true");
      runSparseZ(source,zd,z,"modelB_x26_transition_phi_sparse.csv");
    }
    log("TRANSITION_FINISH out="+out);
  }
  /** Group B：alpha=20 deg 的核心高度完整稀疏周期扫描。 */
  static void runAlpha20Sparse(String source,Path out)throws Exception{
    double[] zs=new double[]{60,80,100,120,140,160};
    for(double z:zs){
      Path zd=out.resolve("alpha20_z"+f(z)); Files.createDirectories(zd);
      log("ALPHA20_START z="+f(z)+" phi=0:45:360 GEOM_MESH_REBUILD=true");
      runSparseZ(source,zd,z,20.0,"modelB_alpha20_phi_sparse.csv");
    }
    log("ALPHA20_FINISH out="+out);
  }
  /** Group B local-height refinement around the z=80 sparse maximum. */
  static void runAlpha20Refinement(String source,Path out)throws Exception{
    double[] zs=new double[]{70,75,85,90};
    for(double z:zs){
      Path zd=out.resolve("alpha20_z"+f(z)); Files.createDirectories(zd);
      log("ALPHA20_REFINEMENT_START z="+f(z)+" phi=0:45:360 GEOM_MESH_REBUILD=true");
      runSparseZ(source,zd,z,20.0,"modelB_alpha20_refinement_phi_sparse.csv");
    }
    log("ALPHA20_REFINEMENT_FINISH out="+out);
  }
  /** Directionality control: alpha=-20 deg, all macro heights and full sparse cycles. */
  static void runAlphaMinus20Sparse(String source,Path out)throws Exception{
    double[] zs=new double[]{60,80,100,120,140,160};
    for(double z:zs){
      Path zd=out.resolve("alpha_minus20_z"+f(z)); Files.createDirectories(zd);
      log("ALPHA_MINUS20_START z="+f(z)+" phi=0:45:360 GEOM_MESH_REBUILD=true");
      runSparseZ(source,zd,z,-20.0,"modelB_alpha_minus20_phi_sparse.csv",false);
    }
    log("ALPHA_MINUS20_FINISH out="+out);
  }
  /** One alpha=-20 sparse height, used only for data-driven transition refinement. */
  static void runAlphaMinus20Z(String source,Path out,double z)throws Exception{
    log("ALPHA_MINUS20_REFINEMENT_START z="+f(z)+" phi=0:45:360 GEOM_MESH_REBUILD=true");
    runSparseZ(source,out,z,-20.0,"modelB_alpha_minus20_phi_sparse.csv",false);
  }
  /** M02 单相位复核，参数 z 与 phi 由命令行给出。 */
  static void runM02Pose(String source,Path out,double z,double phi)throws Exception{
    runM02Pose(source,out,z,phi,0.0,"modelB_z"+f(z)+"_phi"+f(phi)+"_M02_validation.csv",false);
  }
  /** 任意 alpha 的 M02 单姿态三状态复核。 */
  static void runM02Pose(String source,Path out,double z,double phi,double alpha,String fileName,boolean includeAlpha)throws Exception{
    String name="alpha"+f(alpha)+"_m02_z"+f(z)+"_p"+f(phi)+"_"+System.nanoTime(); Model m=null;
    try{
      m=ModelUtil.load(name,source); cleanup(m); removeRotating(m); m.param().set("x_sphere",f(X_SPHERE)+"[mm]");m.param().set("x_gap",f(GAP)+"[mm]");m.param().set("z_sphere",f(z)+"[mm]");m.param().set("alpha",f(alpha)+"[deg]");m.param().set("phi",f(phi)+"[deg]");m.component("comp1").geom("geom1").run();verifyDomains(m);capturePhysics(m);normalize(m);reference(m);poseAtX(m,X_SPHERE,z,phi);mesh(m,"LOCAL_M02",0.02);int ne=m.component("comp1").mesh("mesh1").getNumElem();double q=m.component("comp1").mesh("mesh1").getMinQuality();
      steelOff(m);ballOff(m);poseAtX(m,X_SPHERE,z,phi);double[] b0=solve(m,"B0 M02 alpha="+f(alpha)+" z="+f(z)+" phi="+f(phi));steelOn(m);ballOff(m);poseAtX(m,X_SPHERE,z,phi);double[] b1=solve(m,"B1 M02 alpha="+f(alpha)+" z="+f(z)+" phi="+f(phi));steelOn(m);ballOn(m);poseAtX(m,X_SPHERE,z,phi);double[] b2=solve(m,"B2 M02 alpha="+f(alpha)+" z="+f(z)+" phi="+f(phi));String[] st=m.sol().tags();int d=dof(m,st[st.length-1]);double hold=b1[0]-b0[0],ball=b2[0]-b1[0],total=b2[0]-b0[0];Path csv=out.resolve(fileName);
      try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){
        if(includeAlpha){w.println("z_sphere_mm,alpha_deg,phi_deg,mesh_level,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status");w.println(String.join(",",val(z),val(alpha),val(phi),"LOCAL_M02",val(b0[0]),val(b1[0]),val(b2[0]),val(hold),val(ball),val(total),val(b2[1]-b0[1]),val(b2[2]-b0[2]),Integer.toString(ne),Integer.toString(d),val(q),"true",total>0?"MAGNETIC_RELEASE_CANDIDATE":"MAGNETICALLY_HELD","SUCCESS"));}
        else{w.println("z_sphere_mm,phi_deg,mesh_level,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status");w.println(String.join(",",val(z),val(phi),"LOCAL_M02",val(b0[0]),val(b1[0]),val(b2[0]),val(hold),val(ball),val(total),val(b2[1]-b0[1]),val(b2[2]-b0[2]),Integer.toString(ne),Integer.toString(d),val(q),"true",total>0?"MAGNETIC_RELEASE_CANDIDATE":"MAGNETICALLY_HELD","SUCCESS"));}
      }
      log("M02_RESULT alpha="+f(alpha)+" z="+f(z)+" phi="+f(phi)+" Fx_hold="+f(hold)+" DeltaFx_ball="+f(ball)+" Fx_total="+f(total)+" elements="+ne+" dof="+d+" minq="+f(q));m.save(out.resolve(includeAlpha?"alpha"+f(alpha)+"_z"+f(z)+"_phi"+f(phi)+"_M02_validation.mph":"z"+f(z)+"_phi"+f(phi)+"_M02_validation.mph").toString());
    }finally{try{if(m!=null)m.save(out.resolve("m02_last_state.mph").toString());}catch(Throwable ignored){}try{ModelUtil.remove(name);}catch(Throwable ignored){}}
  }
  static void runZAlpha0(String source,Path out)throws Exception{
    double[] zs=new double[]{60,70,80,90,100,110,120,130,140};Path csv=out.resolve("modelB_alpha0_z_coarse_scan.csv");
    try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){w.println("z_sphere_mm,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status");for(double z:zs){log("Z_START z="+f(z)+" alpha=0 GEOM_ONCE_MESH_ONCE=true");runOneZ(source,out,z,w);}}
    log("FINISH csv="+csv);
  }
  /** 释放候选后的最小复核：边界追加 z=50，以及 z=60 的 phi 角度扫描。 */
  static void runReleaseRefinement(String source,Path out)throws Exception{
    Path zcsv=out.resolve("modelB_alpha0_z_local_refinement.csv");
    try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(zcsv))){w.println("z_sphere_mm,Fx_B0_raw_mN,Fx_B1_raw_mN,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status");runOneZ(source,out,50.0,w);}
    String name="alpha0_phi_z60_"+System.nanoTime();Model m=null;
    try{
      m=ModelUtil.load(name,source);cleanup(m);removeRotating(m);m.param().set("x_gap",f(GAP)+"[mm]");m.param().set("z_sphere","60[mm]");m.param().set("alpha","0[deg]");m.param().set("phi",f(PHI)+"[deg]");m.component("comp1").geom("geom1").run();verifyDomains(m);capturePhysics(m);normalize(m);reference(m);poseAt(m,60.0,PHI);mesh(m,"LOCAL_M03",0.03);int ne=m.component("comp1").mesh("mesh1").getNumElem();double q=m.component("comp1").mesh("mesh1").getMinQuality();
      steelOff(m);ballOff(m);poseAt(m,60.0,PHI);double[] b0=solve(m,"B0 alpha0 z60 phi scan");steelOn(m);ballOff(m);poseAt(m,60.0,PHI);double[] b1=solve(m,"B1 alpha0 z60 phi scan");
      Path pcsv=out.resolve("modelB_alpha0_z60_phi_scan.csv");try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(pcsv))){w.println("z_sphere_mm,alpha_deg,phi_deg,Fx_B2_raw_mN,Fx_hold_corr_mN,DeltaFx_ball_mN,Fx_total_corr_mN,Fy_total_corr_mN,Fz_total_corr_mN,elements,DOF,min_quality,same_mesh_verified,release_candidate,status");for(double phi:new double[]{60,70,80,90,100,110,120}){m.param().set("phi",f(phi)+"[deg]");ballOn(m);poseAt(m,60.0,phi);double[] b2=solve(m,"B2 alpha0 z60 phi="+f(phi));String[] st=m.sol().tags();int d=dof(m,st[st.length-1]);double hold=b1[0]-b0[0],ball=b2[0]-b1[0],total=b2[0]-b0[0];w.println(String.join(",",val(60),"0",val(phi),val(b2[0]),val(hold),val(ball),val(total),val(b2[1]-b0[1]),val(b2[2]-b0[2]),Integer.toString(ne),Integer.toString(d),val(q),"true",total>0?"MAGNETIC_RELEASE_CANDIDATE":"MAGNETICALLY_HELD","SUCCESS"));w.flush();log("PHI_RESULT z=60 phi="+f(phi)+" Fx_hold="+f(hold)+" DeltaFx_ball="+f(ball)+" Fx_total="+f(total)+" status=SUCCESS");}}
      log("FINISH zcsv="+zcsv+" phicsv="+pcsv);
    }finally{try{ModelUtil.remove(name);}catch(Throwable ignored){}}
  }
  public static void main(String[] a)throws Exception{
    if(a.length<2||a.length>5)throw new IllegalArgumentException("Usage: source.mph output_dir [alpha|alpha0m02|zalpha0|xscan|sparse50|m02pose|alpha20m02|alphaminus20m02|macrofirst|alpha20refine|alphaminus20sparse|alphaminus20z] [checkpoint-or-z] [phi]");Path out=Paths.get(a[1]);Files.createDirectories(out);ModelUtil.initStandalone(false);
    try{ModelUtil.showProgress(out.resolve("progress.log").toString());
      if(a.length==3&&"alpha".equalsIgnoreCase(a[2])) runAlpha(a[0],out);
      else if(a.length==4&&"alpha0m02".equalsIgnoreCase(a[2])) runAlpha0M02(a[3],out);
      else if(a.length==3&&"zalpha0".equalsIgnoreCase(a[2])) runZAlpha0(a[0],out);
      else if(a.length==3&&"release".equalsIgnoreCase(a[2])) runReleaseRefinement(a[0],out);
      else if(a.length==3&&"candidateM02".equalsIgnoreCase(a[2])) runCandidateM02(a[0],out);
      else if(a.length==3&&"xscan".equalsIgnoreCase(a[2])) runXScan(a[0],out);
      else if(a.length==3&&"sparse50".equalsIgnoreCase(a[2])) runSparseZ(a[0],out,50.0,"modelB_z50_phi_sparse.csv");
      else if(a.length==5&&"m02pose".equalsIgnoreCase(a[2])) runM02Pose(a[0],out,Double.parseDouble(a[3]),Double.parseDouble(a[4]));
      else if(a.length==5&&"alpha20m02".equalsIgnoreCase(a[2])) runM02Pose(a[0],out,Double.parseDouble(a[3]),Double.parseDouble(a[4]),20.0,"modelB_alpha20_M02_validation.csv",true);
      else if(a.length==5&&"alphaminus20m02".equalsIgnoreCase(a[2])) runM02Pose(a[0],out,Double.parseDouble(a[3]),Double.parseDouble(a[4]),-20.0,"modelB_alpha_minus20_M02_validation.csv",true);
      else if(a.length==3&&"macrofirst".equalsIgnoreCase(a[2])) runMacroFirst(a[0],out);
      else if(a.length==3&&"macrosparse".equalsIgnoreCase(a[2])) runMacroSparseReps(a[0],out);
      else if(a.length==3&&"transition".equalsIgnoreCase(a[2])) runTransitionSparse(a[0],out);
      else if(a.length==3&&"alpha20sparse".equalsIgnoreCase(a[2])) runAlpha20Sparse(a[0],out);
      else if(a.length==3&&"alpha20refine".equalsIgnoreCase(a[2])) runAlpha20Refinement(a[0],out);
      else if(a.length==3&&"alphaminus20sparse".equalsIgnoreCase(a[2])) runAlphaMinus20Sparse(a[0],out);
      else if(a.length==4&&"alphaminus20z".equalsIgnoreCase(a[2])) runAlphaMinus20Z(a[0],out,Double.parseDouble(a[3]));
      else {Path csv=out.resolve("modelB_z120_local_mesh_convergence.csv");try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){header(w);runLevel(a[0],out,"CURRENT_SOURCE_MESH",Double.NaN,w);runLevel(a[0],out,"LOCAL_M03",0.03,w);runLevel(a[0],out,"LOCAL_M02",0.02,w);}log("FINISH csv="+csv);}
    }finally{try{ModelUtil.disconnect();}catch(Throwable ignored){}}
  }
}
