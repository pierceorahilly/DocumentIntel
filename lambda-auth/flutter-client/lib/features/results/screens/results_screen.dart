import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_colors.dart';
import '../../upload/providers/upload_provider.dart';
import '../providers/results_provider.dart';
import '../widgets/bill_alert_card.dart';
import '../widgets/contact_request_section.dart';
import '../widgets/advice_section.dart';
import '../widgets/spending_insights.dart';
import '../widgets/transaction_list.dart';
import '../widgets/spending_pie_chart.dart';

class ResultsScreen extends ConsumerWidget {
  const ResultsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final result = ref.watch(resultsProvider);

    if (result == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Results')),
        body: const Center(child: Text('No results available')),
      );
    }

    final hasBillFlags =
        result.billFlags != null && result.billFlags!.isNotEmpty;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Analysis Results'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () {
            ref.read(uploadProvider.notifier).reset();
            context.go('/upload');
          },
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Success banner
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.success.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                  color: AppColors.success.withValues(alpha: 0.3)),
            ),
            child: Row(
              children: [
                const Icon(Icons.check_circle, color: AppColors.success),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Processed ${result.transactionCount ?? 0} transactions in ${result.processingTime?.toStringAsFixed(1) ?? '?'}s',
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      color: AppColors.success,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // Bill alerts
          if (hasBillFlags) ...[
            BillAlertCard(billFlags: result.billFlags!),
            const SizedBox(height: 8),
            ContactRequestSection(billFlags: result.billFlags!),
            const SizedBox(height: 16),
          ],

          // Financial advice
          if (result.advice != null && result.advice!.isNotEmpty) ...[
            AdviceSection(advice: result.advice!),
            const SizedBox(height: 16),
          ],

          // Spending insights
          if (result.categoryAnalysis != null) ...[
            SpendingInsights(analysis: result.categoryAnalysis!),
            const SizedBox(height: 16),
          ],

          // Pie chart
          if (result.categoryAnalysis?.categoryTotals != null &&
              result.categoryAnalysis!.categoryTotals!.isNotEmpty) ...[
            SpendingPieChart(
                totals: result.categoryAnalysis!.categoryTotals!),
            const SizedBox(height: 16),
          ],

          // Transaction list
          if (result.transactions != null &&
              result.transactions!.isNotEmpty) ...[
            TransactionListWidget(transactions: result.transactions!),
            const SizedBox(height: 24),
          ],
        ],
      ),
    );
  }
}
