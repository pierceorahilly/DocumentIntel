import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/upload_models.dart';
import 'api_client.dart';

final uploadApiProvider = Provider<UploadApi>((ref) {
  return UploadApi(ref.read(dioProvider));
});

class UploadApi {
  final Dio _dio;

  UploadApi(this._dio);

  Future<UploadResponse> uploadPdf(Uint8List pdfBytes, String filename) async {
    final base64Pdf = base64Encode(pdfBytes);
    final response = await _dio.post(
      '/upload',
      data: base64Pdf,
      options: Options(
        headers: {
          'Content-Type': 'application/pdf',
          'X-Filename': filename,
        },
      ),
    );
    return UploadResponse.fromJson(response.data);
  }

  Future<UploadResponse> getUploadStatus(String uploadId) async {
    final response = await _dio.get('/status/$uploadId');
    return UploadResponse.fromJson(response.data);
  }
}
