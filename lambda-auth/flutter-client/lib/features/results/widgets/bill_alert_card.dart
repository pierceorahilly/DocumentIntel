import 'package:flutter/material.dart';
import '../../../core/theme/app_colors.dart';
import '../../../data/models/bill_flag.dart';

class BillAlertCard extends StatefulWidget {
  final List<BillFlag> billFlags;

  const BillAlertCard({super.key, required this.billFlags});

  @override
  State<BillAlertCard> createState() => _BillAlertCardState();
}

class _BillAlertCardState extends State<BillAlertCard> {
  bool _isExpanded = true;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border(
          left: const BorderSide(color: AppColors.warning, width: 4),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        children: [
          // Header
          InkWell(
            onTap: () => setState(() => _isExpanded = !_isExpanded),
            borderRadius:
                const BorderRadius.vertical(top: Radius.circular(12)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const Icon(Icons.warning_amber_rounded,
                      color: AppColors.warning, size: 28),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Bill Alert — You may be overpaying',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                        color: AppColors.textPrimary,
                      ),
                    ),
                  ),
                  Icon(
                    _isExpanded
                        ? Icons.keyboard_arrow_up
                        : Icons.keyboard_arrow_down,
                    color: AppColors.textSecondary,
                  ),
                ],
              ),
            ),
          ),
          // Bill cards
          AnimatedCrossFade(
            firstChild: Column(
              children: widget.billFlags
                  .map((flag) => _BillFlagItem(flag: flag))
                  .toList(),
            ),
            secondChild: const SizedBox.shrink(),
            crossFadeState: _isExpanded
                ? CrossFadeState.showFirst
                : CrossFadeState.showSecond,
            duration: const Duration(milliseconds: 200),
          ),
        ],
      ),
    );
  }
}

class _BillFlagItem extends StatelessWidget {
  final BillFlag flag;

  const _BillFlagItem({required this.flag});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                flag.providerName ?? 'Unknown',
                style: const TextStyle(
                    fontWeight: FontWeight.bold, fontSize: 15),
              ),
              Text(
                '€${flag.amount?.toStringAsFixed(2) ?? '0.00'}',
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                  color: AppColors.error,
                ),
              ),
            ],
          ),
          if (flag.billType != null) ...[
            const SizedBox(height: 4),
            Text(
              flag.billType!,
              style: TextStyle(
                  color: AppColors.textSecondary, fontSize: 13),
            ),
          ],
          if (flag.reason != null) ...[
            const SizedBox(height: 8),
            Text(flag.reason!, style: const TextStyle(fontSize: 14)),
          ],
          if (flag.advice != null) ...[
            const SizedBox(height: 8),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Tip: ',
                    style: TextStyle(
                        color: AppColors.success,
                        fontWeight: FontWeight.w600,
                        fontSize: 14)),
                Expanded(
                  child: Text(flag.advice!,
                      style: const TextStyle(
                          color: AppColors.success, fontSize: 14)),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
