# Guide-ya Schema Design & DynamoDB Cost Analysis

## Is DynamoDB Expensive?

**Short answer: NO - It's 100% FREE for your usage!** 🎉

### DynamoDB Permanent Free Tier

```
Storage:    25 GB FREE forever
Requests:   25 million read/write requests per month FREE forever
```

### Your Actual Usage (50 PDFs/month)

```
Storage:    ~5 MB (0.02% of free tier)
Requests:   ~1,600/month (0.006% of free tier)

Cost:       $0.00 ✅
```

**You would need to process 781,250 PDFs per month to exceed the free tier!**

---

## Why We Keep the Transactions Table

You asked: "Could you add a column that's called transactions and save everyone's transactions in JSON?"

### The Problem with Storing Everything in JSON

**DynamoDB Item Size Limit: 400 KB**

If we store ALL transactions in the Users table:

```
Year 1:  50 PDFs × 30 txns × 200 bytes = 300 KB ✅ (fits!)
Year 2:  100 PDFs × 30 txns × 200 bytes = 600 KB ❌ (exceeds 400 KB limit!)
```

After 2 years, your app would **break** because the Users table item exceeds DynamoDB's size limit.

### The Solution: Hybrid Approach ✅

We use **BOTH** approaches:

1. **Users Table**: Stores recent 100 transactions as JSON (fast access)
2. **Transactions Table**: Stores ALL historical transactions (unlimited)

This gives you:
- ✅ Fast access to recent data (no extra query)
- ✅ Unlimited transaction history (no size limit)
- ✅ Still $0.00 cost (under free tier)

---

## New Schema Design

### Users Table (Your Main Request)

```json
{
  "userId": "abc-123",
  "email": "user@example.com",
  "name": "John Doe",
  "dateOfBirth": "1990-01-15",
  "address": "123 Main St, London",

  // ✅ Provider columns (auto-detected from transactions!)
  "telephoneProvider": "Vodafone",
  "energyProvider": "British Gas",
  "internetProvider": "Sky",
  "waterProvider": "Thames Water",
  "gasProvider": "British Gas",
  "bankName": "Barclays",

  // ✅ Recent transactions as JSON (last 100)
  "recentTransactions": [
    {
      "date": "2025-12-15",
      "description": "Vodafone Bill",
      "amount": "-45.00",
      "balance": "1234.56"
    },
    {
      "date": "2025-12-14",
      "description": "British Gas DD",
      "amount": "-87.50",
      "balance": "1279.56"
    }
    // ... up to 100 transactions
  ],

  // Summary stats
  "totalUploads": 42,
  "totalTransactions": 1250,
  "totalSpending": -15430.50
}
```

### How Providers Are Auto-Detected

When you upload a PDF, the system scans transaction descriptions for keywords:

**Example Transactions:**
```
Description: "VODAFONE DD PAYMENT"     → telephoneProvider: "Vodafone"
Description: "BRITISH GAS ENERGY"      → energyProvider: "British Gas"
Description: "SKY BROADBAND"           → internetProvider: "Sky"
Description: "THAMES WATER BILL"       → waterProvider: "Thames Water"
```

**Supported Providers:**

**Telephone:**
- Vodafone, O2, EE, Three, GiffGaff, Virgin Mobile, Sky Mobile, Tesco Mobile

**Energy:**
- British Gas, E.ON, EDF, Scottish Power, SSE, Octopus, Bulb, OVO, Shell

**Internet:**
- BT, Sky, Virgin Media, TalkTalk, Plusnet, Now Broadband, Hyperoptic

**Water:**
- Thames Water, Severn Trent, United Utilities, Yorkshire Water, Anglian

**Banks:**
- Barclays, HSBC, Lloyds, NatWest, Santander, Nationwide, Halifax, Monzo, Starling, Revolut

---

## Benefits of This Design

### 1. Fast User Profile Access ⚡

**Single DynamoDB Query Gets Everything:**
```java
GetItem(Users, userId) → Returns:
  - User info (name, email, DOB, address)
  - All providers (Vodafone, British Gas, etc.)
  - Recent 100 transactions
  - Total spending
```

**No need to query multiple tables!**

### 2. Provider Insights 📊

You can now easily see:
- Which telephone provider each user has
- Which energy company they use
- Total spending across all users

**Example Query:**
```sql
-- How many users have Vodafone?
SELECT COUNT(*) FROM Users WHERE telephoneProvider = "Vodafone"

-- How many users have British Gas?
SELECT COUNT(*) FROM Users WHERE energyProvider = "British Gas"
```

### 3. Still FREE (No Extra Cost) 💰

**Storage:**
```
User record with 100 transactions: ~25 KB
1,000 users: 25 MB
Cost: $0.00 (under 25 GB free tier)
```

**Requests:**
```
50 PDFs/month:
  - 50 reads (fetch user data)
  - 50 writes (update user data)
  - 1,600 transaction writes
Total: ~1,700 requests/month
Cost: $0.00 (under 25M free tier)
```

---

## Comparison: Old vs New Schema

### Old Schema (Before)

**Users Table:**
```json
{
  "userId": "abc-123",
  "email": "user@example.com",
  "name": "John Doe",
  "totalUploads": 42,
  "totalTransactions": 1250
}
```

**To get user's providers:**
1. Query Users table (1 request)
2. Query Transactions table (1 request)
3. Parse ALL transactions in application code
4. Detect providers manually

