// Jenkins Pipeline Stage Example - Add this to your existing Jenkinsfile

stage('Deploy Agent Network with Properties') {
    steps {
        script {
            // Read and parse exchange.json to generate property arguments
            def exchangeFile = "resources/${params.ENVIRONMENT}/exchange.json"
            if (!fileExists(exchangeFile)) {
                exchangeFile = "exchange.json"
            }
            
            // Generate property arguments using shell script
            def propertyArgs = sh(
                script: """
                    #!/bin/bash
                    set -e
                    
                    EXCHANGE_FILE="$exchangeFile"
                    PROPERTY_ARGS=""
                    
                    # Parse variables from exchange.json
                    if [ -f "\$EXCHANGE_FILE" ]; then
                        # Get all variable names
                        VARIABLE_NAMES=\$(jq -r '.metadata.variables | keys[]' "\$EXCHANGE_FILE" 2>/dev/null || echo "")
                        
                        while IFS= read -r VAR_NAME; do
                            if [ -n "\$VAR_NAME" ]; then
                                # Get all properties for this variable
                                PROP_NAMES=\$(jq -r ".metadata.variables[\\\"\$VAR_NAME\\\"] | keys[]" "\$EXCHANGE_FILE" 2>/dev/null || echo "")
                                
                                while IFS= read -r PROP_NAME; do
                                    if [ -n "\$PROP_NAME" ]; then
                                        # Get the default value
                                        DEFAULT_VALUE=\$(jq -r ".metadata.variables[\\\"\$VAR_NAME\\\"][\\\"\$PROP_NAME\\\"].default // empty" "\$EXCHANGE_FILE" 2>/dev/null)
                                        
                                        if [ -n "\$DEFAULT_VALUE" ] && [ "\$DEFAULT_VALUE" != "null" ]; then
                                            PROPERTY_KEY="\${VAR_NAME}.\${PROP_NAME}"
                                            PROPERTY_ARGS="\$PROPERTY_ARGS --property \\\"\${PROPERTY_KEY}=\${DEFAULT_VALUE}\\\""
                                        fi
                                    fi
                                done <<< "\$PROP_NAMES"
                            fi
                        done <<< "\$VARIABLE_NAMES"
                    fi
                    
                    echo "\$PROPERTY_ARGS"
                """,
                returnStdout: true
            ).trim()
            
            // Execute the deployment command with all properties
            def deployCommand = "anypoint-cli agent-fabric-plugin agent-network project deploy ${propertyArgs}"
            
            echo "Executing deployment with properties:"
            echo deployCommand
            
            sh deployCommand
        }
    }
}