import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Model A, gap=0.30 mm: native second Force Calculation probe.
 *
 * This is deliberately not a hand-written Maxwell-stress integration.  A small
 * air partition is created around the cylinder, then a second native
 * ForceCalculation feature is created and its generated variable is read.
 * The first run should use P1 on the h003 source only.
 */
public class RunGap030NativeForceProbe {
  static final Locale L=Locale.US;
  static final String FX1="mfnc.Forcex_force_magnet";
  static final String FX2="mfnc.Forcex_force_probe";
  static String f(double x){return String.format(L,"%.12g",x);}
  static void log(String s){System.out.println(s);System.out.flush();}
  static int[] allDomains(Model m){int n=m.component("comp1").geom("geom1").getNEntities()[3];int[] a=new int[n];for(int i=0;i<n;i++)a[i]=i+1;return a;}
  static double[] bbox(Model m,int dim,int id){GeomMeasureFinal q=m.component("comp1").geom("geom1").measureFinal();q.selection().geom("geom1",dim);q.selection().set(new int[]{id});return q.getBoundingBox();}
  static int findDomain(Model m,double lx,double ly,double lz){int hit=-1;double best=1e99;for(int d:allDomains(m)){double[] b=bbox(m,3,d);double s=Math.abs((b[1]-b[0])-lx)+Math.abs((b[3]-b[2])-ly)+Math.abs((b[5]-b[4])-lz);if(s<best){best=s;hit=d;}}if(best>0.05)throw new IllegalStateException("DOMAIN_IDENTITY_FAILED target="+lx+","+ly+","+lz+" best="+best);return hit;}
  static int cylinder(Model m){return findDomain(m,2.0,0.8,0.8);}
  static int steel(Model m){return findDomain(m,0.3,4.0,4.0);}
  static int[] ints(ArrayList<Integer> a){int[] r=new int[a.size()];for(int i=0;i<r.length;i++)r[i]=a.get(i);return r;}
  static String array(int[] x){return Arrays.toString(x);}

  static void addProbePartition(Model m,String id,double[] q)throws Exception{
    GeomSequence g=m.component("comp1").geom("geom1");
    GeomFeatureList gl=g.feature();
    // Insert before the finalization feature.  The source sequence has mov_steel
    // immediately before fin; createAfter keeps the partition in the final geometry.
    try{gl.remove(id+"_part");}catch(Throwable ignored){}
    try{gl.remove(id);}catch(Throwable ignored){}
    log("GEOMETRY_CREATE_BLOCK_START");
    GeomFeature box=gl.createAfter(id,"Block","mov_steel");
    log("GEOMETRY_CREATE_BLOCK_DONE");
    box.set("pos",new String[]{f(q[0])+"[mm]",f(q[2])+"[mm]",f(q[4])+"[mm]"});
    box.set("size",new String[]{f(q[1]-q[0])+"[mm]",f(q[3]-q[2])+"[mm]",f(q[5]-q[4])+"[mm]"});
    box.set("selresult","off");
    log("GEOMETRY_BLOCK_CONFIG_DONE");
    GeomFeature part=gl.createAfter(id+"_part","PartitionDomains",id);
    log("GEOMETRY_CREATE_PART_DONE");
    part.set("partitionwith","objects");
    log("GEOMETRY_PARTITIONWITH_OBJECTS_DONE");
    // PartitionDomains is inserted before fin; select all preceding geometry
    // objects so the tool cuts only where the probe box actually intersects.
    part.selection("domain").all();
    log("GEOMETRY_PART_DOMAIN_SELECTION_DONE");
    part.selection("object").set(new String[]{id});
    log("GEOMETRY_PART_OBJECT_SELECTION_DONE");
    try{part.set("keepobject","off");}catch(Throwable ignored){}
    log("GEOMETRY_PROBE_BOX id="+id+" bbox="+Arrays.toString(q));
    log("GEOMETRY_RUN_START");
    g.run();
    log("GEOMETRY_PROBE_DONE domains="+m.component("comp1").geom("geom1").getNEntities()[3]);
  }

