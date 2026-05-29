# Optimal AWS Deployment Order

## TL;DR: The Right Order

```
Phase 1 (Parallel):  S3 + Cognito + DynamoDB
Phase 2:             IAM Role
Phase 3:             Lambda Functions
Phase 4:             API Gateway
```

**Total time: ~15-20 minutes**

---

## Why This Order?

### Phase 1: Independent Services (Do in Parallel!)

These have **zero dependencies** on each other:

#### 1a. S3 (Do First in Sequence)
```bash
aws s3 mb s3://guideya-uploads-YOUR-ID --region eu-west-1
aws s3 mb s3://guideya-lambda-deploy --region eu-west-1
```

**Why first?**
- You'll need `guideya-lambda-deploy` to upload your Lambda .jar file later
- Quick to create (30 seconds)

**Outputs:**
- ✅ Upload bucket name: `guideya-uploads-YOUR-ID`
- ✅ Deploy bucket name: `guideya-lambda-deploy`

---

#### 1b. Cognito (Do Second - Takes Longest!)
```bash
# Via AWS Console (easier) or CLI
# See COGNITO_SETUP.md for full instructions
```

**Why second?**
- Takes 5-10 minutes to configure (lots of settings)
- You can let it run while doing other things
- Need User Pool ID and Client ID for Lambda

**Outputs:**
- ✅ User Pool ID: `eu-west-1_XXXXXXXXX`
- ✅ Client ID: `abc123def456...`

---

#### 1c. DynamoDB (Do Third - Very Quick!)
```bash
aws dynamodb create-table --table-name GuideYa-Users ...
aws dynamodb create-table --table-name GuideYa-Uploads ...
aws dynamodb create-table --table-name GuideYa-Transactions ...
```

**Why third?**
- Fastest to create (30 seconds each)
- Need table names for Lambda environment variables
- Need table ARNs for IAM role permissions

**Outputs:**
- ✅ Table names: `GuideYa-Users`, `GuideYa-Uploads`, `GuideYa-Transactions`
- ✅ Table ARNs: `arn:aws:dynamodb:eu-west-1:ACCOUNT:table/GuideYa-Users`

---

### Phase 2: IAM Role (Depends on Phase 1)

**Why now?**
- Lambda functions **require** an execution role
- IAM role needs **ARNs from S3, DynamoDB** for permissions
- Must exist before Lambda deployment

**Dependencies:**
```
✅ S3 bucket ARN       (from Phase 1a)
✅ DynamoDB table ARNs (from Phase 1c)
```

**Creates:**
```
GuideYaLambdaRole with permissions to:
  - DynamoDB (read/write Users, Uploads, Transactions)
  - S3 (read/write guideya-uploads-*)
  - Textract (analyze documents)
  - Bedrock (invoke Claude models)
  - CloudWatch Logs (write logs)
```

**Command:**
```bash
aws iam create-role --role-name GuideYaLambdaRole ...
aws iam put-role-policy --role-name GuideYaLambdaRole ...
```

**Outputs:**
- ✅ Role ARN: `arn:aws:iam::ACCOUNT:role/GuideYaLambdaRole`

---

### Phase 3: Lambda Functions (Depends on Phase 1 + 2)

**Why now?**
- Needs **all configuration** from previous phases
- Can't deploy without IAM role
- Can't configure without Cognito IDs, DynamoDB names, S3 bucket

**Dependencies:**
```
✅ IAM Role ARN          (from Phase 2)
✅ S3 deploy bucket      (from Phase 1a)
✅ S3 upload bucket name (from Phase 1a)
✅ Cognito User Pool ID  (from Phase 1b)
✅ Cognito Client ID     (from Phase 1b)
✅ DynamoDB table names  (from Phase 1c)
```

**Steps:**
```bash
# 1. Package Lambda
mvn clean package

# 2. Upload to S3
aws s3 cp target/lambda-auth-1.0.0.jar s3://guideya-lambda-deploy/

# 3. Create Lambda functions
aws lambda create-function \
  --function-name GuideYaAuthHandler \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT:role/GuideYaLambdaRole \
  --handler com.example.lambda.AuthHandler::handleRequest \
  --code S3Bucket=guideya-lambda-deploy,S3Key=lambda-auth-1.0.0.jar \
  --environment Variables="{
    COGNITO_USER_POOL_ID=eu-west-1_XXXXX,
    COGNITO_CLIENT_ID=abc123,
    DYNAMODB_USERS_TABLE=GuideYa-Users
  }"

aws lambda create-function \
  --function-name GuideYaUploadHandler \
  ...
```

**Outputs:**
- ✅ Auth Lambda ARN: `arn:aws:lambda:eu-west-1:ACCOUNT:function:GuideYaAuthHandler`
- ✅ Upload Lambda ARN: `arn:aws:lambda:eu-west-1:ACCOUNT:function:GuideYaUploadHandler`

