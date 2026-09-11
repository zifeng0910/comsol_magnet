import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 诊断副本：球和钢片均空气化，只保留中心圆柱磁铁，检查内置力的自力尺度。 */
public class SelfForceAudit {
  static final Locale L=Locale.US;
  static double first(double[][][] d){return d[0][0][0];}
  static String f(double x){return String.format(L,"%.12g",x);}
  static void log(String s){System.out.println(s);}
  static void mainConfig(Model m){
    ModelNode c=m.component("comp1");
    c.material("mat3").selection().set(new int[]{3});
    c.material("mat4").selection().set(new int[]{1,2,4});
    c.material("mat5").selection().set(new int[0]);
    Physics p=c.physics("mfnc");
    p.feature("mfc1").selection().set(new int[]{1,2});
    p.feature("mfc2").selection().set(new int[]{4});
    p.feature("mfcs1").selection().set(new int[]{3});
    p.feature("mfcs2").selection().set(new int[0]);
    p.feature("fcal1").selection().set(new int[]{3});
    PhysicsFeature ball=p.feature("mfc2");
    ball.set("ConstitutiveRelationBH","RelativePermeability");
    ball.set("mur_mat","userdef"); ball.set("mur","1");
    if(ball.hasProperty("normBr_crel_BH_RemanentFluxDensity_mat"))ball.set("normBr_crel_BH_RemanentFluxDensity_mat","userdef");
    if(ball.hasProperty("normBr_crel_BH_RemanentFluxDensity"))ball.set("normBr_crel_BH_RemanentFluxDensity","0[T]");
    ball.set("e_crel_BH_RemanentFluxDensity",new String[]{"1","0","0"});
    log("SELF_CONFIG cylinder_material_domain=[3] air_domains=[1,2,4] steel_physics=empty ball_constitutive=RelativePermeability mur=1 remanence=OFF force_domain=[3]");
  }
  static double readForce(Model m,String sol,String suffix,String unit){
    String ds="ds_self_"+suffix+System.nanoTime(),num="num_self_"+suffix+System.nanoTime();
    try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(num,"Global");NumericalFeature n=m.result().numerical(num);n.set("data",ds);n.set("expr",new String[]{"mfnc.Forcex_force_magnet"});n.set("unit",new String[]{unit});return first(n.getData());}
    finally{try{m.result().numerical().remove(num);}catch(Throwable ignored){}try{m.result().dataset().remove(ds);}catch(Throwable ignored){}}
  }
  public static void main(String[] a)throws Exception{
    if(a.length!=2)throw new IllegalArgumentException("Usage: source.mph outdir");
    Path out=Paths.get(a[1]);Files.createDirectories(out);ModelUtil.initStandalone(false);
    try{Model m=ModelUtil.load("self_force_"+System.nanoTime(),a[0]);
      try{
        LocalForceMeshAudit.cleanup(m);LocalForceMeshAudit.removeRotating(m);m.param().set("z_sphere","140[mm]");LocalForceMeshAudit.setPose(m,90);StaticForceValidation141142.ensureMagneticScalarReference(m);mainConfig(m);
        int el=LocalForceMeshAudit.buildMesh(m,"L1",0.047);
        String st="st_self_l1";m.study().create(st);m.study(st).create("stat","Stationary");m.study(st).createAutoSequences("all");String[] ss=m.sol().tags();String sol=ss[ss.length-1];LocalForceMeshAudit.configure(m,sol,st);m.save(out.resolve("configured.mph").toString());m.sol(sol).runAll();double n=readForce(m,sol,"N","N"),mn=readForce(m,sol,"mN","mN");
        try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out.resolve("self_force_result.csv")))){w.println("case,z_mm,phi_deg,mesh_elements,Fx_N,Fx_mN,status");w.printf(L,"AIR_ONLY_SYMMETRIC,140,90,%d,%.12g,%.12g,SUCCESS%n",el,n,mn);}
        m.save(out.resolve("solved.mph").toString());log(String.format(L,"SELF_FORCE_RESULT elements=%d Fx_N=%.12g Fx_mN=%.12g",el,n,mn));
      }finally{ModelUtil.remove(m.tag());}
    }finally{ModelUtil.disconnect();}
    System.exit(0);
  }
}
