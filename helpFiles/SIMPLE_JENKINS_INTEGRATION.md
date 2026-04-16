# Simple Jenkins Integration - Copy & Paste Ready

## 🚀 Quick Integration for Your Existing Jenkinsfile

### Option 1: Using Helper Script (Recommended)
Add this stage to your existing Jenkinsfile:

```groovy
stage('Deploy Agent Network') {
    steps {
        script {
            // Generate deployment command with all properties from exchange.json
            def deployCommand = sh(
                script: "./jenkins-deploy-helper.sh ${params.ENVIRONMENT ?: 'dev'}",
                returnStdout: true
            ).trim()
            
            echo "Deploying with command: ${deployCommand}"
            sh deployCommand
        }
    }
}
```

### Option 2: All-in-One (No External Script Needed)
Add this stage if you prefer everything inline:

```groovy
stage('Deploy Agent Network') {
    steps {
        script {
            // Determine exchange.json file
            def exchangeFile = "resources/${params.ENVIRONMENT ?: 'dev'}/exchange.json"
            if (!fileExists(exchangeFile)) {
                exchangeFile = "exchange.json"
            }
            
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
                        "--property \\"" + (\$var.key + "." + .key) + "=" + (.value.default | tostring) + "\\""
                    ' ${exchangeFile} | tr '\\n' ' '
                """,
                returnStdout: true
            ).trim()
            
            // Execute deployment
            def deployCommand = "anypoint-cli agent-fabric-plugin agent-network project deploy ${propertyArgs}"
            echo "Executing: ${deployCommand}"
            sh deployCommand
        }
    }
}
```

## 📋 Prerequisites
- Ensure `jq` is installed on your Jenkins agents
- Copy `jenkins-deploy-helper.sh` to your repository (for Option 1)
- Ensure Anypoint CLI is available and authenticated

## 🧪 Testing
Test locally first:
```bash
# Test the helper script
./jenkins-deploy-helper.sh dev

# Verify the generated command works
# (Don't run if you don't have Anypoint CLI configured)
```

## 📝 Your Generated Command
Based on your exchange.json, this will generate:
```bash
anypoint-cli agent-fabric-plugin agent-network project deploy \
  --property "customer-badging-agent.url=https://badging-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/badge-agent/" \
  --property "customer-it-agent.url=https://it-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/pingid/" \
  --property "customer-salesforce-agent.url=https://salesforce-agent-v1-2p52mj.5sc6y6-2.usa-e2.cloudhub.io/salesforce-agent/" \
  --property "customer-talent-pool-mcp.url=https://talent-pool-arvldt.5sc6y6-3.usa-e2.cloudhub.io/" \
  --property "customer-workday-agent.url=https://workday-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/workday-agent/" \
  --property "customer-zendesk-agent.url=https://zendesk-agent-v1-2p52mj.5sc6y6-3.usa-e2.cloudhub.io/zendesk/" \
  --property "itsopenai.modelName=gpt-5-mini" \
  --property "itsopenai.url=https://dev-unified-api.ucsf.edu/general/openai/v1/"
```

## 🔐 Handling Secrets
For the empty `itsopenai.apiKey`, add it as a Jenkins credential:

```groovy
stage('Deploy Agent Network') {
    steps {
        withCredentials([string(credentialsId: 'openai-api-key', variable: 'OPENAI_KEY')]) {
            script {
                def deployCommand = sh(
                    script: "./jenkins-deploy-helper.sh ${params.ENVIRONMENT}",
                    returnStdout: true
                ).trim()
                
                // Add the secret property
                deployCommand += " --property \"itsopenai.apiKey=${OPENAI_KEY}\""
                
                sh deployCommand
            }
        }
    }
}
```

That's it! Your Jenkins pipeline will now automatically use all default values from exchange.json without prompting for manual input. ✅