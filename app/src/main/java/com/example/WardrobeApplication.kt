package com.example

import android.app.Application
import com.example.wardrobe.data.remote.SupabaseClientManager
import com.example.wardrobe.data.repository.WardrobeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WardrobeApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: WardrobeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        SupabaseClientManager.initialize(this)
        repository = WardrobeRepository.create(this)
        applicationScope.launch {
            repository.checkAndSeedInitialData()
        }
    }
}
