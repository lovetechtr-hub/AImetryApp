package com.djmetry.api.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Int, который переживает NaN / "3" / 3.0 от бэкенда: всё нечисловое → null. */
internal object LenientIntSerializer : KSerializer<Int?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeInt()
        val p = element as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.intOrNull ?: p.doubleOrNull?.takeIf { it.isFinite() }?.toInt()
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

/** Место DJ Mag в прошлом году: число; `"NEW"` (впервые в рейтинге) → [DJMAG_NEW]; прочее — null. */
internal object PreviousRankSerializer : KSerializer<Int?> {
    override val descriptor = PrimitiveSerialDescriptor("PreviousRank", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int? {
        val p = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        if (p.isString && p.content.trim().equals("NEW", ignoreCase = true)) return DJMAG_NEW
        return p.intOrNull ?: p.doubleOrNull?.takeIf { it.isFinite() }?.toInt()
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

/** Признак «новичок DJ Mag» в `previousYearRank` (реальные места — от 1). */
const val DJMAG_NEW = 0
