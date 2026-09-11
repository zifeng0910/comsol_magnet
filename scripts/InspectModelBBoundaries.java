import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;
import java.util.*;

/** Read-only boundary identity audit for the fixed Model B source. */
public class InspectModelBBoundaries {
  static double[] bbox(Model m,int b){GeomMeasureFinal q=m.component("comp1").geom("geom1").measureFinal();q.selection().geom("geom1",2);q.selection().set(new int[]{b});return q.getBoundingBox();}
  static String ids(int[] x){return Arrays.toString(x);}
  public static void main(String[] a)throws Exception{
    if(a.length!=1)throw new IllegalArgumentException("source.mph"); ModelUtil.initStandalone(false);
    try{Model m=ModelUtil.load("boundary_audit_"+System.nanoTime(),a[0]);m.param().set("z_sphere","120[mm]");m.param().set("x_gap","0.30[mm]");m.component("comp1").geom("geom1").run();
      GeomSequence g=m.component("comp1").geom("geom1"); int nd=g.getNEntities()[3],nb=g.getNEntities()[2]; System.out.println("ENTITIES="+Arrays.toString(g.getNEntities()));
      for(int b=1;b<=nb;b++){ArrayList<Integer> adj=new ArrayList<Integer>();for(int d=1;d<=nd;d++)try{for(int x:g.getAdjExt(2,d,b))if(x==d){adj.add(d);break;}}catch(Throwable ignored){} double[] q=bbox(m,b); double size=(q[1]-q[0])+(q[3]-q[2])+(q[5]-q[4]); System.out.println(String.format(Locale.US,"BOUNDARY id=%d adj=%s bbox=[%.9g,%.9g,%.9g,%.9g,%.9g,%.9g] span=%.9g",b,adj,q[0],q[1],q[2],q[3],q[4],q[5],size));}
    }finally{ModelUtil.disconnect();}
  }
}
