import 'dart:async';
import 'dart:io';
import 'package:flutter/services.dart';
import 'desktop_vpn_controller.dart';

/// Unified VPN Service bridging Windows desktop subprocesses and Android native VpnService.
/// Provides a unified API for the UI layer regardless of the operating system.
class VpnService {
  static final VpnService _instance = VpnService._internal();
  factory VpnService() => _instance;

  VpnService._internal() {
    _initChannels();
  }

  // Android platform channel definition matching native BpbVpnService
  static const MethodChannel _androidChannel = MethodChannel('com.bpb.vpn/service');
  static const EventChannel _androidLogChannel = EventChannel('com.bpb.vpn/logs');
  static const EventChannel _androidStatusChannel = EventChannel('com.bpb.vpn/status');
  static const EventChannel _androidThroughputChannel = EventChannel('com.bpb.vpn/throughput');

  // Desktop controller for Windows
  final DesktopVpnController _desktopController = DesktopVpnController();
  DesktopVpnController get desktopController => _desktopController;

  final StreamController<String> _logController = StreamController<String>.broadcast();
  final StreamController<bool> _statusController = StreamController<bool>.broadcast();
  final StreamController<Map<String, int>> _throughputController =
      StreamController<Map<String, int>>.broadcast();

  bool _isRunning = false;
  StreamSubscription? _desktopLogSub;
  StreamSubscription? _desktopStatusSub;
  StreamSubscription? _androidLogSub;
  StreamSubscription? _androidStatusSub;
  StreamSubscription? _androidThroughputSub;

  /// Real-time stream of subprocess output (Xray/Psiphon) or Android VpnService log messages.
  Stream<String> get logs => _logController.stream;

  /// Stream of connection state changes (true = connected, false = disconnected).
  Stream<bool> get statusStream => _statusController.stream;

  /// Stream of active VPN tunnel throughput with raw cumulative bytes:
  /// {'rxBytes': int, 'txBytes': int, 'timestamp': int}
  Stream<Map<String, int>> get throughputStream => _throughputController.stream;

  /// Emits throughput telemetry data into the stream
  void emitThroughput({required int rxBytes, required int txBytes}) {
    if (!_throughputController.isClosed) {
      _throughputController.add({
        'rxBytes': rxBytes,
        'txBytes': txBytes,
        'timestamp': DateTime.now().millisecondsSinceEpoch,
      });
    }
  }

  /// Whether the VPN tunnel is currently active.
  bool get isRunning => Platform.isWindows ? _desktopController.isRunning : _isRunning;

  void _initChannels() {
    if (Platform.isWindows) {
      // Listen to DesktopVpnController for Windows
      _desktopLogSub = _desktopController.logs.listen((logLine) {
        _logController.add(logLine);
      });

      _desktopStatusSub = _desktopController.statusStream.listen((status) {
        _isRunning = status;
        _statusController.add(status);
      });
    } else {
      // Listen to Android platform channels
      try {
        _androidLogSub = _androidLogChannel.receiveBroadcastStream().listen(
          (dynamic log) {
            if (log != null) {
              _logController.add(log.toString());
            }
          },
          onError: (dynamic error) {
            _logController.add('[Android Log Channel ERR] $error');
          },
        );

        _androidStatusSub = _androidStatusChannel.receiveBroadcastStream().listen(
          (dynamic event) {
            if (event is bool) {
              _isRunning = event;
              _statusController.add(event);
            } else if (event is Map) {
              final isConnected = event['status'] == 'CONNECTED' || event['connected'] == true;
              _isRunning = isConnected;
              _statusController.add(isConnected);
            }
          },
          onError: (_) {},
        );

        _androidThroughputSub = _androidThroughputChannel.receiveBroadcastStream().listen(
          (dynamic data) {
            if (data is Map) {
              final rx = (data['rxBytes'] ?? data['rx'] ?? 0) as int;
              final tx = (data['txBytes'] ?? data['tx'] ?? 0) as int;
              emitThroughput(rxBytes: rx, txBytes: tx);
            }
          },
          onError: (_) {},
        );
      } catch (_) {
        // May fail in headless/unit testing environments where channels are unmocked
      }
    }
  }

  /// Starts the VPN tunnel with the provided Xray configuration JSON.
  /// Uses Platform.isWindows to delegate to DesktopVpnController on Windows,
  /// or MethodChannel('com.bpb.vpn/service') on Android.
  Future<bool> start({
    required String configJson,
    String serverName = 'BPB Secure Node',
    int socksPort = 10808,
    int httpPort = 10809,
    int mtu = 1420,
    bool enableSystemProxy = true,
  }) async {
    _logController.add('[VpnService] Initiating connection (OS: ${Platform.operatingSystem})...');

    if (Platform.isWindows) {
      final success = await _desktopController.start(
        configJson: configJson,
        socksPort: socksPort,
        httpPort: httpPort,
        enableSystemProxy: enableSystemProxy,
      );
      _isRunning = success;
      _statusController.add(success);
      return success;
    } else {
      try {
        final bool? result = await _androidChannel.invokeMethod<bool>('startVpn', {
          'configJson': configJson,
          'serverName': serverName,
          'socksPort': socksPort,
          'httpPort': httpPort,
          'mtu': mtu,
        });
        final started = result ?? false;
        _isRunning = started;
        _statusController.add(started);
        return started;
      } on PlatformException catch (e) {
        _logController.add('[Android VpnService ERR] Start failed: ${e.message}');
        _isRunning = false;
        _statusController.add(false);
        return false;
      }
    }
  }

  /// Stops the VPN tunnel and cleans up system proxy or Android TUN routes.
  Future<void> stop() async {
    _logController.add('[VpnService] Stopping tunnel...');

    if (Platform.isWindows) {
      await _desktopController.stop();
      _isRunning = false;
      _statusController.add(false);
    } else {
      try {
        await _androidChannel.invokeMethod<void>('stopVpn');
      } on PlatformException catch (e) {
        _logController.add('[Android VpnService ERR] Stop failed: ${e.message}');
      } finally {
        _isRunning = false;
        _statusController.add(false);
      }
    }
  }

  /// Queries whether the tunnel is currently running.
  Future<bool> isConnected() async {
    if (Platform.isWindows) {
      return _desktopController.isRunning;
    }
    try {
      final bool? connected = await _androidChannel.invokeMethod<bool>('isConnected');
      return connected ?? _isRunning;
    } catch (_) {
      return _isRunning;
    }
  }

  /// Android VpnService permission check (returns true immediately on Windows).
  Future<bool> prepareVpn() async {
    if (Platform.isWindows) {
      return true;
    }
    try {
      final bool? prepared = await _androidChannel.invokeMethod<bool>('prepareVpn');
      return prepared ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Cleans up stream controllers and subscriptions.
  Future<void> dispose() async {
    await _desktopLogSub?.cancel();
    await _desktopStatusSub?.cancel();
    await _androidLogSub?.cancel();
    await _androidStatusSub?.cancel();
    await _androidThroughputSub?.cancel();
    if (Platform.isWindows) {
      await _desktopController.dispose();
    }
    await _logController.close();
    await _statusController.close();
    await _throughputController.close();
  }
}

/// Backward compatibility alias for VpnService
typedef VpnChannel = VpnService;
