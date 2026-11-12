import 'dart:async';
import 'package:flutter/material.dart';
import '../services/connectivity_tester_service.dart';
import '../services/throughput_monitor_service.dart';
import '../services/vpn_channel.dart';
import 'widgets/log_overlay.dart';
import 'widgets/speed_chart_fl.dart';

import '../services/battery_monitor_service.dart';
import '../services/settings_service.dart';
import 'settings_page.dart';

/// BPB VPN Home Screen.
/// Displays real-time download and upload speeds using 'fl_chart',
/// active connection status, metrics, and troubleshooting log overlay.
class HomePage extends StatefulWidget {
  final VoidCallback? onNavigateToServers;

  const HomePage({super.key, this.onNavigateToServers});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final VpnService _vpnService = VpnService();
  final ThroughputMonitorService _throughputService = ThroughputMonitorService();
  final ConnectivityTesterService _connectivityTester = ConnectivityTesterService();
  final SettingsService _settingsService = SettingsService();

  StreamSubscription? _statusSubscription;
  StreamSubscription? _speedSubscription;
  StreamSubscription? _settingsSubscription;

  bool _isConnected = false;
  bool _hasCheckedLaunchConnect = false;
  SpeedTelemetry _currentTelemetry = SpeedTelemetry(
    downloadSpeedBps: 0.0,
    uploadSpeedBps: 0.0,
    totalReceivedBytes: 0,
    totalSentBytes: 0,
    sessionDurationSeconds: 0,
  );
  VpnServerEndpoint? _selectedServer;

  @override
  void initState() {
    super.initState();
    _subscribeServices();
    _checkConnectOnLaunch();
  }

  Future<void> _checkConnectOnLaunch() async {
    if (_hasCheckedLaunchConnect) return;
    _hasCheckedLaunchConnect = true;

    final settings = await _settingsService.loadSettings();
    if (settings.connectOnLaunch && !_vpnService.isRunning) {
      await Future.delayed(const Duration(milliseconds: 600));
      if (mounted && !_vpnService.isRunning) {
        await _toggleConnection();
      }
    }
  }

  void _subscribeServices() {
    _isConnected = _vpnService.isRunning;
    _selectedServer = _connectivityTester.currentSettings.selectedServer;

    _statusSubscription = _vpnService.statusStream.listen((connected) {
      if (mounted) {
        setState(() {
          _isConnected = connected;
        });
      }
    });

    _speedSubscription = _throughputService.speedStream.listen((telemetry) {
      if (mounted) {
        setState(() {
          _currentTelemetry = telemetry;
        });
      }
    });

    _settingsSubscription = _connectivityTester.settingsStream.listen((settings) {
      if (mounted) {
        setState(() {
          _selectedServer = settings.selectedServer;
        });
      }
    });
  }

  @override
  void dispose() {
    _statusSubscription?.cancel();
    _speedSubscription?.cancel();
    _settingsSubscription?.cancel();
    super.dispose();
  }

