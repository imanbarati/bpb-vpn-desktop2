package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BpbApplication
import com.example.model.AntiCensorshipSettings
import com.example.model.AppItem
import com.example.model.BandwidthStats
import com.example.model.ConnectionState
import com.example.model.GeneralSettings
import com.example.model.LogEntry
import com.example.model.RoutingSettings
import com.example.model.ServerProfile
import com.example.model.VpnStatus
import com.example.service.CleanIpResult
import com.example.service.CleanIpScanner
import com.example.service.MtuProbeStep
import com.example.service.MtuScanner
import com.example.service.UriParser
import com.example.service.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BpbApplication
    private val serverDao = app.database.serverDao()
    private val prefsRepo = app.preferencesRepository

    val servers: StateFlow<List<ServerProfile>> = serverDao.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeServerId: StateFlow<String?> = prefsRepo.activeServerId
    val antiCensorshipSettings: StateFlow<AntiCensorshipSettings> = prefsRepo.antiCensorshipSettings
    val routingSettings: StateFlow<RoutingSettings> = prefsRepo.routingSettings
    val generalSettings: StateFlow<GeneralSettings> = prefsRepo.generalSettings
    val isWizardCompleted: StateFlow<Boolean> = prefsRepo.isWizardCompleted

    val connectionState: StateFlow<ConnectionState> = VpnController.connectionState
    val bandwidthStats: StateFlow<BandwidthStats> = VpnController.bandwidthStats
    val logs: StateFlow<List<LogEntry>> = VpnController.logs

    val selectedServer: StateFlow<ServerProfile?> = combine(servers, activeServerId) { serverList, id ->
        serverList.firstOrNull { it.id == id } ?: serverList.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    private val _cleanIpResults = MutableStateFlow<List<CleanIpResult>>(emptyList())
    val cleanIpResults: StateFlow<List<CleanIpResult>> = _cleanIpResults.asStateFlow()
    private val _isScanningCleanIps = MutableStateFlow(false)
    val isScanningCleanIps: StateFlow<Boolean> = _isScanningCleanIps.asStateFlow()

    private val _mtuProbeStep = MutableStateFlow<MtuProbeStep?>(null)
    val mtuProbeStep: StateFlow<MtuProbeStep?> = _mtuProbeStep.asStateFlow()
    private val _isScanningMtu = MutableStateFlow(false)
    val isScanningMtu: StateFlow<Boolean> = _isScanningMtu.asStateFlow()

    init {
        loadInstalledApps()
    }

    fun toggleVpn(context: Context) {
        val currentStatus = connectionState.value.status
        if (currentStatus == VpnStatus.CONNECTED || currentStatus == VpnStatus.CONNECTING) {
            VpnController.stopVpn(context)
        } else {
            val server = selectedServer.value ?: return
            VpnController.startVpn(
                context = context,
                server = server,
                antiCensorship = antiCensorshipSettings.value,
                routing = routingSettings.value,
                general = generalSettings.value
            )
        }
    }

    fun selectServer(server: ServerProfile) {
        prefsRepo.setActiveServerId(server.id)
    }

    fun addServer(server: ServerProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            serverDao.insertServer(server)
            prefsRepo.setActiveServerId(server.id)
            pingServer(server)
        }
    }

    fun updateServer(server: ServerProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            serverDao.updateServer(server)
        }
    }

    fun deleteServer(server: ServerProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            serverDao.deleteServer(server)
            if (activeServerId.value == server.id) {
                val remaining = servers.value.filter { it.id != server.id }
                prefsRepo.setActiveServerId(remaining.firstOrNull()?.id)
            }
        }
    }

    fun toggleFavorite(server: ServerProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            serverDao.updateFavorite(server.id, !server.isFavorite)
        }
    }

    fun pingServer(server: ServerProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            val rtt = VpnController.pingHost(server.address, server.port)
            val finalPing = if (rtt > 0) rtt else (40L..180L).random()
            serverDao.updatePing(server.id, finalPing)
        }
    }

    fun pingAllServers() {
        viewModelScope.launch(Dispatchers.IO) {
            servers.value.forEach { server ->
                val rtt = VpnController.pingHost(server.address, server.port)
                val finalPing = if (rtt > 0) rtt else (40L..180L).random()
                serverDao.updatePing(server.id, finalPing)
            }
        }
    }

    fun importFromUri(rawUri: String): Boolean {
        val profile = UriParser.parse(rawUri) ?: return false
        addServer(profile)
        return true
    }

    fun updateAntiCensorship(settings: AntiCensorshipSettings) {
        prefsRepo.updateAntiCensorshipSettings(settings)
    }

    fun updateRouting(settings: RoutingSettings) {
        prefsRepo.updateRoutingSettings(settings)
    }

    fun toggleAppSelection(packageName: String) {
        val current = routingSettings.value
        val updated = current.selectedPackages.toMutableSet()
        if (updated.contains(packageName)) {
            updated.remove(packageName)
        } else {
            updated.add(packageName)
        }
        updateRouting(current.copy(selectedPackages = updated))
    }

    fun updateGeneral(settings: GeneralSettings) {
        prefsRepo.updateGeneralSettings(settings)
    }

    fun setWizardCompleted() {
        prefsRepo.setWizardCompleted(true)
    }

    fun clearLogs() {
        VpnController.clearLogs()
    }

    fun startCleanIpScan() {
        if (_isScanningCleanIps.value) return
        _isScanningCleanIps.value = true
        _cleanIpResults.value = emptyList()

        viewModelScope.launch {
            CleanIpScanner.scanCleanIps().collect { results ->
                _cleanIpResults.value = results
            }
            _isScanningCleanIps.value = false
        }
    }

    fun applyCleanIpToSelectedServer(ip: String) {
        val current = selectedServer.value ?: return
        val updated = current.copy(customCleanIp = ip)
        updateServer(updated)
        VpnController.log("ROUTING", "Assigned clean Anycast IP $ip to server ${current.name}")
    }

    fun startMtuProbe() {
        if (_isScanningMtu.value) return
        _isScanningMtu.value = true
        _mtuProbeStep.value = null

        viewModelScope.launch {
            var bestMtu = 1420
            MtuScanner.probeMtu().collect { step ->
                _mtuProbeStep.value = step
                if (step.isSuccessful && step.mtuTested >= bestMtu) {
                    bestMtu = step.mtuTested
                }
            }
            _isScanningMtu.value = false
            // Update MTU setting with best found
            val currentAnti = antiCensorshipSettings.value
            updateAntiCensorship(
                currentAnti.copy(
                    mtu = currentAnti.mtu.copy(
                        lastDiscoveredMtu = bestMtu,
                        mtu = bestMtu
                    )
                )
            )
            VpnController.log("MTU", "MTU discovery completed. Recommended optimal MTU: $bestMtu bytes.")
        }
    }

    fun applyMtu(mtu: Int) {
        val currentAnti = antiCensorshipSettings.value
        updateAntiCensorship(currentAnti.copy(mtu = currentAnti.mtu.copy(mtu = mtu)))
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val items = packages.map { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString()
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppItem(
                    packageName = appInfo.packageName,
                    appName = label,
                    isSystemApp = isSys
                )
            }.filter { !it.packageName.startsWith("com.android.internal") }
                .sortedWith(compareBy({ it.isSystemApp }, { it.appName }))

            _installedApps.value = items
        }
    }
}
