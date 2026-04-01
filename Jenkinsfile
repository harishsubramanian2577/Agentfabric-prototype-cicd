void buildSteps() {
    echo "Running build ${JOB_NAME} # ${BUILD_NUMBER} "
    withEnv(["JAVA_HOME=${tool 'mule_java_17_home'}", "PATH=${env.JAVA_HOME}/bin:${env.PATH}"]) {   
        // sh '''
        //     unset JDK_JAVA_OPTIONS
        //     mvn clean install  -P $ENV  -Dsecure.key=test -Dhttp.private.port=${random_port}
        //    '''

         sh '''
            # Install the Agent Fabric plugin globally
            anypoint-cli-agent-fabric-plugin conf client_id myClientID

            # Verify installation
            npm list -g @mulesoft/anypoint-cli-agent-fabric-plugin
            
            # Check available commands
            npx anypoint-cli-agent-fabric-plugin --help
        '''

    }
    {

    }
}
 
void deploySteps() {
   	env.deployENV = (ENV=='prod') ? 'Production' : ENV
   		
   	// properties = readProperties file: "/opt/properties/apps/ITS/${APP_NAME.toUpperCase()}/${ENV}_env.properties"
   	// env.noOfWorkers = "${properties.no_of_worker}"
   	// env.workerType = "${properties.worker_type}"
   	// env.ENV_ID = "${properties.env_id}"
   	// env.batch_size_count="${properties.batch_size_count}"
    // env.batch_interval="${properties.batch_interval}"
    // env.batch_size_bytes="${properties.batch_size_bytes}"
         
   		
    withCredentials([
        // string(credentialsId: "${ENV}-${APP_NAME}-secure.key", variable: 'SECURE_KEY'),
        //string(credentialsId: "${ENV}-splunk_token", variable: 'SPLUNK_TOKEN'),
        
        //usernamePassword(credentialsId: "${ENV}-anypoint.platform.credential", usernameVariable: 'ANYPOINT_PLATFORM_CLIENT_ID', passwordVariable: 'ANYPOINT_PLATFORM_CLIENT_SECRET'),
        usernamePassword(credentialsId: "${ENV}-anypointplatform-connected-app", usernameVariable: 'AP_CA_CLIENT_ID', passwordVariable: 'AP_CA_CLIENT_SECRET')
    ]) {
        // sh 'mvn clean deploy -P $ENV -DskipMunitTests -DmuleDeploy -Dapp.name=$APP_NAME_DEPLOYMENT -Denvironment=$deployENV -Dworkers=$noOfWorkers -DworkerType=$workerType'

        echo "Running build with clientId ${AP_CA_CLIENT_ID}"

        sh '''
           npx anypoint-cli-agent-fabric-plugin conf client_id $AP_CA_CLIENT_ID
           npx anypoint-cli-agent-fabric-plugin conf client_secret $AP_CA_CLIENT_SECRET
           npx anypoint-cli-agent-fabric-plugin conf organization ${ORG_ID}
           npx anypoint-cli-agent-fabric-plugin agent-network project build --client_id $AP_CA_CLIENT_ID --client_secret $AP_CA_CLIENT_SECRET --organization ${ORG_ID}
           
           npx anypoint-cli-agent-fabric-plugin agent-network project publish --client_id $AP_CA_CLIENT_ID --client_secret $AP_CA_CLIENT_SECRET --organization ${ORG_ID}
           
           npx anypoint-cli-agent-fabric-plugin agent-network project deploy \
               --environment ${ENV} \
               --target-space ${target_space} \
               --ingress-gw its-small-ingress-gw \
               --egress-gw its-large-egress-gw \
               --client_id $AP_CA_CLIENT_ID \
               --client_secret $AP_CA_CLIENT_SECRET \
               --organization ${ORG_ID} \
               --property customer-workday-agent.url:${customer_workday_agent_url} \
               --property customer-badging-agent.url:${customer_badging_agent_url} \
               --property customer-salesforce-agent.url:${customer_salesforce_agent_url} \
               --property customer-zendesk-agent.url:${customer_zendesk_agent_url} \
               --property customer-it-agent.url:${customer_itagent_agent_url} \
               --property customer-talent-pool-mcp.url:${customer_talent_pool_mcp_url} \
               --property itsopenai.modelName:${itsopenai_modelName} \
               --property itsopenai.url:${itsopenai_url} \
               --property itsopenai.apiKey:${itsopenai_apiKey}
        '''
    }
}
 
