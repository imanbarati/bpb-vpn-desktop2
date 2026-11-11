package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BpbApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val preferencesRepository by lazy { AppPreferencesRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Pre-warm database and ensure default servers are populated
        applicationScope.launch {
            if (database.serverDao().getCount() == 0) {
                database.serverDao().insertServers(com.example.data.DefaultServers.getInitialServers())
            }
        }
    }
}
