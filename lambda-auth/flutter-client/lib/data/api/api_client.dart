import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/config.dart';
import '../services/token_service.dart';

final dioProvider = Provider<Dio>((ref) {
  final tokenService = ref.read(tokenServiceProvider);
  final dio = Dio(BaseOptions(
    baseUrl: AppConfig.apiBaseUrl,
    connectTimeout: const Duration(seconds: 30),
    receiveTimeout: const Duration(seconds: 120),
    sendTimeout: const Duration(seconds: 30),
    headers: {'Content-Type': 'application/json'},
  ));

  dio.interceptors.add(_AuthInterceptor(tokenService, dio));
  return dio;
});

class _AuthInterceptor extends Interceptor {
  final TokenService _tokenService;
  final Dio _dio;

  _AuthInterceptor(this._tokenService, this._dio);

  @override
  void onRequest(
      RequestOptions options, RequestInterceptorHandler handler) async {
    // Don't add auth header to auth endpoints
    if (!options.path.contains('/auth')) {
      final token = await _tokenService.getIdToken();
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode == 401 &&
        !err.requestOptions.path.contains('/auth')) {
      // Try refreshing the token
      final refreshToken = await _tokenService.getRefreshToken();
      if (refreshToken != null) {
        try {
          final response = await _dio.post(
            '/auth/refresh',
            data: {
              'refreshToken': refreshToken,
            },
          );
          final data = response.data as Map<String, dynamic>;
          await _tokenService.saveTokens(
            idToken: data['idToken'],
            accessToken: data['accessToken'],
            refreshToken: refreshToken,
          );
          // Retry the original request
          final retryOptions = err.requestOptions;
          retryOptions.headers['Authorization'] =
              'Bearer ${data['idToken']}';
          final retryResponse = await _dio.fetch(retryOptions);
          handler.resolve(retryResponse);
          return;
        } catch (_) {
          await _tokenService.clearTokens();
        }
      }
    }
    handler.next(err);
  }
}
