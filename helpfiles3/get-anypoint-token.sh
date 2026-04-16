#!/bin/bash

# Anypoint Platform OAuth2 Token Acquisition Script
# Usage: ./get-anypoint-token.sh [environment]

set -e  # Exit on any error

# Default environment
ENVIRONMENT=${1:-dev}

# Configuration based on environment
case "$ENVIRONMENT" in
    "dev")
        CLIENT_ID_VAR="ANYPOINT_CLIENT_ID_DEV"
        CLIENT_SECRET_VAR="ANYPOINT_CLIENT_SECRET_DEV"
        ;;
    "stage")
        CLIENT_ID_VAR="ANYPOINT_CLIENT_ID_STAGE"
        CLIENT_SECRET_VAR="ANYPOINT_CLIENT_SECRET_STAGE"
        ;;
    "prod")
        CLIENT_ID_VAR="ANYPOINT_CLIENT_ID_PROD"
        CLIENT_SECRET_VAR="ANYPOINT_CLIENT_SECRET_PROD"
        ;;
    *)
        echo "Error: Unsupported environment '$ENVIRONMENT'. Use dev, stage, or prod."
        exit 1
        ;;
esac

# Validate required environment variables
if [[ -z "${!CLIENT_ID_VAR}" || -z "${!CLIENT_SECRET_VAR}" ]]; then
    echo "Error: Missing required credentials for $ENVIRONMENT environment"
    echo "Required variables: $CLIENT_ID_VAR, $CLIENT_SECRET_VAR"
    exit 1
fi

CLIENT_ID="${!CLIENT_ID_VAR}"
CLIENT_SECRET="${!CLIENT_SECRET_VAR}"

# OAuth2 token endpoint
TOKEN_URL="https://anypoint.mulesoft.com/accounts/api/v2/oauth2/token"

echo "Requesting OAuth2 token for $ENVIRONMENT environment..."

# Make the token request
RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST "$TOKEN_URL" \
    -H "Content-Type: application/json" \
    -d "{
        \"client_id\": \"$CLIENT_ID\",
        \"client_secret\": \"$CLIENT_SECRET\",
        \"grant_type\": \"client_credentials\"
    }")

# Extract HTTP status code
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$RESPONSE" | head -n -1)

# Check if request was successful
if [[ "$HTTP_CODE" != "200" ]]; then
    echo "Error: Failed to obtain access token (HTTP $HTTP_CODE)"
    echo "Response: $RESPONSE_BODY"
    exit 1
fi

# Parse the access token from response
ACCESS_TOKEN=$(echo "$RESPONSE_BODY" | jq -r '.access_token // empty')
TOKEN_TYPE=$(echo "$RESPONSE_BODY" | jq -r '.token_type // "Bearer"')
EXPIRES_IN=$(echo "$RESPONSE_BODY" | jq -r '.expires_in // empty')

if [[ -z "$ACCESS_TOKEN" || "$ACCESS_TOKEN" == "null" ]]; then
    echo "Error: Could not extract access token from response"
    echo "Response: $RESPONSE_BODY"
    exit 1
fi

echo "Successfully obtained access token for $ENVIRONMENT"
echo "Token type: $TOKEN_TYPE"
if [[ -n "$EXPIRES_IN" && "$EXPIRES_IN" != "null" ]]; then
    echo "Expires in: ${EXPIRES_IN}s"
fi

# Export the token for use in other scripts
export ANYPOINT_ACCESS_TOKEN="$ACCESS_TOKEN"
export ANYPOINT_TOKEN_TYPE="$TOKEN_TYPE"

# Create a temporary file with the token for Jenkins to use
TOKEN_FILE="/tmp/anypoint_token_${ENVIRONMENT}.txt"
echo "$ACCESS_TOKEN" > "$TOKEN_FILE"
echo "Token saved to: $TOKEN_FILE"

# Output the token (for Jenkins to capture)
echo "ANYPOINT_ACCESS_TOKEN=$ACCESS_TOKEN"