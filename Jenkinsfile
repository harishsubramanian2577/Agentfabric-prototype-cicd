void buildSteps() {
    echo "Running build ${JOB_NAME} # ${BUILD_NUMBER} "
    sh 'rm -rf target/build-dependencies'

    withEnv(["JAVA_HOME=${tool 'mule_java_17_home'}", "PATH=${env.JAVA_HOME}/bin:${env.PATH}"]) {
        script {
            sh 'which mvn > /dev/null 2>&1 || { echo "mvn not found on PATH"; exit 1; }'
            // setEnvVersion()
            // mvn clean install -P $ENV -Dsecure.key=test -Dhttp.private.port=${random_port}
            sh '''
                unset JDK_JAVA_OPTIONS
                
            '''
        }
    }
}

void buildSteps2(){
    echo "Running build2 ${JOB_NAME} # ${BUILD_NUMBER} "
    withCredentials([
        usernamePassword(credentialsId: "${ENV}-anypointplatform-connected-app", usernameVariable: 'AP_CA_CLIENT_ID', passwordVariable: 'AP_CA_CLIENT_SECRET')
    ]) 
    {
        echo "Running build with clientId ${AP_CA_CLIENT_ID}"
        sh '''
            npx anypoint-cli-agent-fabric-plugin conf client_id $AP_CA_CLIENT_ID
            npx anypoint-cli-agent-fabric-plugin conf client_secret $AP_CA_CLIENT_SECRET
            npx anypoint-cli-agent-fabric-plugin conf organization ${ORG_ID}
            npx anypoint-cli-agent-fabric-plugin agent-network project build --client_id $AP_CA_CLIENT_ID --client_secret $AP_CA_CLIENT_SECRET --organization ${ORG_ID}
        '''
        echo "Build Comppleted"
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
        //string(credentialsId: "${ENV}-openai-key", variable: 'API_KEY'),
        usernamePassword(credentialsId: "${ENV}-anypointplatform-connected-app", usernameVariable: 'AP_CA_CLIENT_ID', passwordVariable: 'AP_CA_CLIENT_SECRET')
    ]) {
        echo "Running build with clientId ${AP_CA_CLIENT_ID}" 
        //Publish artifacts to Exchange before deploy
        sh '''
            npx anypoint-cli-agent-fabric-plugin agent-network project publish --client_id $AP_CA_CLIENT_ID --client_secret $AP_CA_CLIENT_SECRET --organization ${ORG_ID} 
        '''

        def exchangeFile = "exchange.json"

        sh 'jq --version'

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

        // Extract version using jq
        APP_VERSION = sh(
            script: "jq -r '.version' ${exchangeFile}",
            returnStdout: true
        ).trim()
        echo "Version: ${APP_VERSION}"

         // Extract version using jq
        ASSET_ID = sh(
            script: "jq -r '.assetId' ${exchangeFile}",
            returnStdout: true
        ).trim()
        echo "asset_id: ${ASSET_ID}"

        def deployCommand = "npx anypoint-cli-agent-fabric-plugin agent-network project deploy \
            --environment ${ENV} \
            --target-space ${target_space} \
            --ingress-gw agent-network-ingress-gw \
            --egress-gw agent-network-egress-gw \
            --client_id $AP_CA_CLIENT_ID \
            --client_secret $AP_CA_CLIENT_SECRET \
            --organization ${ORG_ID} \
            --property openai.apiKey:XXXX \
            ${propertyArgs}"
        
        echo "Deploy args: ${deployCommand}"
        echo "Executing Deploy Command..." 
        //sh deployCommand
        echo "Deployment completed for Agent Broker..." 
    }
}


