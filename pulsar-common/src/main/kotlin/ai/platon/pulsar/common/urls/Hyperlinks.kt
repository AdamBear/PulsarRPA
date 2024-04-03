package ai.platon.pulsar.common.urls

import ai.platon.pulsar.common.ResourceStatus
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * A hyperlink datum is a data class that represents a hyperlink.
 * */
data class HyperlinkDatum(
    /**
     * The url specification of the hyperlink, it is usually normalized, and can contain load arguments.
     * */
    val url: String,
    /**
     * A hyperlink should have a text, so the default value is an empty string
     * */
    val text: String = "",
    /**
     * The link order, e.g., the order in which the link appears on the referrer page.
     * */
    val order: Int = 0,
    /**
     * A hyperlink might have a referrer, so the default value is null
     * */
    val referrer: String? = null,
    /**
     * The load argument, can be parsed into a LoadOptions
     * */
    val args: String? = null,
    /**
     * The hypertext reference, It defines the address of the document, which this time is linked from
     * */
    var href: String? = null,
    /**
     * If this link is persistable
     * */
    val isPersistable: Boolean = true,
    /**
     * The depth
     * */
    val depth: Int = 0,
)

/**
 * A [hyperlink](https://en.wikipedia.org/wiki/Hyperlink), or simply a link, is a reference to data that the user can
 * follow by clicking or tapping.
 *
 * A hyperlink points to a whole document or to a specific element within a document.
 * Hypertext is text with hyperlinks. The text that is linked from is called anchor text.
 *
 * The [anchor text](https://en.wikipedia.org/wiki/Anchor_text), link label or link text is the visible,
 * clickable text in an HTML hyperlink.
 * */
open class Hyperlink(
    /**
     * The url specification of the hyperlink, it is usually normalized, and can contain load arguments.
     * */
    url: String,
    /**
     * The anchor text
     * */
    var text: String = "",
    /**
     * The order of this hyperlink in it referrer page
     * */
    var order: Int = 0,
    /**
     * The url of the referrer page
     * */
    referrer: String? = null,
    /**
     * The additional url arguments
     * */
    args: String? = null,
    /**
     * The hypertext reference, It defines the address of the document, which this time is linked from
     * */
    href: String? = null,
) : AbstractUrl(url, args, referrer, href) {
    var depth: Int = 0
    
    /**
     * Construct a hyperlink.
     *
     * This method is compatible with Java.
     * */
    constructor(url: String) : this(url, "", 0)
    constructor(url: UrlAware) : this(url.url, "", 0, url.referrer, url.args, href = url.href)
    constructor(url: Hyperlink) : this(url.url, url.text, url.order, url.referrer, url.args, href = url.href)
    constructor(url: HyperlinkDatum) : this(url.url, url.text, url.order, url.referrer, url.args, href = url.href)
    
    fun data() = HyperlinkDatum(url, text, order, referrer = referrer, args = args, href = href, true, 0)

    override fun serializeTo(sb: StringBuilder): StringBuilder {
        super.serializeTo(sb)

        text.takeUnless { it.isEmpty() }?.let { sb.append(" -text ").append(it) }
        order.takeUnless { it == 0 }?.let { sb.append(" -order ").append(it) }
        depth.takeUnless { it == 0 }?.let { sb.append(" -depth ").append(it) }

        return sb
    }

    companion object {
        fun parse(linkText: String): Hyperlink {
            var url = ""
            var text = ""
            var args: String? = null
            var href: String? = null
            var referrer: String? = null
            var order = 0
            var priority = 0
            var lang = "*"
            var country = "*"
            var district = "*"
            var nMaxRetry = 3

            val names = listOf("text", "args", "href", "referrer", "order", "priority",
                "lang", "country", "district", "nMaxRetry")
            val regex = names.map { " -$it " }.filter { it in linkText }.joinToString("|").toRegex()
            val regex2 = " -\b "
            val values = linkText.split(regex)

            url = values[0]
            var i = 0
            while (i < names.size) {
                val j = i + 1
                if (names[i] == "text") text = values[j]
                if (names[i] == "args") args = values[j]
                if (names[i] == "href") href = values[j]
                if (names[i] == "referrer") referrer = values[j]
                if (names[i] == "order") order = values[j].toIntOrNull() ?: order
                if (names[i] == "priority") priority = values[j].toIntOrNull() ?: priority
                if (names[i] == "lang") lang = values[j]
                if (names[i] == "country") country = values[j]
                if (names[i] == "district") district = values[j]
                if (names[i] == "nMaxRetry") nMaxRetry = values[j].toIntOrNull() ?: nMaxRetry

                ++i
            }

            return Hyperlink(url, text, order, referrer, args, href).also {
                it.priority = priority
                it.lang = lang
                it.country = country
                it.district = district
                it.nMaxRetry = nMaxRetry
            }
        }
    }
}

open class StatefulHyperlink(
    /**
     * The url specification of the hyperlink, it is usually normalized, and can contain load arguments.
     * */
    url: String,
    /**
     * The anchor text of this hyperlink
     * */
    text: String = "",
    /**
     * The order of this hyperlink on its referrer page
     * */
    order: Int = 0,
    /**
     * The url of the referrer page
     * */
    referrer: String? = null,
    /**
     * The url arguments
     * */
    args: String? = null,
    /**
     * The hypertext reference, It defines the address of the document, which this time is linked from
     * */
    href: String? = null,
) : Hyperlink(url, text, order, referrer, args, href), StatefulUrl {
    override var authToken: String? = null
    override var remoteAddr: String? = null
    override var status: Int = ResourceStatus.SC_CREATED
    override var modifiedAt: Instant = Instant.now()
    override val createdAt: Instant = Instant.now()
}

