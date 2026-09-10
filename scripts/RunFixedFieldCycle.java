import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;
import java.nio.file.*;
import java.io.*;
import java.util.*;
public class RunFixedFieldCycle {
 public static void main(String[] a) { try {
  Path out=Paths.get(a[2]); Files.createDirectories(out);
  ModelUtil.initStandalone(false); ModelUtil.showProgress(out.resolve("progress.log").toString());
  Model m=ModelUtil.load("fixedfield",a[0]); double z=Double.parseDouble(a[1]);
  for(String s:m.sol().tags()) m.sol().remove(s);
  for(String s:m.study().tags()) m.study().remove(s);
  for(String s:m.result().numerical().tags())m.result().numerical().remove(s);
  for(String s:m.component("comp1").common().tags()) {
   String type=m.component("comp1").common(s).getType();
   System.out.println("COMMON "+s+" "+type);
   if(type.equals("RotatingDomain"))m.component("comp1").common().remove(s);
  }
  m.param().set("z_sphere",z+"[mm]");
  m.component("comp1").physics("mfnc").feature("mfc2").set("e_crel_BH_RemanentFluxDensity",new String[]{"sin(alpha)","cos(alpha)*cos(omega*t)","-cos(alpha)*sin(omega*t)"});
  m.component("comp1").geom("geom1").run();
  MeshSequence mesh=m.component("comp1").mesh("mesh1");
  // Preserve existing fine surface features; only the global far-field size is coarsened.
  mesh.feature("size").set("hauto",6);
  mesh.feature().create("nearfieldfine","Size");
  mesh.feature("nearfieldfine").selection().geom("geom1",3);
  // 机器人钢片、圆柱磁铁、移动磁球三个实体都必须使用极细网格。
  // 旧版本遗漏域4（移动磁球），导致球面 Maxwell 应力和近场力积分受粗网格污染。
  mesh.feature("nearfieldfine").selection().set(new int[]{2,3,4});
  mesh.feature("nearfieldfine").set("hauto",1);
  mesh.feature().move("nearfieldfine",1);
  for(String f:mesh.feature().tags())System.out.println("MESH_FEATURE "+f+" "+mesh.feature(f).getType());
  mesh.run(); System.out.println("MESH_DONE elements="+mesh.getNumElem());
  m.component("comp1").physics("mfnc").feature("mfc2").set("e_crel_BH_RemanentFluxDensity",new String[]{"sin(alpha)","cos(alpha)","0"});
  m.study().create("fieldinit");m.study("fieldinit").create("stat","Stationary");m.study("fieldinit").run();
  String initSol=m.sol().tags()[0];System.out.println("INITIAL_FIELD_SOLVED");
  m.component("comp1").physics("mfnc").feature("mfc2").set("e_crel_BH_RemanentFluxDensity",new String[]{"sin(alpha)","cos(alpha)*cos(omega*t)","-cos(alpha)*sin(omega*t)"});
  m.study().create("fixedcycle");m.study("fixedcycle").create("time","Transient");
  m.study("fixedcycle").feature("time").set("useinitsol",true);
  m.study("fixedcycle").feature("time").set("initmethod","sol");
  m.study("fixedcycle").feature("time").set("initstudy","fieldinit");
  m.study("fixedcycle").feature("time").set("tlist","range(0,T_cycle/36,T_cycle)");
  m.study("fixedcycle").createAutoSequences("all");
  String sol=m.sol().tags()[m.sol().tags().length-1];
  m.sol(sol).feature("v1").set("initmethod","sol");m.sol(sol).feature("v1").set("initsol",initSol);
  for(String f:m.sol(sol).feature().tags())if(m.sol(sol).feature(f).getType().equals("Time"))System.out.println("DEFAULT_RTOL="+m.sol(sol).feature(f).getString("rtol"));
  m.save(out.resolve("configured.mph").toString());System.out.println("CONFIGURED_SAVED");
  m.sol(sol).runAll();System.out.println("SOLVED");
  m.result().dataset().create("fixeddata","Solution");m.result().dataset("fixeddata").set("solution",sol);
  m.result().numerical().create("force_read","Global");
  m.result().numerical("force_read").set("data","fixeddata");
  m.result().numerical("force_read").set("expr",new String[]{"mfnc.Forcex_force_magnet","t"});
  m.result().numerical("force_read").set("unit",new String[]{"mN","s"});
  double[][][] d=m.result().numerical("force_read").getData();double period=m.param().evaluate("T_cycle");
  double max=-Double.MAX_VALUE,min=Double.MAX_VALUE;
  try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out.resolve("cycle.csv")))) {
   w.println("z_mm,angle_deg,Fx_mN");
   for(int i=0;i<d[0].length;i++){double force=d[0][i][0];max=Math.max(max,force);min=Math.min(min,force);String line=String.format(Locale.US,"%.1f,%.8f,%.12g",z,360*d[1][i][0]/period,force);w.println(line);System.out.println(line);}
  }
  m.save(out.resolve("solved.mph").toString());System.out.println("SUCCESS min="+min+" max="+max);System.exit(0);
 }catch(Throwable e){e.printStackTrace();System.exit(1);} }
}
