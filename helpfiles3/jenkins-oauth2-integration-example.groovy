/**
 * Example integration of OAuth2 token acquisition with existing Jenkins pipeline
 * This shows how to modify your existing functions to include OAuth2 token acquisition
 */

// Load the OAuth2 helper functions
def oauth2Helper = load 'oauth2-token-helper.groovy'

/**
 * Enhanced buildSteps2 function with OAuth2 token acquisition
 */
void buildSteps2WithOAuth2() {
    echo "Running build2 with OAuth2 token acquisition ${JOB_NAME} # ${BUILD_NUMBER}"
    
    withCredentials([
        usernamePassword(credentialsId: "${ENV}-anypointplatform-connected-app", usernameVariable: 'AP_CA_CLIENT_ID', passwordVariable: 'AP_CA_CLIENT_SECRET')
    ]) {
        echo "Running build with clientId ${AP_CA_CLIENT_ID}"

        // Get OAuth2 token for API calls
        def tokenData = oauth2Helper.getAnypointOAuth2TokenConnectedApp(ENV)
        def accessToken = tokenData.accessToken

        sh '''
        npx anypoint-cli-agent-fabric-plugin conf client_id $AP_CA_CLIENT_ID
        npx anypoint-cli-agent-fabric-plugin conf client_secret $AP_CA_CLIENT_SECRET
        npx anypoint-cli-agent-fabric-plugin conf organization ${ORG_ID}
        npx anypoint-cli-agent-fabric-plugin agent-network project build --client_id $AP_CA_CLIENT_ID --client_secret $AP_CA_CLIENT_SECRET --organization ${ORG_ID}
        '''

        // Example: Use the OAuth2 token for API calls
        echo "OAuth2 Token obtained: ${accessToken.take(20)}..."
        
        // You can now use the token for direct API calls if needed
        // Example API call using the token:
        /*
        def apiResponse = sh(
            script: """
                curl -s -H "Authorization: Bearer ${accessToken}" \\
                     -H "Content-Type: application/json" \\
                     "https://anypoint.mulesoft.com/exchange/api/v2/assets"
            """,
            returnStdout: true
        )
        echo "API Response: ${apiResponse}"
        */
    }
}

/**
 * Enhanced deploySteps function with OAuth2 token acquisition
 */
