package com.adf.pirobinetterie

import android.app.Application
import com.adf.pirobinetterie.data.Depot

class PiApp : Application() {
    lateinit var depot: Depot
        private set

    override fun onCreate() {
        super.onCreate()
        depot = Depot(this)
    }
}
