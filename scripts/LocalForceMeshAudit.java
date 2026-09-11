import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 固定工况近场磁力审计：z=140 mm, phi=90 deg。
 *
 * 设计原则：
 * 1) 不修改源模型，L0/L1/L2 每档从同一源模型副本独立加载；
 * 2) 全局空气网格保持 hauto=6，磁体/钢片保留 hauto=1；
 * 3) 通过最终几何的 Box 边界选择，局部细化圆柱、钢片、气隙附近的相邻空气网格；
 * 4) 使用已验证的 Stationary + PARDISO + study.stol=1e-6；
 * 5) 磁力只用已验证的 mfnc.Forcex_force_magnet，并用临时 Global + getData() 读取。
 *
 * 用法：
 *   source.mph outdir
 */
public class LocalForceMeshAudit {
  static final Locale LOCALE = Locale.US;
  static final String FORCE_EXPR = "mfnc.Forcex_force_magnet";
  static final double Z_MM = 140.0;
  static final double PHI_DEG = 90.0;
  static final double[] HGAP_MM = new double[]{0.070, 0.047, 0.035};
  static final String[] LEVELS = new String[]{"L0","L1","L2"};

  static void log(String s) { System.out.println(s); }
  static double first(double[][][] d) {
    if (d==null || d.length==0 || d[0].length==0 || d[0][0].length==0)
      throw new IllegalStateException("Global evaluation returned no scalar data");
    return d[0][0][0];
  }
  static String safe(PropFeature e,String p) { try{return e.getString(p);}catch(Throwable t){return "<NA>";} }
  static String fmt(double v) { return String.format(LOCALE,"%.12g",v); }
  static void require(PropFeature e,String p,String v) {
    if(!e.hasProperty(p)) throw new IllegalStateException("Required property missing: "+p);
    e.set(p,v);
    if(!v.equals(e.getString(p))) throw new IllegalStateException("Property not applied: "+p);
  }
  static double eval(Model m,String e) { return m.param().evaluate(e); }

  static void cleanup(Model m) {
    for(String t:m.sol().tags()) m.sol().remove(t);
    for(String t:m.study().tags()) m.study().remove(t);
    for(String t:m.result().numerical().tags()) m.result().numerical().remove(t);
    for(String t:m.result().dataset().tags()) m.result().dataset().remove(t);
  }

  static void removeRotating(Model m) {
    ModelNode c=m.component("comp1");
    for(String t:c.common().tags()) if("RotatingDomain".equals(c.common(t).getType())) {
      c.common().remove(t); log("REMOVED_ROTATING_DOMAIN="+t);
    }
  }

  static void setPose(Model m,double phi) {
    m.param().set("z_sphere",fmt(Z_MM)+"[mm]");
    m.param().set("phi",fmt(phi)+"[deg]");
    PhysicsFeature f=m.component("comp1").physics("mfnc").feature("mfc2");
    requirePhysics(f,"e_crel_BH_RemanentFluxDensity",new String[]{"sin(alpha)","cos(alpha)*cos(phi)","-cos(alpha)*sin(phi)"});
    log("POSE z_mm="+fmt(Z_MM)+" phi_deg="+fmt(phi)+" direction_eval="+
      Arrays.toString(new double[]{eval(m,"sin(alpha)"),eval(m,"cos(alpha)*cos(phi)"),eval(m,"-cos(alpha)*sin(phi)")}));
  }
  static void require(PropFeature e,String p,String[] v) {
    if(!e.hasProperty(p)) throw new IllegalStateException("Required property missing: "+p);
    e.set(p,v); String[] got=e.getStringArray(p);
    if(got.length!=v.length) throw new IllegalStateException("Vector property length mismatch: "+p);
  }
  static void requirePhysics(PhysicsFeature e,String p,String[] v) {
    if(!e.hasProperty(p)) throw new IllegalStateException("Required physics property missing: "+p);
    e.set(p,v); String[] got=e.getStringArray(p);
    if(got.length!=v.length) throw new IllegalStateException("Physics vector property length mismatch: "+p);
  }

