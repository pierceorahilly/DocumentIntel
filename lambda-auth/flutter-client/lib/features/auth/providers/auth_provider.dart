import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../data/api/auth_api.dart';
import '../../../data/models/auth_models.dart';
import '../../../data/services/token_service.dart';

class AuthState {
  final bool isAuthenticated;
  final bool isLoading;
  final String? userEmail;
  final String? error;

  const AuthState({
    this.isAuthenticated = false,
    this.isLoading = false,
    this.userEmail,
    this.error,
  });

  AuthState copyWith({
    bool? isAuthenticated,
    bool? isLoading,
    String? userEmail,
    String? error,
  }) =>
      AuthState(
        isAuthenticated: isAuthenticated ?? this.isAuthenticated,
        isLoading: isLoading ?? this.isLoading,
        userEmail: userEmail ?? this.userEmail,
        error: error,
      );
}

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(
    ref.read(authApiProvider),
    ref.read(tokenServiceProvider),
  );
});

class AuthNotifier extends StateNotifier<AuthState> {
  final AuthApi _authApi;
  final TokenService _tokenService;

  AuthNotifier(this._authApi, this._tokenService) : super(const AuthState()) {
    tryAutoLogin();
  }

  Future<void> tryAutoLogin() async {
    state = state.copyWith(isLoading: true);
    final hasTokens = await _tokenService.hasTokens();
    if (hasTokens) {
      final email = await _tokenService.getUserEmail();
      state = AuthState(isAuthenticated: true, userEmail: email);
    } else {
      state = const AuthState();
    }
  }

  Future<bool> login(String email, String password) async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      final response = await _authApi.login(
        LoginRequest(email: email, password: password),
      );
      await _tokenService.saveTokens(
        idToken: response.idToken!,
        accessToken: response.accessToken!,
        refreshToken: response.refreshToken!,
      );
      await _tokenService.saveUserEmail(email);
      state = AuthState(isAuthenticated: true, userEmail: email);
      return true;
    } on DioException catch (e) {
      final statusCode = e.response?.statusCode;
      final data = e.response?.data;
      String errorMsg;
      if (statusCode == 401) {
        errorMsg = 'Invalid email or password.';
      } else if (statusCode == 403) {
        errorMsg = 'Email not confirmed. Please check your email.';
      } else if (data is Map && (data['message'] ?? data['error']) != null) {
        errorMsg = (data['message'] ?? data['error']);
      } else {
        errorMsg = 'Login failed. Please try again.';
      }
      state = state.copyWith(isLoading: false, error: errorMsg);
      return false;
    } catch (e) {
      state = state.copyWith(
          isLoading: false, error: 'Connection error. Please try again.');
      return false;
    }
  }

  Future<bool> signup(String email, String password, String name,
      String dateOfBirth, String address) async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      await _authApi.signup(SignupRequest(
        email: email,
        password: password,
        name: name,
        dateOfBirth: dateOfBirth,
        address: address,
      ));
      state = state.copyWith(isLoading: false);
      return true;
    } on DioException catch (e) {
      final data = e.response?.data;
      final errorMsg = (data is Map && (data['message'] ?? data['error']) != null)
          ? (data['message'] ?? data['error'])
          : 'Signup failed. Please try again.';
      state = state.copyWith(isLoading: false, error: errorMsg);
      return false;
    } catch (e) {
      state = state.copyWith(
          isLoading: false, error: 'Connection error. Please try again.');
      return false;
    }
  }

  Future<bool> confirmEmail(String email, String code) async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      await _authApi.confirmEmail(
        ConfirmEmailRequest(email: email, code: code),
      );
      state = state.copyWith(isLoading: false);
      return true;
    } on DioException catch (e) {
      final data = e.response?.data;
      final errorMsg = (data is Map && (data['message'] ?? data['error']) != null)
          ? (data['message'] ?? data['error'])
          : 'Confirmation failed. Please try again.';
      state = state.copyWith(isLoading: false, error: errorMsg);
      return false;
    } catch (e) {
      state = state.copyWith(
          isLoading: false, error: 'Connection error. Please try again.');
      return false;
    }
  }

  Future<bool> forgotPassword(String email) async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      await _authApi.forgotPassword(ForgotPasswordRequest(email: email));
      state = state.copyWith(isLoading: false);
      return true;
    } on DioException catch (e) {
      final data = e.response?.data;
      final errorMsg = (data is Map && (data['message'] ?? data['error']) != null)
          ? (data['message'] ?? data['error'])
          : 'Failed to send reset code.';
      state = state.copyWith(isLoading: false, error: errorMsg);
      return false;
    } catch (e) {
      state = state.copyWith(
          isLoading: false, error: 'Connection error. Please try again.');
      return false;
    }
  }

  Future<bool> resetPassword(
      String email, String code, String newPassword) async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      await _authApi.resetPassword(
        ResetPasswordRequest(
            email: email, code: code, newPassword: newPassword),
      );
      state = state.copyWith(isLoading: false);
      return true;
    } on DioException catch (e) {
      final data = e.response?.data;
      final errorMsg = (data is Map && (data['message'] ?? data['error']) != null)
          ? (data['message'] ?? data['error'])
          : 'Password reset failed.';
      state = state.copyWith(isLoading: false, error: errorMsg);
      return false;
    } catch (e) {
      state = state.copyWith(
          isLoading: false, error: 'Connection error. Please try again.');
      return false;
    }
  }

  Future<void> logout() async {
    await _tokenService.clearTokens();
    state = const AuthState();
  }
}
