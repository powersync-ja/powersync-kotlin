/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.powersync.http

import io.ktor.client.call.UnsupportedContentTypeException
import io.ktor.client.request.*
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.*
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.coroutineScope
import kotlinx.io.readByteArray
import platform.Foundation.*

@OptIn(InternalAPI::class, UnsafeNumber::class)
internal suspend fun HttpRequestData.toNSUrlRequest(): NSMutableURLRequest {
    val url = url.toNSUrl()
    val nativeRequest = NSMutableURLRequest.requestWithURL(url).apply {
        setupSocketTimeout(this@toNSUrlRequest)

        setHTTPBody(readFully(body))
        forEachHeader { key, value -> setValue(value, key) }

        setCachePolicy(NSURLRequestReloadIgnoringCacheData)
        setHTTPMethod(method.value)
    }

    return nativeRequest
}

private suspend fun readFully(body: OutgoingContent): NSData? {
    return when (body) {
        is OutgoingContent.ByteArrayContent -> byteArrayToNSData(body.bytes())
        is OutgoingContent.ContentWrapper -> readFully(body.delegate())
        is OutgoingContent.NoContent -> null
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(body)
        // This is a deviation from upstream: To simplify our code, we don't turn request bodies into NSOutputStreams.
        // Instead, we read them fully at once. This prevents streaming uploads, but we don't need those in our SDK.
        is OutgoingContent.ReadChannelContent -> {
            val contents = body.readFrom().readRemaining()
            byteArrayToNSData(contents.readByteArray())
        }
        is OutgoingContent.WriteChannelContent -> {
            coroutineScope {
                val reader = writer {
                    body.writeTo(channel)
                }.channel

                val contents = reader.readRemaining()
                byteArrayToNSData(contents.readByteArray())
            }
        }
    }
}

@OptIn(UnsafeNumber::class)
private fun byteArrayToNSData(data: ByteArray) = NSMutableData().also { buffer ->
    if (data.isNotEmpty()) {
        data.usePinned { pinned ->
            buffer.appendBytes(pinned.addressOf(0), data.size.convert())
        }
    }
}