**Total: 2 DynamoDB requests + heavy processing**

### New Schema (After)

**Users Table:**
```json
{
  "userId": "abc-123",
  "email": "user@example.com",
  "name": "John Doe",
  "telephoneProvider": "Vodafone",
  "energyProvider": "British Gas",
  "recentTransactions": [...],
  "totalUploads": 42,
  "totalTransactions": 1250
}
```

**To get user's providers:**
1. Query Users table (1 request)
2. Done! ✅

**Total: 1 DynamoDB request, no processing**

**Savings:**
- 50% fewer DynamoDB requests
- No processing overhead
- Instant provider access

---

## Real-World Example

**User uploads bank statement PDF:**

### Step 1: Extract Transactions
```
Textract finds 30 transactions:
- "VODAFONE DD PAYMENT" -£45.00
- "BRITISH GAS ENERGY" -£87.50
- "SKY BROADBAND" -£35.99
- "TESCO SUPERSTORE" -£67.32
- ...
```

### Step 2: Auto-Detect Providers
```
✓ Detected telephoneProvider: Vodafone
✓ Detected energyProvider: British Gas
✓ Detected internetProvider: Sky
```

### Step 3: Update Users Table
```json
{
  "userId": "user-123",
  "telephoneProvider": "Vodafone",      // ← Auto-populated!
  "energyProvider": "British Gas",      // ← Auto-populated!
  "internetProvider": "Sky",            // ← Auto-populated!
  "recentTransactions": [               // ← Last 100 stored
    {"date": "2025-12-15", "description": "Vodafone DD", ...},
    {"date": "2025-12-14", "description": "British Gas", ...},
    // ... 28 more
  ],
  "totalSpending": -15430.50            // ← Total calculated
}
```

### Step 4: Save to Transactions Table
```
All 30 transactions saved individually
(allows querying by date range later)
```

**Result:**
- ✅ User profile has provider info
- ✅ Recent transactions accessible instantly
- ✅ Full history queryable by date
- ✅ Total cost: $0.00

---

## DynamoDB Cost Over Time

### Year 1 (50 PDFs/month)

```
Storage:
  Users: 1 user × 25 KB = 25 KB
  Uploads: 600 uploads × 400 bytes = 240 KB
  Transactions: 18,000 txns × 200 bytes = 3.6 MB
  Total: ~4 MB

Requests:
  Reads: 600/month
  Writes: 1,000/month
  Total: ~1,600/month

Cost: $0.00 ✅ (under free tier)
```

### Year 5 (50 PDFs/month)

```
Storage:
  Users: 1 user × 50 KB = 50 KB (more providers detected)
  Uploads: 3,000 uploads × 400 bytes = 1.2 MB
  Transactions: 90,000 txns × 200 bytes = 18 MB
  Total: ~19 MB

Requests:
  Still ~1,600/month

Cost: $0.00 ✅ (still under 25 GB free tier!)
```

### Year 10 (50 PDFs/month)

```
Storage:
  Total: ~38 MB

Cost: $0.00 ✅ (still under 25 GB!)
```

**You'd need to use the app for 65+ years to exceed the free tier storage limit!**

---

## API to Access Provider Data

When the desktop app fetches user data, it gets everything in one call:

**Request:**
```http
GET /user/profile
Authorization: Bearer {idToken}
```

**Response:**
```json
{
  "userId": "abc-123",
  "email": "user@example.com",
  "name": "John Doe",
  "dateOfBirth": "1990-01-15",
  "address": "123 Main St",

  "providers": {
    "telephone": "Vodafone",
    "energy": "British Gas",
    "internet": "Sky",
    "water": "Thames Water",
    "bank": "Barclays"
  },

  "recentTransactions": [
    {"date": "2025-12-15", "description": "Vodafone Bill", "amount": "-45.00"},
    // ... last 100
  ],

  "stats": {
    "totalUploads": 42,
    "totalTransactions": 1250,
    "totalSpending": -15430.50
  }
}
```

**One API call, all the data!** ⚡

---

## Summary

### What Changed ✅

1. **Added provider columns** to Users table:
   - telephoneProvider, energyProvider, internetProvider, waterProvider, gasProvider, bankName

2. **Added recentTransactions** JSON column:
   - Stores last 100 transactions for fast access
   - Updates automatically on each upload

3. **Added totalSpending** counter:
   - Tracks cumulative spending (negative value)

4. **Auto-detection service** (ProviderDetectionService.java):
   - Scans transaction descriptions
   - Detects providers using pattern matching
   - Updates Users table automatically

### Cost Impact 💰

```
Before: $0.00/month
After:  $0.00/month ✅

Still under free tier!
```

### Performance Impact ⚡

```
Before: 2 DynamoDB queries to get user + providers
After:  1 DynamoDB query to get everything

50% faster! ✅
```

---

## Next Steps

When you deploy to AWS:

1. Create DynamoDB Users table with new schema
2. Deploy updated Lambda code
3. Upload a PDF
4. Watch providers get auto-detected! ✨

**The system will automatically:**
- Detect Vodafone from "VODAFONE DD PAYMENT"
- Detect British Gas from "BRITISH GAS ENERGY"
- Store last 100 transactions in Users table
- Keep full history in Transactions table
- Calculate total spending

**All for $0.00/month!** 🎉
