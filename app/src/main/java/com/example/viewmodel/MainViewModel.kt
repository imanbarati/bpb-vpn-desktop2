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
import com.example.service.BatteryDrainMonitor
import com.example.service.BatteryDrainStats
import com.example.service.CleanIpResult
import com.example.service.CleanIpScanner
import com.example.service.MtuProbeStep
import com.example.service.MtuScanner
import com.example.service.NetworkSpeedTester
import com.example.service.QrCodeDecoder
import com.example.service.SpeedTestSessionState
import com.example.service.SpeedTestStatus
import com.example.service.UriParser
import com.example.service.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    val hasAcceptedVpnDisclosure: StateFlow<Boolean> = prefsRepo.hasAcceptedVpnDisclosure

    val connectionState: StateFlow<ConnectionState> = VpnController.connectionState
    val bandwidthStats: StateFlow<BandwidthStats> = VpnController.bandwidthStats
    val logs: StateFlow<List<LogEntry>> = VpnController.logs
    val batteryStats: StateFlow<BatteryDrainStats> = BatteryDrainMonitor.stats

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

    val lowestLatencyServerId: StateFlow<String?> = servers.map { list ->
        list.filter { it.pingMs > 0 }.minByOrNull { it.pingMs }?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isPeriodicPingRunning = MutableStateFlow(true)
    val isPeriodicPingRunning: StateFlow<Boolean> = _isPeriodicPingRunning.asStateFlow()

    private val _speedTestSession = MutableStateFlow(SpeedTestSessionState())
    val speedTestSession: StateFlow<SpeedTestSessionState> = _speedTestSession.asStateFlow()

    private var periodicPingJob: kotlinx.coroutines.Job? = null
    private var speedTestJob: kotlinx.coroutines.Job? = null

    init {
        loadInstalledApps()
        startPeriodicPingTask()
    }

    fun startPeriodicPingTask() {
        periodicPingJob?.cancel()
        periodicPingJob = viewModelScope.launch(Dispatchers.IO) {
            _isPeriodicPingRunning.value = true
            while (isActive && _isPeriodicPingRunning.value) {
                val currentServers = servers.value
                if (currentServers.isNotEmpty()) {
                    currentServers.forEach { server ->
                        val targetHost = server.customCleanIp.ifBlank { server.address }
                        val rtt = VpnController.pingHost(targetHost, server.port, 1200)
                        serverDao.updatePing(server.id, rtt)
                    }
                }
                kotlinx.coroutines.delay(20_000) // Periodic refresh every 20 seconds
            }
        }
    }

    fun togglePeriodicPing() {
        if (_isPeriodicPingRunning.value) {
            _isPeriodicPingRunning.value = false
            periodicPingJob?.cancel()
            periodicPingJob = null
        } else {
            startPeriodicPingTask()
        }
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
            val targetHost = server.customCleanIp.ifBlank { server.address }
            val rtt = VpnController.pingHost(targetHost, server.port)
            serverDao.updatePing(server.id, rtt)
        }
    }

    fun pingAllServers() {
        viewModelScope.launch(Dispatchers.IO) {
            servers.value.forEach { server ->
                val targetHost = server.customCleanIp.ifBlank { server.address }
                val rtt = VpnController.pingHost(targetHost, server.port)
                serverDao.updatePing(server.id, rtt)
            }
        }
    }

    fun startNetworkSpeedTest(tester: NetworkSpeedTester = NetworkSpeedTester()) {
        speedTestJob?.cancel()
        val snapshot = servers.value
        if (snapshot.isEmpty()) return

        speedTestJob = viewModelScope.launch {
            VpnController.log("SPEED_TEST", "Starting Retrofit + Coroutines speed benchmark across ${snapshot.size} servers")
            tester.benchmarkServers(snapshot).collect { session ->
                _speedTestSession.value = session
                session.results
                    .filter { it.status == SpeedTestStatus.COMPLETED || it.status == SpeedTestStatus.FAILED }
                    .forEach { result ->
                        serverDao.updatePing(result.serverId, result.pingMs)
                    }
            }
            val fastest = _speedTestSession.value.results.firstOrNull { it.serverId == _speedTestSession.value.fastestServerId }
            if (fastest != null) {
                VpnController.log(
                    "SPEED_TEST",
                    "Fastest node: ${fastest.serverName} (${fastest.pingMs} ms, DL=${String.format(java.util.Locale.US, "%.1f", fastest.downloadMbps)} Mbps)"
                )
            }
        }
    }

    fun stopNetworkSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = null
        _speedTestSession.value = _speedTestSession.value.copy(isRunning = false, currentServerName = null)
    }

    fun selectFastestSpeedTestServer(): ServerProfile? {
        val fastestId = _speedTestSession.value.fastestServerId ?: lowestLatencyServerId.value ?: return null
        val target = servers.value.firstOrNull { it.id == fastestId } ?: return null
        selectServer(target)
        VpnController.log("SPEED_TEST", "Selected fastest server before connecting: ${target.name} (${target.pingMs} ms)")
        return target
    }

    fun importFromUri(rawUri: String): Boolean {
        val extracted = QrCodeDecoder.extractSupportedConfigUri(rawUri) ?: rawUri
        val profile = UriParser.parse(extracted) ?: return false
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

    private var hasTriggeredLaunchConnect = false

    fun checkAndTriggerConnectOnLaunch(context: Context, onRequestPermission: (android.content.Intent) -> Unit) {
        if (hasTriggeredLaunchConnect) return
        hasTriggeredLaunchConnect = true

        val settings = generalSettings.value
        if (settings.connectOnLaunch && connectionState.value.status == VpnStatus.DISCONNECTED) {
            val server = selectedServer.value
            if (server != null) {
                val prepareIntent = android.net.VpnService.prepare(context)
                if (prepareIntent != null) {
                    onRequestPermission(prepareIntent)
                } else {
                    toggleVpn(context)
                }
                VpnController.log("STARTUP", "Triggered 'Connect on Launch' for server: ${server.name}")
            }
        }
    }

    fun triggerTestBatteryAlert(context: Context) {
        BatteryDrainMonitor.triggerTestAlert(context)
    }

    fun updateGeneral(settings: GeneralSettings) {
        prefsRepo.updateGeneralSettings(settings)
    }

    fun setWizardCompleted() {
        prefsRepo.setWizardCompleted(true)
    }

    fun acceptVpnDisclosure() {
        prefsRepo.setVpnDisclosureAccepted(true)
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