  Future<void> _toggleConnection() async {
    if (_isConnected) {
      await _vpnService.stop();
    } else {
      final configJson = '''{
        "outbound": {
          "protocol": "${_selectedServer?.protocol ?? 'vless'}",
          "server": "${_selectedServer?.host ?? '162.159.192.1'}",
          "port": ${_selectedServer?.port ?? 443}
        }
      }''';
      await _vpnService.start(
        configJson: configJson,
        serverName: _selectedServer?.name ?? 'BPB Fast Node',
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    const bgColor = Color(0xFF090D1A);
    const cardBg = Color(0xFF0C1322);
    const borderColor = Color(0xFF1E293B);
    const cyan = Color(0xFF00E5FF);
    const electricBlue = Color(0xFF2979FF);
    const emerald = Color(0xFF00E676);

    return Scaffold(
      backgroundColor: bgColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'BPB FREEDOM VPN',
              style: TextStyle(
                color: cyan,
                fontSize: 11,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.5,
              ),
            ),
            Text(
              _isConnected ? 'Protected & Encrypted' : 'Unprotected Connection',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 14),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: (_isConnected ? emerald : const Color(0xFFFF5252)).withOpacity(0.15),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: (_isConnected ? emerald : const Color(0xFFFF5252)).withOpacity(0.4),
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 7,
                  height: 7,
                  decoration: BoxDecoration(
                    color: _isConnected ? emerald : const Color(0xFFFF5252),
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  _isConnected ? 'CONNECTED' : 'DISCONNECTED',
                  style: TextStyle(
                    color: _isConnected ? emerald : const Color(0xFFFF5252),
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.settings, color: cyan, size: 20),
            tooltip: 'Preferences & Battery Guard',
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const SettingsPage()),
              );
            },
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Center Connect / Disconnect Action Button
            Center(
              child: GestureDetector(
                onTap: _toggleConnection,
                child: Container(
                  width: 140,
                  height: 140,
                  margin: const EdgeInsets.symmetric(vertical: 12),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: cardBg,
                    border: Border.all(
                      color: _isConnected ? emerald : cyan,
                      width: 2.5,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: (_isConnected ? emerald : cyan).withOpacity(0.35),
                        blurRadius: 18,
                        spreadRadius: 2,
                      ),
                    ],
                  ),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        _isConnected ? Icons.lock : Icons.lock_open,
                        size: 38,
                        color: _isConnected ? emerald : cyan,
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _isConnected ? 'CONNECTED' : 'CONNECT',
                        style: TextStyle(
                          color: _isConnected ? emerald : Colors.white,
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1.2,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),

            // Active Server Card Selector
            InkWell(
              onTap: widget.onNavigateToServers,
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: cardBg,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: borderColor),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 36,
                      height: 36,
                      decoration: const BoxDecoration(
                        color: Color(0xFF17223D),
                        shape: BoxShape.circle,
                      ),
                      child: const Center(
                        child: Text('🌐', style: TextStyle(fontSize: 18)),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _selectedServer?.name ?? 'Select Server Node',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          Text(
                            '${_selectedServer?.protocol.toUpperCase() ?? 'VLESS'} • ${_selectedServer?.host ?? 'Tap to select'}',
                            style: const TextStyle(
                              color: Color(0xFF94A3B8),
                              fontSize: 11,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: const Color(0xFF17223D),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        _selectedServer?.latencyDisplay ?? '-- ms',
                        style: const TextStyle(
                          color: emerald,
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                    const Icon(Icons.chevron_right, color: cyan, size: 20),
                  ],
                ),
              ),
            ),

            // Speed & Latency Metric Cards
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
              child: Row(
                children: [
                  Expanded(
                    child: _MetricTile(
                      title: 'DOWNLOAD',
                      value: _currentTelemetry.formattedDownloadSpeed,
                      icon: Icons.download,
                      color: cyan,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _MetricTile(
                      title: 'UPLOAD',
                      value: _currentTelemetry.formattedUploadSpeed,
                      icon: Icons.upload,
                      color: electricBlue,
                    ),
                  ),
                ],
              ),
            ),

            // Data Visualization Component using 'fl_chart'
            const SpeedChartFlChart(
              height: 220,
              maxDataPoints: 30,
            ),

            // Subprocess Output Troubleshooting Log Overlay
            const LogOverlay(
              initialExpanded: false,
              maxHeight: 220,
            ),
          ],
        ),
      ),
    );
  }
}

class _MetricTile extends StatelessWidget {
  final String title;
  final String value;
  final IconData icon;
  final Color color;

  const _MetricTile({
    required this.title,
    required this.value,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF0C1322),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFF1E293B)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 14, color: color),
              const SizedBox(width: 6),
              Text(
                title,
                style: const TextStyle(
                  color: Color(0xFF64748B),
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 0.8,
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 17,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
