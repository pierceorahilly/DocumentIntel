import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_colors.dart';
import '../../../data/api/contact_api.dart';
import '../../../data/models/bill_flag.dart';
import '../../../data/models/contact_models.dart';

class ContactRequestSection extends ConsumerStatefulWidget {
  final List<BillFlag> billFlags;

  const ContactRequestSection({super.key, required this.billFlags});

  @override
  ConsumerState<ContactRequestSection> createState() =>
      _ContactRequestSectionState();
}

class _ContactRequestSectionState
    extends ConsumerState<ContactRequestSection> {
  bool _submitted = false;
  bool _loading = false;
  String? _error;

  Future<void> _submitRequest() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      await ref.read(contactApiProvider).submitContactRequest(
            ContactRequest(flaggedBills: widget.billFlags),
          );
      setState(() {
        _submitted = true;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = 'Failed to submit. Try again.';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_submitted) {
      return Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.success.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(8),
        ),
        child: const Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.check_circle, color: AppColors.success),
            SizedBox(width: 8),
            Text(
              "Submitted — We'll be in touch",
              style: TextStyle(
                  color: AppColors.success, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      );
    }

    return Column(
      children: [
        Text(
          'Would you like a support advisor to contact you?',
          style: TextStyle(color: AppColors.textSecondary),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        if (_error != null) ...[
          Text(_error!, style: const TextStyle(color: AppColors.error)),
          const SizedBox(height: 8),
        ],
        ElevatedButton(
          onPressed: _loading ? null : _submitRequest,
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.warning,
            foregroundColor: Colors.white,
          ),
          child: _loading
              ? const SizedBox(
                  height: 20,
                  width: 20,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: Colors.white),
                )
              : const Text('Yes — Contact me with help'),
        ),
      ],
    );
  }
}
