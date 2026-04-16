#!/bin/bash

# Setup script for OAuth2 integration
# This script prepares the OAuth2 files for use in Jenkins CI/CD pipeline

echo "Setting up OAuth2 integration for Anypoint Platform CI/CD..."

# Make scripts executable
chmod +x get-anypoint-token.sh
chmod +x deploy-anypoint.sh
chmod +x setup-oauth2.sh

echo "✓ Made scripts executable"

# Validate required tools
echo "Checking required tools..."

# Check if curl is available
if command -v curl &> /dev/null; then
    echo "✓ curl is available"
else
    echo "✗ curl is not available - please install curl"
    exit 1
fi

# Check if jq is available
if command -v jq &> /dev/null; then
    echo "✓ jq is available"
else
    echo "✗ jq is not available - please install jq"
    echo "  On macOS: brew install jq"
    echo "  On Ubuntu/Debian: apt-get install jq"
    echo "  On RHEL/CentOS: yum install jq"
    exit 1
fi

echo "✓ All required tools are available"

# Test OAuth2 token script syntax
echo "Testing OAuth2 token script syntax..."
bash -n get-anypoint-token.sh
if [ $? -eq 0 ]; then
    echo "✓ get-anypoint-token.sh syntax is valid"
else
    echo "✗ get-anypoint-token.sh has syntax errors"
    exit 1
fi

bash -n deploy-anypoint.sh
if [ $? -eq 0 ]; then
    echo "✓ deploy-anypoint.sh syntax is valid"
else
    echo "✗ deploy-anypoint.sh has syntax errors"
    exit 1
fi

# Update anypoint-config.properties with actual organization IDs from exchange.json files
echo "Updating configuration with organization IDs..."

if [ -f "resources/dev/exchange.json" ]; then
    DEV_ORG_ID=$(jq -r '.organizationId // "unknown"' resources/dev/exchange.json)
    sed -i.bak "s/your-dev-org-id/$DEV_ORG_ID/" anypoint-config.properties
    echo "✓ Updated dev organization ID: $DEV_ORG_ID"
fi

if [ -f "resources/stage/exchange.json" ]; then
    STAGE_ORG_ID=$(jq -r '.organizationId // "unknown"' resources/stage/exchange.json)
    sed -i.bak "s/your-stage-org-id/$STAGE_ORG_ID/" anypoint-config.properties
    echo "✓ Updated stage organization ID: $STAGE_ORG_ID"
fi

if [ -f "resources/prod/exchange.json" ]; then
    PROD_ORG_ID=$(jq -r '.organizationId // "unknown"' resources/prod/exchange.json)
    sed -i.bak "s/your-prod-org-id/$PROD_ORG_ID/" anypoint-config.properties
    echo "✓ Updated prod organization ID: $PROD_ORG_ID"
fi

echo ""
echo "OAuth2 Integration Setup Complete!"
echo ""
echo "Next Steps:"
echo "1. Ensure Jenkins credentials are configured:"
echo "   - dev-anypointplatform-connected-app"
echo "   - stage-anypointplatform-connected-app" 
echo "   - prod-anypointplatform-connected-app"
echo ""
echo "2. Test OAuth2 token acquisition (requires credentials in environment):"
echo "   export ANYPOINT_CLIENT_ID_DEV=\"your-client-id\""
echo "   export ANYPOINT_CLIENT_SECRET_DEV=\"your-client-secret\""
echo "   ./get-anypoint-token.sh dev"
echo ""
echo "3. Integrate with your Jenkins pipeline using the examples in:"
echo "   - oauth2-token-helper.groovy"
echo "   - jenkins-oauth2-integration-example.groovy"
echo ""
echo "4. Read the complete documentation:"
echo "   - README-OAuth2-Integration.md"