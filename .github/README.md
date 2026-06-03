# GitHub Actions Deployment Setup

This document describes the GitHub Actions workflow for deploying the Agent Fabric project. The workflow mirrors the existing Jenkins pipeline functionality.

## Workflow Overview

The deployment workflow (`.github/workflows/deploy.yml`) consists of a single consolidated job:

**build-and-deploy**: Prepares environment, installs dependencies, builds the project, and deploys to the target environment

## Trigger Conditions

The workflow is triggered by:

- **Push events** to branches:
  - `DEV` → automatically deploys to dev environment
  - `STAGE` → automatically deploys to stage environment
  
- **Manual workflow dispatch** for all deployments:
  - Choose target environment: `dev`, `stage`, or `prod`
  - Allows manual deployment to any environment from any branch

## Environment Configuration

The workflow supports three environments:

| Environment | Target Space   | Trigger                           |
|-------------|----------------|-----------------------------------|
| dev         | UCSF-NONPROD   | Push to DEV or Manual dispatch    |
| stage       | UCSF-NONPROD   | Push to STAGE or Manual dispatch  |
| prod        | UCSF-PROD      | Manual dispatch only              |

## Required GitHub Secrets

You must configure the following secrets in your GitHub repository settings (Settings → Secrets and variables → Actions):

### Secret Management Strategy

Secrets are **dynamically selected** in a single centralized step using GitHub Actions conditional expressions:

- **Single Job**: Build and deploy run in one job, sharing the same execution context
- **One Secrets Step**: `Setup Environment Secrets` selects all secrets once
- **Conditional Loading**: Only loads secrets for the active environment (dev/stage/prod)
- **No Shell Logic**: Uses native GitHub Actions `&&` and `||` operators
- **Maximum Efficiency**: Secrets loaded once and reused across build and deploy steps

**How it works:**
```yaml
AP_CA_CLIENT_ID: ${{ 
  steps.set-env.outputs.environment == 'dev' && secrets.DEV_ANYPOINT_CLIENT_ID || 
  steps.set-env.outputs.environment == 'stage' && secrets.STAGE_ANYPOINT_CLIENT_ID || 
  secrets.PROD_ANYPOINT_CLIENT_ID 
}}
```
This only reads the secret for the matching environment, and makes it available to all subsequent steps in the job.

### Development Environment
- `DEV_ANYPOINT_CLIENT_ID` - Anypoint Platform Connected App Client ID
- `DEV_ANYPOINT_CLIENT_SECRET` - Anypoint Platform Connected App Client Secret
- `DEV_SPLUNK_TOKEN` - Splunk logging token
- `DEV_ANYPOINTMQ_CLIENT_ID` - AnypointMQ Client ID
- `DEV_ANYPOINTMQ_CLIENT_SECRET` - AnypointMQ Client Secret

### Stage Environment
- `STAGE_ANYPOINT_CLIENT_ID`
- `STAGE_ANYPOINT_CLIENT_SECRET`
- `STAGE_SPLUNK_TOKEN`
- `STAGE_ANYPOINTMQ_CLIENT_ID`
- `STAGE_ANYPOINTMQ_CLIENT_SECRET`

### Production Environment
- `PROD_ANYPOINT_CLIENT_ID`
- `PROD_ANYPOINT_CLIENT_SECRET`
- `PROD_SPLUNK_TOKEN`
- `PROD_ANYPOINTMQ_CLIENT_ID`
- `PROD_ANYPOINTMQ_CLIENT_SECRET`

## Workflow Steps

### Consolidated Build and Deploy Job (build-and-deploy)

1. **Checkout code** - Retrieves the repository code
2. **Setup Node.js** - Installs Node.js v25
3. **Setup Environment Variables** - Determines environment based on branch/input
4. **Setup Build Environment** - Configures npm and clears cache
5. **Install Anypoint CLI Plugin** - Installs required CLI tools
   - `mulesoft-anypoint-cli-agent-fabric-plugin`
   - `anypoint-cli-v4`
   - `anypoint-cli-exchange-plugin`
