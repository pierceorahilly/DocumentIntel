# Guide-ya Cost Optimization Guide

## 💰 Monthly Cost Breakdown (Free Tier Expired)

### Services that Stay FREE Forever ✅

| Service | Permanent Free Tier | Your Usage | Cost |
|---------|-------------------|------------|------|
| **DynamoDB** | 25 GB storage + 25M requests | ~1,600 requests/month | **$0.00** |
| **Lambda** | 1M requests + 400K GB-seconds | ~150 requests + 375 GB-sec | **$0.00** |
| **Cognito** | 50,000 MAU (monthly active users) | 1-10 users | **$0.00** |
| **CloudWatch** | 5 GB logs + 10 custom metrics | <100 MB logs | **$0.00** |

### Services that Charge (Minimal) 💸

| Service | Pricing | Your Usage (50 PDFs/month) | Monthly Cost |
|---------|---------|---------------------------|--------------|
| **S3** | $0.023/GB storage<br>$0.005 per 1K PUT<br>$0.0004 per 1K GET | 3.5 MB storage<br>150 PUTs<br>500 GETs | **$0.001** |
| **API Gateway** | $3.50 per million requests | 150 requests | **$0.0005** |
| **Textract** | $1.50 per 1,000 pages | 50 pages | **$0.075** |
| **Bedrock (Claude)** | $3/M input tokens<br>$15/M output tokens | 100K input<br>15K output | **$0.53** |

### Total Monthly Cost: **$0.61/month** or **$7.32/year** 🎉

**After optimization (reduced from $0.76/month)**

---

## 🎯 Cost Breakdown by Category

```
Bedrock AI (Claude):  $0.53  (87%)  ← Main cost
Textract (PDF OCR):   $0.08  (13%)
Everything else:      $0.00  (0%)
─────────────────────────────────
TOTAL:                $0.61/month
```

**Cheaper than:**
- 1 coffee at Starbucks ☕
- 1 Netflix month 📺
- 1 Spotify month 🎵
- Parking for 30 minutes 🚗

---

## ✅ Optimizations Already Applied

### 1. Reduced Bedrock Token Limit
**File**: `LambdaBedrockService.java:65`
```java
// Before: max_tokens: 800
// After:  max_tokens: 400
requestBody.put("max_tokens", 400);  // Saves ~$0.15/month
```

**Impact**:
- Shorter AI responses (3-4 tips instead of 5-6)
- Still helpful and actionable
- **20% cost reduction**

### 2. Using On-Demand Billing for DynamoDB
```bash
--billing-mode PAY_PER_REQUEST
```
- No reserved capacity charges
- Only pay for actual reads/writes
- Your usage: ~1,600 requests/month = **FREE** (under 25M limit)

### 3. Disabled S3 Versioning
- Prevents duplicate file storage
- Reduces storage costs over time

### 4. Optimized Lambda Memory
```bash
--memory-size 512
--timeout 30
```
- 512 MB is sweet spot (enough power, low cost)
- Stays well within free tier (400K GB-seconds/month)

---

## 📉 Additional Cost-Saving Strategies

### Strategy 1: Apply S3 Lifecycle Policy (Delete Old PDFs)

**Created file**: `aws-setup/s3-lifecycle-policy.json`

Apply it to automatically delete PDFs older than 90 days:

```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket guideya-uploads-YOUR-BUCKET-ID \
  --lifecycle-configuration file://aws-setup/s3-lifecycle-policy.json
```

**Impact**: Prevents S3 storage from growing unbounded
**Savings**: Prevents future costs (storage is cheap now but grows over time)

---

### Strategy 2: Limit User Uploads (Optional)

If you want to cap costs at a specific level, add upload limits per user:

**In UserService.java**, check totalUploads before allowing upload:

