import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;
import java.nio.file.*;
import java.util.*;

/** 固定 z=140 mm, phi=90 deg，比较外空气球半径 300/400 mm；每档独立加载源模型。 */
public class OuterAirRadiusAudit {
  static final Locale L=Locale.US;
  static String f(double x){return String.format(L,"%.12g",x);}
  static void log(String s){System.out.println(s);}
  public static void main(String[] a)throws Exception{
    if(a.length<3)throw new IllegalArgumentException("Usage: source.mph outdir radius_mm [radius_mm ...]");
    Path root=Paths.get(a[1]);Files.createDirectories(root);
    ModelUtil.initStandalone(false);
    try{
      for(int i=2;i<a.length;i++){
        double r=Double.parseDouble(a[i]); String rt="R"+a[i].replace('.','p'); Path out=root.resolve(rt);Files.createDirectories(out);
        Model m=null;
        try{
          m=ModelUtil.load("outer_audit_"+rt+"_"+System.nanoTime(),a[0]);
          LocalForceMeshAudit.cleanup(m); LocalForceMeshAudit.removeRotating(m);
          GeomFeature airSphere=m.component("comp1").geom("geom1").feature("sph_air_domain");
          if(!airSphere.hasProperty("r"))throw new IllegalStateException("sph_air_domain has no radius property");
          airSphere.set("r",f(r)+"[mm]");
          log("OUTER_RADIUS_SET radius_mm="+f(r));
          LocalForceMeshAudit.setPose(m,90.0);
          StaticForceValidation141142.ensureMagneticScalarReference(m);
          int ne=LocalForceMeshAudit.buildMesh(m,"L1",0.047);
          LocalForceMeshAudit.solveOne(m,out,"L1",ne);
          m.save(out.resolve("outer_radius_"+rt+"_final.mph").toString());
          log("OUTER_RADIUS_FINISH radius_mm="+f(r)+" elements="+ne);
        }catch(Throwable e){log("OUTER_RADIUS_ERROR radius_mm="+f(r)+" message="+e.getMessage());e.printStackTrace();}
        finally{try{if(m!=null)ModelUtil.remove(m.tag());}catch(Throwable ignored){}}
      }
    }finally{ModelUtil.disconnect();}
    System.exit(0);
  }
}
