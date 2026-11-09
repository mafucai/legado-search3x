import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

// 把 Kotlin 里算好的断言位图读出来打印（绕过本机 Kotlin 后端的 println codegen 问题）
public class RunCheck {
    static final String[] NAMES = {
        "默认 27 -> 搜索并发 27",
        "默认 27 相对旧口径 9 恰好 3 倍",
        "用户设 9  -> 只用 9（尊重用户，不强行提速）",
        "用户设 16 -> 用 16",
        "用户设 999 -> 封顶 27（不无限放大）",
        "旧口径仍封顶 9（原行为不变）",
        "所有档位 新并发 >= 旧并发（不会变慢）",
        "MAX_THREAD 仍是 9（下载/解析保护闸未被误伤）",
    };

    static int field(Object obj, Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(obj);
    }

    public static void main(String[] args) throws Exception {
        URL[] urls = { new File("/tmp/vout/").toURI().toURL() };
        URLClassLoader cl = new URLClassLoader(urls, RunCheck.class.getClassLoader());
        Class<?> kt = cl.loadClass("Verify_concurrencyKt");
        kt.getMethod("main").invoke(null);

        Class<?> resCls = cl.loadClass("RESULT");
        Object res = resCls.getField("INSTANCE").get(null);   // Kotlin object 单例
        int bits = field(res, resCls, "bits");
        int total = field(res, resCls, "total");

        System.out.println("[并发口径验证] 共 " + total + " 条断言");
        int fail = 0;
        for (int i = 0; i < total; i++) {
            boolean ok = ((bits >> i) & 1) == 1;
            System.out.println((ok ? "  PASS  " : "  FAIL  ") + NAMES[i]);
            if (!ok) fail++;
        }
        System.out.println();
        System.out.println(fail == 0 ? "==> ALL PASS" : "==> " + fail + " FAILED");
        System.exit(fail == 0 ? 0 : 1);
    }
}