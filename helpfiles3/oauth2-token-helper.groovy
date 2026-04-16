/**
 * OAuth2 Token Helper for Anypoint Platform
 * This file contains reusable functions for obtaining OAuth2 tokens in Jenkins pipelines
 */

/**
 * Get OAuth2 access token from Anypoint Platform
 * @param environment The target environment (dev, stage, prod)
 * @param clientIdCredential Jenkins credential ID for client ID
 * @param clientSecretCredential Jenkins credential ID for client secret
 * @return Map containing access token and metadata
 */
def getAnypointOAuth2Token(String environment, String clientIdCredential, String clientSecretCredential) {
    def tokenUrl = "https://anypoint.mulesoft.com/accounts/api/v2/oauth2/token"
    
    echo "Requesting OAuth2 token for ${environment} environment..."
    
    return withCredentials([
        string(credentialsId: clientIdCredential, variable: 'CLIENT_ID'),
        string(credentialsId: clientSecretCredential, variable: 'CLIENT_SECRET')
    ]) {
        try {
            // Make the OAuth2 token request
            def response = sh(
                script: """
                    curl -s -w "\\n%{http_code}" \\
                        -X POST "${tokenUrl}" \\
                        -H "Content-Type: application/json" \\
                        -d '{
                            "client_id": "'${CLIENT_ID}'",
                            "client_secret": "'${CLIENT_SECRET}'",
                            "grant_type": "client_credentials"
                        }'
                """,
                returnStdout: true
            ).trim()
            
            def lines = response.split('\n')
            def httpCode = lines[-1]
            def responseBody = lines[0..-2].join('\n')
            
            if (httpCode != '200') {
                error("Failed to obtain OAuth2 token. HTTP ${httpCode}: ${responseBody}")
            }
            
            // Parse JSON response
            def jsonSlurper = new groovy.json.JsonSlurper()
            def tokenData = jsonSlurper.parseText(responseBody)
            
            if (!tokenData.access_token) {
                error("No access token found in response: ${responseBody}")
            }
            
            echo "Successfully obtained OAuth2 token for ${environment}"
            echo "Token type: ${tokenData.token_type ?: 'Bearer'}"
            if (tokenData.expires_in) {
                echo "Token expires in: ${tokenData.expires_in} seconds"
            }
            
            return [
                accessToken: tokenData.access_token,
                tokenType: tokenData.token_type ?: 'Bearer',
                expiresIn: tokenData.expires_in ?: 3600,
                environment: environment,
                obtainedAt: System.currentTimeMillis()
            ]
            
        } catch (Exception e) {
            error("Failed to obtain OAuth2 token: ${e.getMessage()}")
        }
    }
}

/**
 * Get OAuth2 token using environment-based credential naming convention
 * Uses the pattern: ${environment}-anypointplatform-oauth-client-id and ${environment}-anypointplatform-oauth-client-secret
 * @param environment The target environment
 * @return Map containing access token and metadata
 */
def getAnypointOAuth2TokenByEnvironment(String environment) {
    def clientIdCredential = "${environment}-anypointplatform-oauth-client-id"
    def clientSecretCredential = "${environment}-anypointplatform-oauth-client-secret"
    
    return getAnypointOAuth2Token(environment, clientIdCredential, clientSecretCredential)
}

/**
 * Get OAuth2 token using your existing credential pattern
 * Uses the pattern: ${environment}-anypointplatform-connected-app
 * @param environment The target environment
 * @return Map containing access token and metadata
 */
