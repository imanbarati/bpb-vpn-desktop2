import 'dart:async';
import 'dart:io';
import 'package:flutter/services.dart';
import 'throughput_monitor_service.dart';
import 'vpn_channel.dart';

class BatteryMonitorTelemetry {
  final int batteryLevelPercent;
  final bool isCharging;
  final bool isTunnelConnected;
  final bool isIdlingInBackground;
  final int idleDurationSeconds;
  final int batteryDroppedDuringIdle;
  final bool hasTriggeredAlert;
  final String? alertMessage;
  final DateTime timestamp;

  BatteryMonitorTelemetry({
    required this.batteryLevelPercent,
    required this.isCharging,
    required this.isTunnelConnected,
    required this.isIdlingInBackground,
    required this.idleDurationSeconds,
    required this.batteryDroppedDuringIdle,
    required this.hasTriggeredAlert,
    this.alertMessage,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();

  String get formattedStatus {
    if (!isTunnelConnected) return 'Tunnel Disconnected';
    if (isIdlingInBackground) {
      final mins = idleDurationSeconds ~/ 60;
      return 'Idling in background (${mins}m • Drain: $batteryDroppedDuringIdle%)';
    }
    return 'Active Data Traffic (Normal Power)';
  }
}

/// Service that monitors the VPN tunnel in the background and notifies the user
/// if the VPN is consuming excessive battery while idling in the background.
class BatteryMonitorService {
  static final BatteryMonitorService _instance = BatteryMonitorService._internal();
  factory BatteryMonitorService() => _instance;

  BatteryMonitorService._internal() {
    _initMonitor();
  }

  static const MethodChannel _batteryChannel = MethodChannel('com.bpb.vpn/battery');

  final VpnService _vpnService = VpnService();
  final ThroughputMonitorService _throughputService = ThroughputMonitorService();

  final StreamController<BatteryMonitorTelemetry> _telemetryController =
      StreamController<BatteryMonitorTelemetry>.broadcast();
  final StreamController<String> _alertNotificationController =
      StreamController<String>.broadcast();

  Stream<BatteryMonitorTelemetry> get telemetryStream => _telemetryController.stream;
  Stream<String> get alertStream => _alertNotificationController.stream;

  Timer? _pollingTimer;
  StreamSubscription? _vpnStatusSub;

  bool _isMonitoringEnabled = true;
  int _batteryDrainThresholdPercent = 3;

  int _currentBatteryLevel = 85;
  bool _isCharging = false;
  int _startIdleBatteryLevel = 85;
  int _idleSeconds = 0;
  bool _hasAlerted = false;
  String? _lastAlert;

  BatteryMonitorTelemetry _currentTelemetry = BatteryMonitorTelemetry(
    batteryLevelPercent: 85,
    isCharging: false,
    isTunnelConnected: false,
    isIdlingInBackground: false,
    idleDurationSeconds: 0,
    batteryDroppedDuringIdle: 0,
    hasTriggeredAlert: false,
  );

  BatteryMonitorTelemetry get currentTelemetry => _currentTelemetry;

  void setMonitoringEnabled(bool enabled) {
    _isMonitoringEnabled = enabled;
    if (!enabled) {
      _stopPolling();
    } else if (_vpnService.isRunning) {
      _startPolling();
    }
  }

  void setDrainThreshold(int thresholdPercent) {
    _batteryDrainThresholdPercent = thresholdPercent;
  }

  void _initMonitor() {
    _vpnStatusSub = _vpnService.statusStream.listen((isRunning) {
      if (isRunning && _isMonitoringEnabled) {
        _startPolling();
      } else {
        _stopPolling();
      }
    });

    if (_vpnService.isRunning && _isMonitoringEnabled) {
      _startPolling();
    }
  }

  void _startPolling() {
    _pollingTimer?.cancel();
    _startIdleBatteryLevel = _currentBatteryLevel;
    _idleSeconds = 0;
    _hasAlerted = false;
    _lastAlert = null;

    _pollingTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      _evaluateBatteryDrain();
    });
  }

