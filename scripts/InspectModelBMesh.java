import com.comsol.model.*;
import com.comsol.model.util.ModelUtil;
import java.util.*;

/** Read-only mesh audit for the fixed-pose Model B source. */
public class InspectModelBMesh {
  public static void main(String[] a) throws Exception {
    if (a.length != 1) throw new IllegalArgumentException("source.mph");
    ModelUtil.initStandalone(false);
    try {
      Model m=ModelUtil.load("mesh_audit_"+System.nanoTime(),a[0]);
      MeshSequence q=m.component("comp1").mesh("mesh1");
      System.out.println("MESH_TAGS="+Arrays.toString(q.feature().tags()));
      for(String t:q.feature().tags()) {
        MeshFeature f=q.feature(t);
        System.out.println("FEATURE tag="+t+" type="+f.getType());
        for(String k:new String[]{"hauto","hmax","hmin","custom","hgrad","hcurve","hnarrow"}) {
          try {System.out.println("  "+k+"="+f.getString(k));} catch(Throwable ignored) {}
        }
      }
      System.out.println("ELEMENTS="+q.getNumElem()+" MINQ="+q.getMinQuality());
      ModelUtil.remove(m.name());
    } finally { ModelUtil.disconnect(); }
  }
}
