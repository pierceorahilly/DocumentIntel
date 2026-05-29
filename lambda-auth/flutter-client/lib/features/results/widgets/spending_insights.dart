import 'package:flutter/material.dart';
import '../../../core/theme/app_colors.dart';
import '../../../data/models/upload_models.dart';

class SpendingInsights extends StatelessWidget {
  final CategoryAnalysis analysis;

  const SpendingInsights({super.key, required this.analysis});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'SPENDING ANALYSIS',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 14,
                color: AppColors.textSecondary,
                letterSpacing: 1.2,
              ),
            ),
            const SizedBox(height: 16),

            // Total spent
            if (analysis.totalSpent != null)
              _InsightRow(
                label: 'Total Spent',
                value: '€${analysis.totalSpent!.toStringAsFixed(2)}',
                valueColor: AppColors.error,
                isBold: true,
              ),

            // Biggest category
            if (analysis.biggestCategory != null) ...[
              const SizedBox(height: 8),
              _InsightRow(
                label: 'Biggest Category',
                value:
                    '${analysis.biggestCategory} (€${analysis.categoryTotals?[analysis.biggestCategory]?.toStringAsFixed(2) ?? '0.00'})',
              ),
            ],

            // Subscriptions
            if (analysis.subscriptions != null &&
                analysis.subscriptions!.isNotEmpty) ...[
              const SizedBox(height: 16),
              const Text(
                'Recurring Subscriptions',
                style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
              ),
              const SizedBox(height: 8),
              ...analysis.subscriptions!.map((sub) => Padding(
                    padding: const EdgeInsets.only(bottom: 6),
                    child: Row(
                      children: [
                        const Icon(Icons.repeat,
                            size: 16, color: AppColors.textSecondary),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            '${sub.merchant ?? 'Unknown'}: €${sub.amount?.toStringAsFixed(2) ?? '0.00'}'
                            ' (${sub.frequency ?? '?'}, ${sub.occurrences ?? 0} occurrences)',
                            style: const TextStyle(fontSize: 14),
                          ),
                        ),
                      ],
                    ),
                  )),
            ],

            // Category breakdown
            if (analysis.categoryTotals != null &&
                analysis.categoryTotals!.isNotEmpty) ...[
              const SizedBox(height: 16),
              const Text(
                'Spending by Category',
                style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
              ),
              const SizedBox(height: 8),
              ..._sortedCategories().map((entry) => _CategoryRow(
                    category: entry.key,
                    total: entry.value,
                    count: analysis.categoryCounts?[entry.key] ?? 0,
                    transactions:
                        analysis.categoryTransactions?[entry.key],
                  )),
            ],
          ],
        ),
      ),
    );
  }

  List<MapEntry<String, double>> _sortedCategories() {
    final entries = analysis.categoryTotals!.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    return entries;
  }
}

class _InsightRow extends StatelessWidget {
  final String label;
  final String value;
  final Color? valueColor;
  final bool isBold;

  const _InsightRow({
    required this.label,
    required this.value,
    this.valueColor,
    this.isBold = false,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(fontSize: 15)),
        Text(
          value,
          style: TextStyle(
            fontSize: 15,
            fontWeight: isBold ? FontWeight.bold : FontWeight.w600,
            color: valueColor ?? AppColors.textPrimary,
          ),
        ),
      ],
    );
  }
}

class _CategoryRow extends StatefulWidget {
  final String category;
  final double total;
  final int count;
  final List<TransactionDetail>? transactions;

  const _CategoryRow({
    required this.category,
    required this.total,
    required this.count,
    this.transactions,
  });

  @override
  State<_CategoryRow> createState() => _CategoryRowState();
}

class _CategoryRowState extends State<_CategoryRow> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        InkWell(
          onTap: widget.transactions != null && widget.transactions!.isNotEmpty
              ? () => setState(() => _expanded = !_expanded)
              : null,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(
              children: [
                if (widget.transactions != null &&
                    widget.transactions!.isNotEmpty)
                  Icon(
                    _expanded ? Icons.expand_less : Icons.expand_more,
                    size: 20,
                    color: AppColors.textSecondary,
                  )
                else
                  const SizedBox(width: 20),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    '${widget.category} (${widget.count})',
                    style: const TextStyle(fontSize: 14),
                  ),
                ),
                Text(
                  '€${widget.total.toStringAsFixed(2)}',
                  style: const TextStyle(
                      fontWeight: FontWeight.w600, fontSize: 14),
                ),
              ],
            ),
          ),
        ),
        if (_expanded && widget.transactions != null)
          ...widget.transactions!.map((t) => Padding(
                padding: const EdgeInsets.only(left: 40, bottom: 4),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        t.description ?? '',
                        style: TextStyle(
                            fontSize: 13, color: AppColors.textSecondary),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    Text(
                      '€${t.amount?.toStringAsFixed(2) ?? '0.00'}',
                      style: TextStyle(
                          fontSize: 13, color: AppColors.textSecondary),
                    ),
                  ],
                ),
              )),
      ],
    );
  }
}
