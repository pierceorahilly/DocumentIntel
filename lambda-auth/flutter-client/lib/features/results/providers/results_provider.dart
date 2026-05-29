import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../upload/providers/upload_provider.dart';
import '../../../data/models/upload_models.dart';

final resultsProvider = Provider<UploadResponse?>((ref) {
  final uploadState = ref.watch(uploadProvider);
  return uploadState.result;
});
