package `fun`.platonic.scent.dom

import `fun`.platonic.scent.dom.nodes.*
import com.ibm.icu.lang.UCharacter.JoiningGroup.TAH
import com.ibm.icu.lang.UCharacter.JoiningGroup.TAW
import org.apache.commons.lang3.StringUtils
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor

class FeatureCalculator(val doc: DescriptiveDocument) : NodeVisitor {
    var sequence: Int = 0
        private set

    companion object {
        init {
            require(FEATURE_NAMES.size == F.N.ordinal)
        }
    }

    // hit when the node is first seen
    override fun head(node: Node, depth: Int) {
        node.features = ArrayRealVector(FEATURE_NAMES.size)

        node.features[DEP] = depth.toDouble()
        node.features[SEQ] = sequence.toDouble()

        calcSelfIndicator(node)
        ++sequence
    }

    // 单个节点统计项
    private fun calcSelfIndicator(node: Node) {
        if (node !is Element && node !is TextNode) {
            return
        }

        val rect = getDOMRect(node)
        if (!rect.isEmpty) {
            node.features[TOP] = rect.top
            node.features[LEFT] = rect.left
            node.features[WIDTH] = rect.width
            node.features[HEIGHT] = rect.height
        }

        if (node is TextNode) {
            // Trim: remove all surrounding unicode white spaces, including all HT, VT, LF, FF, CR, ASCII space, etc
            // @see https://en.wikipedia.org/wiki/Whitespace_character
            val text = node.text().trim()
            val ch = text.length.toDouble()
            var sep = 0.0
            for (separator in SEPARATORS) {
                sep += StringUtils.countMatches(text, separator).toDouble()
            }

            if (ch > 0) {
                accumulateFeatures(node,
                        Feature(CH, ch)
                )
            }
        }

        if (node is Element) {
            var a = 0.0
            var va = 0.0
            var aW = 0.0
            var aH = 0.0

            var img = 0.0
            var vimg = 0.0
            var imgW = 0.0
            var imgH = 0.0

            // link relative
            if (node.nodeName() == "a") {
                ++a
                aW = rect.width
                aH = rect.height
                if (aW > 0 && aH > 0) {
                    ++va
                }
            }

            // image relative
            if (node.nodeName() == "img") {
                ++img
                imgW = rect.width
                imgH = rect.height
                if (imgW > 0 && imgH > 0) {
                    ++vimg
                }
            }

            accumulateFeatures(node,
                    Feature(A, a),
                    Feature(IMG, img)
            )
        }
    }

    // hit when all of the node's children (if any) have been visited
    override fun tail(node: Node, depth: Int) {
        if (node !is Element && node !is TextNode) {
            return
        }

        if (node is TextNode) {
            val parent = node.parent()

            // no-blank own text node
            val otn = if (node.features[CH] == 0.0) 0.0 else 1.0
            val votn = if (otn > 0 && node.features[WIDTH] > 0 && node.features[HEIGHT] > 0) 1.0 else 0.0
            accumulateFeatures(parent,
                    Feature(TN, otn),
                    node.getFeatureEntry(CH)
            )

            return
        }

        if (node is Element) {
            // accumulate features for parent node
            val pe = node.parent() ?: return

            accumulateFeatures(pe,
                    // code structure feature
                    node.getFeatureEntry(CH),
                    node.getFeatureEntry(TN),
                    node.getFeatureEntry(A),
                    node.getFeatureEntry(IMG),
                    Feature(C, 1.0)
            )

            // count of element siblings
            node.childNodes().forEach {
                if (it is Element) {
                    it.features[SIB] = node.features[C]
                }
            }
        } // if

        if (node == doc.body) {
            val rect = calculateBodyRect()
            node.width = rect.width.toInt()
            node.height = rect.height.toInt()
        }
    }

    private fun accumulateFeatures(node: Node, vararg features: Feature) {
        for (feature in features) {
            val old = node.getFeature(feature.first)
            node.setFeature(feature.first, feature.second + old)
        }
    }

    private fun getDOMRect(node: Node): DOMRect {
        return if (node is TextNode) getDOMRectInternal("tv", node)
        else parseDOMRect(node.attr("vi"))
    }

    private fun getDOMRectInternal(attrKey: String, node: TextNode): DOMRect {
        val parent = node.parent()
        val i = node.siblingIndex()
        val vi = parent.attr("$attrKey$i")
        return parseDOMRect(vi)
    }

    private fun calculateBodyRect(): DOMRect {
        val minW = 900.0
        val widths = DescriptiveStatistics()
        widths.addValue(minW)
        var height = doc.body.height
        doc.body.forEachElement {
            if (it.width > minW) {
                widths.addValue(it.width.toDouble())
            }
            if (it.y2 > height) {
                height = it.y2
            }
        }
        return DOMRect(0.0, 0.0, widths.getPercentile(90.0), 20 + height.toDouble())
    }

    private fun divide(ele: Element, numerator: Int, denominator: Int, divideByZeroValue: Double): Double {
        val n = ele.getFeature(numerator)
        val d = ele.getFeature(denominator)

        return if (d == 0.0) divideByZeroValue else n / d
    }
}