void deployStepsWithOAuth2() {
    env.deployENV = (ENV=='prod') ? 'Production' : ENV
    
    withCredentials([
        string(credentialsId: "${ENV}-splunk_token", variable: 'SPLUNK_TOKEN'),
        usernamePassword(credentialsId: "${ENV}-anypointmq.credential", usernameVariable: 'ANYPOINTMQ_CLIENT_ID', passwordVariable: 'ANYPOINTMQ_CLIENT_SECRET'),
        usernamePassword(credentialsId: "${ENV}-anypointplatform-connected-app", usernameVariable: 'AP_CA_CLIENT_ID', passwordVariable: 'AP_CA_CLIENT_SECRET')
    ]) {
        echo "Running deployment with clientId ${AP_CA_CLIENT_ID}"

        // Get OAuth2 token
        def tokenData = oauth2Helper.getOrRefreshToken(ENV)
        def accessToken = tokenData.accessToken

        echo "Using OAuth2 token for deployment operations..."

        // Publish artifacts to Exchange before deploy
        sh '''
            npx anypoint-cli-agent-fabric-plugin agent-network project publish --client_id $AP_CA_CLIENT_ID --client_secret $AP_CA_CLIENT_SECRET --organization ${ORG_ID} 
        '''

        def exchangeFile = "exchange.json"

        // Generate property arguments from exchange.json
        def propertyArgs = sh(
            script: """
                jq -r '
                    .metadata.variables | 
                    to_entries[] | 
                    . as \$var | 
                    .value | 
                    to_entries[] | 
                    select(.value.default != null and .value.default != "") | 
                    "--property \\"" + (\$var.key + "." + .key) + ":" + (.value.default | tostring) + "\\""
                ' ${exchangeFile} | tr '\\n' ' '
            """,
            returnStdout: true
        ).trim()
        
        echo "propertyArgs: ${propertyArgs}"
        echo "APP_NAME: ${APP_NAME_DEPLOYMENT}"

        def deployCommand = "npx anypoint-cli-agent-fabric-plugin agent-network project deploy \
            --environment ${ENV} \
            --target-space ${target_space} \
            --ingress-gw its-small-ingress-gw \
            --egress-gw its-large-egress-gw \
            --client_id $AP_CA_CLIENT_ID \
            --client_secret $AP_CA_CLIENT_SECRET \
            --organization ${ORG_ID} \
            --property itsopenai.apiKey:XXXX \
            --property splunk.token:${SPLUNK_TOKEN} \
            --property anypointmq.clientid:${ANYPOINTMQ_CLIENT_ID} \
            --property anypointmq.clientsecret:${ANYPOINTMQ_CLIENT_SECRET} \
            --property app.name:${APP_NAME_DEPLOYMENT} \
            --property batch_size_count:1 \
            --property batch_interval:0 \
            --property batch_size_bytes:1024 \
            ${propertyArgs}"
        
        echo "Executing: ${deployCommand}"
        sh deployCommand

        sh '''
            # move log4j2.xml to broker app resources folder
            cp -f resources/dev/log4j2.xml target/broker-mule-app/src/main/resources/
            
            # cd in to broker mule app in target folder
            cd target/broker-mule-app 

            # Recreate the broker mule app Jar
            mvn clean package
        '''
       
        echo "Setting vars for anypoint-cli-v4"
        sh '''
            npx anypoint-cli-v4 conf client_id $AP_CA_CLIENT_ID
            npx anypoint-cli-v4 conf client_secret $AP_CA_CLIENT_SECRET
            npx anypoint-cli-v4 conf organization ${ORG_ID}
        '''

        // Example: Post-deployment verification using OAuth2 token
        echo "Performing post-deployment verification with OAuth2 token..."
        
        // You can add API calls here to verify deployment status
        /*
        def deploymentStatus = sh(
            script: """
                curl -s -H "Authorization: Bearer ${accessToken}" \\
                     -H "Content-Type: application/json" \\
                     -H "X-ANYPNT-ORG-ID: ${ORG_ID}" \\
                     "https://anypoint.mulesoft.com/cloudhub/api/v2/applications/${APP_NAME_DEPLOYMENT}"
            """,
            returnStdout: true
        )
        echo "Deployment Status: ${deploymentStatus}"
        */
    }
}

/**
 * New function specifically for OAuth2 token operations
 */
void performOAuth2TokenOperations() {
    echo "Demonstrating OAuth2 token operations..."
    
    // Get token using your existing credential pattern
    def tokenData = oauth2Helper.getAnypointOAuth2TokenConnectedApp(ENV)
    
    // Cache the token
    oauth2Helper.cacheOAuth2Token(tokenData)
    
    // Check token validity
    def isValid = oauth2Helper.isTokenValid(300) // 5 minute buffer
    echo "Token valid: ${isValid}"
    
    // Example API calls using the token
    withCredentials([
        usernamePassword(credentialsId: "${ENV}-anypointplatform-connected-app", usernameVariable: 'CLIENT_ID', passwordVariable: 'CLIENT_SECRET')
    ]) {
        // Example: Get organization information
        def orgInfo = sh(
            script: """
                curl -s -H "Authorization: Bearer ${tokenData.accessToken}" \\
                     -H "Content-Type: application/json" \\
                     "https://anypoint.mulesoft.com/accounts/api/organizations/${ORG_ID}"
            """,
            returnStdout: true
        )
        
        echo "Organization Info: ${orgInfo}"
        
        // Example: List Exchange assets
        def assets = sh(
            script: """
                curl -s -H "Authorization: Bearer ${tokenData.accessToken}" \\
                     -H "Content-Type: application/json" \\
                     "https://anypoint.mulesoft.com/exchange/api/v2/assets?organizationId=${ORG_ID}"
            """,
            returnStdout: true
        )
        
        echo "Exchange Assets: ${assets.take(500)}..." // Truncate for log readability
    }
}

// Example pipeline modification - add this stage to your existing pipeline
/*
stage('OAuth2 Token Test') {
    when {
        expression {
            env.GIT_BRANCH == "DEV"
        }
    }
    environment {
        ENV = 'dev'
    }
    steps {
        script {
            // Load OAuth2 helper
            def oauth2Helper = load 'oauth2-token-helper.groovy'
            
            // Perform OAuth2 operations
            performOAuth2TokenOperations()
        }
    }
}
*/

return this