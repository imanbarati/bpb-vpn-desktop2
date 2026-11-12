import 'dart:async';
import 'package:shared_preferences/shared_preferences.dart';

class VpnAppSettings {
  final bool connectOnLaunch;
  final bool batteryMonitorEnabled;
  final int batteryDrainThresholdPercent;
  final bool autoConnectOnBoot;
  final bool killSwitchEnabled;
  final String dnsProvider;

  const VpnAppSettings({
    this.connectOnLaunch = false,
    this.batteryMonitorEnabled = true,
    this.batteryDrainThresholdPercent = 3,
    this.autoConnectOnBoot = false,
    this.killSwitchEnabled = false,
    this.dnsProvider = 'Cloudflare (1.1.1.1)',
  });

  VpnAppSettings copyWith({
    bool? connectOnLaunch,
    bool? batteryMonitorEnabled,
    int? batteryDrainThresholdPercent,
    bool? autoConnectOnBoot,
    bool? killSwitchEnabled,
    String? dnsProvider,
  }) {
    return VpnAppSettings(
      connectOnLaunch: connectOnLaunch ?? this.connectOnLaunch,
      batteryMonitorEnabled: batteryMonitorEnabled ?? this.batteryMonitorEnabled,
      batteryDrainThresholdPercent:
          batteryDrainThresholdPercent ?? this.batteryDrainThresholdPercent,
      autoConnectOnBoot: autoConnectOnBoot ?? this.autoConnectOnBoot,
      killSwitchEnabled: killSwitchEnabled ?? this.killSwitchEnabled,
      dnsProvider: dnsProvider ?? this.dnsProvider,
    );
  }
}

/// Persistent Settings service using SharedPreferences for user preferences
/// including 'Connect on Launch' and background battery drain monitoring.
class SettingsService {
  static final SettingsService _instance = SettingsService._internal();
  factory SettingsService() => _instance;

  SettingsService._internal() {
    loadSettings();
  }

  static const String keyConnectOnLaunch = 'connect_on_launch';
  static const String keyBatteryMonitor = 'battery_monitor_enabled';
  static const String keyBatteryThreshold = 'battery_drain_threshold';
  static const String keyAutoConnectBoot = 'auto_connect_boot';
  static const String keyKillSwitch = 'kill_switch';
  static const String keyDnsProvider = 'dns_provider';

  final StreamController<VpnAppSettings> _settingsController =
      StreamController<VpnAppSettings>.broadcast();
  Stream<VpnAppSettings> get settingsStream => _settingsController.stream;

  VpnAppSettings _currentSettings = const VpnAppSettings();
  VpnAppSettings get currentSettings => _currentSettings;

  bool _isLoaded = false;
  bool get isLoaded => _isLoaded;

  Future<VpnAppSettings> loadSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _currentSettings = VpnAppSettings(
        connectOnLaunch: prefs.getBool(keyConnectOnLaunch) ?? false,
        batteryMonitorEnabled: prefs.getBool(keyBatteryMonitor) ?? true,
        batteryDrainThresholdPercent: prefs.getInt(keyBatteryThreshold) ?? 3,
        autoConnectOnBoot: prefs.getBool(keyAutoConnectBoot) ?? false,
        killSwitchEnabled: prefs.getBool(keyKillSwitch) ?? false,
        dnsProvider: prefs.getString(keyDnsProvider) ?? 'Cloudflare (1.1.1.1)',
      );
    } catch (_) {
      // In tests/memory environments fallback to defaults
    }
    _isLoaded = true;
    _settingsController.add(_currentSettings);
    return _currentSettings;
  }

  Future<void> setConnectOnLaunch(bool enabled) async {
    _currentSettings = _currentSettings.copyWith(connectOnLaunch = enabled);
    _settingsController.add(_currentSettings);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(keyConnectOnLaunch, enabled);
    } catch (_) {}
  }

  Future<void> setBatteryMonitorEnabled(bool enabled) async {
    _currentSettings = _currentSettings.copyWith(batteryMonitorEnabled = enabled);
    _settingsController.add(_currentSettings);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(keyBatteryMonitor, enabled);
    } catch (_) {}
  }

  Future<void> setBatteryDrainThreshold(int thresholdPercent) async {
    _currentSettings =
        _currentSettings.copyWith(batteryDrainThresholdPercent = thresholdPercent);
    _settingsController.add(_currentSettings);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(keyBatteryThreshold, thresholdPercent);
    } catch (_) {}
  }

  Future<void> setKillSwitch(bool enabled) async {
    _currentSettings = _currentSettings.copyWith(killSwitchEnabled = enabled);
    _settingsController.add(_currentSettings);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(keyKillSwitch, enabled);
    } catch (_) {}
  }
}