  static void printDomainTable(Model m) {
    ModelNode c=m.component("comp1");
    GeomSequence g=c.geom("geom1");
    int[] ne=g.getNEntities();
    log("GEOM_ENTITIES="+Arrays.toString(ne));
    for(int d=1;d<=ne[3];d++) {
      try {
        GeomMeasureFinal gm=g.measureFinal();
        gm.selection().geom("geom1",3); gm.selection().set(new int[]{d});
        double[] b=gm.getBoundingBox();
        log(String.format(LOCALE,"DOMAIN id=%d volume_mm3=%.12g bbox_mm=[%.9g,%.9g,%.9g,%.9g,%.9g,%.9g] center=[%.9g,%.9g,%.9g]",
          d,gm.getVolume(),b[0],b[1],b[2],b[3],b[4],b[5],(b[0]+b[1])/2,(b[2]+b[3])/2,(b[4]+b[5])/2));
      } catch(Throwable t) { log("DOMAIN_CHECK_FAILED id="+d+" message="+t.getMessage()); }
    }
    for(String mt:c.material().tags()) log("MATERIAL tag="+mt+" label="+c.material(mt).label()+" domains="+Arrays.toString(c.material(mt).selection().entities(3)));
    Physics p=c.physics("mfnc");
    for(String ft:p.feature().tags()) {
      PhysicsFeature f=p.feature(ft);
      log("PHYSICS tag="+ft+" type="+f.getType()+" domains="+Arrays.toString(f.selection().entities(3))+" boundaries="+Arrays.toString(f.selection().entities(2)));
    }
  }

  static String createLocalBoundarySize(Model m,String level,double hgap) {
    ModelNode c=m.component("comp1");
    MeshSequence mesh=c.mesh("mesh1");
    // Box selection is an entity selection, not a geometry partition；只选落在近场盒内的最终边界。
    String selTag="sel_nf_boundary_"+level.toLowerCase(Locale.ROOT);
    try{c.selection().remove(selTag);}catch(Throwable ignored){}
    SelectionFeature sel=c.selection().create(selTag,"Box");
    sel.set("entitydim","2");
    sel.set("xmin","-3[mm]"); sel.set("xmax","3[mm]");
    sel.set("ymin","-3[mm]"); sel.set("ymax","3[mm]");
    sel.set("zmin","-3[mm]"); sel.set("zmax","3[mm]");
    sel.set("condition","intersects");
    int[] bnd=sel.entities(2);
    if(bnd.length==0) throw new IllegalStateException("LOCAL_SELECTION_EMPTY no near-field boundaries");
    log("LOCAL_BOUNDARY_SELECTION tag="+selTag+" count="+bnd.length+" ids="+Arrays.toString(bnd));

    String sizeTag="nf_size_"+level.toLowerCase(Locale.ROOT);
    try{mesh.feature(sizeTag);mesh.feature().remove(sizeTag);}catch(Throwable ignored){}
    MeshFeature sf=mesh.feature().create(sizeTag,"Size");
    sf.selection().named(selTag);
    require(sf,"custom","on");
    require(sf,"hmax",fmt(hgap)+"[mm]");
    require(sf,"hmin",fmt(hgap/3.0)+"[mm]");
    require(sf,"hgrad","1.25");
    if(sf.hasProperty("hnarrow")) sf.set("hnarrow","1");
    try{mesh.feature().move(sizeTag,1);}catch(Throwable t){log("MESH_ORDER_WARNING="+t.getMessage());}
    log("LOCAL_SIZE_CONFIG level="+level+" hmax_mm="+fmt(hgap)+" hmin_mm="+fmt(hgap/3.0)+" selected_boundaries="+bnd.length);
    return sizeTag;
  }

  static int buildMesh(Model m,String level,double hgap) {
    ModelNode c=m.component("comp1");
    c.geom("geom1").run();
    MeshSequence mesh=c.mesh("mesh1");
    require(mesh.feature("size"),"hauto","6");
    // 现有基准模型没有 nearfieldfine；如有则重设为磁体/钢片实体，局部空气由边界 Size 控制。
    MeshFeature solid;
    try{solid=mesh.feature("nearfieldfine");}catch(Throwable t){solid=mesh.feature().create("nearfieldfine","Size");}
    solid.selection().geom("geom1",3); solid.selection().set(new int[]{2,3,4}); solid.set("hauto",1);
    try{mesh.feature().move("nearfieldfine",1);}catch(Throwable ignored){}
    createLocalBoundarySize(m,level,hgap);
    mesh.run();
    int elements=mesh.getNumElem();
    log("MESH_DONE level="+level+" elements="+elements);
    printDomainTable(m);
    return elements;
  }

  static void configure(Model m,String solTag,String studyTag) {
    SolverFeature s1=m.sol(solTag).feature("s1");
    SolverFeature d=m.sol(solTag).feature("s1").feature("dDef");
    SolverFeature fc=m.sol(solTag).feature("s1").feature("fc1");
    require(d,"linsolver","pardiso"); require(fc,"linsolver","dDef");
    StudyFeature stat=m.study(studyTag).feature("stat");
    if(!stat.hasProperty("usestol")||!stat.hasProperty("stol")) throw new IllegalStateException("Stationary step lacks stol");
    stat.set("usestol","on"); stat.set("stol","1e-6"); require(s1,"control","stat");
    log("SOLVER_EFFECTIVE study="+studyTag+" solver="+safe(d,"linsolver")+" fullycoupled_to="+safe(fc,"linsolver")+" usestol="+safe(stat,"usestol")+" stol="+safe(stat,"stol"));
  }

