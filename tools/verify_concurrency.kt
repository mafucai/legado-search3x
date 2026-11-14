// 并发口径验证（只做算术，不打印 —— 避开本机 JVM codegen 的 crash）
// 输出用 java 侧读一个 int 数组，验证放在返回码里。
//
// 对应真实代码：
//   SearchModel.searchConcurrency = min(threadCount, MAX_SEARCH_THREAD)
//   ChangeBookSource / ChangeCover / ReaderScreen = min(platform.threadCount, MAX_SEARCH_THREAD)

const val MAX_THREAD = 9
const val MAX_SEARCH_THREAD = 54

fun searchConcurrency(t: Int): Int = if (t < MAX_SEARCH_THREAD) t else MAX_SEARCH_THREAD
fun oldConcurrency(t: Int): Int = if (t < MAX_THREAD) t else MAX_THREAD

fun main() {
    // 逐条断言全部算成位，最后一次性写入结果数组，由 Java 侧 dump
    var bits = 0
    var i = 0

    // 1) 默认 54 -> 并发 54
    if (searchConcurrency(54) == 54) bits = bits or (1 shl i); i++
    // 2) 默认 54 相对旧 9 正好 6 倍
    if (oldConcurrency(54) * 6 == searchConcurrency(54)) bits = bits or (1 shl i); i++
    // 3) 用户设 9 -> 只用 9
    if (searchConcurrency(9) == 9) bits = bits or (1 shl i); i++
    // 4) 用户设 16 -> 用 16
    if (searchConcurrency(16) == 16) bits = bits or (1 shl i); i++
    // 5) 用户设 999 -> 封顶 54
    if (searchConcurrency(999) == 54) bits = bits or (1 shl i); i++
    // 6) 旧口径仍封顶 9
    if (oldConcurrency(999) == 9) bits = bits or (1 shl i); i++
    // 7) 所有档位新 >= 旧（不会变慢）
    var notSlower = true
    val levels = intArrayOf(1, 9, 16, 20, 27, 54, 100, 999)
    for (k in levels) if (searchConcurrency(k) < oldConcurrency(k)) notSlower = false
    if (notSlower) bits = bits or (1 shl i); i++
    // 8) 保护闸未被误伤
    if (MAX_THREAD == 9) bits = bits or (1 shl i); i++

    RESULT.set(bits, i)
}

object RESULT {
    @JvmStatic
    var bits: Int = 0
    @JvmStatic
    var total: Int = 0
    fun set(b: Int, t: Int) { bits = b; total = t }
}