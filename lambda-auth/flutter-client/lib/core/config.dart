class AppConfig {
  static const String apiBaseUrl =
      'https://vz1307i8t2.execute-api.eu-west-1.amazonaws.com/prod';
  static const String cognitoUserPoolId = 'eu-west-1_BSofFOCGg';
  static const String cognitoClientId = '1aiq9l331h0locean5mnut4nlk';
  static const String awsRegion = 'eu-west-1';
  static const bool demoMode = false;

  static String getFullUrl(String endpoint) => '$apiBaseUrl$endpoint';
}
