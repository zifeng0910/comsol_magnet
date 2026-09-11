import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * gap=0.30 mm 的表面 Force Calculation 与体积力变量交叉核验。
 *
 * 重要边界：
 * 1) 只使用 COMSOL 6.3 实际可求值的体积变量；不臆造 Kelvin 力表达式。
 * 2) 永磁体的 mfnc Force Calculation 通常包含磁化/极化表面贡献，
 *    mfnc.FLtz* 仅是 J x B 洛伦兹力密度，不能在没有活动电流时冒充总力。
 * 3) 如果候选变量均不可用，输出明确的 NO_VALID_VOLUME_FORCE_VARIABLE。
 * 4) 不覆盖既有扫描 CSV 或 MPH；每个网格档使用独立模型副本。
 */
public class RunGap030SurfaceVolumeCrosscheck {
  static final Locale L=Locale.US;
  static final String FX="mfnc.Forcex_force_magnet";
  static final String[] VOLUME_CANDIDATES={
    "mfnc.FLtzx","mfnc.FLtzy","mfnc.FLtzz",
    "mfnc.ForceDensityx","mfnc.ForceDensityy","mfnc.ForceDensityz",
    "mfnc.fex","mfnc.fey","mfnc.fez",
    "mfnc.fLx","mfnc.fLy","mfnc.fLz"
  };
  static final double GAP_MM=0.30;

