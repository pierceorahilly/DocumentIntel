import 'bill_flag.dart';

class UploadResponse {
  final String? uploadId;
  final String? s3PdfUrl;
  final String? status;
  final String? message;
  final int? transactionCount;
  final double? processingTime;
  final List<Transaction>? transactions;
  final String? advice;
  final CategoryAnalysis? categoryAnalysis;
  final List<BillFlag>? billFlags;
  final bool? contactPrompt;

  UploadResponse({
    this.uploadId,
    this.s3PdfUrl,
    this.status,
    this.message,
    this.transactionCount,
    this.processingTime,
    this.transactions,
    this.advice,
    this.categoryAnalysis,
    this.billFlags,
    this.contactPrompt,
  });

  factory UploadResponse.fromJson(Map<String, dynamic> json) {
    return UploadResponse(
      uploadId: json['uploadId'] as String?,
      s3PdfUrl: json['s3PdfUrl'] as String?,
      status: json['status'] as String?,
      message: json['message'] as String?,
      transactionCount: (json['transactionCount'] as num?)?.toInt(),
      processingTime: (json['processingTime'] as num?)?.toDouble(),
      transactions: (json['transactions'] as List<dynamic>?)
          ?.map((e) => Transaction.fromJson(e as Map<String, dynamic>))
          .toList(),
      advice: json['advice'] as String?,
      categoryAnalysis: json['categoryAnalysis'] != null
          ? CategoryAnalysis.fromJson(
              json['categoryAnalysis'] as Map<String, dynamic>)
          : null,
      billFlags: (json['billFlags'] as List<dynamic>?)
          ?.map((e) => BillFlag.fromJson(e as Map<String, dynamic>))
          .toList(),
      contactPrompt: json['contactPrompt'] as bool?,
    );
  }
}

class Transaction {
  final String? date;
  final String? description;
  final String? amount;
  final String? balance;
  final String? category;

  Transaction({
    this.date,
    this.description,
    this.amount,
    this.balance,
    this.category,
  });

  factory Transaction.fromJson(Map<String, dynamic> json) => Transaction(
        date: json['date'] as String?,
        description: json['description'] as String?,
        amount: json['amount']?.toString(),
        balance: json['balance']?.toString(),
        category: json['category'] as String?,
      );
}

class CategoryAnalysis {
  final Map<String, double>? categoryTotals;
  final Map<String, int>? categoryCounts;
  final Map<String, List<TransactionDetail>>? categoryTransactions;
  final List<Subscription>? subscriptions;
  final String? biggestCategory;
  final double? totalSpent;

  CategoryAnalysis({
    this.categoryTotals,
    this.categoryCounts,
    this.categoryTransactions,
    this.subscriptions,
    this.biggestCategory,
    this.totalSpent,
  });

  factory CategoryAnalysis.fromJson(Map<String, dynamic> json) {
    // Parse categoryTotals
    Map<String, double>? totals;
    if (json['categoryTotals'] != null) {
      totals = (json['categoryTotals'] as Map<String, dynamic>)
          .map((k, v) => MapEntry(k, (v as num).toDouble()));
    }

    // Parse categoryCounts
    Map<String, int>? counts;
    if (json['categoryCounts'] != null) {
      counts = (json['categoryCounts'] as Map<String, dynamic>)
          .map((k, v) => MapEntry(k, (v as num).toInt()));
    }

    // Parse categoryTransactions
    Map<String, List<TransactionDetail>>? catTransactions;
    if (json['categoryTransactions'] != null) {
      catTransactions =
          (json['categoryTransactions'] as Map<String, dynamic>).map(
        (k, v) => MapEntry(
          k,
          (v as List<dynamic>)
              .map((e) =>
                  TransactionDetail.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
    }

    return CategoryAnalysis(
      categoryTotals: totals,
      categoryCounts: counts,
      categoryTransactions: catTransactions,
      subscriptions: (json['subscriptions'] as List<dynamic>?)
          ?.map((e) => Subscription.fromJson(e as Map<String, dynamic>))
          .toList(),
      biggestCategory: json['biggestCategory'] as String?,
      totalSpent: (json['totalSpent'] as num?)?.toDouble(),
    );
  }
}

class TransactionDetail {
  final String? description;
  final double? amount;
  final String? date;
  final String? category;

  TransactionDetail({this.description, this.amount, this.date, this.category});

  factory TransactionDetail.fromJson(Map<String, dynamic> json) =>
      TransactionDetail(
        description: json['description'] as String?,
        amount: (json['amount'] as num?)?.toDouble(),
        date: json['date'] as String?,
        category: json['category'] as String?,
      );
}

class Subscription {
  final String? merchant;
  final double? amount;
  final int? occurrences;
  final String? frequency;

  Subscription({this.merchant, this.amount, this.occurrences, this.frequency});

  factory Subscription.fromJson(Map<String, dynamic> json) => Subscription(
        merchant: json['merchant'] as String?,
        amount: (json['amount'] as num?)?.toDouble(),
        occurrences: (json['occurrences'] as num?)?.toInt(),
        frequency: json['frequency'] as String?,
      );
}
