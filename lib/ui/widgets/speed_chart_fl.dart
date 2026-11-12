import 'dart:async';
import 'dart:math';
import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import '../../services/throughput_monitor_service.dart';
import '../../services/vpn_channel.dart';

/// Real-time data visualization component using the 'fl_chart' package.
/// Displays live download and upload speeds based on the active VPN connection data stream.
class SpeedChartFlChart extends StatefulWidget {
  final Stream<Map<String, double>>? speedStream;
  final double height;
  final int maxDataPoints;

  const SpeedChartFlChart({
    super.key,
    this.speedStream,
    this.height = 220.0,
    this.maxDataPoints = 30,
  });

  @override
  State<SpeedChartFlChart> createState() => _SpeedChartFlChartState();
}

class _SpeedChartFlChartState extends State<SpeedChartFlChart> {
  final List<FlSpot> _downloadSpots = [];
  final List<FlSpot> _uploadSpots = [];

  StreamSubscription? _speedSubscription;
  Timer? _telemetrySimTimer;

  double _currentDownloadKb = 0.0;
  double _currentUploadKb = 0.0;
  double _peakSpeedKb = 100.0;
  int _pointCounter = 0;

  @override
  void initState() {
    super.initState();
    _initStream();
  }

  void _initStream() {
    if (widget.speedStream != null) {
      _speedSubscription = widget.speedStream!.listen((data) {
        final down = data['download'] ?? 0.0;
        final up = data['upload'] ?? 0.0;
        _addSpeedPoint(down, up);
      });
    } else {
      // Connect to ThroughputMonitorService reactive stream
      final throughput = ThroughputMonitorService();
      _speedSubscription = throughput.speedStream.listen((telemetry) {
        _addSpeedPoint(telemetry.downloadSpeedKbps, telemetry.uploadSpeedKbps);
      });

      // Seed with initial current telemetry
      final current = throughput.currentTelemetry;
      if (current.downloadSpeedKbps > 0 || current.uploadSpeedKbps > 0) {
        _addSpeedPoint(current.downloadSpeedKbps, current.uploadSpeedKbps);
      }
    }
  }

  void _addSpeedPoint(double downKb, double upKb) {
    if (!mounted) return;
    setState(() {
      _currentDownloadKb = downKb;
      _currentUploadKb = upKb;
      _peakSpeedKb = max(_peakSpeedKb, max(downKb, upKb));

      _pointCounter++;
      final x = _pointCounter.toDouble();

      _downloadSpots.add(FlSpot(x, downKb));
      _uploadSpots.add(FlSpot(x, upKb));

      if (_downloadSpots.length > widget.maxDataPoints) {
        _downloadSpots.removeAt(0);
        _uploadSpots.removeAt(0);
      }
    });
  }

  @override
  void dispose() {
    _speedSubscription?.cancel();
    _telemetrySimTimer?.cancel();
    super.dispose();
  }

  String _formatSpeed(double kb) {
    if (kb >= 1024.0) {
      return '${(kb / 1024.0).toStringAsFixed(1)} MB/s';
    }
    return '${kb.toStringAsFixed(0)} KB/s';
  }