  static String f(double x){return String.format(L,"%.12g",x);}
  static void log(String s){System.out.println(s);}
  static double first(double[][][] x){
    if(x==null||x.length==0||x[0].length==0||x[0][0].length==0)throw new IllegalStateException("empty Global result");
    return x[0][0][0];
  }
  static double first2(double[][] x){
    if(x==null||x.length==0||x[0].length==0)throw new IllegalStateException("empty IntVolume result");
    return x[0][0];
  }
  static void clearResults(Model m){
    for(String t:m.result().numerical().tags())try{m.result().numerical().remove(t);}catch(Throwable ignored){}
    for(String t:m.result().dataset().tags())try{m.result().dataset().remove(t);}catch(Throwable ignored){}
  }
  static void clearStudiesSolutions(Model m){
    clearResults(m);
    for(String t:m.sol().tags())try{m.sol().remove(t);}catch(Throwable ignored){}
    for(String t:m.study().tags())try{m.study().remove(t);}catch(Throwable ignored){}
  }
  static boolean hasFeature(Model m,String tag){try{m.component("comp1").physics("mfnc").feature(tag);return true;}catch(Throwable e){return false;}}
  static void removeBall(Model m){
    ModelNode c=m.component("comp1");
    GeomFeature mov=c.geom("geom1").feature("mov1");
    String[] in=mov.getStringArray("input"); ArrayList<String> keep=new ArrayList<String>();
    for(String s:in)if(!"sph1".equals(s))keep.add(s);
    if(keep.size()==in.length)throw new IllegalStateException("sph1 not found in mov1 input="+Arrays.toString(in));
    mov.selection("input").set(keep.toArray(new String[0]));
    c.geom("geom1").feature().remove("sph1");
    if(hasFeature(m,"mfc2"))c.physics("mfnc").feature().remove("mfc2");
  }
  static void normalize(Model m){
    ModelNode c=m.component("comp1");
    log("NORMALIZE mat4");
    c.material("mat4").selection().set(new int[]{1});
    log("NORMALIZE mat3");
    c.material("mat3").selection().set(new int[]{3});
    log("NORMALIZE mat5");
    c.material("mat5").selection().set(new int[]{2});
    log("NORMALIZE mfc1");
    try{c.physics("mfnc").feature("mfc1").selection().set(new int[]{1});}catch(Throwable e){log("NORMALIZE_SKIPPED mfc1="+e.getMessage());}
    log("NORMALIZE mfcs1");
    try{c.physics("mfnc").feature("mfcs1").selection().set(new int[]{3});}catch(Throwable e){log("NORMALIZE_SKIPPED mfcs1="+e.getMessage());}
    log("NORMALIZE mfcs2");
    try{c.physics("mfnc").feature("mfcs2").selection().set(new int[]{2});}catch(Throwable e){log("NORMALIZE_SKIPPED mfcs2="+e.getMessage());}
    log("NORMALIZE fcal1");
    try{c.physics("mfnc").feature("fcal1").selection().set(new int[]{3});}catch(Throwable e){log("NORMALIZE_SKIPPED fcal1="+e.getMessage());}
    log("NORMALIZE init1");
    try{c.physics("mfnc").feature("init1").selection().set(new int[]{1,2,3});}catch(Throwable e){log("NORMALIZE_SKIPPED init1="+e.getMessage());}
  }
  static void reference(Model m){
    PhysicsFeature z=m.component("comp1").physics("mfnc").feature("zsp1");
    int[] p=z.selection().entities(0), b=z.selection().entities(2);
    if(p.length==0&&b.length==0){z.selection().geom("geom1",0);z.selection().set(20);log("ZSP_REFERENCE_SET point=20");}
    else log("ZSP_REFERENCE_EXISTING point="+Arrays.toString(p)+" boundary="+Arrays.toString(b));
  }
  static int cylinderDomain(Model m){
    GeomSequence g=m.component("comp1").geom("geom1");
    int nd=g.getNEntities()[3]; int match=-1; double best=Double.POSITIVE_INFINITY;
    for(int d=1;d<=nd;d++){
      GeomMeasureFinal q=m.component("comp1").measure(); q.selection().geom("geom1",3); q.selection().set(new int[]{d});
      double[] bb=q.getBoundingBox();
      // 当前模型的几何长度单位已经是 mm；getBoundingBox() 返回模型单位，不能再次乘 1000。
      double dx=(bb[1]-bb[0]), dy=(bb[3]-bb[2]), dz=(bb[5]-bb[4]);
      double score=Math.abs(dx-2.0)+Math.abs(dy-0.8)+Math.abs(dz-0.8);
      if(score<best){best=score;match=d;}
      log("DOMAIN id="+d+" bbox_model_unit="+Arrays.toString(bb)+" score="+f(score));
    }
    if(match<0||best>0.05)throw new IllegalStateException("cylinder domain could not be identified best="+best);
    log("CYLINDER_DOMAIN_CONFIRMED domain="+match+" score="+f(best)); return match;
  }
  static int mesh(Model m,double h,int dom){
    ModelNode c=m.component("comp1"); m.param().set("x_gap",f(GAP_MM)+"[mm]"); c.geom("geom1").run();
    log("STEP_NORMALIZE"); normalize(m); log("STEP_REFERENCE"); reference(m); log("STEP_MESH_FEATURE");
    MeshSequence me=c.mesh("mesh1"); me.feature("size").set("hauto",6);
    String s="qa_cross_fine"; try{me.feature().remove(s);}catch(Throwable ignored){}
    me.feature().create(s,"Size"); me.feature(s).selection().geom("geom1",2);
    me.feature(s).selection().set(new int[]{15,16,17,18,19,24});
    me.feature(s).set("custom","on"); me.feature(s).set("hmax",f(h)+"[mm]"); me.feature(s).set("hmin",f(h/3.0)+"[mm]"); me.feature(s).set("hgrad","1.3"); me.feature(s).set("hnarrow","1");
    try{me.feature().move(s,1);}catch(Throwable ignored){}
    me.run(); log("MESH level_hmax_mm="+f(h)+" elements="+me.getNumElem()+" min_quality="+f(me.getMinQuality())); return me.getNumElem();
  }
  static String solve(Model m,String suffix){
    String st="cross_stat_"+suffix+"_"+System.nanoTime(); m.study().create(st);m.study(st).create("stat","Stationary");m.study(st).createAutoSequences("all");
    String sol=m.sol().tags()[m.sol().tags().length-1]; SolverFeature s=m.sol(sol).feature("s1"),d=s.feature("dDef"),fc=s.feature("fc1");
    d.set("linsolver","pardiso");fc.set("linsolver","dDef");StudyFeature stat=m.study(st).feature("stat");stat.set("usestol","on");stat.set("stol","1e-6");s.set("control","stat");
    if(!"pardiso".equals(d.getString("linsolver"))||!"1e-6".equals(stat.getString("stol")))throw new IllegalStateException("solver readback failed");
    log("SOLVER study="+st+" sol="+sol+" PARDISO stol=1e-6");m.sol(sol).runAll();return sol;
  }
  static double global(Model m,String sol,String expr){
    String ds="ds_g_"+System.nanoTime(),n="g_"+System.nanoTime();
    try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"Global");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.set("expr",new String[]{expr});q.set("unit",new String[]{"mN"});return first(q.getData());}
    finally{try{m.result().numerical().remove(n);}catch(Throwable ignored){}try{m.result().dataset().remove(ds);}catch(Throwable ignored){}}
  }
  static String[] volume(Model m,String sol,int dom){
    for(String expr:VOLUME_CANDIDATES){
      String ds="ds_v_"+System.nanoTime(),n="v_"+System.nanoTime();
      try{
        m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"IntVolume");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.selection().geom("geom1",3);q.selection().set(new int[]{dom});q.set("expr",new String[]{expr});q.set("unit",new String[]{"N"});double v=first2(q.getReal())*1000.0;
        log("VOLUME_CANDIDATE expr="+expr+" STATUS=EVALUATED Fx_mN="+f(v));return new String[]{expr,f(v),"EVALUATED"};
      }catch(Throwable e){log("VOLUME_CANDIDATE expr="+expr+" STATUS=UNAVAILABLE message="+String.valueOf(e.getMessage()).replace('\n',' '));}
      finally{try{m.result().numerical().remove(n);}catch(Throwable ignored){}try{m.result().dataset().remove(ds);}catch(Throwable ignored){}}
    }
    return new String[]{"","","NO_VALID_VOLUME_FORCE_VARIABLE"};
  }
  public static void main(String[] args){
    if(args.length<2)throw new IllegalArgumentException("Usage: source.mph output_dir");
    Path out=Paths.get(args[1]);
    try{
      Files.createDirectories(out); ModelUtil.initStandalone(false); Path csv=out.resolve("gap030_surface_vs_volume_crosscheck.csv");
      try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){
        w.println("gap_mm,mesh_level,h_gap_mm,domain_cylinder,Fx_surface_mN,Fx_volume_mN,relative_diff,mesh_elements,DOF,volume_expression,status");
        double[] hs={0.03,0.02};
        for(double h:hs){
          Model m=ModelUtil.load("cross_"+System.nanoTime(),args[0]);
          try{
            removeBall(m);int ne=mesh(m,h,3);int dom=cylinderDomain(m);String sol=solve(m,"h"+f(h));double fs=global(m,sol,FX);String[] vv=volume(m,sol,dom);int dof=m.sol(sol).getSize()[0];String diff="";
            if("EVALUATED".equals(vv[2]))diff=f(Math.abs(fs-Double.parseDouble(vv[1]))/Math.abs(fs));
            String status="EVALUATED".equals(vv[2])?"SUCCESS":"NO_VALID_VOLUME_FORCE_VARIABLE";
            String row=String.join(",","0.30",h<0.025?"M_h002":"M_h003",f(h),Integer.toString(dom),f(fs),vv[1],diff,Integer.toString(ne),Integer.toString(dof),vv[0],status);w.println(row);w.flush();log("CROSSCHECK "+row);m.save(out.resolve("gap030_"+(h<0.025?"h002":"h003")+"_crosscheck.mph").toString());
          }finally{try{clearStudiesSolutions(m);}catch(Throwable ignored){}try{ModelUtil.remove(m.name());}catch(Throwable ignored){}}
        }
      }
      log("FINISH csv="+csv); ModelUtil.disconnect(); System.exit(0);
    }catch(Throwable e){e.printStackTrace();try{ModelUtil.disconnect();}catch(Throwable ignored){}System.exit(1);}
  }
}
