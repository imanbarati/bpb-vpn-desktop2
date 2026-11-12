import 'dart:async';
import 'package:flutter/material.dart';
import '../services/connectivity_tester_service.dart';

/// Server selection UI displaying configured VLESS/VMess server endpoints
/// with real-time latency status updated by the background connectivity tester.
class ServersPage extends StatefulWidget {
  final VoidCallback? onBack;

  const ServersPage({super.key, this.onBack});

  @override
  State<ServersPage> createState() => _ServersPageState();
}

class _ServersPageState extends State<ServersPage> {
  final ConnectivityTesterService _connectivityTester = ConnectivityTesterService();
  StreamSubscription? _settingsSub;
  VpnSettingsModel _settings = VpnSettingsModel(servers: []);
  bool _sortByLatency = false;

  @override
  void initState() {
    super.initState();
    _settings = _connectivityTester.currentSettings;
    _settingsSub = _connectivityTester.settingsStream.listen((s) {
      if (mounted) {
        setState(() {
          _settings = s;
        });
      }
    });
  }

  @override
  void dispose() {
    _settingsSub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const bgColor = Color(0xFF090D1A);
    const cardBg = Color(0xFF0C1322);
    const cyan = Color(0xFF00E5FF);
    const emerald = Color(0xFF00E676);

    final displayServers = List<VpnServerEndpoint>.from(_settings.servers);
    if (_sortByLatency) {
      displayServers.sort((a, b) {
        final aPing = a.latencyMs > 0 ? a.latencyMs : 9999;
        final bPing = b.latencyMs > 0 ? b.latencyMs : 9999;
        return aPing.compareTo(bPing);
      });
    }

    return Scaffold(
      backgroundColor: bgColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: widget.onBack != null
            ? IconButton(
                icon: const Icon(Icons.arrow_back, color: Colors.white),
                onPressed: widget.onBack,
              )
            : null,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'SERVER SELECTION',
              style: TextStyle(
                color: cyan,
                fontSize: 11,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.5,
              ),
            ),
            Text(
              '${_settings.servers.length} Configured Endpoints',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
        actions: [
          // Auto-Ping status pill
          Container(
            margin: const EdgeInsets.symmetric(vertical: 12),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: cyan.withOpacity(0.12),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: cyan.withOpacity(0.35)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 6,
                  height: 6,
                  decoration: const BoxDecoration(color: cyan, shape: BoxShape.circle),
                ),
                const SizedBox(width: 4),
                const Text(
                  'Auto-Ping: 20s',
                  style: TextStyle(color: cyan, fontSize: 10, fontWeight: FontWeight.bold),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          // Sort toggle button
          IconButton(
            icon: Icon(
              Icons.bolt,
              color: _sortByLatency ? emerald : const Color(0xFF94A3B8),
            ),
            tooltip: 'Sort by Lowest Latency',
            onPressed: () {
              setState(() {
                _sortByLatency = !_sortByLatency;
              });
            },
          ),
          const SizedBox(width: 6),
        ],
      ),
      body: ListView.builder(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        itemCount: displayServers.length,
        itemBuilder: (context, index) {
          final server = displayServers[index];
          final isSelected = server.id == _settings.selectedServerId;
          final isFastest = server.isFastest;

          Color pingColor = const Color(0xFF64748B);
          if (server.latencyMs > 0 && server.latencyMs < 100) {
            pingColor = emerald;
          } else if (server.latencyMs <= 200) {
            pingColor = const Color(0xFFFFB300);
          } else if (server.latencyMs > 200) {
            pingColor = const Color(0xFFFF5252);
          }

          return InkWell(
            onTap: () {
              _connectivityTester.selectServer(server.id);
            },
            borderRadius: BorderRadius.circular(14),
            child: Container(
              margin: const EdgeInsets.only(bottom: 10),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: cardBg,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: isFastest
                      ? emerald
                      : (isSelected ? cyan : const Color(0xFF1E293B)),
                  width: isFastest || isSelected ? 1.6 : 1.0,
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (isFastest) ...[
                    Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: emerald.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(color: emerald.withOpacity(0.4)),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.bolt, size: 12, color: emerald),
                          SizedBox(width: 4),
                          Text(
                            'FASTEST NODE (LOWEST LATENCY)',
                            style: TextStyle(
                              color: emerald,
                              fontSize: 9.5,
                              fontWeight: FontWeight.bold,
                              letterSpacing: 0.8,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                  Row(
                    children: [
                      Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: const Color(0xFF17223D),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Center(
                          child: Text(
                            server.protocol.substring(0, 1).toUpperCase(),
                            style: const TextStyle(
                              color: cyan,
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              server.name,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              '${server.protocol.toUpperCase()} • ${server.host}:${server.port}',
                              style: const TextStyle(
                                color: Color(0xFF94A3B8),
                                fontSize: 11,
                              ),
                            ),
                          ],
                        ),
                      ),
                      // Latency & Status Capsule
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: const Color(0xFF070B14),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: pingColor.withOpacity(0.3)),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 6,
                              height: 6,
                              decoration: BoxDecoration(color: pingColor, shape: BoxShape.circle),
                            ),
                            const SizedBox(width: 6),
                            Text(
                              '${server.latencyDisplay} (${server.qualityBadge})',
                              style: TextStyle(
                                color: pingColor,
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