void injectDependencyAndUploadJar() {
   	env.deployENV = (ENV=='prod') ? 'Production' : ENV
    withCredentials([
        usernamePassword(credentialsId: "${ENV}-anypointplatform-connected-app", usernameVariable: 'AP_CA_CLIENT_ID', passwordVariable: 'AP_CA_CLIENT_SECRET')
    ]) {
        //reading agentic-parent-pom dependency
        // 1. Define the assetId we are looking for
        def targetAssetId = "org-agentic-parent-pom"
        // 2. Use jq to filter the dependencies array and return a raw string
        // -r ensures raw output (no quotes around the strings)
        // We fetch the groupId, assetId, and version separated by spaces
        def result = sh(
            script: """
                jq -r '.dependencies[] | select(.assetId == "${targetAssetId}") | "\\(.groupId) \\(.assetId) \\(.version)"' dependency.json
            """,
            returnStdout: true
        ).trim()

        def parentBlock = ""
        if (result) {
            // 3. Split the space-separated string into a list
            def parts = result.split(' ')
            def gId = parts[0]
            def aId = parts[1]
            def vId = parts[2]

            // 4. Construct the XML block
            parentBlock = """
                <parent>
                    <groupId>${gId}</groupId>
                    <artifactId>${aId}</artifactId>
                    <version>${vId}</version>
                </parent>
            """

            echo "Successfully generated XML via jq:"
            echo "parentBlock: ${parentBlock}"
        } else {
            error "Dependency '${targetAssetId}' not found in dependency.json using jq"
        }  
        
        sh '''
            # move log4j2.xml to broker app resources folder
            sudo cp -f resources/log4j2.xml target/broker-mule-app/src/main/resources/log4j2.xml
            
            # cd in to broker mule app in target folder
            cd target/broker-mule-app 
            
            ls -latr

            pwd
         '''
        
        echo "On target folder.."
        
        echo "BROKER_APP_LOCATION: ${BROKER_APP_LOCATION}"
        // Injecting parent Pom reference
        def pomFile =  "${BROKER_APP_LOCATION}/pom.xml"
        echo "Read Pom.xml file from ${pomFile}"
        String content = readFile(pomFile)

        echo "Injecting ParentBlock: ${parentBlock}"

        // Check if parent already exists to avoid double injection
        if (!content.contains("<parent>")) {
            echo "Injecting Parent POM reference..."
            // Regex looks for the <project> tag and replaces it with itself + the parent block
            // The (?s) allows the dot to match newlines if necessary
            content = content.replaceFirst(/(<project[^>]*>)/, "\$1${parentBlock}")
            writeFile(file: pomFile, text: content)
            echo "Parent POM reference injected..."
        } else {
            echo "Parent tag already exists in pom.xml. Skipping."
        }

        sh '''
            # cd in to broker mule app in target folder as it might be in root folder
            cd target/broker-mule-app 

            ls -latr

            # Recreate the broker mule app Jar
            mvn clean package

            # move to target folder of broker-mule-app 
            cd target
        '''

        echo "Setting vars for anypoint-cli-v4"
        sh '''
                npx anypoint-cli-v4 conf client_id $AP_CA_CLIENT_ID
                npx anypoint-cli-v4 conf client_secret $AP_CA_CLIENT_SECRET
                npx anypoint-cli-v4 conf organization ${ORG_ID}
        '''

       echo "Publishing app asset to Exchange"

       def asset_name = "${ASSET_ID}-app"
       echo "asset_name: $asset_name"
       
       def APP_JAR_NAME = "${ASSET_ID}-${APP_VERSION}-mule-application.jar"
       echo "APP_JAR_NAME: $APP_JAR_NAME"

       def APP_JAR_LOCATION = "${BROKER_APP_LOCATION}/target/${APP_JAR_NAME}"
       echo "APP_JAR_LOCATION: $APP_JAR_LOCATION"

       // ASSET UPLOAD COMMENTED: TODO: TO ADD LATER
       sh """
            anypoint-cli-v4 exchange:asset:upload ${ORG_ID}/${asset_name}/${APP_VERSION} \
            --files='{"mule-application.jar": "${APP_JAR_LOCATION}"}' \
            --type='app' \
            --name=${asset_name}
         """
    
        echo "App Jar with dependencies published to Exchange: $APP_JAR_NAME" 
    }
}


