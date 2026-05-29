# BankBuddy Android App

Complete Android mobile app for BankBuddy - upload bank statements and get AI-powered financial advice on your phone!

## Features

✅ **User Authentication** - Signup, login, email confirmation
✅ **Secure Token Storage** - Encrypted SharedPreferences
✅ **PDF Upload** - Select and upload bank statement PDFs
✅ **Real-time Processing** - Watch as AWS processes your statement
✅ **Financial Advice** - Get personalized savings tips from Claude AI
✅ **Transaction History** - View all extracted transactions
✅ **Material Design** - Modern, clean UI

## Screenshots

```
Login Screen → PDF Upload → Processing → Financial Advice → Transactions
```

## Requirements

- **Android Studio** (latest version)
- **Android SDK** 26+ (Android 8.0+)
- **Java 17**
- **AWS API Gateway URL** (from your Lambda deployment)

## Quick Start

### 1. Install Android Studio

Download from: https://developer.android.com/studio

### 2. Open Project

```bash
cd lambda-auth/mobile-client
# Open in Android Studio: File → Open → Select mobile-client folder
```

### 3. Configure API Endpoint

Edit `app/build.gradle`:

```gradle
buildConfigField "String", "API_BASE_URL", "\"https://YOUR_API_ID.execute-api.eu-west-1.amazonaws.com/prod\""
```

Replace `YOUR_API_ID` with your actual API Gateway ID.

### 4. Sync Dependencies

Android Studio will prompt you to sync Gradle. Click "Sync Now".

### 5. Build APK

**Option A: Build in Android Studio**
1. Build → Build Bundle(s) / APK(s) → Build APK(s)
2. Wait for build to complete
3. APK location: `app/build/outputs/apk/debug/app-debug.apk`

**Option B: Build via Command Line**

```bash
./gradlew assembleDebug
```

APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### 6. Install on Phone

**Option A: USB Cable**
1. Enable Developer Options on Android phone
2. Enable USB Debugging
3. Connect phone via USB
4. Click "Run" in Android Studio (green play button)

**Option B: Transfer APK**
1. Copy `app-debug.apk` to phone
2. Open file on phone
3. Allow "Install from Unknown Sources"
4. Install

**Option C: ADB Command**

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
mobile-client/
├── app/
│   ├── build.gradle                           # Dependencies & config
│   ├── src/main/
│   │   ├── AndroidManifest.xml                # App permissions & activities
│   │   ├── java/com/example/bankbuddy/
│   │   │   ├── LoginActivity.java             # Login/signup screen
│   │   │   ├── MainActivity.java              # PDF upload screen
│   │   │   ├── ResultsActivity.java           # Show advice & transactions
│   │   │   ├── api/
│   │   │   │   └── BankBuddyApiClient.java    # AWS API client
│   │   │   ├── models/
│   │   │   │   └── Models.java                # Request/response models
│   │   │   ├── utils/
│   │   │   │   └── TokenManager.java          # Secure token storage
│   │   │   └── adapters/
│   │   │       └── TransactionAdapter.java    # RecyclerView adapter
│   │   └── res/
│   │       └── layout/
│   │           ├── activity_login.xml
│   │           ├── activity_main.xml
│   │           ├── activity_results.xml
│   │           └── item_transaction.xml
│   └── proguard-rules.pro
└── README.md                                  # This file
```

## How to Use the App

### 1. First Time Setup

1. Open app
2. Tap "Don't have an account? Sign up"
3. Enter email, password (min 8 chars), and name
4. Tap "Sign Up"
5. Check email for 6-digit code
6. Enter code and tap "Confirm Email"
7. Login with email and password

### 2. Upload Bank Statement

1. Tap "Select PDF"
2. Choose bank statement PDF from phone
3. Tap "Upload and Process"
4. Wait 10-15 seconds for processing
5. View results!

### 3. View Results

- **Financial Advice**: AI-generated savings tips at top
- **Transactions**: Scroll down to see all extracted transactions
- **Summary**: Transaction count and processing time

## API Integration

The app connects to your AWS Lambda backend:

### Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/auth/signup` | POST | Create account |
| `/auth/login` | POST | Get JWT tokens |
| `/auth/confirm` | POST | Verify email |
| `/upload` | POST | Upload PDF + get advice |

### Authentication Flow

