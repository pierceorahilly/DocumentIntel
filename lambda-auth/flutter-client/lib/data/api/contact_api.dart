import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/auth_models.dart';
import '../models/contact_models.dart';
import 'api_client.dart';

final contactApiProvider = Provider<ContactApi>((ref) {
  return ContactApi(ref.read(dioProvider));
});

class ContactApi {
  final Dio _dio;

  ContactApi(this._dio);

  Future<MessageResponse> submitContactRequest(
      ContactRequest request) async {
    final response = await _dio.post(
      '/contact-request',
      data: request.toJson(),
    );
    return MessageResponse.fromJson(response.data);
  }
}
