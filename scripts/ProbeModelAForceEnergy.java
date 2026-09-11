import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;

/** 只读探针：从已经求解的 Model A 文件读取全局 Force Calculation 和全域 mfnc.Wm。 */
public class ProbeModelAForceEnergy {
  static double first(double[][] x){return x[0][0];}
  static int[] domains(Model m){int n=m.component("comp1").geom("geom1").getNEntities()[3];int[] a=new int[n];for(int i=0;i<n;i++)a[i]=i+1;return a;}
  static double eval(Model m,String sol,String type,String expr,int[] sel,String unit){
    String ds="ds_"+System.nanoTime(),tag="n_"+System.nanoTime();
    try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(tag,type);NumericalFeature q=m.result().numerical(tag);q.set("data",ds);if(!type.equals("Global")){q.selection().geom("geom1",type.equals("IntVolume")?3:2);if(sel!=null)q.selection().set(sel);}q.set("expr",new String[]{expr});q.set("unit",new String[]{unit});return first(q.getReal());}
    finally{try{m.result().numerical().remove(tag);}catch(Throwable e){}try{m.result().dataset().remove(ds);}catch(Throwable e){}}
  }
  public static void main(String[] a){if(a.length==0)throw new IllegalArgumentException("Usage: solved.mph");try{ModelUtil.initStandalone(false);Model m=ModelUtil.load("probe_"+System.nanoTime(),a[0]);String sol=m.sol().tags()[m.sol().tags().length-1];double fx=eval(m,sol,"Global","mfnc.Forcex_force_magnet",null,"N");double wm=eval(m,sol,"IntVolume","mfnc.Wm",domains(m),"J");System.out.println("FILE="+a[0]);System.out.println("SOLUTION="+sol);System.out.println("FX_N="+fx);System.out.println("FX_mN="+(fx*1000));System.out.println("WM_ALL_J="+wm);ModelUtil.disconnect();}catch(Throwable e){e.printStackTrace();try{ModelUtil.disconnect();}catch(Throwable x){}System.exit(1);}}
}
