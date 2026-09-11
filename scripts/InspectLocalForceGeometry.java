import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.util.*;

/**
 * 只读检查固定场磁力模型的几何、外空气域、材料选择和网格属性。
 * 不运行几何、不改模型、不求解。
 */
public class InspectLocalForceGeometry {
  static void p(String s) { System.out.println(s); }
  static String s(PropFeature e, String prop) {
    try { return e.getString(prop); } catch (Throwable t) { return "<NA>"; }
  }
  static String arr(PropFeature e, String prop) {
    try { return Arrays.toString(e.getStringArray(prop)); } catch (Throwable t) { return "<NA>"; }
  }
  static String s(GeomFeature e, String prop) {
    try { return e.getString(prop); } catch (Throwable t) { return "<NA>"; }
  }
  static String arr(GeomFeature e, String prop) {
    try { return Arrays.toString(e.getStringArray(prop)); } catch (Throwable t) { return "<NA>"; }
  }
  static String s(MeshFeature e, String prop) {
    try { return e.getString(prop); } catch (Throwable t) { return "<NA>"; }
  }
  static void geomFeature(ModelNode comp, String tag) {
    GeomFeature f = comp.geom("geom1").feature(tag);
    p("GEOM tag=" + tag + " type=" + f.getType()
      + " pos=" + arr(f,"pos") + " size=" + arr(f,"size")
      + " r=" + s(f,"r") + " h=" + s(f,"h")
      + " base=" + s(f,"base") + " axis=" + arr(f,"axis")
      + " selresult=" + s(f,"selresult")
      + " input=" + arr(f,"input") + " input2=" + arr(f,"input2")
      + " keepinput=" + s(f,"keepinput") + " keeptool=" + s(f,"keeptool"));
  }
  static void domainTable(ModelNode comp, int n) {
    for (int d=1; d<=n; d++) {
      try {
        // 每个域使用新的 measurement 对象；COMSOL 6.3 中对未初始化的
        // GeomMeasureFinal.selection().clear() 会触发 No_entity_dimension_specified。
        GeomMeasureFinal gm = comp.geom("geom1").measureFinal();
        gm.selection().geom("geom1",3);
        gm.selection().set(new int[]{d});
        double v=gm.getVolume(), b[]=gm.getBoundingBox();
        p(String.format(Locale.US,
          "DOMAIN id=%d volume_mm3=%.12g bbox=[%.9g,%.9g,%.9g,%.9g,%.9g,%.9g] center=[%.9g,%.9g,%.9g]",
          d,v,b[0],b[1],b[2],b[3],b[4],b[5],
          (b[0]+b[1])/2,(b[2]+b[3])/2,(b[4]+b[5])/2));
      } catch (Throwable t) { p("DOMAIN id="+d+" ERROR="+t.getMessage()); }
    }
  }
  public static void main(String[] a) throws Exception {
    if (a.length != 1) throw new IllegalArgumentException("Usage: model.mph");
    ModelUtil.initStandalone(false);
    try {
      Model m=ModelUtil.load("inspect_local_geom_"+System.nanoTime(),a[0]);
      ModelNode comp=m.component("comp1");
      p("MODEL="+a[0]);
      p("GEOM_UNIT=mm (verify from model geometry settings and dimensional values)");
      p("PARAM x_sphere="+m.param().get("x_sphere")+" z_sphere="+m.param().get("z_sphere")+" x_gap="+m.param().get("x_gap")+" h_cyl3="+m.param().get("h_cyl3"));
      p("GEOM_TAGS="+Arrays.toString(comp.geom("geom1").feature().tags()));
      for (String tag : new String[]{"sph1","cyl1","cyl2","cyl3","sph_air_domain","blk1","dif1","fin"}) {
        try { geomFeature(comp,tag); } catch (Throwable t) { p("GEOM tag="+tag+" ERROR="+t.getMessage()); }
      }
      int[] ne=comp.geom("geom1").getNEntities();
      p("N_ENTITIES="+Arrays.toString(ne));
      domainTable(comp,ne[3]);
      p("MATERIALS="+Arrays.toString(comp.material().tags()));
      for(String tag:comp.material().tags()) {
        Material mat=comp.material(tag);
        p("MATERIAL tag="+tag+" label="+mat.label()+" domains="+Arrays.toString(mat.selection().entities(3)));
      }
      p("MESH_TAGS="+Arrays.toString(comp.mesh("mesh1").feature().tags()));
      for(String tag:comp.mesh("mesh1").feature().tags()) {
        MeshFeature f=comp.mesh("mesh1").feature(tag);
        p("MESH tag="+tag+" type="+f.getType()+" domains="+Arrays.toString(f.selection().entities(3))
          +" hauto="+s(f,"hauto")+" custom="+s(f,"custom")+" hmax="+s(f,"hmax")+" hmin="+s(f,"hmin")
          +" hgrad="+s(f,"hgrad")+" hnarrow="+s(f,"hnarrow"));
      }
      p("PHYSICS_TAGS="+Arrays.toString(comp.physics().tags()));
      Physics p=comp.physics("mfnc");
      for(String tag:p.feature().tags()) {
        PhysicsFeature f=p.feature(tag);
        p("PHYSICS tag="+tag+" type="+f.getType()+" dom="+Arrays.toString(f.selection().entities(3))+" bnd="+Arrays.toString(f.selection().entities(2)));
      }
    } finally { ModelUtil.disconnect(); }
  }
}
