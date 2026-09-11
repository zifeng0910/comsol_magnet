import com.comsol.model.*;
import com.comsol.model.physics.*;
import com.comsol.model.util.ModelUtil;
import java.util.*;

/**
 * 只读审计 Force Calculation 的公开配置。
 *
 * 重要：COMSOL 6.3 Java API 不保证把 Force Calculation 自动生成的
 * Maxwell 应力展开式作为 Model/Component 变量集合暴露出来。本程序
 * 因此只打印可实际读取的配置，不猜测应力表达式，也不求解或修改 MPH。
 */
public class ForceCalculationAudit {
  static void p(String s) { System.out.println(s); }

  static void dumpFeature(PhysicsFeature f) {
    p("FCAL tag=" + f.tag() + " type=" + f.getType()
      + " dom=" + Arrays.toString(f.selection().entities(3))
      + " bnd=" + Arrays.toString(f.selection().entities(2)));
    for (String q : f.properties()) {
      try {
        p("FCAL_PROPERTY " + q + "=" + Arrays.toString(f.getStringArray(q)));
      } catch (Throwable ignoredArray) {
        try { p("FCAL_PROPERTY " + q + "=" + f.getString(q)); }
        catch (Throwable ignoredScalar) { }
      }
    }
  }

  static void dumpExpressions(String scope, Expr e) {
    try {
      for (String name : e.varnames()) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("force") || lower.contains("stress")
            || lower.contains("maxwell") || lower.contains("fcal")) {
          String value;
          try { value = e.get(name); }
          catch (Throwable t) { value = "<READ_ERROR>"; }
          p("EXPR scope=" + scope + " name=" + name + " value=" + value);
        }
      }
    } catch (Throwable t) {
      p("EXPR_SCOPE_ERROR scope=" + scope + " message=" + t.getMessage());
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Usage: model.mph");
    ModelUtil.initStandalone(false);
    try {
      Model model = ModelUtil.load("fcal_audit_" + System.nanoTime(), args[0]);
      p("MODEL=" + args[0]);
      p("MODEL_VARIABLE_COLLECTIONS=" + Arrays.toString(model.variable().tags()));
      for (String tag : model.variable().tags()) dumpExpressions("model:" + tag, model.variable(tag));

      for (String componentTag : model.component().tags()) {
        ModelNode component = model.component(componentTag);
        p("COMPONENT=" + componentTag);
        try {
          p("COMP_VARIABLE_COLLECTIONS=" + Arrays.toString(component.variable().tags()));
          for (String tag : component.variable().tags()) {
            dumpExpressions("component:" + componentTag + ":" + tag, component.variable(tag));
          }
        } catch (Throwable t) {
          p("COMP_VARIABLE_ERROR=" + t.getMessage());
        }

        Physics physics = component.physics("mfnc");
        for (String tag : physics.feature().tags()) {
          PhysicsFeature feature = physics.feature(tag);
          if ("ForceCalculation".equals(feature.getType())
              || tag.toLowerCase(Locale.ROOT).contains("fcal")) {
            dumpFeature(feature);
          }
        }
      }
      p("ALL_RESULT_NUMERICAL=" + Arrays.toString(model.result().numerical().tags()));
      p("ALL_RESULT_DATASET=" + Arrays.toString(model.result().dataset().tags()));
    } finally {
      ModelUtil.disconnect();
    }
    System.exit(0);
  }
}
