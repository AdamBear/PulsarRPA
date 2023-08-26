package ai.platon.pulsar.common.proxy

import ai.platon.pulsar.common.config.CapabilityTypes.PROXY_POOL_MANAGER_CLASS
import ai.platon.pulsar.common.config.CapabilityTypes.PROXY_POOL_MONITOR_CLASS
import ai.platon.pulsar.common.config.ImmutableConfig
import ai.platon.pulsar.common.stringify
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class ProxyPoolManagerFactory(
        val proxyPool: ProxyPool,
        val conf: ImmutableConfig
): AutoCloseable {
    private val logger = LoggerFactory.getLogger(ProxyPoolManagerFactory::class.java)

    private val proxyPoolManagers = ConcurrentHashMap<String, ProxyPoolManager>()
    
    var specifiedProxyManager: ProxyPoolManager? = null
    
    fun get(): ProxyPoolManager = specifiedProxyManager ?: computeIfAbsent(conf)
    
    override fun close() {
        specifiedProxyManager?.runCatching { close() }?.onFailure { logger.warn(it.stringify()) }
        proxyPoolManagers.values.forEach { runCatching { it.close() }.onFailure { logger.warn(it.stringify()) } }
    }

    private fun computeIfAbsent(conf: ImmutableConfig): ProxyPoolManager {
        synchronized(ProxyPoolManagerFactory::class) {
            val clazz = getClass(conf)
            return proxyPoolManagers.computeIfAbsent(clazz.name) {
                clazz.constructors.first { it.parameters.size == 2 }.newInstance(proxyPool, conf) as ProxyPoolManager
            }
        }
    }

    private fun getClass(conf: ImmutableConfig): Class<*> {
        val defaultClazz = FileProxyLoader::class.java
        var clazz = getClass(conf, PROXY_POOL_MANAGER_CLASS)
        if (clazz == defaultClazz) {
            clazz = getClass(conf, PROXY_POOL_MONITOR_CLASS)
        }
        return clazz
    }
    
    private fun getClass(conf: ImmutableConfig, clazzName: String): Class<*> {
        val defaultClazz = FileProxyLoader::class.java
        return try {
            conf.getClass(clazzName, defaultClazz)
        } catch (e: Exception) {
            logger.warn("Configured proxy loader {}({}) is not found, use default ({})",
                clazzName, conf.get(clazzName), defaultClazz.simpleName)
            defaultClazz
        }
    }
}