```
1. User signs up → Cognito creates account
2. User confirms email → Cognito verifies
3. User logs in → Get JWT tokens
4. Store tokens securely (EncryptedSharedPreferences)
5. Upload PDF → Send JWT in Authorization header
6. API Gateway validates JWT → Lambda processes
```

## Security Features

✅ **Encrypted Token Storage** - Uses Android Security Crypto library
✅ **HTTPS Only** - All network calls over TLS
✅ **No Hardcoded Secrets** - API URL in build config
✅ **Auto Token Expiry** - Handles 401 errors gracefully
✅ **Secure PDF Handling** - PDFs stored in app cache (auto-deleted)

## Customization

### Change Theme Colors

Edit `res/values/colors.xml`:

```xml
<resources>
    <color name="primary">#2EE889</color>
    <color name="primary_dark">#25C46F</color>
    <color name="accent">#FF6B6B</color>
    <color name="background">#F5F5F5</color>
</resources>
```

### Change App Name

Edit `res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">BankBuddy</string>
</resources>
```

### Change Icon

Replace files in `res/mipmap-*/ic_launcher.png`

## Troubleshooting

### Build Errors

**Error: "SDK not found"**
- Install Android SDK via Android Studio SDK Manager
- Tools → SDK Manager → Install SDK 34

**Error: "Java version mismatch"**
- File → Project Structure → JDK Location → Select Java 17

### Runtime Errors

**Error: "Network error: Unable to resolve host"**
- Check API_BASE_URL in build.gradle
- Ensure phone has internet connection
- Verify API Gateway is deployed

**Error: "Session expired. Please login again"**
- JWT token expired (1 hour lifetime)
- Normal behavior - just login again
- TODO: Implement auto token refresh

**Error: "Invalid PDF file"**
- PDF must be a valid bank statement
- Check file size < 50MB (Lambda limit)
- Ensure PDF contains table data

### Testing on Emulator

1. Tools → Device Manager
2. Create Virtual Device (API 34, x86_64)
3. Click Play to launch emulator
4. Run app (Shift+F10)

## Release Build (Production)

### 1. Generate Signing Key

```bash
keytool -genkey -v -keystore bankbuddy-release.keystore \
  -alias bankbuddy -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Configure Signing

Edit `app/build.gradle`:

```gradle
android {
    signingConfigs {
        release {
            storeFile file('bankbuddy-release.keystore')
            storePassword 'YOUR_PASSWORD'
            keyAlias 'bankbuddy'
            keyPassword 'YOUR_PASSWORD'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'

            // Production API URL
            buildConfigField "String", "API_BASE_URL", "\"https://prod.api.bankbuddy.com\""
        }
    }
}
```

### 3. Build Release APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### 4. Distribute

**Option A: Google Play Store**
1. Create developer account ($25 one-time fee)
2. Build AAB: `./gradlew bundleRelease`
3. Upload to Play Console
4. Submit for review

**Option B: Direct Distribution**
1. Host APK on your website
2. Users download and install
3. Requires "Unknown Sources" permission

**Option C: Internal Testing**
1. Share APK file directly
2. Install via ADB or file transfer

## Performance

- **App Size**: ~8 MB (debug), ~4 MB (release with ProGuard)
- **Min Android**: 8.0 (API 26)
- **Target Android**: 14.0 (API 34)
- **Network Usage**: ~70 KB upload per PDF
- **Memory**: ~50 MB RAM usage

## Future Enhancements

- [ ] Upload history screen (GET /uploads)
- [ ] Transaction search & filtering
- [ ] Dark mode
- [ ] Biometric login (fingerprint)
- [ ] Auto token refresh
- [ ] Offline mode (cache results)
- [ ] Push notifications (when processing complete)
- [ ] In-app PDF viewer

## Dependencies

| Library | Purpose | Version |
|---------|---------|---------|
| AndroidX AppCompat | UI compatibility | 1.6.1 |
| Material Components | Material Design | 1.11.0 |
| Retrofit | HTTP client | 2.9.0 |
| OkHttp | Network layer | 4.12.0 |
| Gson | JSON parsing | 2.10.1 |
| Security Crypto | Encrypted storage | 1.1.0 |
| PDF Viewer | (Future) PDF display | 3.2.0 |

## Support

For issues:
1. Check this README
2. Verify API Gateway is deployed
3. Check Android Studio Logcat for errors
4. Test API endpoints with Postman first

## License

MIT License - Use freely for personal or commercial projects

---

**Ready to build!** 🚀

Just update the API URL in `build.gradle` and hit Run in Android Studio!
