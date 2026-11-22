package com.automatelinux.tally

import android.app.Application

class TallyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PurchasePush.register(this)
    }
}
