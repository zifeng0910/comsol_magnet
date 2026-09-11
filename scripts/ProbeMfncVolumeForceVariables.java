import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;
import java.util.*;

/**
 * 仅用于确认 COMSOL 6.3 当前解中是否存在可在域 3 上积分的 mfnc 体积力变量。
 * 不修改模型、不保存模型、不把候选变量当作已确认物理量。
 */
public class ProbeMfncVolumeForceVariables {
  static double first(double[][] a) {
    if (a == null || a.length == 0 || a[0].length == 0) throw new IllegalStateException("empty");
    return a[0][0];
  }
  static void test(Model m, String sol, String expr) {
    String ds="ds_probe_"+System.nanoTime(), n="iv_probe_"+System.nanoTime();
    try {
      m.result().dataset().create(ds,"Solution");
      m.result().dataset(ds).set("solution",sol);
      m.result().numerical().create(n,"IntVolume");
      NumericalFeature q=m.result().numerical(n);
      q.set("data",ds);
      q.selection().geom("geom1",3);
      q.selection().set(new int[]{3});
      q.set("expr",new String[]{expr});
      q.set("unit",new String[]{"N"});
      double[][] v=q.getReal();
      System.out.println("CANDIDATE="+expr+" STATUS=EVALUATED VALUE_N="+first(v));
    } catch(Throwable e) {
      System.out.println("CANDIDATE="+expr+" STATUS=UNAVAILABLE MESSAGE="+String.valueOf(e.getMessage()).replace('\n',' '));
    } finally {
      try{m.result().numerical().remove(n);}catch(Throwable ignored){}
      try{m.result().dataset().remove(ds);}catch(Throwable ignored){}
    }
  }
  public static void main(String[] args) {
    if(args.length<1) throw new IllegalArgumentException("Usage: solved_model.mph [solution_tag]");
    try {
      ModelUtil.initStandalone(false);
      Model m=ModelUtil.load("probe_"+System.nanoTime(),args[0]);
      String sol=args.length>1?args[1]:m.sol().tags()[m.sol().tags().length-1];
      System.out.println("SOURCE="+args[0]+" SOLUTION="+sol+" COMPONENT=comp1 DOMAIN=3");
      String[] candidates={
        "mfnc.FLtzx","mfnc.FLtzy","mfnc.FLtzz",
        "mfnc.ForceDensityx","mfnc.ForceDensityy","mfnc.ForceDensityz",
        "mfnc.fex","mfnc.fey","mfnc.fez",
        "mfnc.fLx","mfnc.fLy","mfnc.fLz"
      };
      for(String e:candidates) test(m,sol,e);
      ModelUtil.disconnect(); System.exit(0);
    } catch(Throwable e) { e.printStackTrace(); try{ModelUtil.disconnect();}catch(Throwable ignored){} System.exit(1); }
  }
}