def getAnypointOAuth2TokenConnectedApp(String environment) {
    def credentialId = "${environment}-anypointplatform-connected-app"
    def tokenUrl = "https://anypoint.mulesoft.com/accounts/api/v2/oauth2/token"
    
    echo "Requesting OAuth2 token for ${environment} environment using connected app..."
    
    return withCredentials([
        usernamePassword(credentialsId: credentialId, usernameVariable: 'CLIENT_ID', passwordVariable: 'CLIENT_SECRET')
    ]) {
        try {
            def response = sh(
                script: """
                    curl -s -w "\\n%{http_code}" \\
                        -X POST "${tokenUrl}" \\
                        -H "Content-Type: application/json" \\
                        -d '{
                            "client_id": "'\$CLIENT_ID'",
                            "client_secret": "'\$CLIENT_SECRET'",
                            "grant_type": "client_credentials"
                        }'
                """,
                returnStdout: true
            ).trim()
            
            def lines = response.split('\n')
            def httpCode = lines[-1]
            def responseBody = lines[0..-2].join('\n')
            
            if (httpCode != '200') {
                error("Failed to obtain OAuth2 token. HTTP ${httpCode}: ${responseBody}")
            }
            
            def jsonSlurper = new groovy.json.JsonSlurper()
            def tokenData = jsonSlurper.parseText(responseBody)
            
            if (!tokenData.access_token) {
                error("No access token found in response: ${responseBody}")
            }
            
            echo "Successfully obtained OAuth2 token for ${environment} (${tokenData.access_token.take(20)}...)"
            
            return [
                accessToken: tokenData.access_token,
                tokenType: tokenData.token_type ?: 'Bearer',
                expiresIn: tokenData.expires_in ?: 3600,
                environment: environment,
                obtainedAt: System.currentTimeMillis()
            ]
            
        } catch (Exception e) {
            error("Failed to obtain OAuth2 token: ${e.getMessage()}")
        }
    }
}

/**
 * Cache OAuth2 token in Jenkins environment variables
 * @param tokenData Token data returned from getAnypointOAuth2Token functions
 */
def cacheOAuth2Token(Map tokenData) {
    env.ANYPOINT_ACCESS_TOKEN = tokenData.accessToken
    env.ANYPOINT_TOKEN_TYPE = tokenData.tokenType
    env.ANYPOINT_TOKEN_ENVIRONMENT = tokenData.environment
    env.ANYPOINT_TOKEN_EXPIRES_IN = tokenData.expiresIn.toString()
    env.ANYPOINT_TOKEN_OBTAINED_AT = tokenData.obtainedAt.toString()
    
    echo "OAuth2 token cached for environment: ${tokenData.environment}"
}

/**
 * Check if cached token is still valid
 * @param bufferSeconds Number of seconds before expiry to consider token invalid
 * @return boolean true if token is valid, false otherwise
 */
def isTokenValid(int bufferSeconds = 300) {
    if (!env.ANYPOINT_ACCESS_TOKEN || !env.ANYPOINT_TOKEN_OBTAINED_AT || !env.ANYPOINT_TOKEN_EXPIRES_IN) {
        return false
    }
    
    def obtainedAt = Long.parseLong(env.ANYPOINT_TOKEN_OBTAINED_AT)
    def expiresIn = Long.parseLong(env.ANYPOINT_TOKEN_EXPIRES_IN)
    def currentTime = System.currentTimeMillis()
    def expiryTime = obtainedAt + (expiresIn * 1000)
    def bufferTime = bufferSeconds * 1000
    
    return (currentTime + bufferTime) < expiryTime
}

/**
 * Get or refresh OAuth2 token using connected app credentials
 * @param environment Target environment
 * @param forceRefresh Force token refresh even if cached token is valid
 * @return Map containing token data
 */
def getOrRefreshToken(String environment, boolean forceRefresh = false) {
    if (!forceRefresh && isTokenValid()) {
        echo "Using cached OAuth2 token for ${environment}"
        return [
            accessToken: env.ANYPOINT_ACCESS_TOKEN,
            tokenType: env.ANYPOINT_TOKEN_TYPE,
            environment: environment,
            cached: true
        ]
    }
    
    echo "Obtaining new OAuth2 token for ${environment}"
    def tokenData = getAnypointOAuth2TokenConnectedApp(environment)
    cacheOAuth2Token(tokenData)
    
    return tokenData
}

/**
 * Get deployment ID for a specific application name from Anypoint Platform
 * @param accessToken OAuth2 access token
 * @param organizationId Organization ID
 * @param environmentId Environment ID  
 * @param applicationName Name of the application to find
 * @return String deployment ID or null if not found
 */
