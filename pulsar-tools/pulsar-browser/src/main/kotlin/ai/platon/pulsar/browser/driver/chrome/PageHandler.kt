package ai.platon.pulsar.browser.driver.chrome

import ai.platon.pulsar.browser.common.ScriptConfuser
import ai.platon.pulsar.browser.driver.chrome.util.ChromeRPCException
import ai.platon.pulsar.common.AppContext
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.common.math.geometric.OffsetD
import com.github.kklisura.cdt.protocol.v2023.types.dom.Rect
import com.github.kklisura.cdt.protocol.v2023.types.runtime.Evaluate
import com.google.common.base.Preconditions.checkState
import kotlin.random.Random

class PageHandler(
    private val devTools: RemoteDevTools,
    private val confuser: ScriptConfuser,
) {
    companion object {
        // see org.w3c.dom.Node.ELEMENT_NODE
        const val ELEMENT_NODE = 1
    }

    private val logger = getLogger(this)

    private val isActive get() = AppContext.isActive && devTools.isOpen
    private val page get() = devTools.page.takeIf { isActive }
    private val dom get() = devTools.dom.takeIf { isActive }
    private val css get() = devTools.css.takeIf { isActive }
    private val runtime get() = devTools.runtime.takeIf { isActive }

    val mouse = Mouse(devTools)
    val keyboard = Keyboard(devTools)

    fun querySelector(selector: String): Int? {
        val rootId = dom?.document?.nodeId
        return if (rootId != null && rootId > 0) {
            dom?.querySelector(rootId, selector)
        } else null
    }

    fun visible(selector: String): Boolean {
        val nodeId = querySelector(selector)
        if (nodeId == null || nodeId <= 0) {
            return false
        }

        return visible(nodeId)
    }

    fun visible(nodeId: Int): Boolean {
        if (nodeId <= 0) {
            return false
        }

        var isVisible = true

        val properties = css?.getComputedStyleForNode(nodeId)
        properties?.forEach { prop ->
            when {
                prop.name == "display" && prop.value == "none" -> isVisible = false
                prop.name == "visibility" && prop.value == "hidden" -> isVisible = false
                prop.name == "opacity" && prop.value == "0" -> isVisible = false
            }
        }

        if (isVisible) {
            isVisible = ClickableDOM.create(page, dom, nodeId)?.isVisible() ?: false
        }

        return isVisible
    }
    
    /**
     * This method fetches an element with `selector` and focuses it. If there's no
     * element matching `selector`, the method returns 0.
     *
     * @param selector - A
     * {@link https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Selectors | selector }
     * of an element to focus. If there are multiple elements satisfying the
     * selector, the first will be focused.
     * @returns  NodeId which resolves when the element matching selector is
     * successfully focused. returns 0 if there is no element
     * matching selector.
     */
    fun focusOnSelector(selector: String): Int {
        val rootId = dom?.document?.nodeId ?: return 0
        
        val nodeId = dom?.querySelector(rootId, selector)
        if (nodeId == 0) {
            return 0
        }

        dom?.focus(nodeId, rootId, null)
        
        return nodeId ?: 0
    }

    fun scrollIntoViewIfNeeded(selector: String, rect: Rect? = null): Int? {
        val nodeId = querySelector(selector)
        if (nodeId == null || nodeId == 0) {
            logger.info("No node found for selector: $selector")
            return null
        }

        return scrollIntoViewIfNeeded(nodeId, selector, rect)
    }

    fun scrollIntoViewIfNeeded(nodeId: Int, selector: String? = null, rect: Rect? = null): Int? {
        try {
            val node = dom?.describeNode(nodeId, null, null, null, false)
            if (node?.nodeType != ELEMENT_NODE) {
                logger.info("Node is not of type HTMLElement | {}", selector ?: nodeId)
                return null
            }

            dom?.scrollIntoViewIfNeeded(nodeId, node.backendNodeId, null, rect)
        } catch (e: ChromeRPCException) {
            logger.debug("DOM.scrollIntoViewIfNeeded is not supported, fallback to Element.scrollIntoView | {} | {} | {}",
                nodeId, e.message, selector)
            // Fallback to Element.scrollIntoView if DOM.scrollIntoViewIfNeeded is not supported
            evaluate("__pulsar_utils__.scrollIntoView('$selector')")
        }

        return nodeId
    }
    
    /**
     * Evaluates expression on global object.
     *
     * @param expression Javascript expression to evaluate
     * @return Remote object value in case of primitive values or JSON values (if it was requested).
     * */
    fun evaluateDetail(expression: String): Evaluate? {
        return runtime?.evaluate(confuser.confuse(expression))
    }

    /**
     * Evaluates expression on global object.
     *
     * @param expression Javascript expression to evaluate
     * @return Remote object value in case of primitive values or JSON values (if it was requested).
     * */
    fun evaluate(expression: String): Any? {
        val evaluate = evaluateDetail(expression)

        val exception = evaluate?.exceptionDetails?.exception
        if (exception != null) {
            logger.info(exception.description + "\n>>>$expression<<<")
        }

        // println(Gson().toJson(evaluate))

        val result = evaluate?.result
        return result?.value
    }
}
