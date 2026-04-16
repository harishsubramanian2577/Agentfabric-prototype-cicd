# Simple Usage Guide: Get Deployment ID for demo-ucsf-onboarding-agent

This guide shows exactly how to add deployment ID retrieval to your existing Jenkins pipeline.

## Quick Integration (Minimal Changes)

### Option 1: Add to existing Deploy stage

Add this to your existing "Deploy to Dev" stage, right after `deploySteps()`:

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
        deploySteps()  // Your existing function
        
        // NEW: Get deployment ID
        script {
            def oauth2Helper = load 'oauth2-token-helper.groovy'
            
            echo "Getting deployment ID for demo-ucsf-onboarding-agent..."
            sleep(10) // Wait for deployment to register
            
            def deploymentId = oauth2Helper.getDeploymentIdWithToken('dev', 'demo-ucsf-onboarding-agent')
            
            if (deploymentId) {
                echo "✅ Deployment ID: ${deploymentId}"
                // Available in env.DEPLOYMENT_ID for rest of pipeline
            } else {
                echo "⚠️ Deployment ID not found"
            }
        }
    }
}
```

### Option 2: Create a new stage after deployment

Add this as a new stage after your deployment stages:

```groovy
stage('Get Deployment Info') {
    when {
        expression {
            env.GIT_BRANCH == "DEV" || env.GIT_BRANCH == "STAGE" 
        }
    }
    steps {
        script {
            def oauth2Helper = load 'oauth2-token-helper.groovy'
            
            def appName = 'demo-ucsf-onboarding-agent'
            echo "Retrieving deployment information for ${appName}..."
            
            def deploymentId = oauth2Helper.getDeploymentIdWithToken(ENV, appName)
            
            if (deploymentId) {
                echo "🎉 Found deployment!"
                echo "Application: ${appName}"
                echo "Deployment ID: ${deploymentId}"
                echo "Environment: ${ENV}"
                
                // Store in environment variables
                env.CURRENT_DEPLOYMENT_ID = deploymentId
                
            } else {
                echo "Could not find deployment for ${appName}"
            }
        }
    }
}
```

## What You Get

After running either option, these environment variables will be available:

- `env.DEPLOYMENT_ID` - The deployment ID (e.g., "5a8abc64-4e1b-46a0-8787-30d5a3b9d1d4")
- `env.DEPLOYMENT_APP_NAME` - The application name
- `env.DEPLOYMENT_ENVIRONMENT` - The environment name

## Example Output

When successful, you'll see output like:

```
Getting deployment ID for demo-ucsf-onboarding-agent...
Getting deployment info for demo-ucsf-onboarding-agent in dev environment
Organization ID: fd48cc65-d939-425c-a990-099a23d246ae
Environment ID: a4bc7f3d-b8a5-4ec5-996c-1a7ed5c7e493
API URL: https://anypoint.mulesoft.com/amc/application-manager/api/v2/organizations/fd48cc65-d939-425c-a990-099a23d246ae/environments/a4bc7f3d-b8a5-4ec5-996c-1a7ed5c7e493/deployments
Found 2 deployments in environment
✓ Found deployment for demo-ucsf-onboarding-agent:
  - ID: 5a8abc64-4e1b-46a0-8787-30d5a3b9d1d4
  - Status: APPLIED
  - Application Status: RUNNING
  - Runtime Version: 4.11.2:3e-java17
✓ Deployment ID stored in environment variables:
  DEPLOYMENT_ID = 5a8abc64-4e1b-46a0-8787-30d5a3b9d1d4
  DEPLOYMENT_APP_NAME = demo-ucsf-onboarding-agent
  DEPLOYMENT_ENVIRONMENT = dev
```

## Troubleshooting

If the deployment ID is not found:

1. **Check application name**: The API returns the exact name as deployed. It might be different from what you expect.

2. **Use debug function**: Add this to see all deployments:
```groovy
script {
    def oauth2Helper = load 'oauth2-token-helper.groovy'
    // This will list all deployments in the environment
    debugListAllDeployments()
}
```

3. **Check timing**: Add a longer sleep if deployment takes time to register:
```groovy
sleep(30) // Wait 30 seconds
```

4. **Try multiple names**: Use this if you're unsure of the exact name:
```groovy
def possibleNames = [
    'demo-ucsf-onboarding-agent',
    'dev-demo-ucsf-onboarding-agent',
    env.APP_NAME_DEPLOYMENT
]

def foundId = null
for (name in possibleNames) {
    foundId = oauth2Helper.getDeploymentIdWithToken(ENV, name)
    if (foundId) break
}
```

## Using the Deployment ID

Once you have the deployment ID, you can use it for:

- Updating deployment configurations
- Checking deployment status
- Triggering post-deployment actions
- Storing in external systems
- Notifications with deployment details

The deployment ID is available throughout your pipeline in `env.DEPLOYMENT_ID`.