  @override
  Widget build(BuildContext context) {
    final maxY = (_peakSpeedKb * 1.2).clamp(100.0, 100000.0);
    final minX = _downloadSpots.isNotEmpty ? _downloadSpots.first.x : 0.0;
    final maxX = _downloadSpots.isNotEmpty ? _downloadSpots.last.x : 10.0;

    return Container(
      height: widget.height,
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF0C1322),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFF1E293B)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.35),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Header Bar with Legend and Live Speed Badges
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'VPN TRAFFIC STREAM',
                    style: TextStyle(
                      color: Color(0xFF00E5FF),
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 1.2,
                    ),
                  ),
                  const SizedBox(height: 2),
                  const Text(
                    'Live Throughput (fl_chart)',
                    style: TextStyle(
                      color: Color(0xFFF0F4FC),
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
              Row(
                children: [
                  // Download Indicator
                  _LegendIndicator(
                    color: const Color(0xFF00E5FF),
                    label: 'DL: ${_formatSpeed(_currentDownloadKb)}',
                  ),
                  const SizedBox(width: 10),
                  // Upload Indicator
                  _LegendIndicator(
                    color: const Color(0xFF2979FF),
                    label: 'UL: ${_formatSpeed(_currentUploadKb)}',
                  ),
                ],
              ),
            ],
          ),

          const SizedBox(height: 12),

          // fl_chart LineChart Canvas
          Expanded(
            child: _downloadSpots.length < 2
                ? const Center(
                    child: Text(
                      'Accumulating stream telemetry points...',
                      style: TextStyle(color: Color(0xFF64748B), fontSize: 11, fontFamily: 'monospace'),
                    ),
                  )
                : LineChart(
                    LineChartData(
                      minX: minX,
                      maxX: maxX,
                      minY: 0,
                      maxY: maxY,
                      gridData: FlGridData(
                        show: true,
                        drawVerticalLine: false,
                        horizontalInterval: maxY / 4,
                        getDrawingHorizontalLine: (value) => const FlLine(
                          color: Color(0xFF17223D),
                          strokeWidth: 1,
                        ),
                      ),
                      titlesData: FlTitlesData(
                        show: true,
                        rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                        topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                        bottomTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                        leftTitles: AxisTitles(
                          sideTitles: SideTitles(
                            showTitles: true,
                            reservedSize: 46,
                            interval: maxY / 3,
                            getTitlesWidget: (value, meta) {
                              return Padding(
                                padding: const EdgeInsets.only(right: 4),
                                child: Text(
                                  _formatSpeed(value),
                                  style: const TextStyle(
                                    color: Color(0xFF64748B),
                                    fontSize: 9,
                                    fontFamily: 'monospace',
                                  ),
                                  textAlign: TextAlign.right,
                                ),
                              );
                            },
                          ),
                        ),
                      ),
                      borderData: FlBorderData(show: false),
                      lineTouchData: LineTouchData(
                        handleBuiltInTouches: true,
                        touchTooltipData: LineTouchTooltipData(
                          getTooltipItems: (touchedSpots) {
                            return touchedSpots.map((spot) {
                              final isDown = spot.barIndex == 0;
                              return LineTooltipItem(
                                '${isDown ? "DL" : "UL"}: ${_formatSpeed(spot.y)}',
                                TextStyle(
                                  color: isDown ? const Color(0xFF00E5FF) : const Color(0xFF2979FF),
                                  fontWeight: FontWeight.bold,
                                  fontSize: 11,
                                ),
                              );
                            }).toList();
                          },
                        ),
                      ),
                      lineBarsData: [
                        // Download Line (Cyan with gradient area)
                        LineChartBarData(
                          spots: _downloadSpots,
                          isCurved: true,
                          curveSmoothness: 0.35,
                          color: const Color(0xFF00E5FF),
                          barWidth: 2.2,
                          isStrokeCapRound: true,
                          dotData: const FlDotData(show: false),
                          belowBarData: BarAreaData(
                            show: true,
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [
                                const Color(0xFF00E5FF).withOpacity(0.35),
                                const Color(0xFF00E5FF).withOpacity(0.0),
                              ],
                            ),
                          ),
                        ),
                        // Upload Line (Electric Blue with gradient area)
                        LineChartBarData(
                          spots: _uploadSpots,
                          isCurved: true,
                          curveSmoothness: 0.35,
                          color: const Color(0xFF2979FF),
                          barWidth: 1.8,
                          isStrokeCapRound: true,
                          dotData: const FlDotData(show: false),
                          belowBarData: BarAreaData(
                            show: true,
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [
                                const Color(0xFF2979FF).withOpacity(0.22),
                                const Color(0xFF2979FF).withOpacity(0.0),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}

class _LegendIndicator extends StatelessWidget {
  final Color color;
  final String label;

  const _LegendIndicator({required this.color, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: const Color(0xFF17223D),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 5),
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 10.5,
              fontWeight: FontWeight.bold,
              fontFamily: 'monospace',
            ),
          ),
        ],
      ),
    );
  }
}
