# OAuth2 Token Integration for Anypoint Platform CI/CD

This guide explains how to integrate OAuth2 token acquisition with your existing Jenkins pipeline for Anypoint Platform deployments.

## Overview

The OAuth2 integration provides secure token-based authentication for Anypoint Platform API calls in your Jenkins CI/CD pipeline. This eliminates the need to store long-lived credentials and provides better security through short-lived access tokens.

## Files Created

### 1. `get-anypoint-token.sh`
A standalone bash script that obtains OAuth2 tokens from Anypoint Platform. Can be used independently or as part of the Jenkins pipeline.

**Usage:**
```bash
# Make executable
chmod +x get-anypoint-token.sh

# Get token for dev environment
./get-anypoint-token.sh dev

# Get token for production
./get-anypoint-token.sh prod
```

### 2. `oauth2-token-helper.groovy`
A Jenkins pipeline library containing reusable functions for OAuth2 token management.

**Key Functions:**
- `getAnypointOAuth2TokenConnectedApp(environment)` - Get token using your existing credential pattern
- `getOrRefreshToken(environment, forceRefresh)` - Smart token caching with automatic refresh
- `cacheOAuth2Token(tokenData)` - Cache token in Jenkins environment variables
- `isTokenValid(bufferSeconds)` - Check if cached token is still valid

### 3. `jenkins-oauth2-integration-example.groovy`
Examples showing how to integrate OAuth2 functions with your existing Jenkins pipeline functions.

### 4. `deploy-anypoint.sh`
Enhanced deployment script that uses OAuth2 tokens for Anypoint Platform operations.

### 5. `anypoint-config.properties`
Configuration file containing environment-specific settings and API endpoints.

## Integration with Your Existing Pipeline

Your existing Jenkins pipeline already uses the credential pattern `${ENV}-anypointplatform-connected-app` for client ID and secret. The OAuth2 integration works seamlessly with this pattern.

### Quick Integration Steps

1. **Add OAuth2 helper to your pipeline:**
```groovy
// At the top of your Jenkinsfile or in your functions
def oauth2Helper = load 'oauth2-token-helper.groovy'
```

2. **Get OAuth2 token in your build or deploy functions:**
```groovy
void deployStepsWithOAuth2() {
    withCredentials([
        usernamePassword(credentialsId: "${ENV}-anypointplatform-connected-app", 
                        usernameVariable: 'AP_CA_CLIENT_ID', 
                        passwordVariable: 'AP_CA_CLIENT_SECRET')
    ]) {
        // Get OAuth2 token
        def tokenData = oauth2Helper.getAnypointOAuth2TokenConnectedApp(ENV)
        def accessToken = tokenData.accessToken
        
        echo "OAuth2 token obtained: ${accessToken.take(20)}..."
        
        // Your existing deployment code...
        // Now you can also make direct API calls using the token
    }
}
```

3. **Use token for API calls:**
```groovy
// Example API call using OAuth2 token
def apiResponse = sh(
    script: """
        curl -s -H "Authorization: Bearer ${accessToken}" \\
             -H "Content-Type: application/json" \\
             -H "X-ANYPNT-ORG-ID: ${ORG_ID}" \\
             "https://anypoint.mulesoft.com/exchange/api/v2/assets"
    """,
    returnStdout: true
)
```

## Environment Variables

After token acquisition, the following environment variables are available:

- `ANYPOINT_ACCESS_TOKEN` - The OAuth2 access token
- `ANYPOINT_TOKEN_TYPE` - Token type (usually "Bearer")
- `ANYPOINT_TOKEN_ENVIRONMENT` - Environment the token was obtained for
- `ANYPOINT_TOKEN_EXPIRES_IN` - Token expiration time in seconds
- `ANYPOINT_TOKEN_OBTAINED_AT` - Timestamp when token was obtained

## Security Best Practices

1. **Credentials Storage:**
   - Store client ID and secret in Jenkins credential store
   - Use the existing pattern: `${ENV}-anypointplatform-connected-app`
   - Never log client secrets or access tokens in build output

2. **Token Handling:**
   - Tokens are automatically cached and refreshed when needed
   - Tokens are cleared from environment after pipeline completion
   - Use token validation to avoid unnecessary API calls

3. **Environment Separation:**
   - Each environment (dev, stage, prod) has separate credentials
   - Tokens are environment-specific and cannot be used across environments

## API Endpoints Available

With OAuth2 token, you can call various Anypoint Platform APIs:

### Exchange API
```bash
# List assets
curl -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     "https://anypoint.mulesoft.com/exchange/api/v2/assets?organizationId=${ORG_ID}"

# Get specific asset
curl -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     "https://anypoint.mulesoft.com/exchange/api/v2/assets/${GROUP_ID}/${ASSET_ID}/${VERSION}"
```

### CloudHub API
```bash
# List applications
curl -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     -H "X-ANYPNT-ORG-ID: ${ORG_ID}" \
     "https://anypoint.mulesoft.com/cloudhub/api/v2/applications"

# Get application status
curl -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     -H "X-ANYPNT-ORG-ID: ${ORG_ID}" \
     "https://anypoint.mulesoft.com/cloudhub/api/v2/applications/${APP_NAME}"
```

### Runtime Fabric API
```bash
# List targets
curl -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     -H "X-ANYPNT-ORG-ID: ${ORG_ID}" \
     "https://anypoint.mulesoft.com/runtimefabric/api/organizations/${ORG_ID}/targets"
```

## Troubleshooting

### Common Issues

1. **Token acquisition fails:**
   - Check that credentials exist in Jenkins: `${ENV}-anypointplatform-connected-app`
   - Verify client ID and secret are correct in Anypoint Platform
   - Ensure the connected app has proper permissions
   - Check network connectivity to https://anypoint.mulesoft.com

2. **Token validation errors:**
   - Token may have expired (default 3600 seconds)
   - Check system time synchronization
   - Verify token was cached correctly

3. **API calls fail with 401 Unauthorized:**
   - Token may be invalid or expired
   - Check if token has required scopes/permissions
   - Verify organization ID is correct

4. **Curl command issues:**
   - Ensure jq is available on Jenkins agents
   - Check curl version compatibility
   - Verify JSON formatting in requests

### Debug Information

To troubleshoot issues, you can add debug logging:

```groovy
// Enable debug mode
def tokenData = oauth2Helper.getAnypointOAuth2TokenConnectedApp(ENV)
echo "Token Debug Info:"
echo "- Environment: ${tokenData.environment}"
echo "- Token Type: ${tokenData.tokenType}"
echo "- Expires In: ${tokenData.expiresIn} seconds"
echo "- Token Length: ${tokenData.accessToken.length()}"
```

## Example Integration with Your Current Pipeline

Here's how to add OAuth2 token acquisition to one of your existing stages:

```groovy
stage('Deploy to Dev') {
    when {
        expression {
            env.GIT_BRANCH == "DEV"
        }
    }
    environment {
        ENV = 'dev'
        APP_NAME_DEPLOYMENT = "${ENV}-${APP_NAME}"
        target_space = 'UCSF-NONPROD'
    }
    steps {
        script {
            // Load OAuth2 helper
            def oauth2Helper = load 'oauth2-token-helper.groovy'
            
            // Get OAuth2 token at the beginning
            def tokenData = oauth2Helper.getAnypointOAuth2TokenConnectedApp(ENV)
            
            // Your existing deploySteps() function
            deploySteps()
            
            // Optional: Use token for additional API operations
            echo "Performing post-deployment verification..."
            
            def deploymentStatus = sh(
                script: """
                    curl -s -H "Authorization: Bearer ${tokenData.accessToken}" \\
                         -H "Content-Type: application/json" \\
                         -H "X-ANYPNT-ORG-ID: ${ORG_ID}" \\
                         "https://anypoint.mulesoft.com/exchange/api/v2/assets?organizationId=${ORG_ID}"
                """,
                returnStdout: true
            )
            
            echo "Exchange Assets Available: ${deploymentStatus.take(200)}..."
        }
    }
}
```

## Testing the Integration

1. **Test OAuth2 token acquisition:**
```bash
# Test the bash script
./get-anypoint-token.sh dev
```

2. **Test in Jenkins pipeline:**
Add a test stage to your pipeline:
```groovy
stage('Test OAuth2 Token') {
    when {
        expression {
            env.GIT_BRANCH == "DEV"
        }
    }
    steps {
        script {
            def oauth2Helper = load 'oauth2-token-helper.groovy'
            def tokenData = oauth2Helper.getAnypointOAuth2TokenConnectedApp('dev')
            echo "OAuth2 test successful! Token obtained."
        }
    }
}
```

## Next Steps

1. **Update your existing pipeline** by loading the OAuth2 helper in stages that need API access
2. **Test the integration** in your DEV environment first
3. **Add API verification calls** to enhance deployment validation
4. **Configure proper credentials** in Jenkins for all environments
5. **Monitor token usage** and adjust caching settings if needed

For additional support or questions, refer to the MuleSoft Anypoint Platform documentation or your organization's DevOps team.
