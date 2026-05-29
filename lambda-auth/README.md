# BankBuddy Lambda with Authentication & Database

Complete serverless implementation with:
- ✅ AWS Cognito user authentication
- ✅ DynamoDB for user data & transactions
- ✅ User-specific S3 storage
- ✅ API Gateway with Cognito authorizer
- ✅ Full audit trail of uploads

## Architecture

```
Client (Swing)
    ↓ POST /auth/signup → Create account
Cognito
    ↓ POST /auth/login → Get JWT token
Client stores JWT
    ↓ POST /upload (+ Authorization: Bearer <token>)
API Gateway (validates JWT)
    ↓ Extract userId from token
Lambda (AuthenticatedUploadHandler)
    ↓ Upload to s3://bucket/users/{userId}/pdfs/...
    ↓ Save to DynamoDB (Users, Uploads, Transactions)
    ↓ Return response to client
```

## Project Structure

```
lambda-auth/
├── src/main/java/com/example/lambda/
│   ├── AuthHandler.java                      # Signup/login/confirm
│   ├── AuthenticatedUploadHandler.java       # PDF upload (requires auth)
│   ├── UserService.java                      # DynamoDB Users table
│   ├── UploadService.java                    # DynamoDB Uploads table
│   ├── TransactionService.java               # DynamoDB Transactions table
│   ├── UserSpecificStorageService.java       # S3 with user folders
│   ├── LambdaTextractService.java            # PDF text extraction
│   └── LambdaBedrockService.java             # Claude AI advice
├── pom.xml                                   # Maven dependencies
├── DATABASE_SCHEMA.md                        # DynamoDB tables design
├── COGNITO_SETUP.md                          # Cognito configuration
└── README.md                                 # This file
```

## Quick Start

### 1. Build

```bash
cd lambda-auth
mvn clean package
```

Creates `target/bankbuddy-lambda-auth-1.0.0.jar` (~20-25 MB)

### 2. Create DynamoDB Tables

```bash
# Users table
aws dynamodb create-table \
  --table-name BankBuddy-Users \
  --attribute-definitions AttributeName=userId,AttributeType=S \
  --key-schema AttributeName=userId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region eu-west-1

# Uploads table (with GSI for user queries)
aws dynamodb create-table \
  --table-name BankBuddy-Uploads \
  --attribute-definitions \
    AttributeName=uploadId,AttributeType=S \
    AttributeName=userId,AttributeType=S \
    AttributeName=uploadDate,AttributeType=S \
  --key-schema AttributeName=uploadId,KeyType=HASH \
  --global-secondary-indexes \
    '[{
      "IndexName": "UserUploadsIndex",
      "KeySchema": [
        {"AttributeName": "userId", "KeyType": "HASH"},
        {"AttributeName": "uploadDate", "KeyType": "RANGE"}
      ],
      "Projection": {"ProjectionType": "ALL"}
    }]' \
  --billing-mode PAY_PER_REQUEST \
  --region eu-west-1

# Transactions table (with GSIs)
aws dynamodb create-table \
  --table-name BankBuddy-Transactions \
  --attribute-definitions \
    AttributeName=transactionId,AttributeType=S \
    AttributeName=userId,AttributeType=S \
    AttributeName=date,AttributeType=S \
    AttributeName=uploadId,AttributeType=S \
  --key-schema AttributeName=transactionId,KeyType=HASH \
  --global-secondary-indexes \
    '[{
      "IndexName": "UserTransactionsIndex",
      "KeySchema": [
        {"AttributeName": "userId", "KeyType": "HASH"},
        {"AttributeName": "date", "KeyType": "RANGE"}
      ],
      "Projection": {"ProjectionType": "ALL"}
    },
    {
      "IndexName": "UploadTransactionsIndex",
      "KeySchema": [
        {"AttributeName": "uploadId", "KeyType": "HASH"}
      ],
      "Projection": {"ProjectionType": "ALL"}
    }]' \
  --billing-mode PAY_PER_REQUEST \
  --region eu-west-1
```

### 3. Create Cognito User Pool

Via AWS Console (recommended):
1. Go to Amazon Cognito
2. Create user pool
3. Sign-in: Email
4. Password policy: Defaults
5. MFA: Optional (or none for testing)
6. User pool name: `BankBuddyUserPool`
7. App client: `BankBuddyApp` (no client secret)
8. Auth flows: USER_PASSWORD_AUTH, REFRESH_TOKEN_AUTH
9. Note the **User Pool ID** and **Client ID**

### 4. Create Lambda Functions

**Auth Lambda** (handles signup/login):

```bash
aws lambda create-function \
  --function-name BankBuddy-Auth \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT_ID:role/BankBuddyLambdaRole \
  --handler com.example.lambda.AuthHandler::handleRequest \
  --zip-file fileb://target/bankbuddy-lambda-auth-1.0.0.jar \
  --timeout 30 \
  --memory-size 512 \
  --environment Variables="{
    AWS_REGION=eu-west-1,
    COGNITO_USER_POOL_ID=eu-west-1_XXXXXXXXX,
    COGNITO_CLIENT_ID=abc123...,
    DYNAMODB_USERS_TABLE=BankBuddy-Users
  }" \
  --region eu-west-1
```

**Upload Lambda** (handles authenticated PDF uploads):

