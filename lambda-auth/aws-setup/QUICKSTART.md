# Quick Start - Deploy Guide-ya to AWS

This guide will get your Guide-ya app deployed to AWS in ~30 minutes.

## Prerequisites (5 minutes)

### 1. Install AWS CLI

**Windows:**
```powershell
winget install Amazon.AWSCLI
```

**Or download:** https://aws.amazon.com/cli/

### 2. Configure AWS Credentials

```powershell
aws configure
```

Enter:
- **AWS Access Key ID:** Get from AWS Console → IAM → Users → Security credentials
- **AWS Secret Access Key:** Get from AWS Console → IAM → Users → Security credentials
- **Default region:** `eu-west-1` (or your preferred region)
- **Default output format:** `json`

### 3. Verify Installation

```powershell
# Check AWS CLI
aws --version

# Check credentials work
aws sts get-caller-identity

# You should see your Account ID and User ARN
```

---

## Configuration (2 minutes)

### 1. Edit config.properties

Open `config.properties` in a text editor.

**Change these 3 lines:**

```properties
# 1. Your AWS Account ID (from: aws sts get-caller-identity --query Account --output text)
AWS_ACCOUNT_ID=YOUR_ACCOUNT_ID_HERE

# 2. Unique S3 bucket name for uploads (must be globally unique!)
S3_UPLOADS_BUCKET=guideya-uploads-YOUR-UNIQUE-ID

# 3. Unique S3 bucket name for Lambda code (must be globally unique!)
S3_LAMBDA_BUCKET=guideya-lambda-deploy-YOUR-UNIQUE-ID
```

**Example:**
```properties
AWS_ACCOUNT_ID=123456789012
S3_UPLOADS_BUCKET=guideya-uploads-john2025
S3_LAMBDA_BUCKET=guideya-lambda-john2025
```

**Tips:**
- Replace `YOUR-UNIQUE-ID` with your name + year (e.g., `john2025`)
- Bucket names must be globally unique across ALL AWS accounts
- Use lowercase letters, numbers, and hyphens only

---

## Deployment (30 minutes automated)

### 1. Run the Deployment Script

```powershell
cd D:\simple-bank-ai_patch\lambda-auth\aws-setup
.\deploy-complete.ps1
```

**What happens:**
- ✅ Validates prerequisites (AWS CLI, Java, Maven)
- ✅ Builds Lambda JAR (~15 MB)
- ✅ Creates S3 buckets
- ✅ Creates Cognito User Pool
- ✅ Creates DynamoDB tables
- ✅ Creates IAM roles with permissions
- ✅ Deploys Lambda functions
- ✅ Creates API Gateway
- ✅ Enables CORS
- ✅ Deploys to production
- ✅ Validates everything

**Grab coffee! This takes ~30 minutes.**

### 2. Script Output

When complete, you'll see:

```
╔════════════════════════════════════════╗
║        DEPLOYMENT SUCCESSFUL!          ║
╚════════════════════════════════════════╝

API Configuration:
  API URL: https://abc123xyz.execute-api.eu-west-1.amazonaws.com/prod

Cognito Configuration:
  User Pool ID: eu-west-1_XXXXX
  Client ID: abc123def456

API Endpoints:
  POST https://abc123xyz.execute-api.eu-west-1.amazonaws.com/prod/auth (signup, login, confirm)
  POST https://abc123xyz.execute-api.eu-west-1.amazonaws.com/prod/upload (requires JWT)

Next Steps:
  1. Update desktop app Config.java:
     public static final String API_BASE_URL = "https://abc123xyz.execute-api.eu-west-1.amazonaws.com/prod";
     public static final boolean DEMO_MODE = false;

  2. Rebuild desktop app:
     cd D:\simple-bank-ai_patch\lambda-auth\desktop-client
     mvn clean package

  3. Run desktop app:
     java -jar target\bankbuddy-desktop-1.0.0.jar

  4. Test signup and login!

Configuration saved to: deployment-output.json
```

**Copy the API URL!** You'll need it for the next step.

---

## Update Desktop App (2 minutes)

### 1. Edit Config.java

Open `D:\simple-bank-ai_patch\lambda-auth\desktop-client\src\main\java\com\example\desktop\config\Config.java`

**Change these lines:**

```java
// OLD:
public static final String API_BASE_URL = "https://YOUR_API_ID.execute-api.eu-west-1.amazonaws.com/prod";
public static final boolean DEMO_MODE = true;

// NEW (paste your API URL from deployment output):
public static final String API_BASE_URL = "https://abc123xyz.execute-api.eu-west-1.amazonaws.com/prod";
public static final boolean DEMO_MODE = false;
```

### 2. Rebuild Desktop App

```powershell
cd D:\simple-bank-ai_patch\lambda-auth\desktop-client
mvn clean package
```

### 3. Run Desktop App

```powershell
java -jar target\bankbuddy-desktop-1.0.0.jar
```

---

## Test It! (2 minutes)

### 1. Sign Up

- Click "Sign Up"
- Enter:
  - Email: `yourname@example.com`
  - Password: `Password123!`
  - Name: `Your Name`
  - Date of Birth: `1990-01-15`
  - Address: `123 Main St`
