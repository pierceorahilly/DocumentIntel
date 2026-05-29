class BillFlag {
  final String? billType;
  final String? providerName;
  final double? amount;
  final String? reason;
  final String? advice;

  BillFlag({
    this.billType,
    this.providerName,
    this.amount,
    this.reason,
    this.advice,
  });

  factory BillFlag.fromJson(Map<String, dynamic> json) => BillFlag(
        billType: json['billType'] as String?,
        providerName: json['providerName'] as String?,
        amount: (json['amount'] as num?)?.toDouble(),
        reason: json['reason'] as String?,
        advice: json['advice'] as String?,
      );

  Map<String, dynamic> toJson() => {
        'billType': billType,
        'providerName': providerName,
        'amount': amount,
        'reason': reason,
        'advice': advice,
      };
}
