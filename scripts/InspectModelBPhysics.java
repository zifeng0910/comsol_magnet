import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.util.*;

/** Read-only source-model physics audit; avoids mesh/result traversal. */
public class InspectModelBPhysics {
  static void dump(PhysicsFeature p) {
    System.out.println("FEATURE tag="+p.tag()+" type="+p.getType()+" dom="+Arrays.toString(p.selection().entities(3)));
    for(String k:p.properties()) {
      try { System.out.println("  "+k+"="+Arrays.toString(p.getStringArray(k))); }
      catch(Throwable t) { try { System.out.println("  "+k+"="+p.getString(k)); } catch(Throwable ignored) {} }
    }
  }
  public static void main(String[] a) throws Exception {
    if(a.length!=1) throw new IllegalArgumentException("Usage: model.mph");
    ModelUtil.initStandalone(false);
    try { Model m=ModelUtil.load("phys_audit_"+System.nanoTime(),a[0]); Physics p=m.component("comp1").physics("mfnc");
      System.out.println("MODEL="+a[0]+" physics="+Arrays.toString(p.feature().tags()));
      for(String t:new String[]{"mfc1","mfcs1","mfcs2","mfc2","fcal1","zsp1"}) try{dump(p.feature(t));}catch(Throwable e){System.out.println("FEATURE tag="+t+" ERROR="+e.getMessage());}
      System.out.println("MATERIALS="+Arrays.toString(m.component("comp1").material().tags()));
      for(String t:m.component("comp1").material().tags()) { Material x=m.component("comp1").material(t); System.out.println("MATERIAL tag="+t+" label="+x.label()+" domains="+Arrays.toString(x.selection().entities(3))); }
    } finally { ModelUtil.disconnect(); }
  }
}
