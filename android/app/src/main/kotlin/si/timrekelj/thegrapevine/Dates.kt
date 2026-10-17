package si.timrekelj.thegrapevine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Supabase returns `timestamptz` as ISO8601, with or without fractional seconds
 * (`2026-10-13T12:00:00.123456+00:00` and `2026-10-13T12:00:00Z` both occur).
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    }

    override fun deserialize(decoder: Decoder): Instant = parse(decoder.decodeString())

    fun parse(text: String): Instant =
        OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()

    fun format(value: Instant): String = DateTimeFormatter.ISO_INSTANT.format(value)
}
