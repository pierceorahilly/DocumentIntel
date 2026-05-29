# BankBuddy Database Schema (DynamoDB)

## Overview

Three DynamoDB tables to store user data, upload history, and individual transactions.

## Table 1: Users

**Purpose**: Store user profile and settings

**Primary Key**:
- `userId` (String, Partition Key) - Cognito user sub (UUID)

**Attributes**:
```json
{
  "userId": "cognito-sub-uuid",
  "email": "user@example.com",
  "name": "John Doe",
  "dateOfBirth": "1990-01-15",
  "address": "123 Main St, City, Country",
  "s3Prefix": "users/cognito-sub-uuid/",
  "createdAt": "2025-12-17T10:30:00Z",
  "lastLogin": "2025-12-17T10:30:00Z",
  "subscriptionTier": "free",
  "totalUploads": 42,
  "totalTransactions": 1250,
  "totalSpending": -15430.50,

  "_comment_providers": "Auto-detected from transaction descriptions",
  "telephoneProvider": "Vodafone",
  "energyProvider": "British Gas",
  "internetProvider": "Sky",
  "waterProvider": "Thames Water",
  "gasProvider": "British Gas",
  "bankName": "Barclays",

  "_comment_recentTransactions": "Last 100 transactions as JSON (for quick access)",
  "recentTransactions": "[{\"date\":\"2025-12-15\",\"description\":\"Vodafone Bill\",\"amount\":\"-45.00\",\"balance\":\"1234.56\"},{\"date\":\"2025-12-14\",\"description\":\"Tesco\",\"amount\":\"-23.50\",\"balance\":\"1280.06\"}]"
}
```

**Why store recent transactions in Users table?**
- **Fast access**: No need to query Transactions table for recent data
- **Under size limit**: 100 transactions × 200 bytes = ~20 KB (well under 400 KB limit)
- **Full history in Transactions table**: All historical data still queryable by date range

**GSI**: None needed (small table, query by userId only)

**Provisioning**: On-demand (pay per request)

---

## Table 2: Uploads

**Purpose**: Track each PDF upload and processing status

**Primary Key**:
- `uploadId` (String, Partition Key) - UUID

**GSI #1: UserUploadsIndex**
- Partition Key: `userId`
- Sort Key: `uploadDate` (descending)
- Purpose: Get all uploads for a user, sorted by date

**Attributes**:
```json
{
  "uploadId": "upload-uuid",
  "userId": "cognito-sub-uuid",
  "filename": "statement-jan-2025.pdf",
  "uploadDate": "2025-12-17T10:30:00Z",
  "status": "completed",
  "s3PdfUrl": "s3://bucket/users/{userId}/pdfs/2025/12/17/uuid-statement.pdf",
  "s3TransactionsUrl": "s3://bucket/users/{userId}/results/uuid/transactions.json",
  "s3AdviceUrl": "s3://bucket/users/{userId}/results/uuid/advice.json",
  "transactionCount": 42,
  "fileSize": 71680,
  "processingTime": 8.5,
  "error": null
}
```

**Status values**: `processing`, `completed`, `failed`

**Provisioning**: On-demand

---

## Table 3: Transactions

**Purpose**: Store individual transaction records extracted from PDFs

**Primary Key**:
- `transactionId` (String, Partition Key) - UUID

**GSI #1: UserTransactionsIndex**
- Partition Key: `userId`
- Sort Key: `date` (descending)
- Purpose: Get all transactions for a user, sorted by date

**GSI #2: UploadTransactionsIndex**
- Partition Key: `uploadId`
- Purpose: Get all transactions from a specific upload

**Attributes**:
```json
{
  "transactionId": "txn-uuid",
  "userId": "cognito-sub-uuid",
  "uploadId": "upload-uuid",
  "date": "2025-11-15",
  "description": "AMAZON.COM PURCHASE",
  "amount": "-45.99",
  "balance": "1234.56",
  "category": "shopping",
  "uploadDate": "2025-12-17T10:30:00Z"
}
```

**Provisioning**: On-demand

---

## DynamoDB Cost Estimate

**Assumptions**:
- 50 PDFs/month
- 30 transactions per PDF
- Read pattern: 10 reads per upload (view history)

### Storage Costs

**Users table**:
- 1 user × 500 bytes = 500 bytes ≈ **$0.00/month**

**Uploads table**:
- 50 uploads/month × 400 bytes = 20 KB/month
- After 12 months: 600 uploads × 400 bytes = 240 KB ≈ **$0.00/month**

**Transactions table**:
- 50 uploads × 30 txns × 200 bytes = 300 KB/month
- After 12 months: 18,000 txns × 200 bytes = 3.6 MB ≈ **$0.001/month**

**Storage pricing**: $0.25 per GB/month
- **Total storage**: Negligible (<$0.01/month)

### Request Costs

**On-Demand Pricing** (EU-West-1):
- Write: $1.25 per million requests
- Read: $0.25 per million requests

**Per upload cycle (50/month)**:
- 1 write to Users (update counters)
- 1 write to Uploads
- 30 writes to Transactions
- 5 reads (fetch user data, query history)
- **Total**: 32 writes + 5 reads per upload

**Monthly costs (50 uploads)**:
- Writes: 50 × 32 × ($1.25 / 1M) = $0.002
- Reads: 50 × 5 × ($0.25 / 1M) = $0.00006
- **Total**: **$0.002/month** (negligible)

**Free tier**: 25 GB storage + 25 WCU + 25 RCU per month FREE (permanent)

---

## S3 Organization Structure

