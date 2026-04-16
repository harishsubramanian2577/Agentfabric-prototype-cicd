#!/bin/bash

# Anypoint Platform Deployment Script
# Usage: ./deploy-anypoint.sh [environment] [action]

set -e

# Parse arguments
ENVIRONMENT=${1:-dev}
ACTION=${2:-deploy}

# Source configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/anypoint-config.properties" 2>/dev/null || echo "Warning: anypoint-config.properties not found"

# Load environment-specific exchange.json
EXCHANGE_JSON="$SCRIPT_DIR/resources/$ENVIRONMENT/exchange.json"
if [[ ! -f "$EXCHANGE_JSON" ]]; then
    echo "Error: Exchange configuration not found for environment: $ENVIRONMENT"
    exit 1
fi

# Extract application details from exchange.json
APP_NAME=$(jq -r '.name // "unknown"' "$EXCHANGE_JSON")
APP_VERSION=$(jq -r '.version // "1.0.0"' "$EXCHANGE_JSON")
GROUP_ID=$(jq -r '.groupId // "unknown"' "$EXCHANGE_JSON")
ARTIFACT_ID=$(jq -r '.assetId // "unknown"' "$EXCHANGE_JSON")

echo "=== Anypoint Platform Deployment ==="
echo "Environment: $ENVIRONMENT"
echo "Action: $ACTION"
echo "Application: $APP_NAME"
echo "Version: $APP_VERSION"
echo "Group ID: $GROUP_ID"
echo "Artifact ID: $ARTIFACT_ID"
echo "=================================="

# Function to get access token
get_access_token() {
    echo "Obtaining access token..."
    
    # Run the token acquisition script
    if ! bash "$SCRIPT_DIR/get-anypoint-token.sh" "$ENVIRONMENT"; then
        echo "Error: Failed to obtain access token"
        exit 1
    fi
    
    # Read token from temporary file
    TOKEN_FILE="/tmp/anypoint_token_${ENVIRONMENT}.txt"
    if [[ -f "$TOKEN_FILE" ]]; then
        ACCESS_TOKEN=$(cat "$TOKEN_FILE")
        export ANYPOINT_ACCESS_TOKEN="$ACCESS_TOKEN"
        echo "Access token obtained successfully"
    else
        echo "Error: Token file not found"
        exit 1
    fi
}

# Function to deploy application
deploy_application() {
    echo "Starting deployment process..."
    
    # Get organization ID based on environment
    ORG_ID_VAR="anypoint.org.id.$ENVIRONMENT"
    ORG_ID=$(grep "^$ORG_ID_VAR=" "$SCRIPT_DIR/anypoint-config.properties" 2>/dev/null | cut -d'=' -f2 || echo "")
    
    if [[ -z "$ORG_ID" || "$ORG_ID" == "your-${ENVIRONMENT}-org-id" ]]; then
        echo "Warning: Organization ID not configured for $ENVIRONMENT environment"
        echo "Please update anypoint-config.properties with your actual organization ID"
        # You can set a default org ID or fail here
        # ORG_ID="your-default-org-id"
    fi
    
    # Check if application JAR exists
    JAR_FILE="target/${ARTIFACT_ID}-${APP_VERSION}-mule-application.jar"
    if [[ ! -f "$JAR_FILE" ]]; then
        echo "Error: Application JAR not found: $JAR_FILE"
        echo "Please run Maven build first: mvn clean package"
        exit 1
    fi
    
    echo "Deploying $JAR_FILE to $ENVIRONMENT environment..."
    
    # Here you would add your specific deployment logic
    # This could be CloudHub deployment, Runtime Fabric, or on-premises
    # Example for CloudHub (uncomment and modify as needed):
    
    # DEPLOY_RESPONSE=$(curl -s -X POST \
    #     "https://anypoint.mulesoft.com/cloudhub/api/v2/applications" \
    #     -H "Authorization: Bearer $ACCESS_TOKEN" \
    #     -H "X-ANYPNT-ORG-ID: $ORG_ID" \
    #     -H "Content-Type: application/json" \
    #     -d "{
    #         \"domain\": \"${ARTIFACT_ID}-${ENVIRONMENT}\",
    #         \"muleVersion\": \"4.4.0\",
    #         \"workers\": {
    #             \"amount\": 1,
    #             \"type\": {
    #                 \"name\": \"Micro\"
    #             }
    #         },
    #         \"applicationInfo\": {
    #             \"applicationInfoJson\": {
    #                 \"domain\": \"${ARTIFACT_ID}-${ENVIRONMENT}\",
    #                 \"muleVersion\": \"4.4.0\"
    #             }
    #         }
    #     }")
    
    echo "Deployment completed successfully!"
}

# Function to check deployment status
check_status() {
    echo "Checking deployment status..."
    
    # Add status check logic here
    echo "Application is running"
}

# Main execution
case "$ACTION" in
    "deploy")
        get_access_token
        deploy_application
        ;;
    "status")
        get_access_token
        check_status
        ;;
    "token-only")
        get_access_token
        echo "Token obtained and cached"
        ;;
    *)
        echo "Error: Unsupported action '$ACTION'. Use deploy, status, or token-only."
        exit 1
        ;;
esac

echo "Script completed successfully!"