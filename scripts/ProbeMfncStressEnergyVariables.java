import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;
import java.util.*;

/** Probe only: verify GUI/documentation candidate stress and energy expressions. */
public class ProbeMfncStressEnergyVariables {
  static double val(double[][] x){if(x==null||x.length==0||x[0].length==0)throw new IllegalStateException("empty");return x[0][0];}
  static void surface(Model m,String sol,String e){
    String ds="ds_s_"+System.nanoTime(),n="s_"+System.nanoTime();
    try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"IntSurface");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.selection().geom("geom1",2);q.selection().set(new int[]{15});q.set("expr",new String[]{e});q.set("unit",new String[]{"N"});System.out.println("SURFACE "+e+" STATUS=EVALUATED VALUE_N="+val(q.getReal()));}
    catch(Throwable x){System.out.println("SURFACE "+e+" STATUS=UNAVAILABLE MESSAGE="+String.valueOf(x.getMessage()).replace('\n',' '));}
    finally{try{m.result().numerical().remove(n);}catch(Throwable x){}try{m.result().dataset().remove(ds);}catch(Throwable x){}}
  }
  static void volume(Model m,String sol,String e){
    String ds="ds_v_"+System.nanoTime(),n="v_"+System.nanoTime();
    try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"IntVolume");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.selection().geom("geom1",3);q.selection().set(new int[]{3});q.set("expr",new String[]{e});q.set("unit",new String[]{"J/m^3"});System.out.println("VOLUME "+e+" STATUS=EVALUATED VALUE="+val(q.getReal()));}
    catch(Throwable x){System.out.println("VOLUME "+e+" STATUS=UNAVAILABLE MESSAGE="+String.valueOf(x.getMessage()).replace('\n',' '));}
    finally{try{m.result().numerical().remove(n);}catch(Throwable x){}try{m.result().dataset().remove(ds);}catch(Throwable x){}}
  }
  public static void main(String[] a){
    if(a.length<1)throw new IllegalArgumentException("Usage: solved.mph [solution]");
    try{ModelUtil.initStandalone(false);Model m=ModelUtil.load("probe_"+System.nanoTime(),a[0]);String sol=a.length>1?a[1]:m.sol().tags()[m.sol().tags().length-1];System.out.println("SOURCE="+a[0]+" SOLUTION="+sol);
      String[] s={"mfnc.nToutx_force_magnet","mfnc.unTmx","mfnc.dnTmx","mfnc.Tmx","mfnc.nTmx","mfnc.unTmy","mfnc.dnTmy","mfnc.unTmz","mfnc.dnTmz"};
      for(String e:s)surface(m,sol,e);
      String[] v={"mfnc.Wm","mfnc.Wco","mfnc.Wmco","mfnc.wm","mfnc.wmco","mfnc.uh","mfnc.Wmag","mfnc.emag","mfnc.u","0.5*(mfnc.Bx*mfnc.Hx+mfnc.By*mfnc.Hy+mfnc.Bz*mfnc.Hz)"};
      for(String e:v)volume(m,sol,e);
      ModelUtil.disconnect();System.exit(0);
    }catch(Throwable e){e.printStackTrace();try{ModelUtil.disconnect();}catch(Throwable x){}System.exit(1);}
  }
}
