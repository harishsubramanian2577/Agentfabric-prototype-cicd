# Jenkins Agent Network Deployment Guide

## Problem
The Anypoint CLI prompts for variable values during deployment even when defaults are defined in `exchange.json`. This breaks automated Jenkins deployments.

## Solution
Parse the `exchange.json` file and pass all variables as `--property` arguments to the CLI command.

## Your Exchange.json Structure
Based on your configuration, you have these variables:
- `customer-workday-agent.url`
- `customer-badging-agent.url`
- `customer-salesforce-agent.url`
- `customer-zendesk-agent.url`
- `customer-it-agent.url`
- `customer-talent-pool-mcp.url`
- `itsopenai.url`
- `itsopenai.apiKey` (secret)
- `itsopenai.modelName`

## Implementation Options

### Option 1: Using Helper Script (Recommended)
```bash
# Generate the complete command
./jenkins-deploy-helper.sh dev

# Output example:
anypoint-cli agent-fabric-plugin agent-network project deploy \
  --property "customer-workday-agent.url=https://workday-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/workday-agent/" \
  --property "customer-badging-agent.url=https://badging-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/badge-agent/" \
  --property "customer-salesforce-agent.url=https://salesforce-agent-v1-2p52mj.5sc6y6-2.usa-e2.cloudhub.io/salesforce-agent/" \
  --property "customer-zendesk-agent.url=https://zendesk-agent-v1-2p52mj.5sc6y6-3.usa-e2.cloudhub.io/zendesk/" \
  --property "customer-it-agent.url=https://it-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/pingid/" \
  --property "customer-talent-pool-mcp.url=https://talent-pool-arvldt.5sc6y6-3.usa-e2.cloudhub.io/" \
  --property "itsopenai.url=https://dev-unified-api.ucsf.edu/general/openai/v1/" \
  --property "itsopenai.modelName=gpt-5-mini"
```

### Option 2: Direct Jenkins Integration
Add this stage to your existing Jenkinsfile:

```groovy
stage('Deploy Agent Network') {
    steps {
        script {
            // Generate deployment command with properties
            def deployCommand = sh(
                script: "./jenkins-deploy-helper.sh ${params.ENVIRONMENT}",
                returnStdout: true
            ).trim()
            
            echo "Executing: ${deployCommand}"
            sh deployCommand
        }
    }
}
```

### Option 3: Manual Property List
If you prefer to manually specify properties in your Jenkins pipeline:

```groovy
stage('Deploy Agent Network') {
    steps {
        script {
            def properties = [
                "customer-workday-agent.url=https://workday-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/workday-agent/",
                "customer-badging-agent.url=https://badging-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/badge-agent/",
                "customer-salesforce-agent.url=https://salesforce-agent-v1-2p52mj.5sc6y6-2.usa-e2.cloudhub.io/salesforce-agent/",
                "customer-zendesk-agent.url=https://zendesk-agent-v1-2p52mj.5sc6y6-3.usa-e2.cloudhub.io/zendesk/",
                "customer-it-agent.url=https://it-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/pingid/",
                "customer-talent-pool-mcp.url=https://talent-pool-arvldt.5sc6y6-3.usa-e2.cloudhub.io/",
                "itsopenai.url=https://dev-unified-api.ucsf.edu/general/openai/v1/",
                "itsopenai.modelName=gpt-5-mini"
            ]
            
            def propertyArgs = properties.collect { "--property \"${it}\"" }.join(' ')
            def deployCommand = "anypoint-cli agent-fabric-plugin agent-network project deploy ${propertyArgs}"
            
            sh deployCommand
        }
    }
}
```

## Files Created
1. `parse-exchange-variables.sh` - Basic parser for exchange.json variables
2. `jenkins-deploy-helper.sh` - Complete Jenkins helper script
3. `Jenkinsfile.example` - Full Jenkins pipeline example
4. `jenkins-inline-example.groovy` - Code snippet for existing pipeline

## Usage in Jenkins

### Prerequisites
- `jq` must be installed on Jenkins agents
- Anypoint CLI must be available

### Environment-Specific Deployments
```bash
# For dev environment
./jenkins-deploy-helper.sh dev

# For stage environment  
./jenkins-deploy-helper.sh stage

# For prod environment
./jenkins-deploy-helper.sh prod
```

## Handling Secret Values
Note: The script skips empty default values (like `itsopenai.apiKey`). For secret values:

1. Set them as Jenkins credentials
2. Pass them as additional properties:
```groovy
withCredentials([string(credentialsId: 'openai-api-key', variable: 'OPENAI_KEY')]) {
    sh """
        ${deployCommand} --property "itsopenai.apiKey=${OPENAI_KEY}"
    """
}
```

## Troubleshooting
1. **jq not found**: Install jq on Jenkins agents
2. **Property format errors**: Check that property names match exchange.json structure
3. **Authentication issues**: Ensure Anypoint CLI login before deployment
4. **Empty values**: Script skips empty defaults - add them manually if needed

## Testing
Test the script locally:
```bash
chmod +x jenkins-deploy-helper.sh
./jenkins-deploy-helper.sh dev
```

This will show you exactly what command will be executed in Jenkins.