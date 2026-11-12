import 'dart:async';
import 'dart:convert';
import 'dart:io';

/// Autonomous Windows Desktop VPN Controller for BPB VPN Desktop.
/// Manages bpb-xray.exe, psiphon tunnel, and Windows System Proxy registry keys.
class DesktopVpnController {
  Process? _xrayProcess;
  Process? _psiphonProcess;
  final StreamController<String> _logController = StreamController<String>.broadcast();
  final StreamController<bool> _statusController = StreamController<bool>.broadcast();

  Stream<String> get logs => _logController.stream;
  Stream<bool> get statusStream => _statusController.stream;
  bool get isRunning => _xrayProcess != null;

  /// Starts Xray-core and optionally configures the Windows system proxy.
  Future<bool> start({
    required String configJson,
    int socksPort = 10808,
    int httpPort = 10809,
    bool enableSystemProxy = true,
  }) async {
    if (!Platform.isWindows) {
      _log('[WARN] DesktopVpnController called on non-Windows platform: ${Platform.operatingSystem}');
    }
    await stop();

    try {
      final execDir = File(Platform.resolvedExecutable).parent.path;
      final xrayExe = _resolveBinary(execDir, 'bpb-xray.exe');
      final workDir = await Directory.systemTemp.createTemp('bpb_vpn_');
      final configFile = File('${workDir.path}${Platform.pathSeparator}config.json');

      await configFile.writeAsString(configJson);
      _log('Generated Xray core configuration at: ${configFile.path}');

      _log('Starting core process: $xrayExe run -c ${configFile.path}');
      _xrayProcess = await Process.start(
        xrayExe,
        ['run', '-c', configFile.path],
        workingDirectory: workDir.path,
      );

      _statusController.add(true);

      _xrayProcess!.stdout
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen((line) => _log('[Xray] $line'));

      _xrayProcess!.stderr
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen((line) => _log('[Xray ERR] $line'));

      _xrayProcess!.exitCode.then((code) {
        _log('[Xray] Core process exited with code $code');
        _xrayProcess = null;
        _statusController.add(false);
        if (enableSystemProxy && Platform.isWindows) {
          _setWindowsSystemProxy(enable: false);
        }
      });

      if (enableSystemProxy && Platform.isWindows) {
        await _setWindowsSystemProxy(
          enable: true,
          server: '127.0.0.1:$httpPort',
        );
      }

      return true;
    } catch (e, stack) {
      _log('[ERROR] Failed to start Desktop VPN: $e\n$stack');
      await stop();
      return false;
    }
  }

  /// Stops all running tunnel processes and clears the Windows system proxy.
  Future<void> stop() async {
    if (Platform.isWindows) {
      await _setWindowsSystemProxy(enable: false);
    }
    if (_psiphonProcess != null) {
      _psiphonProcess!.kill(ProcessSignal.sigterm);
      _psiphonProcess = null;
      _log('Stopped Psiphon tunnel process.');
    }
    if (_xrayProcess != null) {
      _xrayProcess!.kill(ProcessSignal.sigterm);
      _xrayProcess = null;
      _log('Stopped Xray core process.');
    }
    _statusController.add(false);
  }

  String _resolveBinary(String execDir, String binaryName) {
    final sep = Platform.pathSeparator;
    final candidates = [
      '$execDir$sep$binaryName',
      '$execDir${sep}data${sep}flutter_assets${sep}bin$sep$binaryName',
      '${Directory.current.path}${sep}build${sep}bin$sep$binaryName',
      '${Directory.current.path}${sep}xray$sep$binaryName',
      '${Directory.current.path}${sep}bin$sep$binaryName',
    ];
    for (final path in candidates) {
      if (File(path).existsSync()) {
        _log('Located binary at: $path');
        return path;
      }
    }
    _log('Binary not found in candidate paths. Falling back to system PATH: $binaryName');
    return binaryName;
  }

  Future<void> _setWindowsSystemProxy({
    required bool enable,
    String server = '127.0.0.1:10809',
  }) async {
    const regKey = r'HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings';
    try {
      await Process.run('reg', [
        'add',
        regKey,
        '/v',
        'ProxyEnable',
        '/t',
        'REG_DWORD',
        '/d',
        enable ? '1' : '0',
        '/f'
      ]);
      if (enable) {
        await Process.run('reg', [
          'add',
          regKey,
          '/v',
          'ProxyServer',
          '/t',
          'REG_SZ',
          '/d',
          server,
          '/f'
        ]);
        await Process.run('reg', [
          'add',
          regKey,
          '/v',
          'ProxyOverride',
          '/t',
          'REG_SZ',
          '/d',
          'localhost;127.*;10.*;192.168.*;<local>',
          '/f'
        ]);
        _log('Windows System Proxy enabled -> $server');
      } else {
        _log('Windows System Proxy disabled.');
      }
    } catch (e) {
      _log('[WARN] Failed to configure Windows System Proxy: $e');
    }
  }

  void _log(String message) {
    if (!_logController.isClosed) {
      _logController.add('[${DateTime.now().toIso8601String()}] $message');
    }
  }

  Future<void> dispose() async {
    await stop();
    await _logController.close();
    await _statusController.close();
  }
}
