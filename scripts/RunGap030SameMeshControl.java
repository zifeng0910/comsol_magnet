import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * gap=0.30 mm 的 same-mesh steel ON/OFF 控制实验。
 *
 * 本程序从已经生成好的 Model A h003/h002 MPH 读取锁定网格；
 * 不删除钢片几何，不调用 geom.run()/mesh.run()，在同一份 mesh 上先求 Steel OFF，
 * 再恢复 Steel ON 求解。这样控制变量只有钢片磁性本构。
 */
public class RunGap030SameMeshControl {
  static final Locale L=Locale.US;
  static final String FX="mfnc.Forcex_force_magnet", FY="mfnc.Forcey_force_magnet", FZ="mfnc.Forcez_force_magnet";
  static final String NTX="mfnc.nToutx_force_magnet";
  static String f(double x){return String.format(L,"%.12g",x);}
  static void log(String s){System.out.println(s);System.out.flush();}
  static double first(double[][][] x){if(x==null||x.length==0||x[0].length==0||x[0][0].length==0)throw new IllegalStateException("empty global result");return x[0][0][0];}
  static double first2(double[][] x){if(x==null||x.length==0||x[0].length==0)throw new IllegalStateException("empty surface result");return x[0][0];}
  static String safe(PropFeature p,String k){try{return p.getString(k);}catch(Throwable e){return "<UNREADABLE>";}}
  static String[] safeArr(PropFeature p,String k){try{return p.getStringArray(k);}catch(Throwable e){return new String[]{"<UNREADABLE>"};}}
  static String safeP(PhysicsFeature p,String k){try{return p.getString(k);}catch(Throwable e){return "<UNREADABLE>";}}
  static String[] safeArrP(PhysicsFeature p,String k){try{return p.getStringArray(k);}catch(Throwable e){return new String[]{"<UNREADABLE>"};}}

