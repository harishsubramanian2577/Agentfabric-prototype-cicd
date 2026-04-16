// Add this stage to your existing Jenkinsfile

stage('Deploy Agent Network') {
    steps {
        script {
            // Generate the complete deployment command with all properties from exchange.json
            def deployCommand = sh(
                script: "./jenkins-deploy-helper.sh ${params.ENVIRONMENT ?: 'dev'}",
                returnStdout: true
            ).trim()
            
            echo "Generated deployment command:"
            echo deployCommand
            
            // Execute the deployment with all properties
            sh deployCommand
        }
    }
}

// Alternative: If you prefer inline parsing (without helper script)
stage('Deploy Agent Network - Inline') {
    steps {
        script {
            def exchangeFile = "resources/${params.ENVIRONMENT ?: 'dev'}/exchange.json"
            if (!fileExists(exchangeFile)) {
                exchangeFile = "exchange.json"
            }
            
            def propertyArgs = sh(
                script: """
                    jq -r '.metadata.variables | to_entries[] | . as \$var | .value | to_entries[] | "--property \\"\\\(.key | \$var.key + "." + .)=\\\(.value.default // empty)\\"" | select(. != "--property \\"\\"")' ${exchangeFile} | tr '\\n' ' '
                """,
                returnStdout: true
            ).trim()
            
            def deployCommand = "anypoint-cli agent-fabric-plugin agent-network project deploy ${propertyArgs}"
            
            echo "Executing: ${deployCommand}"
            sh deployCommand
        }
    }
}