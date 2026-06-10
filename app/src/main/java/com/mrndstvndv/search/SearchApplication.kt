package com.mrndstvndv.search

import android.app.Application
import com.mrndstvndv.search.di.AppContainer

class SearchApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
