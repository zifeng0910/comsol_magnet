import java.lang.reflect.Method;

/**
 * Alpha 粗扫启动器。
 *
 * COMSOL 6.3 的 comsolcompile 对跨源文件默认包依赖解析不稳定；实际实现已经
 * 内联在 RunModelBLocalMeshConvergence.runAlpha() 中。本启动器保留一个可编译的
 * 反射入口，要求 RunModelBLocalMeshConvergence.class 已经在同一 classpath 中。
 */
public class RunModelBAlphaCoarse {
  public static void main(String[] args) throws Exception {
    if (args.length != 2) throw new IllegalArgumentException("Usage: source.mph output_dir");
    Class<?> c = Class.forName("RunModelBLocalMeshConvergence");
    Method m = c.getMethod("main", String[].class);
    m.invoke(null, (Object)new String[]{args[0], args[1], "alpha"});
  }
}
