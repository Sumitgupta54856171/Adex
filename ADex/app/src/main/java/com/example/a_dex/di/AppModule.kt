package com.example.a_dex.di
import com.example.a_dex.network.NsdBroadcaster
import com.example.a_dex.network.VideoStreamer
import com.example.a_dex.network.InputServer
import org.koin.dsl.module

val appModule = module {
    single { NsdBroadcaster(get()) }
    single { VideoStreamer() }
    single { InputServer() }
}
