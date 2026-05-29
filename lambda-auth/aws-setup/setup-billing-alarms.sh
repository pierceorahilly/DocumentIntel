#!/bin/bash

# Setup AWS Billing Alarms
# This script creates SNS topic and CloudWatch alarms to alert you when costs exceed thresholds

set -e

echo "========================================="
echo "  Guide-ya Billing Alarms Setup"
echo "========================================="
echo ""

# Get user input
read -p "Enter your email for billing alerts: " EMAIL
read -p "Enter your AWS Account ID: " ACCOUNT_ID

echo ""
echo "Creating SNS topic for billing alerts..."

# Create SNS topic (must be in us-east-1 for billing)
TOPIC_ARN=$(aws sns create-topic \
  --name GuideYaBillingAlerts \
  --region us-east-1 \
  --output text)

echo "✓ Topic created: $TOPIC_ARN"

# Subscribe email to topic
echo "Subscribing $EMAIL to topic..."
aws sns subscribe \
  --topic-arn $TOPIC_ARN \
  --protocol email \
  --notification-endpoint $EMAIL \
  --region us-east-1

echo ""
echo "⚠️  CHECK YOUR EMAIL and confirm the subscription!"
echo "Press Enter after confirming..."
read

# Create billing alarm for $1
echo "Creating $1 threshold alarm..."
aws cloudwatch put-metric-alarm \
  --alarm-name GuideYa-BillingAlert-1USD \
  --alarm-description "Alert when monthly charges exceed $1" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 21600 \
  --evaluation-periods 1 \
  --threshold 1.0 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=Currency,Value=USD \
  --alarm-actions $TOPIC_ARN \
  --region us-east-1

echo "✓ $1 threshold alarm created"

# Create billing alarm for $3
echo "Creating $3 threshold alarm..."
aws cloudwatch put-metric-alarm \
  --alarm-name GuideYa-BillingAlert-3USD \
  --alarm-description "Alert when monthly charges exceed $3 (something may be wrong!)" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 21600 \
  --evaluation-periods 1 \
  --threshold 3.0 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=Currency,Value=USD \
  --alarm-actions $TOPIC_ARN \
  --region us-east-1

echo "✓ $3 threshold alarm created"

# Create billing alarm for $5 (emergency)
echo "Creating $5 threshold alarm..."
aws cloudwatch put-metric-alarm \
  --alarm-name GuideYa-BillingAlert-5USD-URGENT \
  --alarm-description "URGENT: Monthly charges exceeded $5! Check AWS console immediately." \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 21600 \
  --evaluation-periods 1 \
  --threshold 5.0 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=Currency,Value=USD \
  --alarm-actions $TOPIC_ARN \
  --region us-east-1

echo "✓ $5 threshold alarm created"

echo ""
echo "========================================="
echo "  ✅ Billing Alarms Setup Complete!"
echo "========================================="
echo ""
echo "You will receive email alerts at:"
echo "  • $1/month - Normal usage notification"
echo "  • $3/month - Check for unexpected usage"
echo "  • $5/month - URGENT - investigate immediately"
echo ""
echo "Expected monthly cost: ~$0.61/month"
echo ""
