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
