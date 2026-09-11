import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * gap=0.30 mm 三路独立核验：
 * 1) 原 Force Calculation；2) 空气内部闭合包络面 Maxwell 应力；
 * 3) Wm(gap) 中心差分虚功法。
 *
 * 所有几何、网格、模型均在独立副本中完成，不覆盖既有扫描结果。
 */
public class RunGap030ThreeWayCrosscheck {
  static final Locale L=Locale.US;
  static final String FX="mfnc.Forcex_force_magnet";
  static final String NTX="mfnc.nToutx_force_magnet";
  static final String UTX="mfnc.unTmx", DTX="mfnc.dnTmx";
  static final double[] H={0.03,0.02};
  static final double[] MARGINS={0.20,0.40,0.80};
  static final double GAP=0.30, DG=0.005;
  static final String[] ENERGY={"mfnc.Wm"};

  static String f(double x){return String.format(L,"%.12g",x);}
  static void log(String s){System.out.println(s);}
  static double first3(double[][][] x){if(x==null||x.length==0||x[0].length==0||x[0][0].length==0)throw new IllegalStateException("empty global");return x[0][0][0];}
  static double first2(double[][] x){if(x==null||x.length==0||x[0].length==0)throw new IllegalStateException("empty numerical");return x[0][0];}
  static void clearResults(Model m){for(String t:m.result().numerical().tags())try{m.result().numerical().remove(t);}catch(Throwable e){}for(String t:m.result().dataset().tags())try{m.result().dataset().remove(t);}catch(Throwable e){}}
  static void clearStudiesSolutions(Model m){clearResults(m);for(String t:m.sol().tags())try{m.sol().remove(t);}catch(Throwable e){}for(String t:m.study().tags())try{m.study().remove(t);}catch(Throwable e){}}
  static boolean has(Model m,String tag){try{m.component("comp1").physics("mfnc").feature(tag);return true;}catch(Throwable e){return false;}}