val StatefulHyperlink.isCreated get() = this.status == ResourceStatus.SC_CREATED
val StatefulHyperlink.isAccepted get() = this.status == ResourceStatus.SC_ACCEPTED
val StatefulHyperlink.isProcessing get() = this.status == ResourceStatus.SC_PROCESSING
val StatefulHyperlink.isFinished get() = !isCreated && !isAccepted && !isProcessing

/**
 * A (fat link)[https://en.wikipedia.org/wiki/Hyperlink#Fat_link] (also known as a "one-to-many" link, an "extended link"
 * or a "multi-tailed link") is a hyperlink which leads to multiple endpoints; the link is a multivalued function.
 * */
open class FatLink(
    /**
     * The url specification of the hyperlink, it is usually normalized, and can contain load arguments.
     * */
    url: String,
    /**
     * The anchor text of this hyperlink
     * */
    text: String = "",
    /**
     * The order of this hyperlink on its referrer page
     * */
    order: Int = 0,
    /**
     * The url of the referrer page
     * */
    referrer: String? = null,
    /**
     * The url arguments
     * */
    args: String? = null,
    /**
     * The hypertext reference, it defines the address of the document on the referrer page
     * */
    href: String? = null,
    /**
     * The tail links
     * */
    var tailLinks: List<StatefulHyperlink>,
) : Hyperlink(url, text, order, referrer, args, href) {
    val size get() = tailLinks.size
    val isEmpty get() = size == 0
    val isNotEmpty get() = !isEmpty

    override fun toString() = "$size | $url"
}

open class StatefulFatLink(
    /**
     * The url specification of the hyperlink, it is usually normalized, and can contain load arguments.
     * */
    url: String,
    /**
     * The anchor text of this hyperlink
     * */
    text: String = "",
    /**
     * The order of this hyperlink in its referrer page
     * */
    order: Int = 0,
    /**
     * The url of the referrer page
     * */
    referrer: String? = null,
    /**
     * The url arguments
     * */
    args: String? = null,
    /**
     * The hypertext reference, It defines the address of the document, which this time is linked from
     * */
    href: String? = null,
    /**
     * The tail links
     * */
    tailLinks: List<StatefulHyperlink>,
) : FatLink(url, text, order, referrer, args, href, tailLinks), StatefulUrl {
    override var authToken: String? = null
    override var remoteAddr: String? = null
    override var status: Int = ResourceStatus.SC_CREATED
    override var modifiedAt: Instant = Instant.now()
    override val createdAt: Instant = Instant.now()

    override fun toString() = "$status $createdAt $modifiedAt ${super.toString()}"
}

/**
 * A [crawlable fat link](https://en.wikipedia.org/wiki/Hyperlink#Fat_link) is a hyperlink which leads to multiple endpoints;
 * the link is a multivalued function. It is used in web crawling to represent a page with multiple links.
 * */
open class CrawlableFatLink(
    /**
     * The url specification of the hyperlink, it is usually normalized, and can contain load arguments.
     * */
    url: String,
    /**
     * The anchor text of this hyperlink
     * */
    text: String = "",
    /**
     * The order of this hyperlink in its referrer page
     * */
    order: Int = 0,
    /**
     * The url of the referrer page
     * */
    referrer: String? = null,
    /**
     * The url arguments
     * */
    args: String? = null,
    /**
     * The hypertext reference, It defines the address of the document, which this time is linked from
     * */
    href: String? = null,
    /**
     * The tail links
     * */
    tailLinks: List<StatefulHyperlink> = listOf(),
) : StatefulFatLink(url, text, order, referrer, args, href, tailLinks) {

    private val log = LoggerFactory.getLogger(CrawlableFatLink::class.java)

    @Volatile
    var finishedTailLinkCount = 0
    var aborted = false
    /**
     * The number of active tail links
     * */
    val numActive get() = size - finishedTailLinkCount
    /**
     * Whether the crawlable fat link is aborted
     * */
    val isAborted get() = aborted
    /**
     * Whether the crawlable fat link is finished
     * */
    val isFinished get() = finishedTailLinkCount >= size
    /**
     * The idle time of the crawlable fat link
     * */
    val idleTime get() = Duration.between(modifiedAt, Instant.now())
    /**
     * Abort the crawlable fat link
     * */
    fun abort() {
        aborted = true
    }
    /**
     * Finish a stateful hyperlink
     * */
    fun finish(url: StatefulHyperlink, status: Int = ResourceStatus.SC_OK): Boolean {
        aborted = false

        if (log.isDebugEnabled) {
            log.debug(
                "Try to finish stateful hyperlink, ({}) \n{} \n{}",
                if (url in tailLinks) "found" else "not found", url, this
            )
        }

        if (tailLinks.none { url.url == it.url }) {
            return false
        }

        require(url.referrer == this.url)
        url.modifiedAt = Instant.now()
        url.status = status
        modifiedAt = Instant.now()
        ++finishedTailLinkCount
        if (isFinished) {
            log.info("Crawlable fat link is finished | {}", this)
            this.status = ResourceStatus.SC_OK
        }

        return true
    }
    
    override fun toString() = "$finishedTailLinkCount/${tailLinks.size} ${super.toString()}"
}

/**
 * The hyperlink helper object.
 * */
object Hyperlinks {

    /**
     * Convert a [UrlAware] to a [Hyperlink], might loss information
     * */
    fun toHyperlink(url: UrlAware): Hyperlink {
        return if (url is Hyperlink) url
        else Hyperlink(url.url, args = url.args, href = url.href, referrer = url.referrer)
    }
}