```
s3://bankbud/
├── users/
│   └── {userId}/                          # Cognito sub UUID
│       ├── pdfs/                          # Original PDFs
│       │   └── 2025/
│       │       └── 12/
│       │           └── 17/
│       │               └── {uploadId}-statement.pdf
│       └── results/                       # Processing results
│           └── {uploadId}/
│               ├── transactions.json
│               └── advice.json
```

**Benefits**:
- User data isolation
- Easy to implement data deletion (delete user folder)
- Simple access control (IAM policies per userId)
- Organized by date hierarchy

---

## Access Patterns

### 1. User signup
**Operation**: Create user in Cognito → Write to Users table
```
PutItem(Users, userId=cognitoSub, email=..., s3Prefix=...)
```

### 2. User login
**Operation**: Cognito authentication → Read user data
```
GetItem(Users, userId=cognitoSub)
```

### 3. Upload PDF
**Operation**:
1. GetItem(Users, userId) - verify user exists
2. PutItem(Uploads, uploadId, userId, status=processing)
3. Upload to S3: users/{userId}/pdfs/...
4. Process with Textract + Bedrock
5. BatchWriteItem(Transactions, 30 items)
6. UpdateItem(Uploads, status=completed, transactionCount=30)
7. UpdateItem(Users, totalUploads++, totalTransactions+=30)

### 4. View upload history
**Operation**: Query user's uploads sorted by date
```
Query(Uploads, GSI=UserUploadsIndex, userId=..., SortKey=uploadDate DESC, Limit=20)
```

### 5. View transactions
**Operation**: Query user's transactions sorted by date
```
Query(Transactions, GSI=UserTransactionsIndex, userId=..., SortKey=date DESC, Limit=100)
```

### 6. View single upload details
**Operation**: Get upload + all its transactions
```
GetItem(Uploads, uploadId)
Query(Transactions, GSI=UploadTransactionsIndex, uploadId=...)
```

---

## Table Creation (AWS CLI)

### Users Table

```bash
aws dynamodb create-table \
  --table-name BankBuddy-Users \
  --attribute-definitions \
    AttributeName=userId,AttributeType=S \
  --key-schema \
    AttributeName=userId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region eu-west-1
```

### Uploads Table

```bash
aws dynamodb create-table \
  --table-name BankBuddy-Uploads \
  --attribute-definitions \
    AttributeName=uploadId,AttributeType=S \
    AttributeName=userId,AttributeType=S \
    AttributeName=uploadDate,AttributeType=S \
  --key-schema \
    AttributeName=uploadId,KeyType=HASH \
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
```

### Transactions Table

```bash
aws dynamodb create-table \
  --table-name BankBuddy-Transactions \
  --attribute-definitions \
    AttributeName=transactionId,AttributeType=S \
    AttributeName=userId,AttributeType=S \
    AttributeName=date,AttributeType=S \
    AttributeName=uploadId,AttributeType=S \
  --key-schema \
    AttributeName=transactionId,KeyType=HASH \
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

---

## Data Retention Policy

**Recommended**: Keep all data indefinitely (storage is cheap)

**Alternative**: Lifecycle policies
- Archive to S3 Glacier after 90 days (PDFs only)
- Delete transactions older than 2 years
- Keep Uploads metadata indefinitely (small)

**GDPR Compliance**: Implement data deletion on user request
```
1. Delete from DynamoDB: Users, Uploads, Transactions (filter by userId)
2. Delete from S3: s3://bucket/users/{userId}/ (entire folder)
3. Delete from Cognito: Delete user pool entry
```

---

## Security

1. **Encryption at rest**: Enabled by default (AWS-managed keys)
2. **Encryption in transit**: HTTPS only
3. **Access control**: Lambda IAM role with least-privilege
4. **User isolation**: S3 paths and DynamoDB userId ensure data separation
5. **Audit logging**: Enable CloudTrail for DynamoDB access logs

---

## Monitoring

**CloudWatch Metrics**:
- ReadCapacityUnits
- WriteCapacityUnits
- UserErrors (throttling)
- SystemErrors

**Alarms**:
- Set alarm if writes fail (indicates Lambda issues)
- Set alarm if read latency > 100ms

---

## Migration from File-Based Storage

If you have existing local JSON files:

```python
# Python script to migrate uploads/results/* to DynamoDB
import boto3
import json
import os
from datetime import datetime

dynamodb = boto3.resource('dynamodb', region_name='eu-west-1')
uploads_table = dynamodb.Table('BankBuddy-Uploads')
transactions_table = dynamodb.Table('BankBuddy-Transactions')

# For each transactions-*.json file in uploads/results/
for file in os.listdir('uploads/results'):
    if file.startswith('transactions-'):
        upload_id = file.replace('transactions-', '').replace('.json', '')

        with open(f'uploads/results/{file}') as f:
            transactions = json.load(f)

        # Create upload record
        uploads_table.put_item(Item={
            'uploadId': upload_id,
            'userId': 'MIGRATION_USER',  # or map to real user
            'uploadDate': datetime.now().isoformat(),
            'status': 'completed',
            'transactionCount': len(transactions)
        })

        # Create transaction records
        for txn in transactions:
            transactions_table.put_item(Item={
                'transactionId': str(uuid.uuid4()),
                'userId': 'MIGRATION_USER',
                'uploadId': upload_id,
                'date': txn.get('date', ''),
                'description': txn.get('description', ''),
                'amount': txn.get('amount', ''),
                'balance': txn.get('balance', '')
            })
```

---

**Next**: See Cognito setup in `COGNITO_SETUP.md`