void modifyDeploySteps() {
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
        string(credentialsId: "${ENV}-splunk_token", variable: 'SPLUNK_TOKEN'),        
        //string(credentialsId: "${ENV}-openai-key", variable: 'API_KEY'),
        usernamePassword(credentialsId: "${ENV}-anypointmq.credential", usernameVariable: 'ANYPOINTMQ_CLIENT_ID', passwordVariable: 'ANYPOINTMQ_CLIENT_SECRET'),
        usernamePassword(credentialsId: "${ENV}-anypointplatform-connected-app", usernameVariable: 'AP_CA_CLIENT_ID', passwordVariable: 'AP_CA_CLIENT_SECRET')
    ]) {

        echo "Setting vars for anypoint-cli-v4"
        sh '''
                npx anypoint-cli-v4 conf client_id $AP_CA_CLIENT_ID
                npx anypoint-cli-v4 conf client_secret $AP_CA_CLIENT_SECRET
                npx anypoint-cli-v4 conf organization ${ORG_ID}
        '''

        def asset_name = "${ASSET_ID}-app"
        echo "asset_name: $asset_name"

        echo "Env is ${ENV}, getting app list"
        def rawAppListJson = sh(
            script: "anypoint-cli-v4 runtime-mgr:application:list --environment ${ENV} --output json",
            returnStdout: true
        ).trim()

        echo "rawAppListJson: $rawAppListJson"
        // 3. Parse and Extract the ID
        def appJson = readJSON text: rawAppListJson
        echo "appJson: $appJson"
        // Use .find to locate the object where name matches
        echo "Finding Asset ${ASSET_ID} in response"
        def targetApp = appJson.find { it.name == "${ASSET_ID}" }
        
        def deploymentId = ''
        if (targetApp) {
            deploymentId = targetApp.id
            echo "Successfully extracted Deployment ID: ${deploymentId} for app: ${ASSET_ID}"
        } else {
            error "Could not find deployment with name: ${ASSET_ID}"
        }

        echo "Getting Properties for deploymentId ${deploymentId}"
        def rawAppPropsJson = sh(
            script: "anypoint-cli-v4 runtime-mgr:application:describe --environment ${ENV} ${deploymentId} --output json",
            returnStdout: true
        ).trim()

        echo "rawAppPropsJson: $rawAppPropsJson"
        // 3. Parse and Extract the ID
        def appPropsJson = readJSON text: rawAppPropsJson
        echo "appPropsJson: $appPropsJson"

        echo "Extract properties in response"
        // Note: Since the key contains dots, we must use quotes: json.path.'key.with.dots'
        def props = appPropsJson.application.configuration.'mule.agent.application.properties.service'.properties
        //def secureProps = appPropsJson.application.configuration.'mule.agent.application.properties.service'.secureProperties

        // 4. Access specific values or loop through them
        echo "Found ${props.size()} properties."
        
        echo "Printing properties."
        // Optional: Print all properties to the console
        props.each { key, value ->
            echo "${key} = ${value}"
        }

        // We modify the 'props' object you extracted in the previous step
        props.remove('itsopenai.apiKey')
        props.remove('splunk.token')
        props.remove('anypointmq.clientid')
        props.remove('anypointmq.clientsecret')

        def formattedPropsArgs = props.collect { key, value ->
            "--property ${key}:${value}"
        }.join(" ")

        echo "Generated Properties String: ${formattedPropsArgs}"

        echo "Modifying deployment for appId ${deploymentId}, asset_name ${asset_name} and ${APP_VERSION}"
        //echo "with additional property args: ${propertyArgs}"
        def rawAppModifyJson = sh(
            script: "anypoint-cli-v4 runtime-mgr:application:modify ${deploymentId} \
                    --groupId ${ORG_ID} \
                    --artifactId ${asset_name} \
                    --assetVersion ${APP_VERSION} \
                    --environment ${ENV} \
                    ${formattedPropsArgs} \
                    --property app.name:${APP_NAME_DEPLOYMENT} \
                    --property batch_size_count:1 \
                    --property batch_interval:0 \
                    --property batch_size_bytes:1024 \
                    --property environment:${ENV} \
                    --secureProperty splunk.token:${SPLUNK_TOKEN} \
                    --secureProperty anypointmq.clientid:${ANYPOINTMQ_CLIENT_ID} \
                    --secureProperty anypointmq.clientsecret:${ANYPOINTMQ_CLIENT_SECRET} \
                    --secureProperty openai.apiKey:XXXX \
                    --output json",
            returnStdout: true
        ).trim()

        echo "Redeploy command response: ${rawAppModifyJson}"
    }
}

 
pipeline {
    agent any
    environment {
        //APP_NAME = 'its-agentfabrics-prototype'
        APP_NAME = 'ITS-AGENTFABRICS-PROTOTYPE'
        ORG_ID = 'a19065cb-4dd4-4916-9401-bb64b169742c'
        ASSET_ID = ''
        APP_VERSION = ''

        //APP_LOCATION = "/home/cdelivery/.jenkins/jobs/${APP_NAME}"
        APP_LOCATION = "/var/jenkins_home/workspace"
        min = 8081
        max = 40000
        random_port = "${(int)(Math.random() * (max - min) + 1) + min}"

        // Node.js and npm configuration
        NODE_VERSION = 'Node-25'
        NPM_REGISTRY = 'https://registry.npmjs.org/'

        // This adds the current workspace directory to the PATH 
        // so 'jq' can be found like a system command
        PATH = "${WORKSPACE}:${env.PATH}"
    }

    tools {
        nodejs "${NODE_VERSION}"
        maven 'mvn'
    }

    stages {
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
                    
                    #Install anypoint-cli-v4
                    npm install -g anypoint-cli-v4

                    npx anypoint-cli-v4 plugins:install anypoint-cli-exchange-plugin

                    npx anypoint-cli-v4 --help
        
                '''
            }
        }

        stage('Install jq plugin'){
            steps {
                echo 'Installing jq'

                sh """
                    curl -L https://github.com/jqlang/jq/releases/latest/download/jq-linux64 -o jq
                    chmod +x jq
                    ./jq -r '.assetId' exchange.json
                """

                // def resultId = sh(
                //     script: """
                //         ./jq -r '.assetId' exchange.json
                //     """,
                //     returnStdout: true
                // ).trim()

                // echo "resultId: ${resultId}"

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
                    env.GIT_BRANCH == "DEV"
                }
            }
            environment {
                //ENV = 'dev'
                //ENV = 'DEV'
                ENV = 'Dev'
                target_space = 'HarishNewPS1'
                ENV_ID = '2e16bf69-7e76-4387-ad51-1fdd939b4d86'
            }
            steps {
                sh 'cp -f resources/dev/exchange.json ./'
                //buildSteps()
                buildSteps2()
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
                target_space = 'HarishNewPS1'
                ENV_ID = '50f2dbae-fde6-4b6d-b75b-3fcd027dcf9d'
            }
            steps {
                sh 'cp -f resources/stage/exchange.json ./'
               //buildSteps()
                buildSteps2()
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
                target_space = 'TEST-PROD'
                ENV_ID = '8b4f2d7b-77fd-42af-ad4f-79b1bcae6947'
            }
            steps {
                sh 'cp -f resources/prod/exchange.json ./'
                //buildSteps()
                buildSteps2()
            }
        }
        stage('Deploy to Dev') {
            when {
                expression {
                    env.GIT_BRANCH == "DEV"
                }
            }
            environment {
                //ENV = 'dev'
                //ENV = 'DEV'
                ENV = 'Dev'
                APP_NAME_DEPLOYMENT = "${ENV}-${APP_NAME}"
                //BROKER_APP_LOCATION = "${APP_LOCATION}/branches/${ENV}/workspace/target/broker-mule-app"
                BROKER_APP_LOCATION = "${APP_LOCATION}/${APP_NAME}_DEV/target/broker-mule-app"
                target_space = 'HarishNewPS1'
            }
            steps {
                deploySteps()
                injectDependencyAndUploadJar()
                modifyDeploySteps()
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
                BROKER_APP_LOCATION = "${APP_LOCATION}/branches/${ENV}/workspace/target/broker-mule-app"
                target_space = 'HarishNewPS1'
            }
            steps {
                deploySteps()
                modifyDeploySteps()
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
                BROKER_APP_LOCATION = "${APP_LOCATION}/branches/${ENV}/workspace/target/broker-mule-app"
                target_space = 'TEST-PROD'
            }
            steps {
                deploySteps()
                modifyDeploySteps()
            }
        }
    }
 }