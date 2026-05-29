import 'package:flutter/material.dart';
import '../../../core/theme/app_colors.dart';
import '../../../data/models/upload_models.dart';

class TransactionListWidget extends StatelessWidget {
  final List<Transaction> transactions;

  const TransactionListWidget({super.key, required this.transactions});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Transactions (${transactions.length})',
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
              ),
            ),
            const SizedBox(height: 12),
            // Header row
            Container(
              padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
              decoration: BoxDecoration(
                border: Border(
                    bottom: BorderSide(color: AppColors.divider)),
              ),
              child: const Row(
                children: [
                  SizedBox(
                      width: 80,
                      child: Text('Date',
                          style: TextStyle(
                              fontWeight: FontWeight.w600, fontSize: 12))),
                  Expanded(
                      child: Text('Description',
                          style: TextStyle(
                              fontWeight: FontWeight.w600, fontSize: 12))),
                  SizedBox(
                      width: 70,
                      child: Text('Amount',
                          textAlign: TextAlign.right,
                          style: TextStyle(
                              fontWeight: FontWeight.w600, fontSize: 12))),
                ],
              ),
            ),
            // Transaction rows
            ...transactions.map((t) => _TransactionRow(transaction: t)),
          ],
        ),
      ),
    );
  }
}

class _TransactionRow extends StatelessWidget {
  final Transaction transaction;

  const _TransactionRow({required this.transaction});

  @override
  Widget build(BuildContext context) {
    final amountStr = transaction.amount ?? '0';
    final isNegative =
        amountStr.startsWith('-') || amountStr.startsWith('−');

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
      decoration: BoxDecoration(
        border:
            Border(bottom: BorderSide(color: AppColors.divider.withValues(alpha: 0.3))),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 80,
            child: Text(
              transaction.date ?? '',
              style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
            ),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  transaction.description ?? '',
                  style: const TextStyle(fontSize: 13),
                  overflow: TextOverflow.ellipsis,
                ),
                if (transaction.category != null)
                  Text(
                    transaction.category!,
                    style: TextStyle(
                        fontSize: 11, color: AppColors.textSecondary),
                  ),
              ],
            ),
          ),
          SizedBox(
            width: 70,
            child: Text(
              amountStr,
              textAlign: TextAlign.right,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: isNegative ? AppColors.error : AppColors.success,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
