import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../services/desktop_vpn_controller.dart';
import '../../services/vpn_channel.dart';

/// Interactive real-time log overlay widget for troubleshooting VPN connections.
/// Listens directly to DesktopVpnController.logs or VpnService.logs,
/// displaying subprocess output (Xray-core, Psiphon, Proxy) and live status.
class LogOverlay extends StatefulWidget {
  final DesktopVpnController? controller;
  final bool initialExpanded;
  final double maxHeight;

  const LogOverlay({
    super.key,
    this.controller,
    this.initialExpanded = false,
    this.maxHeight = 280.0,
  });

  @override
  State<LogOverlay> createState() => _LogOverlayState();
}

class _LogOverlayState extends State<LogOverlay> {
  final List<String> _logs = [];
  final ScrollController _scrollController = ScrollController();
  StreamSubscription<String>? _logSubscription;
  StreamSubscription<bool>? _statusSubscription;

  bool _isExpanded = false;
  bool _autoScroll = true;
  bool _isConnected = false;
  String _filterQuery = '';

  @override
  void initState() {
    super.initState();
    _isExpanded = widget.initialExpanded;
    _subscribeToStreams();
  }

  void _subscribeToStreams() {
    // Listen to DesktopVpnController.logs or fallback to unified VpnService.logs
    final logStream = widget.controller?.logs ?? VpnService().logs;
    _logSubscription = logStream.listen((line) {
      if (!mounted) return;
      setState(() {
        _logs.add(line);
        if (_logs.length > 500) {
          _logs.removeAt(0);
        }
      });

      if (_autoScroll && _isExpanded && _scrollController.hasClients) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (_scrollController.hasClients) {
            _scrollController.animateTo(
              _scrollController.position.maxScrollExtent,
              duration: const Duration(milliseconds: 150),
              curve: Curves.easeOut,
            );
          }
        });
      }
    });

    // Listen to connection status
    final statusStream = widget.controller?.statusStream ?? VpnService().statusStream;
    _statusSubscription = statusStream.listen((connected) {
      if (mounted) {
        setState(() {
          _isConnected = connected;
        });
      }
    });
  }

  @override
  void dispose() {
    _logSubscription?.cancel();
    _statusSubscription?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  void _copyLogsToClipboard() {
    final text = _logs.join('\n');
    Clipboard.setData(ClipboardData(text: text));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Logs copied to clipboard'),
        duration: Duration(seconds: 2),
        backgroundColor: Color(0xFF17223D),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final filteredLogs = _filterQuery.isEmpty
        ? _logs
        : _logs.where((l) => l.toLowerCase().contains(_filterQuery.toLowerCase())).toList();

    return Material(
      color: Colors.transparent,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeInOut,
        margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: const Color(0xFF0C1322),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: _isConnected ? const Color(0xFF00E676).withOpacity(0.5) : const Color(0xFF263963),
            width: 1.2,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.4),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Header Bar
            InkWell(
              borderRadius: BorderRadius.circular(16),
              onTap: () {
                setState(() {
                  _isExpanded = !_isExpanded;
                });
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                child: Row(
                  children: [
                    // Connection Status Pulse Dot
                    Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                        color: _isConnected ? const Color(0xFF00E676) : const Color(0xFFFF5252),
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: (_isConnected ? const Color(0xFF00E676) : const Color(0xFFFF5252)).withOpacity(0.5),
                            blurRadius: 6,
                            spreadRadius: 1,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _isConnected ? 'VPN ACTIVE (Core Subprocess Running)' : 'VPN DISCONNECTED (Core Idle)',
                            style: TextStyle(
                              color: _isConnected ? const Color(0xFF00E676) : const Color(0xFFE2E8F0),
                              fontSize: 12,
                              fontWeight: FontWeight.bold,
                              letterSpacing: 0.5,
                            ),
                          ),
                          Text(
                            _logs.isEmpty
                                ? 'No active subprocess output'
                                : 'Last log: ${_logs.last.replaceAll(RegExp(r'\s+'), ' ').trim()}',
                            style: const TextStyle(
                              color: Color(0xFF94A3B8),
                              fontSize: 10,
                              fontFamily: 'monospace',
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: const Color(0xFF17223D),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        '${_logs.length} lines',
                        style: const TextStyle(
                          color: Color(0xFF00E5FF),
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    const SizedBox(width: 4),
                    Icon(
                      _isExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
                      color: const Color(0xFF00E5FF),
                      size: 20,
                    ),
                  ],
                ),
              ),
            ),

            // Expanded Terminal View
            if (_isExpanded) ...[
              const Divider(color: Color(0xFF1E293B), height: 1),
              // Controls Toolbar
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                child: Row(
                  children: [
                    Expanded(
                      child: SizedBox(
                        height: 32,
                        child: TextField(
                          onChanged: (val) {
                            setState(() {
                              _filterQuery = val;
                            });
                          },
                          style: const TextStyle(color: Colors.white, fontSize: 11),
                          decoration: InputDecoration(
                            hintText: 'Filter subprocess output...',
                            hintStyle: const TextStyle(color: Color(0xFF64748B), fontSize: 11),
                            prefixIcon: const Icon(Icons.search, size: 14, color: Color(0xFF64748B)),
                            contentPadding: const EdgeInsets.symmetric(vertical: 0, horizontal: 8),
                            filled: true,
                            fillColor: const Color(0xFF070B14),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(8),
                              borderSide: BorderSide.none,
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    // Auto-scroll toggle
                    IconButton(
                      icon: Icon(
                        _autoScroll ? Icons.vertical_align_bottom : Icons.pause_circle_outline,
                        size: 16,
                        color: _autoScroll ? const Color(0xFF00E5FF) : const Color(0xFF64748B),
                      ),
                      tooltip: _autoScroll ? 'Auto-scroll ON' : 'Auto-scroll PAUSED',
                      onPressed: () {
                        setState(() {
                          _autoScroll = !_autoScroll;
                        });
                      },
                    ),
                    // Copy button
                    IconButton(
                      icon: const Icon(Icons.copy, size: 16, color: Color(0xFF00E5FF)),
                      tooltip: 'Copy all logs',
                      onPressed: _copyLogsToClipboard,
                    ),
                    // Clear button
                    IconButton(
                      icon: const Icon(Icons.delete_sweep, size: 16, color: Color(0xFFFF5252)),
                      tooltip: 'Clear output',
                      onPressed: () {
                        setState(() {
                          _logs.clear();
                        });
                      },
                    ),
                  ],
                ),
              ),

              // Console Output Box
              Container(
                height: widget.maxHeight,
                padding: const EdgeInsets.all(8),
                margin: const EdgeInsets.only(left: 8, right: 8, bottom: 8),
                decoration: BoxDecoration(
                  color: const Color(0xFF050811),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: const Color(0xFF1E293B)),
                ),
                child: filteredLogs.isEmpty
                    ? const Center(
                        child: Text(
                          'Waiting for subprocess logs...\n(Start the VPN to see Xray & Psiphon output)',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Color(0xFF64748B), fontSize: 11, fontFamily: 'monospace'),
                        ),
                      )
                    : ListView.builder(
                        controller: _scrollController,
                        itemCount: filteredLogs.length,
                        itemBuilder: (context, index) {
                          final line = filteredLogs[index];
                          final isError = line.contains('[ERR') || line.contains('[ERROR]');
                          final isWarning = line.contains('[WARN]');
                          final isXray = line.contains('[Xray]');

                          Color textColor = const Color(0xFFCBD5E1);
                          if (isError) {
                            textColor = const Color(0xFFFF5252);
                          } else if (isWarning) {
                            textColor = const Color(0xFFFFB300);
                          } else if (isXray) {
                            textColor = const Color(0xFF00E5FF);
                          }

                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 1.5),
                            child: SelectableText(
                              line,
                              style: TextStyle(
                                color: textColor,
                                fontSize: 10.5,
                                fontFamily: 'monospace',
                                height: 1.3,
                              ),
                            ),
                          );
                        },
                      ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