```java
public boolean canUpload(String userId, int monthlyLimit) {
    Map<String, AttributeValue> user = getUser(userId);
    int totalUploads = Integer.parseInt(user.get("totalUploads").n());

    // Reset counter monthly (implement date checking)
    return totalUploads < monthlyLimit;
}
```

**Example limits**:
- Free tier: 10 uploads/month → **$0.12/month**
- Paid tier: 50 uploads/month → **$0.61/month** (current)
- Premium: Unlimited

---

### Strategy 3: Use Bedrock Batch Processing (Future)

If you get many uploads at once, batch them:
- Process 5 PDFs → 1 Bedrock call
- Analyze all transactions together
- **Potential savings**: 50% on Bedrock costs

Not implemented yet, but possible optimization.

---

### Strategy 4: Cache Common Advice (Advanced)

If multiple users have similar spending patterns, cache responses:
- Check DynamoDB for similar transaction patterns
- Return cached advice instead of calling Bedrock
- **Potential savings**: 70-80% on Bedrock costs

Requires pattern matching logic.

---

## 🚨 Billing Alarms Setup

**File**: `aws-setup/setup-billing-alarms.sh`

Run this script to get email alerts when costs exceed thresholds:

```bash
cd D:\simple-bank-ai_patch\lambda-auth\aws-setup
bash setup-billing-alarms.sh
```

**What it does**:
1. Creates SNS topic for billing alerts
2. Subscribes your email
3. Creates CloudWatch alarms at:
   - **$1/month** - Normal usage notification
   - **$3/month** - Warning (check for unexpected usage)
   - **$5/month** - URGENT (investigate immediately)

**Expected alerts**:
- You should get the $1 alert every month (normal)
- If you get $3+ alerts, something is wrong (investigate!)

---

## 📊 Cost Monitoring Checklist

### Daily (First Week)
- [ ] Check AWS Cost Explorer
- [ ] Verify Bedrock token usage
- [ ] Check Textract page count

### Weekly
- [ ] Review total costs in billing dashboard
- [ ] Check for unexpected S3 storage growth
- [ ] Verify Lambda invocation counts

### Monthly
- [ ] Review full bill breakdown
- [ ] Compare actual vs expected costs
- [ ] Adjust alarms if needed

---

## 🔍 How to Check Costs in AWS Console

### Option 1: Billing Dashboard (Quick View)

1. Go to **AWS Console** → Your Name (top right) → **Billing and Cost Management**
2. Click **Bills** (left sidebar)
3. See current month's charges by service

**Expected view**:
```
Service           Charges
─────────────────────────
Bedrock           $0.53
Textract          $0.08
S3                $0.00
Lambda            $0.00
DynamoDB          $0.00
─────────────────────────
Total             $0.61
```

### Option 2: Cost Explorer (Detailed Analysis)

1. Go to **AWS Console** → **Billing** → **Cost Explorer**
2. Enable Cost Explorer (if not enabled)
3. View:
   - **Daily costs** (see spikes)
   - **Service breakdown** (which services cost most)
   - **Forecast** (predicted month-end total)

**Useful filters**:
- Group by: Service
- Time: Last 30 days
- Chart type: Bar chart

### Option 3: Cost Anomaly Detection (Smart Alerts)

1. Go to **AWS Console** → **Billing** → **Cost Anomaly Detection**
2. Click **Create monitor**
3. Select **All AWS services**
4. Set alert threshold: **$1.00**
5. Add your email

This automatically detects unusual spending patterns!

---

## 💡 Cost Comparison with Alternatives

### Guide-ya (Serverless)
- **Monthly**: $0.61
- **Annual**: $7.32
- **Scales automatically**
- **No maintenance**

### EC2 t3.micro (Always-on server)
- **Monthly**: $7.00
- **Annual**: $84.00
- **Limited to 1 instance**
- **Requires maintenance**

### Heroku Free Tier (Deprecated)
- **Monthly**: $7.00 minimum
- **Annual**: $84.00
- **Sleeps after inactivity**