---

### Phase 4: API Gateway (Depends on Phase 1b + 3)

**Why last?**
- Needs **Lambda ARNs** to know which functions to invoke
- Needs **Cognito User Pool ARN** for JWT authorizer
- This is the final "wiring" step that connects everything

**Dependencies:**
```
✅ Auth Lambda ARN     (from Phase 3)
✅ Upload Lambda ARN   (from Phase 3)
✅ Cognito User Pool ARN (from Phase 1b)
```

**Steps:**
```bash
# 1. Create REST API
aws apigateway create-rest-api --name GuideYaAPI

# 2. Create resources (/auth, /upload)
# 3. Create Cognito authorizer
# 4. Link Lambda functions to endpoints
# 5. Deploy to 'prod' stage
```

**Outputs:**
- ✅ API URL: `https://abc123.execute-api.eu-west-1.amazonaws.com/prod`

---

## What Happens if You Do It Wrong?

### ❌ Bad Order 1: Lambda Before S3

```bash
# Package Lambda
mvn clean package

# Try to deploy Lambda
aws lambda create-function --code S3Bucket=guideya-lambda-deploy ...
# ERROR: Bucket doesn't exist!

# Oh no, need to create S3 first
aws s3 mb s3://guideya-lambda-deploy

# Now re-upload
aws s3 cp target/lambda-auth-1.0.0.jar s3://guideya-lambda-deploy/

# Finally can deploy Lambda
aws lambda create-function ...
```

**Result:** Wasted time backtracking

---

### ❌ Bad Order 2: IAM Before DynamoDB

```bash
# Create IAM role
aws iam create-role --role-name GuideYaLambdaRole ...

# Try to add DynamoDB permissions
aws iam put-role-policy --policy-document '{
  "Resource": "arn:aws:dynamodb:eu-west-1:ACCOUNT:table/GuideYa-Users"
}'
# Oops, what's the table ARN? Tables don't exist yet!

# Have to create tables first
aws dynamodb create-table ...

# Then go back and update IAM policy
aws iam put-role-policy ...
```

**Result:** Had to update IAM policy later anyway

---

### ✅ Good Order: S3 → Cognito → DynamoDB → IAM → Lambda → API Gateway

```bash
# Create S3 (for Lambda code storage)
aws s3 mb s3://guideya-lambda-deploy ✅

# Create Cognito (long config, start early)
# ... via console ... ✅

# Create DynamoDB (get table ARNs)
aws dynamodb create-table ... ✅

# Create IAM (has all ARNs now)
aws iam create-role ... ✅

# Package and upload Lambda
mvn clean package
aws s3 cp target/lambda-auth-1.0.0.jar s3://guideya-lambda-deploy/ ✅

# Deploy Lambda (has role ARN, config values)
aws lambda create-function ... ✅

# Create API Gateway (has Lambda ARNs, Cognito ARN)
aws apigateway create-rest-api ... ✅
```

**Result:** Smooth deployment, no backtracking!

---

## Time Comparison

### Sequential (One at a Time)
```
S3:           2 min
Cognito:     10 min  ← Slowest!
DynamoDB:     2 min
IAM:          3 min
Lambda:       5 min
API Gateway:  8 min
─────────────────
Total:       30 min
```

### Optimized (S3 → Cognito || DynamoDB)
```
S3:           2 min
Cognito:     10 min } ← While Cognito loads, do DynamoDB
DynamoDB:     2 min }   (saves 2 min!)
IAM:          3 min
Lambda:       5 min
API Gateway:  8 min
─────────────────
Total:       28 min
```

### Fully Parallel (All Phase 1 at Once)
```
Phase 1 (parallel):  10 min  (limited by slowest = Cognito)
Phase 2 (IAM):        3 min
Phase 3 (Lambda):     5 min
Phase 4 (API GW):     8 min
─────────────────────
Total:               26 min
```

**Savings: 4 minutes!**

---

## Summary: Why Each Step is Where It Is

| Step | Position | Reason |
|------|----------|--------|
| **S3** | 1st | Need bucket for Lambda .jar upload |
| **Cognito** | 2nd | Takes longest, start early |
| **DynamoDB** | 3rd | Quick, need ARNs for IAM |
| **IAM** | 4th | Needs ARNs from S3 + DynamoDB |
| **Lambda** | 5th | Needs IAM role + all configs |
| **API Gateway** | 6th | Needs Lambda ARNs + Cognito ARN |

---

## The Real Answer to "Why DynamoDB First?"

**It doesn't have to be!**

The only rules are:
1. S3 + Cognito + DynamoDB **before** IAM
2. IAM **before** Lambda
3. Lambda + Cognito **before** API Gateway

**Within Phase 1 (S3, Cognito, DynamoDB), the order doesn't matter.**

I said "DynamoDB first" but that was arbitrary. **S3 first is actually better** because you need it for Lambda deployment.
