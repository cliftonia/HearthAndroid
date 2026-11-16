package app.hearth.data

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Times as the hub's wire format writes them: ISO-8601 in UTC with exactly three fractional digits,
 * `2026-11-16T09:30:12.345Z`. Worked out from whole milliseconds since 1970, so a time is never rounded differently
 * on the way out than on the way back in.
 */
object WireTime {
    /** The time at a whole number of milliseconds since 1970, in the wire's form. */
    fun format(milliseconds: Long): String {
        val millisecond = Math.floorMod(milliseconds, 1000L).toInt()
        val moment = Instant.ofEpochMilli(milliseconds - millisecond).atOffset(ZoneOffset.UTC)
        // In the root locale, so that the digits are always ASCII whatever language the phone speaks.
        return String.format(
            Locale.ROOT,
            "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
            moment.year, moment.monthValue, moment.dayOfMonth, moment.hour, moment.minute, moment.second, millisecond,
        )
    }

    /**
     * Milliseconds since 1970 from a UTC time written `YYYY-MM-DDTHH:MM:SS`, with or without a fraction of a second,
     * ending in `Z`. Digits past the millisecond are dropped. `null` for anything else.
     */
    fun parse(text: String): Long? {
        val match = PATTERN.matchEntire(text) ?: return null
        val (year, month, day, hour, minute, second) = match.destructured
        val fraction = match.groupValues[7]
        val millisecond = fraction.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        return try {
            val moment = LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
            moment.toInstant(ZoneOffset.UTC).toEpochMilli() + millisecond
        } catch (failure: DateTimeException) {
            null
        }
    }

    private val PATTERN = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?Z$""")
}

/** Reads and writes a time in milliseconds since 1970 as the wire's ISO-8601 text. */
object WireTimeSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.hearth.data.WireTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(WireTime.format(value))

    override fun deserialize(decoder: Decoder): Long {
        val text = decoder.decodeString()
        return WireTime.parse(text) ?: throw SerializationException("Not an ISO-8601 UTC time")
    }
}