**Serverless is 92% cheaper!** 💰

---

## 🎯 Optimized for Different Usage Levels

### Light Usage (10 PDFs/month)
```
Textract:  10 × $1.50/1K = $0.015
Bedrock:   10 × $0.53/50 = $0.106
Total:     $0.12/month ($1.44/year)
```

### Current (50 PDFs/month)
```
Total: $0.61/month ($7.32/year)
```

### Heavy Usage (200 PDFs/month)
```
Textract:  200 × $1.50/1K = $0.30
Bedrock:   200 × $0.53/50 = $2.12
Total:     $2.42/month ($29.04/year)
```

Still cheaper than most alternatives!

---

## ⚠️ What Could Go Wrong (and How to Prevent)

### Problem 1: Runaway Bedrock Costs

**Symptom**: Bill shows $50+ for Bedrock
**Cause**: Someone called API in a loop, made 1000+ requests
**Prevention**:
- ✅ Set max_tokens limit (done: 400)
- ✅ Set billing alarms (do: run setup-billing-alarms.sh)
- ⚠️ Add rate limiting in Lambda (future)

**Fix**: Add request throttling in API Gateway:
```bash
aws apigateway update-stage \
  --rest-api-id YOUR-API-ID \
  --stage-name prod \
  --patch-operations \
    op=replace,path=/throttle/rateLimit,value=10 \
    op=replace,path=/throttle/burstLimit,value=20
```

### Problem 2: S3 Storage Grows Unbounded

**Symptom**: S3 bill shows $5+ after 6 months
**Cause**: PDFs not being deleted, storage accumulating
**Prevention**:
- ✅ Apply lifecycle policy (delete after 90 days)

**Fix**:
```bash
# Apply the lifecycle policy
aws s3api put-bucket-lifecycle-configuration \
  --bucket guideya-uploads-YOUR-BUCKET-ID \
  --lifecycle-configuration file://aws-setup/s3-lifecycle-policy.json
```

### Problem 3: DynamoDB Exceeds Free Tier

**Symptom**: DynamoDB bill shows $10+
**Cause**: Over 25M requests/month (extremely unlikely at your scale)
**Prevention**:
- ✅ Using on-demand billing (only charges after 25M requests)

**Your usage**: 1,600 requests/month = 0.006% of free tier limit!

### Problem 4: Lambda Timeout Charges

**Symptom**: Lambda bill higher than expected
**Cause**: Functions timing out and retrying
**Prevention**:
- ✅ Set timeout to 30-60 seconds
- ✅ Monitor CloudWatch logs for timeouts

**Check for timeouts**:
```bash
aws logs filter-log-events \
  --log-group-name /aws/lambda/GuideYaUploadHandler \
  --filter-pattern "Task timed out"
```

---

## 📋 Quick Setup Checklist

- [ ] Reduced Bedrock max_tokens to 400 ✅ (already done)
- [ ] Set up billing alarms (run `setup-billing-alarms.sh`)
- [ ] Apply S3 lifecycle policy (delete PDFs after 90 days)
- [ ] Enable AWS Cost Anomaly Detection
- [ ] Add API Gateway rate limiting (10 req/sec)
- [ ] Set up weekly cost review reminder

---

## 🎉 Summary

**Current Optimized Cost**: **$0.61/month** ($7.32/year)

**Breakdown**:
- Permanent free: DynamoDB, Lambda, Cognito, CloudWatch
- Minimal costs: S3 ($0.001), API Gateway ($0.0005)
- Main costs: Textract ($0.08), Bedrock ($0.53)

**Next Steps**:
1. Run `setup-billing-alarms.sh` to get email alerts
2. Apply S3 lifecycle policy to prevent storage growth
3. Check billing dashboard weekly
4. Expected alert: $1 threshold (normal usage)

**You're all set to deploy without breaking the bank!** 🚀
