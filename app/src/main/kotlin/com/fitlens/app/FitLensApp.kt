package com.fitlens.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class FitLensApp : Application() {

    companion object {
        private const val TAG = "FitLensApp"
    }

    override fun onCreate() {
        super.onCreate()
        initFirebaseAppCheck()
    }

    private fun initFirebaseAppCheck() {
        try {
            FirebaseApp.initializeApp(this)
            val firebaseAppCheck = FirebaseAppCheck.getInstance()

            if (BuildConfig.DEBUG) {
                // In debug mode, use the Debug Provider for emulator/device testing
                firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Firebase App Check initialized with DebugAppCheckProviderFactory")
            } else {
                // In production, enforce Play Integrity attestation
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.i(TAG, "Firebase App Check initialized with PlayIntegrityAppCheckProviderFactory")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization deferred or skipped: ${e.message}")
        }
    }
}
