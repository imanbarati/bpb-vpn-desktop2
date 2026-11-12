import 'package:flutter/material.dart';
import '../services/battery_monitor_service.dart';
import '../services/settings_service.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final SettingsService _settingsService = SettingsService();
  final BatteryMonitorService _batteryMonitor = BatteryMonitorService();

  late VpnAppSettings _settings;
  late BatteryMonitorTelemetry _batteryTelemetry;

  @override
  void initState() {
    super.initState();
    _settings = _settingsService.currentSettings;
    _batteryTelemetry = _batteryMonitor.currentTelemetry;

    _settingsService.settingsStream.listen((settings) {
      if (mounted) setState(() => _settings = settings);
    });

    _batteryMonitor.telemetryStream.listen((telemetry) {
      if (mounted) setState(() => _batteryTelemetry = telemetry);
    });

    _batteryMonitor.alertStream.listen((alert) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(alert),
            backgroundColor: const Color(0xFFFFB300),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    const bgColor = Color(0xFF090D1A);
    const cardBg = Color(0xFF0C1322);
    const borderColor = Color(0xFF1E293B);
    const cyan = Color(0xFF00E5FF);
    const amber = Color(0xFFFFB300);
    const emerald = Color(0xFF00E676);

    return Scaffold(
      backgroundColor: bgColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        title: const Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'PREFERENCES & POWER',
              style: TextStyle(
                color: cyan,
                fontSize: 10,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.5,
              ),
            ),
            Text(
              'Settings',
              style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold),
            ),
          ],
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        children: [
          // 1. Startup & Automation Section
          _buildSectionHeader('STARTUP & AUTOMATION', Icons.play_arrow),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: cardBg,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: borderColor),
            ),
            child: Column(
              children: [
                // Connect on Launch Toggle
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Text(
                                'Connect on Launch',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 13,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(width: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                                decoration: BoxDecoration(
                                  color: cyan.withOpacity(0.15),
                                  borderRadius: BorderRadius.circular(4),
                                ),
                                child: const Text(
                                  'AUTO',
                                  style: TextStyle(
                                    color: cyan,
                                    fontSize: 9,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 2),
                          const Text(
                            'Initiates connection immediately upon app startup (stored in SharedPreferences)',
                            style: TextStyle(color: Color(0xFF94A3B8), fontSize: 11),
                          ),
                        ],
                      ),
                    ),
                    Switch(
                      value: _settings.connectOnLaunch,
                      onChanged: (val) {
                        _settingsService.setConnectOnLaunch(val);
                      },
                      activeColor: cyan,
                    ),
                  ],
                ),
                const Divider(color: borderColor, height: 18),
                // Kill Switch
                Row(
                  children: [
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Kill Switch (Block Non-VPN)',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          SizedBox(height: 2),
                          Text(
                            'Disables unencrypted traffic if VPN drops unexpectedly',
                            style: TextStyle(color: Color(0xFF94A3B8), fontSize: 11),
                          ),
                        ],
                      ),
                    ),
                    Switch(
                      value: _settings.killSwitchEnabled,
                      onChanged: (val) {
                        _settingsService.setKillSwitch(val);
                      },
                      activeColor: const Color(0xFFFF5252),
                    ),
                  ],
                ),
              ],
            ),
          ),

          const SizedBox(height: 16),

          // 2. Background Battery Service Monitoring
          _buildSectionHeader('BACKGROUND BATTERY GUARD', Icons.battery_alert),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: cardBg,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: borderColor),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Background Idle Drain Warning',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          SizedBox(height: 2),
                          Text(
                            'Notifies you if VPN tunnel consumes excessive battery while idling in background',
                            style: TextStyle(color: Color(0xFF94A3B8), fontSize: 11),
                          ),
                        ],
                      ),
                    ),
                    Switch(
                      value: _settings.batteryMonitorEnabled,
                      onChanged: (val) {
                        _settingsService.setBatteryMonitorEnabled(val);
                        _batteryMonitor.setMonitoringEnabled(val);
                      },
                      activeColor: amber,
                    ),
                  ],
                ),

                const SizedBox(height: 10),

                // Live Battery Status Box
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFF10192C),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: borderColor),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            'Battery Level: ${_batteryTelemetry.batteryLevelPercent}% ${_batteryTelemetry.isCharging ? "(Charging ⚡)" : ""}',
                            style: TextStyle(
                              color: _batteryTelemetry.batteryLevelPercent > 20 ? emerald : amber,
                              fontSize: 11.5,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: (_batteryTelemetry.isIdlingInBackground ? amber : cyan).withOpacity(0.15),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              _batteryTelemetry.isIdlingInBackground ? 'IDLING' : 'ACTIVE',
                              style: TextStyle(
                                color: _batteryTelemetry.isIdlingInBackground ? amber : cyan,
                                fontSize: 9.5,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _batteryTelemetry.formattedStatus,
                        style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 10.5),
                      ),
                      if (_batteryTelemetry.hasTriggeredAlert && _batteryTelemetry.alertMessage != null) ...[
                        const SizedBox(height: 4),
                        Text(
                          '⚠️ ${_batteryTelemetry.alertMessage}',
                          style: const TextStyle(color: amber, fontSize: 10.5, fontWeight: FontWeight.bold),
                        ),
                      ],
                      const SizedBox(height: 8),
                      // Trigger Test Alert Button
                      SizedBox(
                        width: double.infinity,
                        height: 34,
                        child: OutlinedButton.icon(
                          onPressed: () {
                            _batteryMonitor.triggerTestAlert();
                          },
                          icon: const Icon(Icons.notifications_active, size: 14, color: amber),
                          label: const Text(
                            'Trigger Test Battery Alert Notification',
                            style: TextStyle(color: amber, fontSize: 11, fontWeight: FontWeight.bold),
                          ),
                          style: OutlinedButton.styleFrom(
                            side: BorderSide(color: amber.withOpacity(0.4)),
                            shape: RoundedCornerShape(8),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title, IconData icon) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6, top: 4),
      child: Row(
        children: [
          Icon(icon, size: 13, color: const Color(0xFF00E5FF)),
          const SizedBox(width: 5),
          Text(
            title,
            style: const TextStyle(
              color: Color(0xFF94A3B8),
              fontSize: 10.5,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.0,
            ),
          ),
        ],
      ),
    );
  }
}
