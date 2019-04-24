package ai.platon.pulsar.common.options

import ai.platon.pulsar.common.SParser
import ai.platon.pulsar.persist.metadata.BrowserType
import ai.platon.pulsar.persist.metadata.FetchMode
import com.beust.jcommander.IStringConverter
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.apache.commons.lang3.tuple.Pair
import java.time.Duration
import java.time.Instant
import java.util.HashMap

/**
 * Created by vincent on 17-4-7.
 * Copyright @ 2013-2017 Platon AI. All rights reserved
 */
class DurationConverter: IStringConverter<Duration> {
    override fun convert(value: String): Duration {
        return SParser(value).duration
    }
}

class InstantConverter: IStringConverter<Instant> {
    override fun convert(value: String): Instant {
        return SParser(value).getInstant(Instant.EPOCH)
    }
}

class PairConverter: IStringConverter<Pair<Int, Int>> {
    override fun convert(value: String): Pair<Int, Int> {
        val parts = value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return Pair.of(Integer.valueOf(parts[0]), Integer.valueOf(parts[1]))
    }
}

class BrowserTypeConverter: IStringConverter<BrowserType> {
    override fun convert(value: String): BrowserType {
        return BrowserType.fromString(value)
    }
}

class FetchModeConverter: IStringConverter<FetchMode> {
    override fun convert(value: String): FetchMode {
        return FetchMode.fromString(value)
    }
}

/**
 * Created by vincent on 17-4-7.
 * Copyright @ 2013-2017 Platon AI. All rights reserved
 */
class WeightedKeywordsConverter : IStringConverter<Map<String, Double>> {
    override fun convert(value: String): Map<String, Double> {
        var value = value
        val keywords = HashMap<String, Double>()
        value = StringUtils.remove(value, ' ')
        val parts = value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (part in parts) {
            var k = part
            var v = "1"

            val pos = part.indexOf('^')
            if (pos >= 1 && pos < part.length - 1) {
                k = part.substring(0, pos)
                v = part.substring(pos + 1)
            }

            keywords[k] = NumberUtils.toDouble(v, 1.0)
        }

        return keywords
    }
}