```bash
aws lambda create-function \
  --function-name BankBuddy-Upload \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT_ID:role/BankBuddyLambdaRole \
  --handler com.example.lambda.AuthenticatedUploadHandler::handleRequest \
  --zip-file fileb://target/bankbuddy-lambda-auth-1.0.0.jar \
  --timeout 900 \
  --memory-size 1024 \
  --environment Variables="{
    S3_BUCKET=bankbud,
    AWS_REGION=eu-west-1,
    BEDROCK_MODEL_ID=arn:aws:bedrock:eu-west-1:073118365801:inference-profile/eu.anthropic.claude-3-5-sonnet-20240620-v1:0,
    DYNAMODB_USERS_TABLE=BankBuddy-Users,
    DYNAMODB_UPLOADS_TABLE=BankBuddy-Uploads,
    DYNAMODB_TRANSACTIONS_TABLE=BankBuddy-Transactions
  }" \
  --snap-start ApplyOn=PublishedVersions \
  --region eu-west-1

# Publish version for SnapStart
aws lambda publish-version --function-name BankBuddy-Upload --region eu-west-1
```

### 5. Create API Gateway

1. Create REST API: `BankBuddyAPI`
2. Create resources and methods:

| Resource | Method | Lambda | Auth Required |
|----------|--------|--------|---------------|
| `/auth/signup` | POST | BankBuddy-Auth | No |
| `/auth/login` | POST | BankBuddy-Auth | No |
| `/auth/confirm` | POST | BankBuddy-Auth | No |
| `/upload` | POST | BankBuddy-Upload | **Yes (Cognito)** |

3. Create Cognito Authorizer:
   - Type: Cognito User Pools
   - Name: `CognitoAuthorizer`
   - User Pool: `BankBuddyUserPool`
   - Token Source: `Authorization`

4. Apply authorizer to `/upload` POST method

5. Deploy API to stage: `prod`

## API Endpoints

### Signup

**POST** `/auth/signup`

```json
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "name": "John Doe"
}
```

**Response**:
```json
{
  "message": "User created successfully. Please check your email to confirm.",
  "userId": "cognito-sub-uuid",
  "userConfirmed": false
}
```

### Login

**POST** `/auth/login`

```json
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

**Response**:
```json
{
  "idToken": "eyJraWQiOiI...",
  "accessToken": "eyJraWQiOiI...",
  "refreshToken": "eyJjdHki...",
  "expiresIn": 3600,
  "tokenType": "Bearer"
}
```

### Upload PDF (Authenticated)

**POST** `/upload`

**Headers**:
```
Authorization: Bearer <idToken>
Content-Type: application/pdf
X-Filename: statement.pdf
```

**Body**: base64-encoded PDF

**Response**:
```json
{
  "uploadId": "upload-uuid",
  "s3PdfUrl": "s3://bankbud/users/.../pdfs/.../statement.pdf",
  "transactionCount": 42,
  "processingTime": 8.5,
  "transactions": [...],
  "advice": "..."
}
```

## S3 Structure

```
s3://bankbud/
└── users/
    └── {userId}/              # Cognito sub UUID
        ├── pdfs/
        │   └── 2025/12/17/
        │       └── {uploadId}-statement.pdf
        └── results/
            └── {uploadId}/
                ├── transactions.json
                └── advice.json
```

## DynamoDB Schema

### Users Table
- PK: `userId` (Cognito sub)
- Attributes: email, name, s3Prefix, totalUploads, totalTransactions

### Uploads Table
- PK: `uploadId`
- GSI: `userId` + `uploadDate` (for querying user's uploads)
- Attributes: filename, s3PdfUrl, transactionCount, status

### Transactions Table
- PK: `transactionId`
- GSI1: `userId` + `date` (for querying user's transactions)
- GSI2: `uploadId` (for querying upload's transactions)
- Attributes: date, description, amount, balance, category

## Cost Estimate (50 uploads/month)

| Service | Cost/Month | Notes |
|---------|------------|-------|
| Lambda | $0.01 | Within free tier |
| Textract | $1.13 | 70KB PDFs, ~1.5 pages each |
| Bedrock | $0.41 | Claude 3.5 Sonnet |
| S3 | $0.00 | Negligible storage |
| DynamoDB | $0.00 | Within free tier (25 GB + requests) |
| Cognito | $0.00 | Free (under 50K MAU) |
| **Total** | **$1.55/month** | **~$19/year** |

**Savings vs EC2**: 78% cheaper ($84/year → $19/year)

## Client Updates

Update your Swing client to:

1. **Add login screen**: Collect email/password, call `/auth/login`
2. **Store JWT token**: Save `idToken` after login
3. **Send token with uploads**: Add `Authorization: Bearer <token>` header
4. **Handle token expiry**: Use refresh token to get new tokens

See `CLIENT_UPDATES.md` for Java code examples.

## Security

- ✅ Cognito JWT validation (API Gateway)
- ✅ User data isolation (S3 folders + DynamoDB userId)
- ✅ Encrypted S3 (AES-256)
- ✅ HTTPS only
- ✅ Password complexity requirements
- ⚠️ Consider MFA for production

## Monitoring

**CloudWatch Logs**:
- `/aws/lambda/BankBuddy-Auth`
- `/aws/lambda/BankBuddy-Upload`

**DynamoDB Metrics**:
- ReadCapacityUnits, WriteCapacityUnits
- UserErrors (throttling)

## Next Steps

1. ✅ Deploy infrastructure (DynamoDB, Cognito, Lambda)
2. ✅ Test API endpoints with Postman
3. 🔲 Update Swing client with auth flow
4. 🔲 Add user profile endpoints (GET /user, PUT /user)
5. 🔲 Add upload history endpoint (GET /uploads)
6. 🔲 Add transaction search endpoint (GET /transactions?date=...)

---

**Documentation**:
- [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md) - Detailed DynamoDB schema
- [COGNITO_SETUP.md](./COGNITO_SETUP.md) - Cognito configuration guide

**Ready to deploy!** 🚀