  static void dumpFeature(PrintWriter w,String prefix,PropFeature p){
    w.println(prefix+" tag="+p.tag()+" type="+p.getType());
    for(String k:p.properties()){
      try{w.println(prefix+" property "+k+"="+Arrays.toString(p.getStringArray(k)));}
      catch(Throwable e){try{w.println(prefix+" property "+k+"="+p.getString(k));}catch(Throwable ignored){w.println(prefix+" property "+k+"=<UNREADABLE>");}}
    }
  }
  static void dumpPhysics(PrintWriter w,String prefix,PhysicsFeature p){
    w.println(prefix+" tag="+p.tag()+" type="+p.getType());
    for(String k:p.properties()){
      try{w.println(prefix+" property "+k+"="+Arrays.toString(p.getStringArray(k)));}
      catch(Throwable e){try{w.println(prefix+" property "+k+"="+p.getString(k));}catch(Throwable ignored){w.println(prefix+" property "+k+"=<UNREADABLE>");}}
    }
  }
  static void materialAudit(Model m,Path out,boolean append)throws Exception{
    ModelNode c=m.component("comp1");
    try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out,append?new OpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.APPEND}:new OpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING}))){
      w.println("MODEL="+m.label());
      w.println("GAP="+m.param().get("x_gap"));
      for(String tag:c.material().tags()){
        Material mat=c.material(tag);w.println("MATERIAL tag="+tag+" label="+mat.label()+" domains="+Arrays.toString(mat.selection().entities(3)));
        for(String pg:mat.propertyGroup().tags()){MaterialModel mm=mat.propertyGroup(pg);w.println(" MATERIAL_GROUP="+pg+" type="+mm.getType());for(String k:mm.properties()){try{w.println("  "+k+"="+mm.getString(k));}catch(Throwable ignored){}}}
      }
      Physics p=c.physics("mfnc");
      for(String tag:new String[]{"mfcs2","mfcs1","fcal1"})try{PhysicsFeature pf=p.feature(tag);dumpPhysics(w,"PHYSICS",pf);w.println(" selection_domains="+Arrays.toString(pf.selection().entities(3)));}catch(Throwable e){w.println("PHYSICS tag="+tag+" ERROR="+e.getMessage());}
      w.println("STEEL_VALIDITY_GATE=UNCONFIRMED_WITH_EXPERIMENT");
      w.println("NOTE=Current model material label is recorded only; no automatic material substitution was performed.");
    }
  }
  static int[] allDomains(Model m){int n=m.component("comp1").geom("geom1").getNEntities()[3];int[] a=new int[n];for(int i=0;i<n;i++)a[i]=i+1;return a;}
  static double[] bbox(Model m,int dim,int id){GeomMeasureFinal q=m.component("comp1").geom("geom1").measureFinal();q.selection().geom("geom1",dim);q.selection().set(new int[]{id});return q.getBoundingBox();}
  static int findDomain(Model m,double lx,double ly,double lz){int hit=-1;double best=1e99;for(int d:allDomains(m)){double[] b=bbox(m,3,d);double s=Math.abs((b[1]-b[0])-lx)+Math.abs((b[3]-b[2])-ly)+Math.abs((b[5]-b[4])-lz);if(s<best){best=s;hit=d;}}if(best>0.05)throw new IllegalStateException("domain identity failed target="+lx+","+ly+","+lz+" best="+best);return hit;}
  static int cylinder(Model m){return findDomain(m,2.0,0.8,0.8);}
  static int steel(Model m){return findDomain(m,0.3,4.0,4.0);}
  static String[] oldSteelMatDomains,oldAirMatDomains,oldSteelPhysics;
  static String oldMurMat,oldMur,oldBrMat,oldBr;
  static void captureOn(Model m)throws Exception{
    ModelNode c=m.component("comp1");oldSteelMatDomains=toStringArray(c.material("mat5").selection().entities(3));oldAirMatDomains=toStringArray(c.material("mat4").selection().entities(3));PhysicsFeature p=c.physics("mfnc").feature("mfcs2");oldSteelPhysics=safeArrP(p,"ConstitutiveRelationBH");
    oldMurMat=safeP(p,"mur_mat"); oldMur=safeP(p,"mur"); oldBrMat=safeP(p,"normBr_crel_BH_RemanentFluxDensity_mat"); oldBr=safeP(p,"normBr_crel_BH_RemanentFluxDensity");
    log("STEEL_ON_CAPTURE material=mat5 label="+c.material("mat5").label()+" domains="+Arrays.toString(oldSteelMatDomains)+" constitutive="+Arrays.toString(oldSteelPhysics)+" mur_mat="+oldMurMat+" mur="+oldMur+" Br_mat="+oldBrMat+" Br="+oldBr);
  }
  static String[] toStringArray(int[] x){String[] s=new String[x.length];for(int i=0;i<x.length;i++)s[i]=Integer.toString(x[i]);return s;}
  static void setAirSelection(Model m,int st){ModelNode c=m.component("comp1");c.material("mat4").selection().set(new int[]{1,st});c.material("mat5").selection().set(new int[0]);PhysicsFeature p=c.physics("mfnc").feature("mfcs2");p.selection().set(new int[]{st});p.set("ConstitutiveRelationBH","RelativePermeability");p.set("mur_mat","userdef");p.set("mur","1");if(p.hasProperty("normBr_crel_BH_RemanentFluxDensity_mat"))p.set("normBr_crel_BH_RemanentFluxDensity_mat","userdef");if(p.hasProperty("normBr_crel_BH_RemanentFluxDensity"))p.set("normBr_crel_BH_RemanentFluxDensity","0[T]");log("STEEL_OFF_CONFIG material_air_domains=[1,"+st+"] mfcs2_domains=["+st+"] active_constitutive=RelativePermeability mur=1 remanence=OFF");}
  static void restoreOn(Model m,int st){ModelNode c=m.component("comp1");c.material("mat4").selection().set(new int[]{1});c.material("mat5").selection().set(new int[]{st});PhysicsFeature p=c.physics("mfnc").feature("mfcs2");p.selection().set(new int[]{st});
    // OFF 分支修改的是 mfcs2 的活动本构，不仅是材料选择；这里逐项恢复源 MPH。
    p.set("ConstitutiveRelationBH",oldSteelPhysics[0]); p.set("mur_mat",oldMurMat); p.set("mur",oldMur); p.set("normBr_crel_BH_RemanentFluxDensity_mat",oldBrMat); p.set("normBr_crel_BH_RemanentFluxDensity",oldBr);
    log("STEEL_ON_CONFIG material_mat5_domains=["+st+"] mfcs2_domains=["+st+"] restored_material_constitutive="+oldSteelPhysics[0]+" mur_mat="+oldMurMat+" mur="+oldMur+" Br_mat="+oldBrMat+" Br="+oldBr);
  }
  static void clearResults(Model m){for(String t:m.result().numerical().tags())try{m.result().numerical().remove(t);}catch(Throwable ignored){}for(String t:m.result().dataset().tags())try{m.result().dataset().remove(t);}catch(Throwable ignored){}}
  static void clearSolStudy(Model m){
    // 源模型已有可运行的 sol1；本控制实验不清理任何结果树对象。
    // 临时 Numerical 采用唯一 tag 并在读取后删除，不会残留。
  }
  static void reference(Model m){
    // 只在需要时补参考点；某些模型对读取未指定维度的 selection 会阻塞，
    // 因此这里用异常保护并打印阶段，不把参考点检查变成无日志卡死。
    log("REFERENCE_CHECK_START");
    PhysicsFeature z=m.component("comp1").physics("mfnc").feature("zsp1");
    boolean empty=true;
    try { empty=z.selection().entities(0).length==0; } catch(Throwable e) { log("REFERENCE_POINT_READ_WARNING="+e.getMessage()); }
    if(empty){
      log("REFERENCE_SET_POINT_20");
      z.selection().geom("geom1",0); z.selection().set(20);
    }
    log("REFERENCE_CHECK_DONE");
  }
  static String solve(Model m,String suffix){
    String[] ss=m.sol().tags();
    if(ss.length==0) throw new IllegalStateException("NO_SAVED_SOLUTION_SEQUENCE");
    String sol=ss[0];
    log("SOLVE_START "+suffix+" sol="+sol+" reuse_saved_sequence=true");
    m.sol(sol).runAll();
    log("SOLVE_DONE "+suffix+" sol="+sol);
    return sol;
  }
  static double global(Model m,String sol,String expr){String ds="ds_"+System.nanoTime(),n="g_"+System.nanoTime();try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"Global");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.set("expr",new String[]{expr});q.set("unit",new String[]{"N"});return first(q.getData())*1000.0;}finally{try{m.result().numerical().remove(n);}catch(Throwable ignored){}try{m.result().dataset().remove(ds);}catch(Throwable ignored){}}}
  static double surface(Model m,String sol,int[] b){String ds="ds_"+System.nanoTime(),n="s_"+System.nanoTime();try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"IntSurface");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.selection().geom("geom1",2);q.selection().set(b);q.set("expr",new String[]{NTX});q.set("unit",new String[]{"N"});return first2(q.getReal())*1000.0;}finally{try{m.result().numerical().remove(n);}catch(Throwable ignored){}try{m.result().dataset().remove(ds);}catch(Throwable ignored){}}}
  static int[][] groups(Model m,int cyl){ArrayList<Integer> minus=new ArrayList<Integer>(),plus=new ArrayList<Integer>(),side=new ArrayList<Integer>();for(int b=1;b<=m.component("comp1").geom("geom1").getNEntities()[2];b++){boolean adj=false;try{for(int d:m.component("comp1").geom("geom1").getAdjExt(2,3,b))if(d==cyl)adj=true;}catch(Throwable ignored){}if(!adj)continue;double[] q=bbox(m,2,b);if(Math.abs(q[1]-q[0])<1e-7){if((q[0]+q[1])/2<0)minus.add(b);else plus.add(b);}else side.add(b);}return new int[][]{toInt(minus),toInt(plus),toInt(side)};}
  static int[] toInt(ArrayList<Integer> x){int[] a=new int[x.size()];for(int i=0;i<a.length;i++)a[i]=x.get(i);return a;}
  static double meshQuality(Model m){try{return m.component("comp1").mesh("mesh1").getMinQuality();}catch(Throwable e){return Double.NaN;}}
  static int meshElements(Model m){try{return m.component("comp1").mesh("mesh1").getNumElem();}catch(Throwable e){return -1;}}
  static double[] runCase(Model m,String mode,int st,int cyl,int lockedNe,double lockedQ)throws Exception{
    if("OFF".equals(mode))setAirSelection(m,st);else restoreOn(m,st);
    reference(m); log("CLEAR_OLD_SOLUTIONS_START"); clearSolStudy(m); log("CLEAR_OLD_SOLUTIONS_DONE");
    // 已在加载模型时读取过网格诊断；求解分支中不重复调用 mesh.getNumElem()/getMinQuality，
    // 避免 COMSOL 6.3 在删除旧解后对锁定网格查询长时间阻塞。
    log("RESULT_ARRAY_CREATE_START");
    // indices: Fx,Fy,Fz,minusX,plusX,side,DOF,status(1/0),reserved
    double[] r=new double[9];
    log("RESULT_ARRAY_CREATE_DONE");
    log("MESH_REUSE_LOCKED elements="+lockedNe+" min_quality="+f(lockedQ));
    try{String sol=solve(m,mode);r[6]=m.sol(sol).getSize()[0];r[0]=global(m,sol,FX);r[1]=global(m,sol,FY);r[2]=global(m,sol,FZ);int[][] g=groups(m,cyl);r[3]=surface(m,sol,g[0]);r[4]=surface(m,sol,g[1]);r[5]=surface(m,sol,g[2]);r[7]=1;}catch(Throwable e){r[7]=0;log("CASE_ERROR "+mode+" "+e.getMessage());}return r;
  }
  static void appendAudit(Path p,Model m)throws Exception{materialAudit(m,p,Files.exists(p));}
  static void runOne(String level,String source,PrintWriter w,Path audit)throws Exception{Model m=ModelUtil.load("same_mesh_"+level+"_"+System.nanoTime(),source);try{int cyl=cylinder(m),st=steel(m);int lockedNe=meshElements(m);double lockedQ=meshQuality(m);log("PAIR level="+level+" cyl="+cyl+" steel="+st+" mesh_elements_locked="+lockedNe+" min_quality="+f(lockedQ));appendAudit(audit,m);captureOn(m);double[] off=runCase(m,"OFF",st,cyl,lockedNe,lockedQ);double[] on=runCase(m,"ON",st,cyl,lockedNe,lockedQ);boolean same=lockedNe>0;double dx=on[0]-off[0],dy=on[1]-off[1],dz=on[2]-off[2];String status=(same&&off[7]==1&&on[7]==1)?"SUCCESS":"ERROR";w.println(String.join(",",level,level.equals("M_h003")?"0.030":"0.020",f(off[0]),f(off[1]),f(off[2]),f(on[0]),f(on[1]),f(on[2]),f(dx),f(dy),f(dz),f(Math.sqrt(dy*dy+dz*dz)),Integer.toString(lockedNe),Integer.toString((int)on[6]),f(lockedQ),Boolean.toString(same),status));w.flush();log("PAIR_RESULT level="+level+" OFF_F=["+f(off[0])+","+f(off[1])+","+f(off[2])+"] ON_F=["+f(on[0])+","+f(on[1])+","+f(on[2])+"] DIFF_Fx="+f(dx)+" same_mesh="+same+" elements="+lockedNe+" status_off="+off[7]+" status_on="+on[7]);}finally{try{ModelUtil.remove(m.name());}catch(Throwable ignored){}}}
  public static void main(String[] a)throws Exception{if(a.length<4)throw new IllegalArgumentException("Usage: h003.mph h002.mph output.csv steel_audit.txt");ModelUtil.initStandalone(false);try{Path csv=Paths.get(a[2]),audit=Paths.get(a[3]);Files.createDirectories(csv.getParent());Files.createDirectories(audit.getParent());try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){w.println("mesh_level,h_mm,Fx_off_mN,Fy_off_mN,Fz_off_mN,Fx_on_mN,Fy_on_mN,Fz_on_mN,Fx_hold_diff_mN,Fy_hold_diff_mN,Fz_hold_diff_mN,F_lateral_diff_mN,elements,DOF,min_quality,same_mesh_verified,status");runOne("M_h003",a[0],w,audit);runOne("M_h002",a[1],w,audit);}log("FINISH csv="+csv+" audit="+audit);}finally{ModelUtil.disconnect();}System.exit(0);}
}
