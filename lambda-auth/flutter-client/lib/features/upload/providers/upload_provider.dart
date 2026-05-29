import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../data/api/upload_api.dart';
import '../../../data/models/upload_models.dart';
import '../../../data/services/pdf_validator.dart';

class UploadState {
  final bool isIdle;
  final String? selectedFilename;
  final Uint8List? selectedBytes;
  final bool isUploading;
  final bool isPolling;
  final int pollSeconds;
  final UploadResponse? result;
  final String? error;

  const UploadState({
    this.isIdle = true,
    this.selectedFilename,
    this.selectedBytes,
    this.isUploading = false,
    this.isPolling = false,
    this.pollSeconds = 0,
    this.result,
    this.error,
  });

  bool get hasFile => selectedFilename != null && selectedBytes != null;
  bool get isBusy => isUploading || isPolling;
}

final uploadProvider =
    StateNotifierProvider<UploadNotifier, UploadState>((ref) {
  return UploadNotifier(ref.read(uploadApiProvider));
});

class UploadNotifier extends StateNotifier<UploadState> {
  final UploadApi _uploadApi;

  UploadNotifier(this._uploadApi) : super(const UploadState());

  Future<void> pickFile() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pdf'],
      withData: true,
    );

    if (result != null && result.files.single.bytes != null) {
      final file = result.files.single;
      final validation = PdfValidator.validate(file.bytes!, file.name);
      if (!validation.isValid) {
        state = UploadState(error: validation.error);
        return;
      }
      state = UploadState(
        isIdle: false,
        selectedFilename: file.name,
        selectedBytes: file.bytes,
      );
    }
  }

  Future<void> uploadAndProcess() async {
    if (!state.hasFile) return;

    state = UploadState(
      selectedFilename: state.selectedFilename,
      selectedBytes: state.selectedBytes,
      isUploading: true,
    );

    try {
      final response = await _uploadApi.uploadPdf(
        state.selectedBytes!,
        state.selectedFilename!,
      );

      // If we got a full response with transactions, we're done
      if (response.transactions != null && response.transactions!.isNotEmpty) {
        state = UploadState(result: response);
        return;
      }

      // Otherwise poll for status
      if (response.uploadId != null) {
        await _pollStatus(response.uploadId!);
        return;
      }

      state = UploadState(result: response);
    } on DioException catch (e) {
      final data = e.response?.data;
      final errorMsg = (data is Map && data['message'] != null)
          ? data['message']
          : 'Upload failed. Please try again.';
      state = UploadState(error: errorMsg);
    } catch (e) {
      state = UploadState(error: 'Upload failed: ${e.toString()}');
    }
  }

  Future<void> _pollStatus(String uploadId) async {
    state = UploadState(
      selectedFilename: state.selectedFilename,
      selectedBytes: state.selectedBytes,
      isPolling: true,
      pollSeconds: 0,
    );

    for (int i = 0; i < 60; i++) {
      await Future.delayed(const Duration(seconds: 2));
      if (!mounted) return;

      state = UploadState(
        selectedFilename: state.selectedFilename,
        selectedBytes: state.selectedBytes,
        isPolling: true,
        pollSeconds: (i + 1) * 2,
      );

      try {
        final status = await _uploadApi.getUploadStatus(uploadId);

        if (status.status == 'completed') {
          state = UploadState(result: status);
          return;
        }
        if (status.status == 'failed') {
          state = UploadState(
              error: status.message ?? 'Processing failed');
          return;
        }
      } on DioException {
        // Network blip during polling — retry
        continue;
      }
    }

    state = UploadState(
        error: 'Processing timed out. Please try again later.');
  }

  void reset() {
    state = const UploadState();
  }
}
