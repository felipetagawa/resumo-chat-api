#!/bin/bash

# Configuration
SERVICE_NAME="gemini-resumo-api"
REGION="southamerica-east1"

echo "========================================================"
echo "Deploying $SERVICE_NAME to Cloud Run (Region: $REGION)"
echo "========================================================"

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null
then
    echo "Error: 'gcloud' command not found."
    echo "Please install the Google Cloud SDK or ensure it is in your PATH."
    exit 1
fi

# Confirm project selection
CURRENT_PROJECT=$(gcloud config get-value project)
echo "Current Google Cloud Project: $CURRENT_PROJECT"
echo "If this is incorrect, please cancel and run 'gcloud config set project [YOUR_PROJECT_ID]'"
echo "Waiting 5 seconds..."
sleep 5

# Deploy using source
# This automatically builds the container using Cloud Buildpacks and deploys it
echo "Starting deployment..."
gcloud run deploy "$SERVICE_NAME" \
  --source . \
  --region "$REGION" \
  --platform managed \
  --allow-unauthenticated

echo "========================================================"
echo "Deployment command finished."
