package ai.platon.pulsar.rest.api.service

import ai.platon.pulsar.PulsarSession
import ai.platon.pulsar.common.ResourceStatus
import ai.platon.pulsar.crawl.common.GlobalCache
import ai.platon.pulsar.persist.metadata.ProtocolStatusCodes
import ai.platon.pulsar.rest.api.common.ScrapeAPIUtils
import ai.platon.pulsar.rest.api.common.SinkAwareHyperlink
import ai.platon.pulsar.rest.api.entities.ScrapeRequest
import ai.platon.pulsar.rest.api.entities.ScrapeResponse
import ai.platon.pulsar.rest.api.entities.ScrapeStatusRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentSkipListMap

@Service
class ScrapeService(
    val session: PulsarSession,
    val globalCache: GlobalCache,
) {
    private val logger = LoggerFactory.getLogger(ScrapeService::class.java)
    private val responseCache = ConcurrentSkipListMap<String, ScrapeResponse>()
    private val fetchCaches get() = globalCache.fetchCacheManager

    /**
     * Execute a scrape task and wait until the execution is done,
     * for test purpose only, no customer should access this api
     * */
    fun executeQuery(request: ScrapeRequest): ScrapeResponse {
        val hyperlink = createScrapeHyperlink(request)
        fetchCaches.highestCache.reentrantQueue.add(hyperlink)
        return hyperlink.get()
    }

    /**
     * Submit a scraping task
     * */
    fun submitJob(request: ScrapeRequest): String {
        val hyperlink = createScrapeHyperlink(request)
        responseCache[hyperlink.uuid] = hyperlink.response
        fetchCaches.normalCache.reentrantQueue.add(hyperlink)
        return hyperlink.uuid
    }

    /**
     * Get the response
     * */
    fun getStatus(request: ScrapeStatusRequest): ScrapeResponse {
        return responseCache.computeIfAbsent(request.uuid) {
            ScrapeResponse(request.uuid, ResourceStatus.SC_NOT_FOUND, ProtocolStatusCodes.NOT_FOUND)
        }
    }

    private fun createScrapeHyperlink(request: ScrapeRequest): SinkAwareHyperlink {
        val scrapeSQL = ScrapeAPIUtils.normalizeScrapeSQL(request.sql)
        return SinkAwareHyperlink(request, scrapeSQL, session, globalCache)
    }
}
