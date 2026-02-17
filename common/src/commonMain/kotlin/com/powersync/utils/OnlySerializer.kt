package com.powersync.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder

/**
 * A [KSerializer] that throws when deserializing.
 */
internal abstract class OnlySerializer<T> : KSerializer<T> {
    override fun deserialize(decoder: Decoder): T = throw UnsupportedOperationException("this serializer only supports serialization")
}
