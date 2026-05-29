# Deployment Checklist - Are the APIs Built?

## Current Status

### ✅ CODED (Exists in your project)
```
✅ Lambda code written (AuthHandler.java, AuthenticatedUploadHandler.java)
✅ Desktop client written (LoginPanel.java, MainPanel.java, ApiClient.java)
✅ Database schema documented (DATABASE_SCHEMA.md)
✅ All business logic implemented
```

### ❌ NOT DEPLOYED (Needs to be created in AWS)
```
❌ Lambda functions (not uploaded to AWS)
❌ API Gateway (endpoints don't exist yet)
❌ DynamoDB tables (not created)
❌ S3 buckets (you have one, need to configure)
❌ Cognito User Pool (not created)
❌ IAM roles (not created)
```

**Think of it like this:**
- You have the **source code** for a website
- But the website is **not live** on the internet yet
- You need to **deploy** it to a server

---

## What "Building the APIs" Means

### Step 1: Package Lambda Code ✅ (You Can Do Now)

```bash
cd D:\simple-bank-ai_patch\lambda-auth
mvn clean package

# Output:
# target/lambda-auth-1.0.0.jar ← This is your Lambda code!
```

**Status:** ✅ This works on your PC right now!

---

### Step 2: Upload Lambda to AWS ❌ (Not Done Yet)

```bash
# Upload JAR to S3
aws s3 cp target/lambda-auth-1.0.0.jar s3://YOUR-BUCKET/

# Create Lambda function
aws lambda create-function \
  --function-name GuideYaAuthHandler \
  --runtime java17 \
  --handler com.example.lambda.AuthHandler::handleRequest \
  --code S3Bucket=YOUR-BUCKET,S3Key=lambda-auth-1.0.0.jar
```

**Status:** ❌ These Lambda functions don't exist in AWS yet!

**What this means:**
- Right now, if you try to call `https://YOUR-API.amazonaws.com/auth/login`, you'll get **404 Not Found**
- Because the API doesn't exist in the cloud yet!

---

### Step 3: Create API Gateway Endpoints ❌ (Not Done Yet)

```bash
# Create API
aws apigateway create-rest-api --name GuideYaAPI

# Create resource /auth
aws apigateway create-resource --rest-api-id API_ID --parent-id ROOT_ID --path-part auth

# Create method POST /auth/login
aws apigateway put-method \
  --rest-api-id API_ID \
  --resource-id RESOURCE_ID \
  --http-method POST \
  --authorization-type NONE

# Link to Lambda
aws apigateway put-integration \
  --rest-api-id API_ID \
  --resource-id RESOURCE_ID \
  --http-method POST \
  --type AWS_PROXY \
  --integration-http-method POST \
  --uri arn:aws:apigateway:REGION:lambda:path/2015-03-31/functions/LAMBDA_ARN/invocations
```

**Status:** ❌ API Gateway doesn't exist yet!

**What this means:**
- No public URL like `https://abc123.execute-api.eu-west-1.amazonaws.com/prod/auth/login` exists
- Your desktop app can't call these endpoints because they're not live!

---

## Visual: What Exists vs What Doesn't

### Your Local Machine ✅

```
D:\simple-bank-ai_patch\lambda-auth\
├── src\
│   ├── AuthHandler.java          ✅ CODE EXISTS
│   └── AuthenticatedUploadHandler.java ✅ CODE EXISTS
├── desktop-client\
│   └── ApiClient.java             ✅ CODE EXISTS
└── target\
    └── lambda-auth-1.0.0.jar      ✅ COMPILED JAR EXISTS
```

### AWS Cloud ❌

```
AWS Account:
├── Lambda Functions:              ❌ NONE DEPLOYED
│   ├── GuideYaAuthHandler         ❌ Doesn't exist
│   └── GuideYaUploadHandler       ❌ Doesn't exist
│
├── API Gateway:                   ❌ NONE CREATED
│   ├── POST /auth/login           ❌ Doesn't exist
│   ├── POST /auth/signup          ❌ Doesn't exist
│   └── POST /upload               ❌ Doesn't exist
│
├── DynamoDB:                      ❌ NONE CREATED
│   ├── GuideYa-Users              ❌ Doesn't exist
│   └── GuideYa-Transactions       ❌ Doesn't exist
│
└── Cognito:                       ❌ NONE CREATED
    └── GuideYaUserPool            ❌ Doesn't exist
```

---

## Quick Test: Are APIs Live?

### Test 1: Try to call login endpoint

Open PowerShell and run:

```powershell
# Replace with your API URL (when deployed)
curl https://YOUR-API-ID.execute-api.eu-west-1.amazonaws.com/prod/auth/login `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"email":"test@test.com","password":"password123"}'
```

**Current result:** ❌ Error (API doesn't exist)

```
curl: (6) Could not resolve host: YOUR-API-ID.execute-api.eu-west-1.amazonaws.com
```

**After deployment:** ✅ Returns JWT tokens

---

### Test 2: Check Lambda functions exist

```bash
aws lambda list-functions --query 'Functions[?starts_with(FunctionName, `GuideYa`)].FunctionName'
```

**Current result:**
```json
[]
```

**After deployment:**
```json
[
  "GuideYaAuthHandler",
  "GuideYaUploadHandler"
]
```

---

### Test 3: Check API Gateway exists

```bash
aws apigateway get-rest-apis --query 'items[?name==`GuideYaAPI`]'
```

**Current result:**
```json
[]
```

**After deployment:**
```json
[
  {
    "id": "abc123xyz",
    "name": "GuideYaAPI",
    "createdDate": "2025-12-20T10:30:00Z"
  }
]
```

---

## What Happens When You Run Desktop App Now?

### Current Behavior (Before AWS Deployment)

**User clicks "Login":**

```
Desktop App (Config.java):
  API_BASE_URL = "https://YOUR_API_ID.execute-api.eu-west-1.amazonaws.com/prod"