pipeline {
    agent any
    environment {
        APP_NAME = 'its-agentfabric-prototype'
        ORG_ID = 'fd48cc65-d939-425c-a990-099a23d246ae'

        customer_workday_agent_url = 'https://workday-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/workday-agent/'
        customer_badging_agent_url = 'https://badging-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/badge-agent/'
        customer_salesforce_agent_url = 'https://salesforce-agent-v1-2p52mj.5sc6y6-2.usa-e2.cloudhub.io/salesforce-agent/'
        customer_zendesk_agent_url = 'https://zendesk-agent-v1-2p52mj.5sc6y6-3.usa-e2.cloudhub.io/zendesk/'
        customer_itagent_agent_url = 'https://it-agent-v1-2p52mj.5sc6y6-4.usa-e2.cloudhub.io/pingid/'
        customer_talent_pool_mcp_url = 'https://talent-pool-arvldt.5sc6y6-3.usa-e2.cloudhub.io/'
        itsopenai_modelName= 'gpt-5-mini'
        itsopenai_url = 'https://dev-unified-api.ucsf.edu/general/openai/v1/'
        itsopenai_apiKey = 'sadfsafasf'

        min = 8081
        max = 40000
        random_port = "${(int)(Math.random() * (max - min) + 1) + min}"

         // Node.js and npm configuration
        // NODE_VERSION = '18.x'
        // NODE_VERSION = 'Node-18'
        NODE_VERSION = 'Node-25'
        NPM_REGISTRY = 'https://registry.npmjs.org/'
    }

    tools {
        nodejs "${NODE_VERSION}"
        //node "${NODE_VERSION}"
    }

    stages {
       
    //    stage('Install Dependencies') {
    //     steps {
    //         // We use -y to auto-confirm the installation
    //         sh 'sudo apt-get update && sudo apt-get install -y libatomic1'
    //         }
    //     }
      
       stage('Setup Environment') {
            steps {
                echo 'Setting up build environment...'
                sh '''
                    echo "Node.js version:"
                    node --version
                    echo "npm version:"
                    npm --version
                    
                    # Clean npm cache
                    npm cache clean --force
                    
                    # Set npm registry
                    npm config set registry ${NPM_REGISTRY}
                '''
            }
        }

        stage('Install Anypoint CLI Plugin') {
            steps {
                echo 'Installing MuleSoft Anypoint CLI Agent Fabric Plugin...'
                sh '''
                    # Install the Agent Fabric plugin globally
                    npm i mulesoft-anypoint-cli-agent-fabric-plugin
                    
                    # Check available commands
                    npx mulesoft-anypoint-cli-agent-fabric-plugin --help
                '''
            }
        }

      stage('Setup parameters') {
           when {
                branch "master"
            }
            steps {
                script {
                    properties([
                        parameters([
                            choice(
                                choices: ['NO', 'YES'],
                                name: 'DEPLOY_TO_PROD'
                            )
                        ])
                    ])
                }
            }
        }
        stage('DEV Build') {
            when {
                expression {
                    // env.GIT_BRANCH == "DEV"
                    env.GIT_BRANCH == "feature-prototype"
                }
            }
            environment {
                ENV = 'dev'
                target_space = 'UCSF-NONPROD'
            }
            steps {
                sh 'cp -f resources/dev/exchange.json ./'
                // buildSteps()
            }
        }
        stage('STAGE Build') {
            when {
                expression {
                    env.GIT_BRANCH == "STAGE"
                }
            }
            environment {
                ENV = 'stage'
                target_space = 'UCSF-NONPROD'
            }
            steps {
                sh 'cp -f resources/stage/exchange.json ./'
                buildSteps()
            }
 
        }
        stage('PROD Build') {
            when {
                expression {
                      params.DEPLOY_TO_PROD == 'YES'
                }
            }
            environment {
                ENV = 'prod'
                target_space = 'UCSF-PROD'
            }
            steps {
                sh 'cp -f resources/prod/exchange.json ./'
                buildSteps()
            }
 
        }
        stage('Deploy to Dev') {
            when {
                expression {
                    // env.GIT_BRANCH == "DEV"
                    env.GIT_BRANCH == "feature-prototype"
                }
            }
            environment {
                ENV = 'dev'
                APP_NAME_DEPLOYMENT = "${ENV}-${APP_NAME}"
                target_space = 'UCSF-NONPROD'
            }
            steps {
                deploySteps()
            }
        }
       stage('Deploy to Stage') {
            when {
                expression {
                    env.GIT_BRANCH == "STAGE"
                }
            }
            environment {
                ENV = 'stage'
                APP_NAME_DEPLOYMENT = "${ENV}-${APP_NAME}"
                target_space = 'UCSF-NONPROD'
            }
            steps {
                deploySteps()
            }
        }
         stage('Deploy to Prod') {
            when {
                expression {
                    params.DEPLOY_TO_PROD == 'YES'
                }
            }
            environment {
                ENV = 'prod'
                APP_NAME_DEPLOYMENT = "${ENV}-${APP_NAME}"
                target_space = 'UCSF-PROD'
            }
            steps {
                deploySteps()
            }
        }
    }
 }