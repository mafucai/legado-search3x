package io.legado.app.constant

/**
 * 跨端纯常量区。androidId/appInfo/authority/时间格式等安卓面见 app 侧 AppConstAndroid.kt
 * (同包 AppConst 扩展属性, 调用处 AppConst.xxx 写法不变)。
 */
@Suppress("ConstPropertyName")
object AppConst {

    const val APP_TAG = "Legado"

    const val channelIdDownload = "channel_download"
    const val channelIdReadAloud = "channel_read_aloud"
    const val channelIdWeb = "channel_web"

    const val UA_NAME = "User-Agent"

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 注入所有 JS eval 作用域的 platform 变量值，KMP 各端按 expect/actual 取真实平台标识 (android/desktop/ios/ohos)。 */
    val JS_PLATFORM: String get() = jsPlatform()

    const val MAX_THREAD = 9

    /**
     * 搜索/换源的并发上限。
     *
     * 与 [MAX_THREAD] 分离的理由：搜索是"一源一请求、源之间互不相干"的扇出场景，
     * 单源只发 1 个请求、发完即回，瓶颈在"7168 个源排队等 9 个名额"的排队时间，
     * 不存在单源被并发压垮的问题（书源自身的 concurrentRate 仍由 ConcurrentRateLimiter 生效）。
     * 而 [MAX_THREAD]=9 是为"同源多章节/多图片同时抓"设定的保护闸，两者语义不同，不该共用。
     *
     * 取 54 = 9 × 6，目标是排队时间降到约 1/6。
     * 注意：这是按"排队模型"推出的理论值；实际倍数受设备 CPU、网络带宽、书源
     * 服务器限速影响，可能低于 6 倍。并发过大会放大对书源服务器的瞬时压力，
     * 若真机上出现反爬/封 IP，需回落。
     */
    const val MAX_SEARCH_THREAD = 54

    const val DEFAULT_WEBDAV_ID = -1L

    const val imagePathKey = "imagePath"

    val charsets =
        arrayListOf("UTF-8", "GB2312", "GB18030", "GBK", "Unicode", "UTF-16", "UTF-16LE", "ASCII")

    const val timeLimit = 15000L

    /**
     * 注入到 evalJS 作用域的变量名白名单。
     * 通过 [io.legado.app.model.analyzeRule.AnalyzeUrl] / [io.legado.app.model.analyzeRule.AnalyzeRule] 的 `variables` 参数注入时，仅允许使用此处声明的键，
     * 防止散落字符串导致拼写错误或难以追踪。
     */
    enum class JsVarName(val key: String) {
        KEY("key"),
        PAGE("page"),
        SPEAK_TEXT("speakText"),
        SPEAK_SPEED("speakSpeed"),
        PARAGRAPH_INDEX("paragraphIndex"),
        SORT("sort"),
        REVIEW_ID("reviewId"),
        SELECTED("selected"),
    }

}

/**
 * 各端 JS 引擎可见的平台标识 (android/desktop/ios/ohos)。
 *
 * expect/actual 拆分: androidMain/jvmMain/iosMain/ohosMain 各提供 actual
 * (见各源集 AppConst.<平台>.kt), 替代原硬编码 const —— JS `platform` 变量反映真实运行平台。
 */
internal expect fun jsPlatform(): String
