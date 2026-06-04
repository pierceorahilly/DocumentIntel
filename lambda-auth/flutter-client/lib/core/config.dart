import '../config.local.dart' as localConfig;

class AppConfig {
  static const String apiBaseUrl = localConfig.API_BASE_URL;
  static const String cognitoUserPoolId = 'eu-west-1_BSofFOCGg';
  static const String cognitoClientId = '1aiq9l331h0locean5mnut4nlk';
  static const String awsRegion = 'eu-west-1';
  static const bool demoMode = false;

  static String getFullUrl(String endpoint) => '$apiBaseUrl$endpoint';
}