  void _stopPolling() {
    _pollingTimer?.cancel();
    _pollingTimer = null;
    _idleSeconds = 0;

    _currentTelemetry = BatteryMonitorTelemetry(
      batteryLevelPercent: _currentBatteryLevel,
      isCharging: _isCharging,
      isTunnelConnected: false,
      isIdlingInBackground: false,
      idleDurationSeconds: 0,
      batteryDroppedDuringIdle: 0,
      hasTriggeredAlert: false,
      alertMessage: _lastAlert,
    );
    _telemetryController.add(_currentTelemetry);
  }

  Future<void> _evaluateBatteryDrain() async {
    if (!_vpnService.isRunning || !_isMonitoringEnabled) {
      _stopPolling();
      return;
    }

    // Query platform battery level if supported
    try {
      final int? lvl = await _batteryChannel.invokeMethod<int>('getBatteryLevel');
      if (lvl != null && lvl > 0) {
        _currentBatteryLevel = lvl;
      }
      final bool? charging = await _batteryChannel.invokeMethod<bool>('isCharging');
      if (charging != null) {
        _isCharging = charging;
      }
    } catch (_) {
      // In desktop/mock fallback environments
    }

    final throughput = _throughputService.currentTelemetry;
    // Tunnel is idling if both download and upload speeds are under 10 KB/s
    final isTrafficIdle = throughput.downloadSpeedKbps < 10.0 && throughput.uploadSpeedKbps < 10.0;

    if (isTrafficIdle) {
      _idleSeconds += 5;
      final drop = (!_isCharging && _startIdleBatteryLevel > _currentBatteryLevel)
          ? (_startIdleBatteryLevel - _currentBatteryLevel)
          : 0;

      final exceededDrop = drop >= _batteryDrainThresholdPercent;
      // Also detect abnormal background drain during prolonged idle (> 60s)
      final exceededIdleRate = _idleSeconds >= 60 && drop >= 1;

      if (!_isCharging && (exceededDrop || exceededIdleRate) && !_hasAlerted) {
        _hasAlerted = true;
        _lastAlert = 'Excessive battery drain detected: $drop% drop while idling for ${_idleSeconds ~/ 60}m.';
        _alertNotificationController.add(_lastAlert!);
        _dispatchSystemNotification(_lastAlert!);
      }

      _currentTelemetry = BatteryMonitorTelemetry(
        batteryLevelPercent: _currentBatteryLevel,
        isCharging: _isCharging,
        isTunnelConnected: true,
        isIdlingInBackground: true,
        idleDurationSeconds: _idleSeconds,
        batteryDroppedDuringIdle: drop,
        hasTriggeredAlert: _hasAlerted,
        alertMessage: _lastAlert,
      );
    } else {
      // Active data flow resets idle counter
      _startIdleBatteryLevel = _currentBatteryLevel;
      _idleSeconds = 0;

      _currentTelemetry = BatteryMonitorTelemetry(
        batteryLevelPercent: _currentBatteryLevel,
        isCharging: _isCharging,
        isTunnelConnected: true,
        isIdlingInBackground: false,
        idleDurationSeconds: 0,
        batteryDroppedDuringIdle: 0,
        hasTriggeredAlert: _hasAlerted,
        alertMessage: _lastAlert,
      );
    }

    _telemetryController.add(_currentTelemetry);
  }

  /// Manually trigger a test notification for UI verification
  void triggerTestAlert() {
    _hasAlerted = true;
    _lastAlert = 'Test Warning: VPN has consumed 4% excessive battery while idling in background.';
    _alertNotificationController.add(_lastAlert!);
    _dispatchSystemNotification(_lastAlert!);

    _currentTelemetry = BatteryMonitorTelemetry(
      batteryLevelPercent: _currentBatteryLevel,
      isCharging: false,
      isTunnelConnected: true,
      isIdlingInBackground: true,
      idleDurationSeconds: 15 * 60,
      batteryDroppedDuringIdle: 4,
      hasTriggeredAlert: true,
      alertMessage: _lastAlert,
    );
    _telemetryController.add(_currentTelemetry);
  }

  void _dispatchSystemNotification(String message) {
    try {
      _batteryChannel.invokeMethod('showBatteryDrainNotification', {
        'title': '⚠️ High Battery Drain in Background',
        'message': message,
      });
    } catch (_) {}
  }

  void dispose() {
    _vpnStatusSub?.cancel();
    _pollingTimer?.cancel();
    _telemetryController.close();
    _alertNotificationController.close();
  }
}
