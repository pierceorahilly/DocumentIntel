import 'bill_flag.dart';

class ContactRequest {
  final List<BillFlag> flaggedBills;

  ContactRequest({required this.flaggedBills});

  Map<String, dynamic> toJson() => {
        'flaggedBills': flaggedBills.map((b) => b.toJson()).toList(),
      };
}
