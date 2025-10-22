package com.akistoy.app.ui

import android.content.Context
import android.content.Intent
import com.akistoy.app.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainActivityIntentFactory @Inject constructor() {
    fun create(context: Context): Intent = Intent(context, MainActivity::class.java)
}
