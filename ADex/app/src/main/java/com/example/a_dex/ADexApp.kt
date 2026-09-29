package com.example.a_dex
import android.app.Application
import com.example.a_dex.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class ADexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        startKoin {
            androidContext(this@ADexApp)
            modules(appModule)
        }
    }
}
