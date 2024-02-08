package com.powersync.plugins.sonatype.internal

import com.powersync.plugins.sonatype.SonatypeCentralExtension
import org.gradle.api.reflect.HasPublicType
import org.gradle.api.reflect.TypeOf

abstract class DefaultSonatypeCentralExtension : SonatypeCentralExtension, HasPublicType {
    override fun getPublicType(): TypeOf<*> {
        return TypeOf.typeOf<Any>(SonatypeCentralExtension::class.java)
    }
}