def getDeploymentId(String accessToken, String organizationId, String environmentId, String applicationName) {
    def apiUrl = "https://anypoint.mulesoft.com/amc/application-manager/api/v2/organizations/${organizationId}/environments/${environmentId}/deployments"
    
    echo "Getting deployment ID for application: ${applicationName}"
    echo "API URL: ${apiUrl}"
    
    try {
        def response = sh(
            script: """
                curl -s -H "Authorization: Bearer ${accessToken}" \\
                     -H "Content-Type: application/json" \\
                     -H "X-ANYPNT-ORG-ID: ${organizationId}" \\
                     "${apiUrl}"
            """,
            returnStdout: true
        ).trim()
        
        // Parse JSON response
        def jsonSlurper = new groovy.json.JsonSlurper()
        def deploymentData = jsonSlurper.parseText(response)
        
        echo "Found ${deploymentData.total ?: 0} deployments in environment"
        
        // Find deployment by application name
        def deployment = deploymentData.items?.find { it.name == applicationName }
        
        if (deployment) {
            echo "✓ Found deployment for ${applicationName}:"
            echo "  - ID: ${deployment.id}"
            echo "  - Status: ${deployment.status}"
            echo "  - Application Status: ${deployment.application?.status}"
            echo "  - Runtime Version: ${deployment.currentRuntimeVersion}"
            
            return deployment.id
        } else {
            echo "✗ No deployment found for application: ${applicationName}"
            echo "Available applications:"
            deploymentData.items?.each { item ->
                echo "  - ${item.name} (ID: ${item.id}, Status: ${item.status})"
            }
            return null
        }
        
    } catch (Exception e) {
        error("Failed to get deployment information: ${e.getMessage()}")
    }
}

/**
 * Get deployment information for a specific application using environment configuration
 * This function uses the ORG_ID from Jenkins environment and maps environment names to environment IDs
 * @param accessToken OAuth2 access token
 * @param environment Environment name (dev, stage, prod)
 * @param applicationName Name of the application to find
 * @return Map containing deployment information
 */
def getApplicationDeployment(String accessToken, String environment, String applicationName) {
    def organizationId = env.ORG_ID
    
    // Environment ID mapping - you'll need to update these with your actual environment IDs
    def environmentIds = [
        'dev': 'a4bc7f3d-b8a5-4ec5-996c-1a7ed5c7e493',  // Update with your actual dev environment ID
        'stage': 'your-stage-env-id',                      // Update with your actual stage environment ID
        'prod': 'your-prod-env-id'                         // Update with your actual prod environment ID
    ]
    
    def environmentId = environmentIds[environment]
    if (!environmentId || environmentId.startsWith('your-')) {
        error("Environment ID not configured for environment: ${environment}. Please update environmentIds map in oauth2-token-helper.groovy")
    }
    
    echo "Getting deployment info for ${applicationName} in ${environment} environment"
    echo "Organization ID: ${organizationId}"
    echo "Environment ID: ${environmentId}"
    
    def deploymentId = getDeploymentId(accessToken, organizationId, environmentId, applicationName)
    
    return [
        deploymentId: deploymentId,
        applicationName: applicationName,
        environment: environment,
        organizationId: organizationId,
        environmentId: environmentId,
        found: deploymentId != null
    ]
}

/**
 * Enhanced function to get deployment ID with automatic token management
 * @param environment Environment name (dev, stage, prod)
 * @param applicationName Name of the application to find
 * @return String deployment ID or null if not found
 */
def getDeploymentIdWithToken(String environment, String applicationName) {
    // Get or refresh OAuth2 token
    def tokenData = getOrRefreshToken(environment)
    
    // Get deployment information
    def deploymentInfo = getApplicationDeployment(tokenData.accessToken, environment, applicationName)
    
    if (deploymentInfo.found) {
        // Store deployment ID in environment variable for easy access
        env.DEPLOYMENT_ID = deploymentInfo.deploymentId
        env.DEPLOYMENT_APP_NAME = applicationName
        env.DEPLOYMENT_ENVIRONMENT = environment
        
        echo "✓ Deployment ID stored in environment variables:"
        echo "  DEPLOYMENT_ID = ${env.DEPLOYMENT_ID}"
        echo "  DEPLOYMENT_APP_NAME = ${env.DEPLOYMENT_APP_NAME}"
        echo "  DEPLOYMENT_ENVIRONMENT = ${env.DEPLOYMENT_ENVIRONMENT}"
    }
    
    return deploymentInfo.deploymentId
}

return this