  static double readForce(Model m,String sol,String ds,String num,String unit) {
    try {
      m.result().dataset().create(ds,"Solution"); m.result().dataset(ds).set("solution",sol);
      m.result().numerical().create(num,"Global");
      NumericalFeature n=m.result().numerical(num); n.set("data",ds); n.set("expr",new String[]{FORCE_EXPR}); n.set("unit",new String[]{unit});
      double v=first(n.getData());
      log("FORCE_READ expr="+FORCE_EXPR+" unit="+unit+" value="+fmt(v));
      return v;
    } finally { try{m.result().numerical().remove(num);}catch(Throwable ignored){} try{m.result().dataset().remove(ds);}catch(Throwable ignored){} }
  }

  static int[] solutionSize(Model m,String sol) {
    try{return m.sol(sol).getSize();}catch(Throwable t){log("DOF_READ_FAILED="+t.getMessage());return new int[0];}
  }

  static void solveOne(Model m,Path out,String level,int elements) throws Exception {
    String study="st_audit_"+level.toLowerCase(Locale.ROOT)+"_z140_p90";
    m.study().create(study); m.study(study).create("stat","Stationary"); m.study(study).createAutoSequences("all");
    String[] sols=m.sol().tags(); String sol=sols[sols.length-1];
    configure(m,sol,study);
    Path dir=out.resolve(level); Files.createDirectories(dir); m.save(dir.resolve("configured.mph").toString());
    log("SOLVE_START level="+level+" z=140 phi=90 sol="+sol);
    m.sol(sol).runAll();
    int[] sz=solutionSize(m,sol);
    double fxN=readForce(m,sol,"ds_force_"+level,"num_force_"+level,"N");
    double fxmN=readForce(m,sol,"dsm_force_"+level,"numm_force_"+level,"mN");
    if(Math.abs(fxN*1000.0-fxmN)>Math.max(1e-9,Math.abs(fxmN)*1e-6)) throw new IllegalStateException("N/mN mismatch");
    try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(dir.resolve("result.csv")))) {
      w.println("mesh_level,z_mm,phi_deg,hgap_mm,mesh_elements,solution_size,Fx_N,Fx_mN,status");
      w.printf(LOCALE,"%s,140,90,%.8f,%d,%s,%.12g,%.12g,SUCCESS%n",level,HGAP_MM[Arrays.asList(LEVELS).indexOf(level)],elements,Arrays.toString(sz),fxN,fxmN);
    }
    log(String.format(LOCALE,"AUDIT_RESULT level=%s z=140 phi=90 hgap_mm=%.8f elements=%d size=%s Fx_N=%.12g Fx_mN=%.12g status=SUCCESS",level,HGAP_MM[Arrays.asList(LEVELS).indexOf(level)],elements,Arrays.toString(sz),fxN,fxmN));
    m.save(dir.resolve("solved.mph").toString());
  }

  public static void main(String[] args) {
    if(args.length!=2) throw new IllegalArgumentException("Usage: source.mph outdir");
    Path out=Paths.get(args[1]);
    try {
      Files.createDirectories(out);
      try(PrintWriter logFile=new PrintWriter(Files.newBufferedWriter(out.resolve("audit_console_copy.log")))) { }
      ModelUtil.initStandalone(false);
      ModelUtil.showProgress(out.resolve("progress.log").toString());
      for(int i=0;i<LEVELS.length;i++) {
        String level=LEVELS[i];
        Model m=null;
        try {
          m=ModelUtil.load("local_force_audit_"+level+"_"+System.nanoTime(),args[0]);
          log("LOAD level="+level+" source="+args[0]); cleanup(m); removeRotating(m); setPose(m,PHI_DEG);
          StaticForceValidation141142.ensureMagneticScalarReference(m);
          int elements=buildMesh(m,level,HGAP_MM[i]);
          solveOne(m,out,level,elements);
          log("LEVEL_FINISH="+level);
        } catch(Throwable e) {
          log("LEVEL_ERROR="+level+" message="+e.getMessage()); e.printStackTrace();
          try{if(m!=null)m.save(out.resolve(level+"_failed_state.mph").toString());}catch(Throwable ignored){}
        } finally { try{ModelUtil.remove("local_force_audit_"+level);}catch(Throwable ignored){} }
      }
      log("AUDIT_FINISH out="+out);
    } catch(Throwable e) { e.printStackTrace(); System.exit(1); }
    finally { try{ModelUtil.disconnect();}catch(Throwable ignored){} }
  }
}
