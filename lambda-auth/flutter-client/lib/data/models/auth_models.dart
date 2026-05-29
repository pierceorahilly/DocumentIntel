class LoginRequest {
  final String email;
  final String password;

  LoginRequest({required this.email, required this.password});

  Map<String, dynamic> toJson() => {'email': email, 'password': password};
}

class SignupRequest {
  final String email;
  final String password;
  final String name;
  final String dateOfBirth;
  final String address;

  SignupRequest({
    required this.email,
    required this.password,
    required this.name,
    required this.dateOfBirth,
    required this.address,
  });

  Map<String, dynamic> toJson() => {
        'email': email,
        'password': password,
        'name': name,
        'dateOfBirth': dateOfBirth,
        'address': address,
      };
}

class ConfirmEmailRequest {
  final String email;
  final String code;

  ConfirmEmailRequest({required this.email, required this.code});

  Map<String, dynamic> toJson() => {'email': email, 'code': code};
}

class ForgotPasswordRequest {
  final String email;

  ForgotPasswordRequest({required this.email});

  Map<String, dynamic> toJson() => {'email': email};
}

class ResetPasswordRequest {
  final String email;
  final String code;
  final String newPassword;

  ResetPasswordRequest({
    required this.email,
    required this.code,
    required this.newPassword,
  });

  Map<String, dynamic> toJson() => {
        'email': email,
        'code': code,
        'newPassword': newPassword,
      };
}

class LoginResponse {
  final String? idToken;
  final String? accessToken;
  final String? refreshToken;
  final int? expiresIn;
  final String? tokenType;
  final String? message;
  final String? userId;
  final bool? userConfirmed;

  LoginResponse({
    this.idToken,
    this.accessToken,
    this.refreshToken,
    this.expiresIn,
    this.tokenType,
    this.message,
    this.userId,
    this.userConfirmed,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) => LoginResponse(
        idToken: json['idToken'] as String?,
        accessToken: json['accessToken'] as String?,
        refreshToken: json['refreshToken'] as String?,
        expiresIn: json['expiresIn'] as int?,
        tokenType: json['tokenType'] as String?,
        message: json['message'] as String?,
        userId: json['userId'] as String?,
        userConfirmed: json['userConfirmed'] as bool?,
      );
}

class MessageResponse {
  final String? message;

  MessageResponse({this.message});

  factory MessageResponse.fromJson(Map<String, dynamic> json) =>
      MessageResponse(message: json['message'] as String?);
}
