import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 从已求解的 L0/L1/L2 模型读取固定空气探针 B/H，不重新求解。 */
public class LocalFieldProbeAudit {
  static final Locale LOCALE=Locale.US;
  static double first(double[][][] d){
    if(d==null||d.length==0||d[0].length==0||d[0][0].length==0)throw new IllegalStateException("empty probe data");
    return d[0][0][0];
  }
  static String q(String s){return s.replace("\\","/");}
  public static void main(String[] a)throws Exception{
    if(a.length<2)throw new IllegalArgumentException("Usage: out.csv model1 [model2 ...]");
    Path out=Paths.get(a[0]);
    try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out))){
      w.println("model,Bprobe,point_x_mm,point_y_mm,point_z_mm,Bx_T,By_T,Bz_T,normB_T,Hx_Apm,Hy_Apm,Hz_Apm,normH_Apm,status");
      ModelUtil.initStandalone(false);
      try{
        double[][] pts=new double[][]{{-1.14,0,0},{0,0.6,0},{0,0,0.6},{2.0,0,0}};
        String[] names={"gap_mid","cylinder_plus_y_air","cylinder_plus_z_air","near_outer_air"};
        for(int k=1;k<a.length;k++){
          String path=a[k]; Model m=ModelUtil.load("probe_"+System.nanoTime(),path);
          try{
            String[] sols=m.sol().tags(); String sol=sols[sols.length-1];
            String ds="ds_probe_"+System.nanoTime(),num="num_probe_"+System.nanoTime();
            m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);
            m.result().numerical().create(num,"Interp");NumericalFeature n=m.result().numerical(num);n.set("data",ds);
            String[] expr={"mfnc.Bx","mfnc.By","mfnc.Bz","mfnc.normB","mfnc.Hx","mfnc.Hy","mfnc.Hz","mfnc.normH"};
            n.set("expr",expr);
            n.set("unit",new String[]{"T","T","T","T","A/m","A/m","A/m","A/m"});
            for(int i=0;i<pts.length;i++){
              n.set("coord",new double[][]{{pts[i][0]},{pts[i][1]},{pts[i][2]}});
              try{
                double[][][] d=n.getData(); double[] v=new double[expr.length];
                for(int j=0;j<expr.length;j++) v[j]=d[j][0][0];
                w.printf(LOCALE,"%s,%s,%.6f,%.6f,%.6f,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,SUCCESS%n",
                  q(path),names[i],pts[i][0],pts[i][1],pts[i][2],v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7]);
              }catch(Throwable e){w.printf(LOCALE,"%s,%s,%.6f,%.6f,%.6f,,,,,,,,,ERROR:%s%n",q(path),names[i],pts[i][0],pts[i][1],pts[i][2],e.getMessage().replace(',',';'));}
            }
            m.result().numerical().remove(num);m.result().dataset().remove(ds);
          }finally{ModelUtil.remove(m.tag());}
        }
      }finally{ModelUtil.disconnect();}
    }
    System.exit(0);
  }
}