  static void removeBall(Model m){
    ModelNode c=m.component("comp1"); GeomFeature mov=c.geom("geom1").feature("mov1");
    String[] in=mov.getStringArray("input"); ArrayList<String> keep=new ArrayList<String>();
    for(String s:in)if(!"sph1".equals(s))keep.add(s);
    if(keep.size()!=in.length){mov.selection("input").set(keep.toArray(new String[0])); c.geom("geom1").feature().remove("sph1");}
    else log("SPHERE_ALREADY_ABSENT=source_model_is_ModelA");
    if(has(m,"mfc2"))c.physics("mfnc").feature().remove("mfc2");
  }
  static void addEnvelope(Model m,double margin){
    GeomSequence g=m.component("comp1").geom("geom1");
    try{g.feature().remove("qa_env_part");}catch(Throwable e){} try{g.feature().remove("qa_env_box");}catch(Throwable e){}
    GeomFeature box=g.feature().create("qa_env_box","Block");
    double ym=0.4+margin, xmin=-1.2, xmax=1.0+margin;
    box.set("pos",new String[]{f(xmin)+"[mm]",f(-ym)+"[mm]",f(-ym)+"[mm]"});
    box.set("size",new String[]{f(xmax-xmin)+"[mm]",f(2*ym)+"[mm]",f(2*ym)+"[mm]"}); box.set("selresult","on");
    GeomFeature part=g.feature().create("qa_env_part","PartitionDomains");
    part.selection("domain").set(new String[]{"1"}); part.selection("object").set(new String[]{"qa_env_box"});
    try{part.set("keepobject","off");}catch(Throwable e){}
    log("ENVELOPE_GEOMETRY margin_mm="+f(margin)+" x=[-1.2,"+f(xmax)+"] y/z=["+f(-ym)+","+f(ym)+"]");
  }
  static int[] allDomains(Model m){int n=m.component("comp1").geom("geom1").getNEntities()[3];int[] a=new int[n];for(int i=0;i<n;i++)a[i]=i+1;return a;}
  static double[] bbox(Model m,int dim,int id){GeomMeasureFinal q=m.component("comp1").measure();q.selection().geom("geom1",dim);q.selection().set(new int[]{id});return q.getBoundingBox();}
  static int findDomain(Model m,double tx,double ty,double tz){
    int n=m.component("comp1").geom("geom1").getNEntities()[3],hit=-1;double best=1e99;
    for(int d=1;d<=n;d++){double[] b=bbox(m,3,d);double score=Math.abs((b[1]-b[0])-tx)+Math.abs((b[3]-b[2])-ty)+Math.abs((b[5]-b[4])-tz);if(score<best){best=score;hit=d;}}
    if(best>0.05)throw new IllegalStateException("domain not found target="+tx+","+ty+","+tz+" best="+best);
    return hit;
  }
  static int cylinder(Model m){int d=findDomain(m,2.0,0.8,0.8);log("CYLINDER_DOMAIN="+d+" bbox="+Arrays.toString(bbox(m,3,d)));return d;}
  static int steel(Model m){return findDomain(m,0.3,4.0,4.0);}
  static int[] minus(int[] all,int a,int b){ArrayList<Integer>x=new ArrayList<Integer>();for(int v:all)if(v!=a&&v!=b)x.add(v);int[]r=new int[x.size()];for(int i=0;i<r.length;i++)r[i]=x.get(i);return r;}
  static void normalize(Model m,int cyl,int st){
    ModelNode c=m.component("comp1");c.material("mat3").selection().set(new int[]{cyl});c.material("mat5").selection().set(new int[]{st});c.material("mat4").selection().set(minus(allDomains(m),cyl,st));
    Physics p=c.physics("mfnc");try{p.feature("mfcs1").selection().set(new int[]{cyl});}catch(Throwable e){}try{p.feature("mfcs2").selection().set(new int[]{st});}catch(Throwable e){}try{p.feature("fcal1").selection().set(new int[]{cyl});}catch(Throwable e){}try{p.feature("mfc1").selection().set(minus(allDomains(m),cyl,st));}catch(Throwable e){}try{p.feature("init1").selection().set(allDomains(m));}catch(Throwable e){}
  }
  static void reference(Model m){PhysicsFeature z=m.component("comp1").physics("mfnc").feature("zsp1");if(z.selection().entities(0).length==0&&z.selection().entities(2).length==0){z.selection().geom("geom1",0);z.selection().set(20);log("ZSP_REFERENCE_SET=20");}}
  static int[] adjacentBoundaries(Model m,int dom){GeomSequence g=m.component("comp1").geom("geom1");int nb=g.getNEntities()[2];ArrayList<Integer>x=new ArrayList<Integer>();for(int b=1;b<=nb;b++){try{for(int d:g.getAdjExt(2,dom,b))if(d==dom){x.add(b);break;}}catch(Throwable e){}}int[]r=new int[x.size()];for(int i=0;i<r.length;i++)r[i]=x.get(i);return r;}
  static int mesh(Model m,double h,int cyl,int st){
    ModelNode c=m.component("comp1");MeshSequence me=c.mesh("mesh1");/* 保留已验证 Model A 的全局网格档位；本交叉验证只新增机器人/钢片边界的绝对局部尺寸，避免把 200 mm 外空气域整体强制到极细。 */String t="qa_three_fine";try{me.feature().remove(t);}catch(Throwable e){}me.feature().create(t,"Size");me.feature(t).selection().geom("geom1",2);ArrayList<Integer>b=new ArrayList<Integer>();for(int v:adjacentBoundaries(m,cyl))b.add(v);for(int v:adjacentBoundaries(m,st))b.add(v);int[] bb=new int[b.size()];for(int i=0;i<bb.length;i++)bb[i]=b.get(i);me.feature(t).selection().set(bb);me.feature(t).set("custom","on");me.feature(t).set("hmax",f(h)+"[mm]");me.feature(t).set("hmin",f(h/3)+"[mm]");me.feature(t).set("hgrad","1.3");me.feature(t).set("hnarrow","1");try{me.feature().move(t,1);}catch(Throwable e){}log("MESH_START h="+f(h)+" selected_boundaries="+Arrays.toString(bb));me.run();log("MESH_DONE h="+f(h)+" elements="+me.getNumElem()+" minq="+f(me.getMinQuality()));return me.getNumElem();
  }
  static String solve(Model m,String suffix){String st="three_stat_"+suffix+"_"+System.nanoTime();m.study().create(st);m.study(st).create("stat","Stationary");m.study(st).createAutoSequences("all");String sol=m.sol().tags()[m.sol().tags().length-1];SolverFeature s=m.sol(sol).feature("s1"),d=s.feature("dDef"),fc=s.feature("fc1");d.set("linsolver","pardiso");fc.set("linsolver","dDef");StudyFeature stat=m.study(st).feature("stat");stat.set("usestol","on");stat.set("stol","1e-6");s.set("control","stat");m.sol(sol).runAll();return sol;}
  static double global(Model m,String sol,String e,String unit){String ds="dg_"+System.nanoTime(),n="ng_"+System.nanoTime();try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"Global");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.set("expr",new String[]{e});q.set("unit",new String[]{unit});return first3(q.getData());}finally{try{m.result().numerical().remove(n);}catch(Throwable x){}try{m.result().dataset().remove(ds);}catch(Throwable x){}}}
  static double surf(Model m,String sol,String e,int[] b){String ds="ds_"+System.nanoTime(),n="ns_"+System.nanoTime();try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"IntSurface");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.selection().geom("geom1",2);q.selection().set(b);q.set("expr",new String[]{e});q.set("unit",new String[]{"N"});return first2(q.getReal())*1000;}finally{try{m.result().numerical().remove(n);}catch(Throwable x){}try{m.result().dataset().remove(ds);}catch(Throwable x){}}}
  static double volume(Model m,String sol,String e,int[] d){String ds="dv_"+System.nanoTime(),n="nv_"+System.nanoTime();try{m.result().dataset().create(ds,"Solution");m.result().dataset(ds).set("solution",sol);m.result().numerical().create(n,"IntVolume");NumericalFeature q=m.result().numerical(n);q.set("data",ds);q.selection().geom("geom1",3);q.selection().set(d);q.set("expr",new String[]{e});q.set("unit",new String[]{"J"});return first2(q.getReal());}finally{try{m.result().numerical().remove(n);}catch(Throwable x){}try{m.result().dataset().remove(ds);}catch(Throwable x){}}}
  static double[] saveBase(Model m,String source,double h,double gap,boolean env,double margin){m.param().set("x_gap",f(gap)+"[mm]");removeBall(m);if(env)addEnvelope(m,margin);m.component("comp1").geom("geom1").run();int cyl=cylinder(m),st=steel(m);normalize(m,cyl,st);reference(m);int ne=mesh(m,h,cyl,st);String sol=solve(m,(env?"env":"direct")+h+"_"+gap);double fx=global(m,sol,FX,"mN");return new double[]{fx,ne,m.sol(sol).getSize()[0],cyl,st};}
  static int[] boxFaces(Model m,double margin){
    double ym=0.4+margin,xmin=-1.2,xmax=1.0+margin;int nb=m.component("comp1").geom("geom1").getNEntities()[2];ArrayList<Integer> ids=new ArrayList<Integer>();double tol=1e-5;
    for(int b=1;b<=nb;b++){double[] q=bbox(m,2,b);boolean x1=Math.abs(q[0]-xmin)<tol&&Math.abs(q[1]-xmin)<tol, x2=Math.abs(q[0]-xmax)<tol&&Math.abs(q[1]-xmax)<tol;boolean y1=Math.abs(q[2]+ym)<tol&&Math.abs(q[3]+ym)<tol,y2=Math.abs(q[2]-ym)<tol&&Math.abs(q[3]-ym)<tol;boolean z1=Math.abs(q[4]+ym)<tol&&Math.abs(q[5]+ym)<tol,z2=Math.abs(q[4]-ym)<tol&&Math.abs(q[5]-ym)<tol;if(x1||x2||y1||y2||z1||z2)ids.add(b);}
    int[] r=new int[ids.size()];for(int i=0;i<r.length;i++)r[i]=ids.get(i);if(r.length!=6)log("ENVELOPE_FACE_COUNT="+r.length+" ids="+Arrays.toString(r));else log("ENVELOPE_FACES="+Arrays.toString(r));return r;
  }
  static double envelope(Model m,String sol,int[] faces){double sum=0,abs=0;for(int b:faces){double u=surf(m,sol,UTX,new int[]{b}),d=surf(m,sol,DTX,new int[]{b});/* On an internal air-air partition the two one-sided fields are diagnostic outputs. We use the GUI-confirmed up-side variable consistently, rather than selecting whichever magnitude is larger. */double use=u;sum+=use;abs+=Math.abs(use);log("ENVELOPE_FACE id="+b+" unTmx_mN="+f(u)+" dnTmx_mN="+f(d)+" side_difference_mN="+f(u-d)+" used=unTmx");}log("ENVELOPE_SUM_UNTmx mN="+f(sum)+" cancellation="+f(abs/Math.abs(sum)));return sum;}
  static class Row{String s;Row(String x){s=x;}}
  public static void main(String[] a){if(a.length<2)throw new IllegalArgumentException("Usage: source.mph output_dir");Path out=Paths.get(a[1]);try{Files.createDirectories(out);ModelUtil.initStandalone(false);Path csv=out.resolve("gap030_threeway_crosscheck.csv");try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(csv))){w.println("gap_mm,mesh_level,method,envelope_margin_mm,Fx_mN,cancellation_ratio,mesh_elements,DOF,energy_minus_J,energy_center_J,energy_plus_J,energy_dgap_mm,status");
      for(double h:H){
        // direct and virtual-work models use separate fresh copies for every gap.
        Model direct=ModelUtil.load("direct_"+System.nanoTime(),a[0]);clearStudiesSolutions(direct);double[] dr=saveBase(direct,a[0],h,GAP,false,0);int cyl=(int)dr[3];int st=(int)dr[4];String sol=direct.sol().tags()[direct.sol().tags().length-1];int[] cb=adjacentBoundaries(direct,cyl);double fs=dr[0];double cs=Math.abs(surf(direct,sol,NTX,cb))/Math.abs(fs);String row=String.join(",","0.30",h<0.025?"M_h002":"M_h003","direct_surface","",f(fs),f(cs),Integer.toString((int)dr[1]),Integer.toString((int)dr[2]),"","","","","SUCCESS");w.println(row);w.flush();mSave(direct,out.resolve("direct_"+(h<0.025?"h002":"h003")+".mph"));close(direct);
        double em=0,ec=0,ep=0;int ne=0,dof=0;for(double g:new double[]{GAP-DG,GAP,GAP+DG}){Model vm=ModelUtil.load("vw_"+System.nanoTime(),a[0]);clearStudiesSolutions(vm);double[] vr=saveBase(vm,a[0],h,g,false,0);String vs=vm.sol().tags()[vm.sol().tags().length-1];double en=volume(vm,vs,"mfnc.Wm",allDomains(vm));if(Math.abs(g-GAP)<1e-9){ec=en;ne=(int)vr[1];dof=(int)vr[2];}else if(g<GAP)em=en;else ep=en;close(vm);}double fw=-(ep-em)/(2*DG*1e-3)*1000.0;String vw=String.join(",","0.30",h<0.025?"M_h002":"M_h003","virtual_work","",f(fw),"",Integer.toString(ne),Integer.toString(dof),f(em),f(ec),f(ep),f(DG),"SUCCESS");w.println(vw);w.flush();
        for(double margin:MARGINS){Model ev=ModelUtil.load("env_"+System.nanoTime(),a[0]);clearStudiesSolutions(ev);double[] er=saveBase(ev,a[0],h,GAP,true,margin);String es=ev.sol().tags()[ev.sol().tags().length-1];int[] faces=boxFaces(ev,margin);double fe=envelope(ev,es,faces);double cancel=0;String erow=String.join(",","0.30",h<0.025?"M_h002":"M_h003","envelope_surface",f(margin),f(fe),f(cancel),Integer.toString((int)er[1]),Integer.toString((int)er[2]),"","","","","SUCCESS");w.println(erow);w.flush();mSave(ev,out.resolve("envelope_"+(h<0.025?"h002_":"h003_")+f(margin)+".mph"));close(ev);}
      }
    }log("FINISH csv="+csv);ModelUtil.disconnect();System.exit(0);}catch(Throwable e){e.printStackTrace();try{ModelUtil.disconnect();}catch(Throwable x){}System.exit(1);}}
  static void mSave(Model m,Path p){try{m.save(p.toString());}catch(Throwable e){log("SAVE_ERROR="+e.getMessage());}}
  static void close(Model m){try{clearStudiesSolutions(m);}catch(Throwable e){}try{ModelUtil.remove(m.name());}catch(Throwable e){}}
}