ApiClient.login(email, password):
  POST https://YOUR_API_ID.execute-api.eu-west-1.amazonaws.com/prod/auth/login

HTTP Client:
  ❌ ERROR: Could not resolve host
  OR
  ❌ ERROR: 404 Not Found (if you put placeholder URL)

Desktop App:
  Shows error message: "Login failed: Connection refused"
```

**Why?** Because the API endpoint doesn't exist in AWS yet!

---

### After AWS Deployment

**User clicks "Login":**

```
Desktop App:
  API_BASE_URL = "https://abc123xyz.execute-api.eu-west-1.amazonaws.com/prod"

ApiClient.login(email, password):
  POST https://abc123xyz.execute-api.eu-west-1.amazonaws.com/prod/auth/login

API Gateway:
  ✅ Receives request
  ✅ Routes to Lambda

Lambda (AuthHandler):
  ✅ Calls Cognito
  ✅ Returns JWT tokens

Desktop App:
  ✅ Stores tokens
  ✅ Shows main screen
```

---

## Deployment Steps Summary

### Phase 1: AWS Setup (You Need to Do This)

```
1. Create S3 bucket          (aws s3 mb ...)
2. Create Cognito User Pool  (via Console or CLI)
3. Create DynamoDB tables    (aws dynamodb create-table ...)
4. Create IAM role           (aws iam create-role ...)
```

**Time:** ~30 minutes
**Status:** ❌ Not done yet

---

### Phase 2: Lambda Deployment (You Need to Do This)

```
5. Package Lambda            (mvn clean package) ✅ Can do now
6. Upload JAR to S3          (aws s3 cp ...)
7. Create Lambda functions   (aws lambda create-function ...)
8. Set environment variables (COGNITO_POOL_ID, etc.)
```

**Time:** ~15 minutes
**Status:** ❌ Not done yet

---

### Phase 3: API Gateway Setup (You Need to Do This)

```
9. Create API Gateway        (aws apigateway create-rest-api ...)
10. Create resources         (/auth, /upload)
11. Create methods           (POST /auth/login, etc.)
12. Link to Lambda           (put-integration)
13. Create Cognito authorizer
14. Deploy to 'prod' stage
```

**Time:** ~20 minutes
**Status:** ❌ Not done yet

---

### Phase 4: Desktop App Configuration

```
15. Get API URL              (from API Gateway)
16. Update Config.java       (API_BASE_URL = "https://...")
17. Rebuild desktop app      (mvn clean package)
18. Test login!
```

**Time:** 5 minutes
**Status:** ❌ Waiting for Phase 1-3

---

## Can You Use DEMO_MODE Now? ✅

**YES!** Demo mode works without AWS:

### Config.java
```java
public static final boolean DEMO_MODE = true;
```

**Demo mode behavior:**
- Login: ✅ Simulates successful login (no AWS call)
- Signup: ✅ Simulates account creation
- Upload: ✅ Shows sample transactions and advice
- No network calls to AWS ✅

**Use this to test the desktop UI while deploying AWS!**

---

## Timeline to Go Live

### Fast Track (Manual AWS Console - Easiest)
```
Day 1:
  - 1 hour: Create Cognito User Pool (lots of clicking)
  - 30 min: Create DynamoDB tables
  - 30 min: Create S3 bucket + IAM role
  - 1 hour: Create Lambda functions (upload JAR, configure)
  - 1 hour: Create API Gateway (lots of clicking)

  Total: ~4 hours
```

### Automated (AWS CLI Scripts)
```
Day 1:
  - Run deployment script
  - Wait ~30 minutes

  Total: 30 minutes (but requires learning CLI)
```

---

## What You Should Do Next

### Option 1: Manual Deployment (Recommended for Learning)

Follow the step-by-step guides:
1. `COGNITO_SETUP.md` - Create User Pool
2. `DATABASE_SCHEMA.md` - Create DynamoDB tables
3. Lambda deployment guide (I can create this)
4. API Gateway setup guide (I can create this)

**Pros:**
- Learn how everything works
- Easy to debug
- See what you're creating

**Cons:**
- Takes 3-4 hours
- Lots of clicking

---

### Option 2: Automated Script (Faster but Complex)

I can create a deployment script:

```bash
./deploy.sh
  - Creates all AWS resources
  - Uploads Lambda
  - Configures API Gateway
  - Outputs API URL
```

**Pros:**
- Fast (30 min)
- Repeatable

**Cons:**
- Harder to debug if something fails
- Less learning

---

## Summary

**Question:** "Are all these APIs built, like POST /auth/login?"

**Answer:**

✅ **CODE is built** - AuthHandler.java has all the logic
✅ **DESKTOP CLIENT is built** - ApiClient.java makes the calls
❌ **APIs NOT DEPLOYED** - They don't exist in AWS yet
❌ **Can't use the app** (except DEMO_MODE)

**Think of it like:**
- You have the **recipe** ✅
- But you haven't **cooked the meal** yet ❌

**Next step:** Deploy to AWS to make the APIs live!

---

## Want Me To Create Deployment Scripts?

I can create:
1. ✅ Step-by-step manual guide (learn how it works)
2. ✅ Automated deployment script (faster)
3. ✅ Both!

Let me know what you prefer!
