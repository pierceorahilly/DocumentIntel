# AWS Cognito Setup for BankBuddy

## Overview

AWS Cognito provides user authentication, including signup, login, password reset, and JWT token management.

## Architecture

```
Client
  ↓ 1. Sign up (email + password)
Cognito User Pool
  ↓ 2. Send verification email
  ↓ 3. User confirms email
  ↓ 4. Login (email + password)
  ↓ 5. Return JWT tokens (ID token, Access token, Refresh token)
Client stores tokens
  ↓ 6. Upload PDF with ID token in Authorization header
API Gateway
  ↓ 7. Cognito Authorizer validates JWT
  ↓ 8. Extracts userId (sub claim) and passes to Lambda
Lambda
  ↓ 9. Process PDF for authenticated user
```

## Step 1: Create Cognito User Pool

### Via AWS Console

1. Go to **Amazon Cognito** Console
2. Click "Create user pool"

**Step 1: Configure sign-in experience**
- **Provider types**: Cognito user pool
- **Cognito user pool sign-in options**: ✅ Email
- Click "Next"

**Step 2: Configure security requirements**
- **Password policy**: Cognito defaults (8 chars, uppercase, lowercase, numbers, symbols)
- **Multi-factor authentication**: No MFA (or Optional MFA)
- **User account recovery**: Email only
- Click "Next"

**Step 3: Configure sign-up experience**
- **Self-service sign-up**: ✅ Enable
- **Attribute verification**: Email
- **Required attributes**:
  - ✅ email
  - ✅ name
  - ✅ birthdate (date of birth)
  - ✅ address
- Click "Next"

**Step 4: Configure message delivery**
- **Email**: Send email with Cognito (for testing)
  - For production: Use Amazon SES
- Click "Next"

**Step 5: Integrate your app**
- **User pool name**: `BankBuddyUserPool`
- **App type**: Public client
- **App client name**: `BankBuddyApp`
- **Client secret**: Don't generate a client secret (not needed for public clients)
- **Authentication flows**:
  - ✅ ALLOW_USER_PASSWORD_AUTH
  - ✅ ALLOW_REFRESH_TOKEN_AUTH
  - ✅ ALLOW_USER_SRP_AUTH (Secure Remote Password)
- Click "Next"

**Step 6: Review and create**
- Review settings
- Click "Create user pool"

### Via AWS CLI

```bash
aws cognito-idp create-user-pool \
  --pool-name BankBuddyUserPool \
  --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=true}" \
  --auto-verified-attributes email \
  --username-attributes email \
  --schema Name=email,Required=true \
  --region eu-west-1
```

Get the User Pool ID from output (format: `eu-west-1_XXXXXXXXX`)

Create app client:

```bash
aws cognito-idp create-user-pool-client \
  --user-pool-id eu-west-1_XXXXXXXXX \
  --client-name BankBuddyApp \
  --no-generate-secret \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH ALLOW_USER_SRP_AUTH \
  --region eu-west-1
```

Get the Client ID from output (format: `abc123...`)

---

## Step 2: Configure API Gateway Authorizer

### Via AWS Console

1. Go to **API Gateway** Console
2. Select your `BankBuddyAPI`
3. Click "Authorizers" in left menu
4. Click "Create New Authorizer"
   - **Name**: `CognitoAuthorizer`
   - **Type**: Cognito
   - **Cognito User Pool**: Select `BankBuddyUserPool`
   - **Token Source**: `Authorization` (header name)
   - **Token Validation**: Leave blank (uses ID token)
5. Click "Create"

6. Apply authorizer to endpoints:
   - Go to "Resources"
   - Click on `/upload` POST method
   - Click "Method Request"
   - **Authorization**: Select `CognitoAuthorizer`
   - Save

7. Deploy API:
   - Actions → Deploy API → Stage: `prod`

### Via AWS CLI

```bash
# Create authorizer
aws apigateway create-authorizer \
  --rest-api-id YOUR_API_ID \
  --name CognitoAuthorizer \
  --type COGNITO_USER_POOLS \
  --provider-arns arn:aws:cognito-idp:eu-west-1:ACCOUNT_ID:userpool/eu-west-1_XXXXXXXXX \
  --identity-source method.request.header.Authorization \
  --region eu-west-1

# Update method to use authorizer
aws apigateway update-method \
  --rest-api-id YOUR_API_ID \
  --resource-id RESOURCE_ID \
  --http-method POST \
  --patch-operations \
    op=replace,path=/authorizationType,value=COGNITO_USER_POOLS \
    op=replace,path=/authorizerId,value=AUTHORIZER_ID \
  --region eu-west-1
```

---

## Step 3: Add Authentication Endpoints

Create additional Lambda functions for auth operations:

### Endpoints to Add

| Endpoint | Method | Purpose | Auth Required |
|----------|--------|---------|---------------|
| `/auth/signup` | POST | Create new user | No |
| `/auth/login` | POST | Get JWT tokens | No |
| `/auth/confirm` | POST | Confirm email | No |
| `/auth/refresh` | POST | Refresh tokens | No |
| `/auth/forgot-password` | POST | Request reset | No |
| `/auth/reset-password` | POST | Reset password | No |
| `/upload` | POST | Upload PDF | **Yes** |
| `/uploads` | GET | List user uploads | **Yes** |
| `/transactions` | GET | Get user transactions | **Yes** |

---

## Step 4: Lambda Environment Variables

Update Lambda functions with Cognito config:

```bash
aws lambda update-function-configuration \
  --function-name BankBuddyProcessor \
  --environment Variables="{
    S3_BUCKET=bankbud,
    AWS_REGION=eu-west-1,
    BEDROCK_MODEL_ID=arn:...,
    COGNITO_USER_POOL_ID=eu-west-1_XXXXXXXXX,
    COGNITO_CLIENT_ID=abc123...,
    DYNAMODB_USERS_TABLE=BankBuddy-Users,
    DYNAMODB_UPLOADS_TABLE=BankBuddy-Uploads,
    DYNAMODB_TRANSACTIONS_TABLE=BankBuddy-Transactions
  }" \
  --region eu-west-1
```

---

## Step 5: IAM Permissions

Update Lambda IAM role to include DynamoDB and Cognito:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:PutItem",
        "dynamodb:GetItem",
        "dynamodb:UpdateItem",
        "dynamodb:Query",
        "dynamodb:BatchWriteItem"
      ],
      "Resource": [
        "arn:aws:dynamodb:eu-west-1:ACCOUNT_ID:table/BankBuddy-Users",
        "arn:aws:dynamodb:eu-west-1:ACCOUNT_ID:table/BankBuddy-Uploads",
        "arn:aws:dynamodb:eu-west-1:ACCOUNT_ID:table/BankBuddy-Uploads/index/*",
        "arn:aws:dynamodb:eu-west-1:ACCOUNT_ID:table/BankBuddy-Transactions",
        "arn:aws:dynamodb:eu-west-1:ACCOUNT_ID:table/BankBuddy-Transactions/index/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "cognito-idp:AdminCreateUser",
        "cognito-idp:AdminSetUserPassword",
        "cognito-idp:AdminGetUser"
      ],
      "Resource": "arn:aws:cognito-idp:eu-west-1:ACCOUNT_ID:userpool/eu-west-1_XXXXXXXXX"
    }
  ]
}
```

Attach via CLI:

```bash
aws iam put-role-policy \
  --role-name BankBuddyLambdaRole \
  --policy-name DynamoDBAndCognitoAccess \
  --policy-document file://permissions.json
```

---

## Authentication Flow Examples

### Signup

**Request**: `POST /auth/signup`
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
  "userId": "cognito-sub-uuid"
}
```

### Confirm Email

**Request**: `POST /auth/confirm`
```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

**Response**:
```json
{
  "message": "Email confirmed successfully"
}
```

### Login

**Request**: `POST /auth/login`
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

**Request**: `POST /upload`
```
Headers:
  Authorization: Bearer eyJraWQiOiI...  (ID token)
  Content-Type: application/pdf

Body: <base64-encoded-pdf>
```

**Response**:
```json
{
  "uploadId": "upload-uuid",
  "s3": "s3://bankbud/users/cognito-sub-uuid/pdfs/...",
  "transactions": [...],
  "advice": "..."
}
```

---

## JWT Token Details

### ID Token Claims

```json
{
  "sub": "cognito-user-uuid",  // User ID - use this!
  "email": "user@example.com",
  "name": "John Doe",
  "email_verified": true,
  "iss": "https://cognito-idp.eu-west-1.amazonaws.com/eu-west-1_XXXXXXXXX",
  "aud": "abc123...",  // Client ID
  "token_use": "id",
  "auth_time": 1702825200,
  "exp": 1702828800,
  "iat": 1702825200
}
```

**Extract userId in Lambda**:
```java
// API Gateway passes Cognito claims in request context
String userId = event.getRequestContext()
    .getAuthorizer()
    .getClaims()
    .get("sub");
```

---

## Security Best Practices

1. **Use HTTPS only** - Never send tokens over HTTP
2. **Store tokens securely** - Use secure storage in client app
3. **Validate tokens** - Always let API Gateway validate (don't skip)
4. **Short token expiry** - ID tokens expire in 1 hour (use refresh token)
5. **Rotate refresh tokens** - Implement token rotation
6. **Rate limiting** - Use API Gateway throttling
7. **Account lockout** - Enable in Cognito (5 failed attempts)
8. **MFA** - Consider enabling for premium users

---

## Testing with Postman/cURL

### Signup

```bash
curl -X POST https://YOUR_API_ID.execute-api.eu-west-1.amazonaws.com/prod/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestPass123!",
    "name": "Test User"
  }'
```

### Login

```bash
curl -X POST https://YOUR_API_ID.execute-api.eu-west-1.amazonaws.com/prod/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestPass123!"
  }'
```

### Upload (with token)

```bash
TOKEN="eyJraWQiOiI..."  # ID token from login response

curl -X POST https://YOUR_API_ID.execute-api.eu-west-1.amazonaws.com/prod/upload \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/pdf" \
  -H "X-Filename: test.pdf" \
  --data-binary "@statement.pdf"
```

---

## Cognito Pricing

**Free tier (permanent)**:
- 50,000 monthly active users (MAUs)

**After free tier**:
- $0.00550 per MAU (MAU = user who authenticates in a month)

**Your costs**:
- Personal use (1 user): **$0.00/month** (within free tier)
- Small business (10 users): **$0.00/month** (within free tier)
- Medium business (100 users): **$0.00/month** (within free tier)

**Cognito is effectively FREE for most use cases!**

---

## Troubleshooting

### "User is not confirmed"
- User didn't verify email
- Check Cognito console → Users → confirm manually for testing

### "Invalid token"
- Token expired (1 hour lifetime)
- Use refresh token to get new ID token

### "User already exists"
- Email already registered
- Implement proper error handling in client

### "Invalid password"
- Check password policy requirements
- Must meet minimum complexity

---

**Next**: See Java code implementation in `AuthHandler.java`
