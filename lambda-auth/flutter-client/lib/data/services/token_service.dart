import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final tokenServiceProvider = Provider<TokenService>((ref) => TokenService());

class TokenService {
  static const _keyIdToken = 'id_token';
  static const _keyAccessToken = 'access_token';
  static const _keyRefreshToken = 'refresh_token';
  static const _keyUserEmail = 'user_email';

  final _storage = const FlutterSecureStorage();

  Future<void> saveTokens({
    required String idToken,
    required String accessToken,
    required String refreshToken,
  }) async {
    await Future.wait([
      _storage.write(key: _keyIdToken, value: idToken),
      _storage.write(key: _keyAccessToken, value: accessToken),
      _storage.write(key: _keyRefreshToken, value: refreshToken),
    ]);
  }

  Future<void> saveUserEmail(String email) =>
      _storage.write(key: _keyUserEmail, value: email);

  Future<String?> getIdToken() => _storage.read(key: _keyIdToken);
  Future<String?> getAccessToken() => _storage.read(key: _keyAccessToken);
  Future<String?> getRefreshToken() => _storage.read(key: _keyRefreshToken);
  Future<String?> getUserEmail() => _storage.read(key: _keyUserEmail);

  Future<bool> hasTokens() async {
    final token = await getIdToken();
    return token != null && token.isNotEmpty;
  }

  Future<void> clearTokens() => _storage.deleteAll();
}
