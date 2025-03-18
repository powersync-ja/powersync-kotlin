package com.powersync.demo.backgroundsync

import android.app.Application
import com.powersync.DatabaseDriverFactory
import com.powersync.demos.AuthOptions
import com.powersync.demos.sharedAppModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@MainApplication)

            modules(sharedAppModule, module {
                single { AuthOptions(connectFromViewModel = false) }
                singleOf(::DatabaseDriverFactory)
            })
        }
    }
}
