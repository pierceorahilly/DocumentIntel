import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/auth_models.dart';
import 'api_client.dart';

final authApiProvider = Provider<AuthApi>((ref) {
  return AuthApi(ref.read(dioProvider));
});

class AuthApi {
  final Dio _dio;

  AuthApi(this._dio);

  Future<LoginResponse> login(LoginRequest request) async {
    final response = await _dio.post('/auth/login', data: request.toJson());
    return LoginResponse.fromJson(response.data);
  }

  Future<LoginResponse> signup(SignupRequest request) async {
    final response = await _dio.post('/auth/signup', data: request.toJson());
    return LoginResponse.fromJson(response.data);
  }

  Future<MessageResponse> confirmEmail(ConfirmEmailRequest request) async {
    final response =
        await _dio.post('/auth/confirm', data: request.toJson());
    return MessageResponse.fromJson(response.data);
  }

  Future<LoginResponse> refreshToken(String refreshToken) async {
    final response = await _dio.post('/auth/refresh', data: {
      'refreshToken': refreshToken,
    });
    return LoginResponse.fromJson(response.data);
  }

  Future<MessageResponse> forgotPassword(ForgotPasswordRequest request) async {
    final response =
        await _dio.post('/auth/forgot-password', data: request.toJson());
    return MessageResponse.fromJson(response.data);
  }

  Future<MessageResponse> resetPassword(ResetPasswordRequest request) async {
    final response =
        await _dio.post('/auth/reset-password', data: request.toJson());
    return MessageResponse.fromJson(response.data);
  }
}
