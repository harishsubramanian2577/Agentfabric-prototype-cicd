/**
 * Simple example showing how to get deployment ID in your existing Jenkins pipeline
 * Add this to your existing deploySteps() function or create a new stage
 */

// Example of how to modify your existing "Deploy to Dev" stage
/*
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
        // Your existing deploySteps() function
        deploySteps()
        
        // NEW: Add deployment ID retrieval
        script {
            def oauth2Helper = load 'oauth2-token-helper.groovy'
            
            echo "Getting deployment ID for demo-ucsf-onboarding-agent..."
            
            // Get the deployment ID
            def deploymentId = oauth2Helper.getDeploymentIdWithToken('dev', 'demo-ucsf-onboarding-agent')
            
            if (deploymentId) {
                echo "✅ SUCCESS: Deployment ID = ${deploymentId}"
                
                // The deployment ID is now available in environment variables:
                echo "Available environment variables:"
                echo "  DEPLOYMENT_ID = ${env.DEPLOYMENT_ID}"
                echo "  DEPLOYMENT_APP_NAME = ${env.DEPLOYMENT_APP_NAME}"  
                echo "  DEPLOYMENT_ENVIRONMENT = ${env.DEPLOYMENT_ENVIRONMENT}"
                
                // You can now use the deployment ID for other operations
                // For example, trigger additional deployment steps, notifications, etc.
                
            } else {
                echo "⚠️  WARNING: Could not find deployment ID for demo-ucsf-onboarding-agent"
            }
        }
    }
}
*/

/**
 * Simple function to add to your existing deploySteps() 
 * Call this at the end of your deploySteps() function
 */
void getDeploymentIdAfterDeploy() {
    echo "Retrieving deployment ID after successful deployment..."
    
    script {
        def oauth2Helper = load 'oauth2-token-helper.groovy'
        
        // Wait a moment for deployment to register in the system
        sleep(5)
        
        // Get deployment ID for the demo application
        def deploymentId = oauth2Helper.getDeploymentIdWithToken(ENV, 'demo-ucsf-onboarding-agent')
        
        if (deploymentId) {
            echo "✅ Deployment tracking successful!"
            echo "   Application: demo-ucsf-onboarding-agent"
            echo "   Environment: ${ENV}"
            echo "   Deployment ID: ${deploymentId}"
            
            // Store in Jenkins environment for use in subsequent stages
            env.CURRENT_DEPLOYMENT_ID = deploymentId
            
        } else {
            // This is not necessarily an error - the application might have a different name
            echo "ℹ️  Note: Could not find deployment ID for 'demo-ucsf-onboarding-agent'"
            echo "   This might be normal if:"
            echo "   - The application name in the deployment differs"
            echo "   - The deployment is still in progress"
            echo "   - The application is deployed to a different environment"
        }
    }
}

/**
 * Alternative: Get deployment ID by modifying the application name
 * Use this if your application name in deployments is different
 */
void getDeploymentIdWithCustomName() {
    script {
        def oauth2Helper = load 'oauth2-token-helper.groovy'
        
        // Try different possible application names
        def possibleNames = [
            'demo-ucsf-onboarding-agent',
            "${ENV}-demo-ucsf-onboarding-agent", 
            "${APP_NAME_DEPLOYMENT}",
            'its-agentfabric-prototype'
        ]
        
        def foundDeploymentId = null
        
        for (appName in possibleNames) {
            echo "Trying to find deployment for: ${appName}"
            def deploymentId = oauth2Helper.getDeploymentIdWithToken(ENV, appName)
            
            if (deploymentId) {
                foundDeploymentId = deploymentId
                echo "✅ Found deployment ID ${deploymentId} for application: ${appName}"
                break
            }
        }
        
        if (!foundDeploymentId) {
            echo "⚠️  Could not find deployment ID with any of the tried names"
            echo "   Tried names: ${possibleNames.join(', ')}"
        }
    }
}

/**
 * Debug function to see all deployments in the environment
 * Use this to troubleshoot and see what applications are actually deployed
 */
void debugListAllDeployments() {
    script {
        def oauth2Helper = load 'oauth2-token-helper.groovy'
        def tokenData = oauth2Helper.getOrRefreshToken(ENV)
        
        def organizationId = env.ORG_ID
        def environmentId = 'a4bc7f3d-b8a5-4ec5-996c-1a7ed5c7e493' // Dev environment ID
        
        echo "=== DEBUG: All deployments in ${ENV} environment ==="
        
        def response = sh(
            script: """
                curl -s -H "Authorization: Bearer ${tokenData.accessToken}" \\
                     -H "Content-Type: application/json" \\
                     -H "X-ANYPNT-ORG-ID: ${organizationId}" \\
                     "https://anypoint.mulesoft.com/amc/application-manager/api/v2/organizations/${organizationId}/environments/${environmentId}/deployments"
            """,
            returnStdout: true
        ).trim()
        
        def jsonSlurper = new groovy.json.JsonSlurper()
        def deploymentData = jsonSlurper.parseText