- Click "Sign Up"

### 2. Confirm Email

Check your email for confirmation code, then:
- Enter confirmation code
- Click "Confirm"

### 3. Login

- Email: `yourname@example.com`
- Password: `Password123!`
- Click "Login"

**You should see the main screen!** 🎉

### 4. Upload PDF

- Click "Upload Bank Statement"
- Select a PDF bank statement (max 4 MB)
- Click "Upload"
- Wait for AI analysis (~30 seconds)
- View transactions and financial advice!

---

## Troubleshooting

### Error: "Bucket name already taken"

S3 bucket names are globally unique. Change in `config.properties`:

```properties
S3_UPLOADS_BUCKET=guideya-uploads-yourname-2025-v2
S3_LAMBDA_BUCKET=guideya-lambda-yourname-2025-v2
```

Then re-run the script (it's safe to re-run!):
```powershell
.\deploy-complete.ps1
```

### Error: "AWS credentials not configured"

Run:
```powershell
aws configure
```

Enter your AWS Access Key ID and Secret Access Key.

### Error: "Maven build failed"

Make sure you're in the right directory:
```powershell
cd D:\simple-bank-ai_patch\lambda-auth
mvn clean package
```

### Error: "IAM role propagation"

The script automatically waits 10 seconds. If it still fails, just re-run:
```powershell
.\deploy-complete.ps1
```

The script is idempotent - it skips resources that already exist.

### Script Partially Failed

If the script fails partway through:

1. Check what was created:
   ```powershell
   # Check Lambda functions
   aws lambda list-functions --query 'Functions[?starts_with(FunctionName, `GuideYa`)].FunctionName'

   # Check API Gateway
   aws apigateway get-rest-apis --query 'items[?name==`GuideYaAPI`]'

   # Check DynamoDB tables
   aws dynamodb list-tables
   ```

2. Re-run the script (it will skip existing resources):
   ```powershell
   .\deploy-complete.ps1
   ```

---

## Advanced Options

### Dry Run (Test Without Creating)

```powershell
.\deploy-complete.ps1 -DryRun
```

Shows what would be created without making changes.

### Skip JAR Build (Use Existing)

```powershell
.\deploy-complete.ps1 -SkipBuild
```

Skips Maven build if JAR already exists.

---

## Cost Information

**During free tier (12 months):**
- Everything: **$0.61/month** (for 20 PDFs)

**After free tier:**
- Same usage: **$0.76/month**

**50K users (5K active, 10K PDFs/month):**
- **$120/month** total
  - Bedrock: $105 (87%)
  - Textract: $15 (13%)

**Monetization strategy:**
- Free tier: 3 PDFs/month
- Pro: $5/month (20 PDFs)
- **Revenue:** $3,495/month (699 paying users at 10% conversion)
- **Profit:** $3,375/month

---

## What Was Created?

The deployment script created:

**S3 Buckets:**
- `guideya-uploads-xxx` - Stores uploaded PDFs
- `guideya-lambda-xxx` - Stores Lambda deployment package

**Cognito:**
- User Pool: `GuideYaUserPool` - Manages user authentication
- App Client: `GuideYaDesktopClient` - Desktop app integration

**DynamoDB Tables:**
- `GuideYa-Users` - User profiles, providers, recent transactions
- `GuideYa-Uploads` - Upload metadata
- `GuideYa-Transactions` - All transactions history

**IAM:**
- Role: `GuideYaLambdaExecutionRole` - Permissions for Lambda functions

**Lambda Functions:**
- `GuideYaAuthHandler` - Handles signup, login, confirm
- `GuideYaUploadHandler` - Processes PDFs, extracts transactions, generates advice

**API Gateway:**
- `GuideYaAPI` - REST API
  - `POST /auth` - Authentication endpoints
  - `POST /upload` - PDF upload endpoint (JWT protected)

---

## Next Steps

1. ✅ Deploy to AWS (you just did this!)
2. ✅ Update desktop app
3. ✅ Test signup and login

**Optional:**
- Add more features to desktop app
- Build mobile app (uses same API)
- Add export to Excel feature
- Add budgeting features
- Add recurring payment detection

---

## Need Help?

**Check logs:**
```powershell
# Lambda logs
aws logs tail /aws/lambda/GuideYaAuthHandler --follow

# API Gateway logs (if enabled)
aws logs tail /aws/apigateway/GuideYaAPI --follow
```

**Common issues:**
- Bucket name conflicts → Use more unique name
- IAM permission errors → Re-run script (waits for propagation)
- Maven build errors → Check Java version (needs 17+)

**Manual cleanup (if needed):**
```powershell
# Delete everything
aws cloudformation delete-stack --stack-name guideya-stack
# OR manually delete resources via AWS Console
```

---

## Summary

That's it! You now have:
- ✅ Serverless backend deployed to AWS
- ✅ Cognito authentication working
- ✅ DynamoDB storing data
- ✅ Lambda processing PDFs
- ✅ Bedrock AI generating financial advice
- ✅ Desktop app connected to live API

**Total time:** ~40 minutes
**Total cost (free tier):** $0.61/month

Enjoy your Guide-ya financial advisor app! 🎉