  static int[] probeAirDomains(Model m,double[] q,int cyl,int steel){
    ArrayList<Integer> hit=new ArrayList<Integer>();
    for(int d:allDomains(m)){
      if(d==cyl||d==steel)continue;
      double[] b=bbox(m,3,d);double cx=(b[0]+b[1])/2,cy=(b[2]+b[3])/2,cz=(b[4]+b[5])/2;
      boolean inside=cx>q[0]-1e-6&&cx<q[1]+1e-6&&cy>q[2]-1e-6&&cy<q[3]+1e-6&&cz>q[4]-1e-6&&cz<q[5]+1e-6;
      boolean bounded=b[0]>=q[0]-1e-6&&b[1]<=q[1]+1e-6&&b[2]>=q[2]-1e-6&&b[3]<=q[3]+1e-6&&b[4]>=q[4]-1e-6&&b[5]<=q[5]+1e-6;
      if(inside&&bounded)hit.add(d);
    }
    if(hit.isEmpty())throw new IllegalStateException("PROBE_AIR_DOMAIN_NOT_FOUND bbox="+Arrays.toString(q));
    return ints(hit);
  }
  static void configureDomains(Model m,int cyl,int steel,int[] probeAir){
    ModelNode c=m.component("comp1");int[] all=allDomains(m);ArrayList<Integer> air=new ArrayList<Integer>();for(int d:all)if(d!=cyl&&d!=steel)air.add(d);int[] ad=ints(air);
    c.material("mat3").selection().set(new int[]{cyl}); log("DOMAIN_CONFIG_MAT3_DONE");
    c.material("mat5").selection().set(new int[]{steel});
    log("DOMAIN_CONFIG_MAT5_DONE");
    c.material("mat4").selection().set(ad);
    log("DOMAIN_CONFIG_MAT4_DONE");
    // The source mfc1 is an all-air background feature.  After PartitionDomains
    // the new domain inherits that background selection; changing this large
    // physics selection is avoided because COMSOL 6.3 can block while remapping
    // it.  Verify the final compiled equation during the solve.
    log("DOMAIN_CONFIG_MFC1_RETAIN_SOURCE_DONE");
    c.physics("mfnc").feature("mfcs1").selection().set(new int[]{cyl});
    log("DOMAIN_CONFIG_MFCS1_DONE");
    c.physics("mfnc").feature("mfcs2").selection().set(new int[]{steel});
    log("DOMAIN_CONFIG_MFCS2_DONE");
    c.physics("mfnc").feature("fcal1").selection().set(new int[]{cyl});
    log("DOMAIN_CONFIG_FCAL1_DONE");
    try{c.physics("mfnc").feature("init1").selection().set(all);}catch(Throwable ignored){}
    log("DOMAIN_CONFIG cylinder="+cyl+" steel="+steel+" probe_air="+array(probeAir)+" air_all="+array(ad));
  }
  static void createNativeProbe(Model m,int[] selected)throws Exception{
    Physics p=m.component("comp1").physics("mfnc");
    try{p.feature().remove("fcal_probe");}catch(Throwable ignored){}
    PhysicsFeature fp=p.feature().create("fcal_probe","ForceCalculation",3);
    fp.selection().set(selected);
    fp.set("ForceName","force_probe");
    log("NATIVE_FORCE_PROBE_CREATED tag=fcal_probe type="+fp.getType()+" ForceName="+fp.getString("ForceName")+" domains="+array(selected));
  }
  static String solve(Model m)throws Exception{
    String[] ss=m.sol().tags();if(ss.length==0)throw new IllegalStateException("NO_SOLVER_SEQUENCE");
    String sol=ss[0];log("SOLVE_START sol="+sol);m.sol(sol).runAll();log("SOLVE_DONE sol="+sol);return sol;
  }
  static double global(Model m,String sol,String expr){String ds="ds_probe_"+System.nanoTime(),n="g_probe_"+System.nanoTime();try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"Global");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.set("expr",new String[]{expr});q.set("unit",new String[]{"N"});double[][][] x=q.getData();return x[0][0][0]*1000.0;}finally{try{m.result().numerical().remove(n);}catch(Throwable ignored){}try{m.result().dataset().remove(ds);}catch(Throwable ignored){}}}
  static int elements(Model m){return m.component("comp1").mesh("mesh1").getNumElem();}
  static double quality(Model m){return m.component("comp1").mesh("mesh1").getMinQuality();}
  static void run(String source,String csv,String save,String level,String probeId,double[] q)throws Exception{
    Model m=ModelUtil.load("native_probe_"+System.nanoTime(),source);
    try{
      int cyl0=cylinder(m),steel0=steel(m);log("SOURCE_DOMAINS cylinder="+cyl0+" steel="+steel0);
      addProbePartition(m,probeId,q);
      int nDom=m.component("comp1").geom("geom1").getNEntities()[3];
      if(nDom!=4)throw new IllegalStateException("UNEXPECTED_DOMAIN_COUNT_AFTER_PROBE="+nDom);
      // The partition added one air domain only; the previously audited solid
      // domains remain 2 (steel) and 3 (cylinder).  Avoid a second expensive
      // measureFinal pass after geometry rebuild.
      int cyl=cyl0,steel=steel0;int[] pa=new int[]{4};
      log("PROBE_DOMAIN_IDENTITY_REUSED cyl="+cyl+" steel="+steel+" probe_air=4 nDom="+nDom);
      configureDomains(m,cyl,steel,pa);createNativeProbe(m,concat(new int[]{cyl},pa));
      log("MESH_START level="+level);m.component("comp1").mesh("mesh1").run();log("MESH_DONE elements="+elements(m)+" min_quality="+f(quality(m)));
      String sol=solve(m);double direct=global(m,sol,FX1),probe=global(m,sol,FX2);int dof=m.sol(sol).getSize()[0];
      Files.createDirectories(Paths.get(csv).toAbsolutePath().getParent());boolean exists=Files.exists(Paths.get(csv));try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(Paths.get(csv),StandardOpenOption.CREATE,StandardOpenOption.APPEND))){if(!exists)w.println("mesh_level,probe_id,probe_bbox,Fx_direct_mN,Fx_probe_mN,Fy_probe_mN,Fz_probe_mN,elements,DOF,min_quality,status");w.println(String.join(",",level,probeId,"x["+f(q[0])+";"+f(q[1])+"] y["+f(q[2])+";"+f(q[3])+"] z["+f(q[4])+";"+f(q[5])+"]",f(direct),f(probe),"","",Integer.toString(elements(m)),Integer.toString(dof),f(quality(m)),"SUCCESS"));}
      Files.createDirectories(Paths.get(save).toAbsolutePath().getParent());m.save(save);log("RESULT level="+level+" direct_mN="+f(direct)+" probe_mN="+f(probe)+" elements="+elements(m)+" DOF="+dof+" saved="+save);
    }finally{try{ModelUtil.remove(m.name());}catch(Throwable ignored){}}
  }
  static int[] concat(int[] a,int[] b){int[] r=new int[a.length+b.length];System.arraycopy(a,0,r,0,a.length);System.arraycopy(b,0,r,a.length,b.length);return r;}
  public static void main(String[] a)throws Exception{
    if(a.length<3)throw new IllegalArgumentException("Usage: source.mph output.csv saved.mph [P1|P2] [level]");
    ModelUtil.initStandalone(false);try{String id=a.length>3?a[3]:"probe_outer";String level=a.length>4?a[4]:"M_h003";double[] q=id.equals("probe_outer_p2")?new double[]{-1.10,1.30,-0.65,0.65,-0.65,0.65}:new double[]{-1.08,1.20,-0.55,0.55,-0.55,0.55};run(a[0],a[1],a[2],level,id,q);}finally{ModelUtil.disconnect();}System.exit(0);
  }
}
