package ai.platon.pulsar.browser.common

import ai.platon.pulsar.common.AppPaths
import ai.platon.pulsar.common.ResourceLoader
import ai.platon.pulsar.common.config.AppConstants
import ai.platon.pulsar.common.config.AppConstants.CLIENT_JS_PROPERTY_NAMES
import ai.platon.pulsar.common.config.CapabilityTypes.FETCH_CLIENT_JS_COMPUTED_STYLES
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.getLogger
import com.google.gson.GsonBuilder
import java.nio.file.Files
import kotlin.io.path.isReadable
import kotlin.io.path.listDirectoryEntries

class ScriptLoader(
    val confuser: ScriptConfuser = ScriptConfuser(),
    val conf: ImmutableConfig
) {
    private val logger = getLogger(this)

    private val jsInitParameters: MutableMap<String, Any> = mutableMapOf()

    private val resources = """
            stealth.js
            __pulsar_utils__.js
            configs.js
            node_ext.js
            node_traversor.js
            feature_calculator.js
        """.trimIndent().split("\n").map { "js/" + it.trim() }.toMutableList()

    /**
     * The javascript to execute by Web browsers.
     * */
    private val propertyNames
        get() = conf.getTrimmedStrings(FETCH_CLIENT_JS_COMPUTED_STYLES, CLIENT_JS_PROPERTY_NAMES)

    private val jsCache: MutableMap<String, String> = LinkedHashMap()
    /**
     * The javascript code to inject into the browser.
     * */
    private var preloadJs = ""

    init {
        initDefaultJsParameters()
    }

    fun addInitParameter(name: String, value: String) {
        jsInitParameters[name] = value
    }

    /**
     * Make sure generatePreloadJs is thread safe
     * */
    @Synchronized
    fun getPreloadJs(reload: Boolean = false): String {
        if (reload) {
            preloadJs = ""
        }

        if (preloadJs.isEmpty()) {
            load()
        }

        return preloadJs
    }

    private fun load(): String {
        jsCache.clear()

        val sb = StringBuilder()

        val jsVariables = generatePredefinedJsConfig()
        sb.appendLine(jsVariables).appendLine("\n\n\n")

        loadExternalResource()
        loadDefaultResource()
        jsCache.values.joinTo(sb, ";\n")

        preloadJs = sb.toString()

        reportPreloadJs(preloadJs)

        return preloadJs
    }

    private fun generatePredefinedJsConfig(): String {
        // Note: Json-2.6.2 does not recognize MutableMap, but knows Map
        val configs = GsonBuilder().create().toJson(jsInitParameters.toMap())

        // set predefined variables shared between javascript and jvm program
        val configVar = confuser.confuse( "${ScriptConfuser.SCRIPT_NAME_PREFIX}CONFIGS")
        return """
            ;
            let $configVar = $configs;
        """.trimIndent()
    }

    private fun loadDefaultResource() {
        resources.associateWithTo(jsCache) {
            ResourceLoader.readAllLines(it).joinToString("\n") { confuser.confuse(it) }
        }
    }

    private fun loadExternalResource() {
        val dir = AppPaths.BROWSER_DATA_DIR.resolve("browser/js/preload")
        if (Files.isDirectory(dir)) {
            dir.listDirectoryEntries()
                .filter { it.isReadable() }
                .filter { it.toString().endsWith(".js") }
                .associateTo(jsCache) { it.toString() to Files.readString(it) }
        }
    }

    private fun reportPreloadJs(script: String) {
        val dir = AppPaths.REPORT_DIR.resolve("browser/js")
        Files.createDirectories(dir)
        val report = Files.writeString(dir.resolve("preload.gen.js"), script)
        logger.info("Generated js: file://$report")
    }

    private fun initDefaultJsParameters() {
        mapOf(
            "propertyNames" to propertyNames,
            "viewPortWidth" to BrowserSettings.screenViewport.width,
            "viewPortHeight" to BrowserSettings.screenViewport.height,

            "META_INFORMATION_ID" to AppConstants.PULSAR_META_INFORMATION_ID,
            "SCRIPT_SECTION_ID" to AppConstants.PULSAR_SCRIPT_SECTION_ID,
            "ATTR_HIDDEN" to AppConstants.PULSAR_ATTR_HIDDEN,
            "ATTR_OVERFLOW_HIDDEN" to AppConstants.PULSAR_ATTR_OVERFLOW_HIDDEN,
            "ATTR_OVERFLOW_VISIBLE" to AppConstants.PULSAR_ATTR_OVERFLOW_VISIBLE,
            "ATTR_ELEMENT_NODE_VI" to AppConstants.PULSAR_ATTR_ELEMENT_NODE_VI,
            "ATTR_TEXT_NODE_VI" to AppConstants.PULSAR_ATTR_TEXT_NODE_VI,
        ).also { jsInitParameters.putAll(it) }
    }
}