6. **Build Steps 1** - Prepares build environment
7. **Copy Environment Exchange Config** - Copies appropriate `exchange.json`
8. **Setup Environment Secrets** - **Single centralized step** that selects all secrets based on environment
9. **Build Steps 2** - Builds the agent network project using selected secrets
10. **Setup Java** - Installs Java 17 for Maven packaging
11. **Deploy Steps** - Executes full deployment using the same selected secrets:
    - Publishes artifacts to Exchange
    - Extracts configuration from `exchange.json`
    - Reads dependencies from `dependency.json`
    - Deploys to target environment
    - Packages broker mule app
    - Injects parent POM reference
    - Uploads app to Exchange
    - Modifies runtime deployment with properties
12. **Upload Deployment Artifacts** - Stores final JAR files

## Key Differences from Jenkinsfile

1. **Job Structure**: Single consolidated job instead of separate build/deploy stages
2. **Secrets Management**: Uses GitHub Secrets with conditional loading based on environment
3. **Single Secret Selection**: Secrets loaded once in a centralized step, reused throughout
4. **Path References**: Uses `$GITHUB_WORKSPACE` instead of hardcoded Jenkins paths
5. **Conditional Execution**: Uses GitHub Actions `if` conditions instead of `when` blocks
6. **Security**: All user inputs are passed through environment variables to prevent injection
7. **No Job Boundaries**: Build and deploy share the same execution context and secrets

## Manual Deployment

To manually trigger a deployment to any environment:

1. Go to the **Actions** tab in GitHub
2. Select **Deploy Agent Fabric** workflow
3. Click **Run workflow**
4. Select the branch you want to deploy from
5. Choose the **Target Environment**: `dev`, `stage`, or `prod`
6. Click **Run workflow**

### Deployment Notes
- **DEV**: Deploys automatically on push to DEV branch OR can be triggered manually
- **STAGE**: Deploys automatically on push to STAGE branch OR can be triggered manually
- **PROD**: Must be triggered manually (recommended to use with environment protection)

## Monitoring Deployments

- View workflow runs in the **Actions** tab
- Each run shows logs for both build and deploy jobs
- Artifacts are available for download from completed runs
- Deployment artifacts are retained for 30 days
- Build artifacts are retained for 5 days

## Environment Protection (Recommended)

Consider configuring environment protection rules in GitHub:

1. Go to Settings → Environments
2. For `prod` environment, configure:
   - Required reviewers
   - Wait timer
   - Deployment branches (restrict to specific branches)

This adds an approval step before production deployments.

## Troubleshooting

### Build Failures
- Check that all required secrets are configured
- Verify the `exchange.json` exists in `resources/{env}/`
- Ensure Node.js and Java versions match requirements

### Deployment Failures
- Verify Anypoint Platform credentials are valid
- Check that target spaces and environments exist
- Review deployment logs for specific error messages
- Ensure the asset exists in Exchange before modification

### Secret Management
- Secrets are environment-specific (prefix: DEV_, STAGE_, PROD_)
- Never commit secrets to the repository
- Rotate secrets regularly for security

## Comparison with Jenkins

| Feature | Jenkins | GitHub Actions |
|---------|---------|----------------|
| Trigger | Branch detection | Push events / Manual dispatch |
| Secrets | Jenkins credentials | GitHub Secrets |
| Artifacts | Workspace files | Upload/Download artifacts |
| Java Tool | Jenkins tool config | setup-java action |
| Node Tool | Jenkins tool config | setup-node action |
| Paths | Hardcoded workspace | $GITHUB_WORKSPACE |
| Build Status | Jenkins UI | Actions tab |

The Jenkinsfile remains preserved and functional for existing Jenkins-based deployments.
