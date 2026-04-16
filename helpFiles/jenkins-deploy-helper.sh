#!/bin/bash

set -e

# Jenkins Deploy Helper for Agent Network
# This script generates the complete anypoint-cli command with all properties from exchange.json

generate_anypoint_command() {
    local environment="$1"
    local exchange_file=""
    
    # Determine which exchange.json file to use
    if [ -n "$environment" ] && [ -f "resources/$environment/exchange.json" ]; then
        exchange_file="resources/$environment/exchange.json"
    elif [ -f "exchange.json" ]; then
        exchange_file="exchange.json"
    else
        echo "Error: No exchange.json file found" >&2
        exit 1
    fi
    
    echo "Using exchange file: $exchange_file" >&2
    
    local property_args=""
    
    # Parse variables and build property arguments
    if command -v jq >/dev/null 2>&1; then
        # Get all variable names
        variable_names=$(jq -r '.metadata.variables | keys[]' "$exchange_file" 2>/dev/null || echo "")
        
        while IFS= read -r var_name; do
            if [ -n "$var_name" ]; then
                # Get all properties for this variable
                prop_names=$(jq -r ".metadata.variables[\"$var_name\"] | keys[]" "$exchange_file" 2>/dev/null || echo "")
                
                while IFS= read -r prop_name; do
                    if [ -n "$prop_name" ]; then
                        # Get the default value
                        default_value=$(jq -r ".metadata.variables[\"$var_name\"][\"$prop_name\"].default // empty" "$exchange_file" 2>/dev/null)
                        
                        # Skip empty or null values (except for secret fields which might be empty)
                        if [ -n "$default_value" ] && [ "$default_value" != "null" ]; then
                            property_key="${var_name}.${prop_name}"
                            property_args="$property_args --property \"${property_key}=${default_value}\""
                        fi
                    fi
                done <<< "$prop_names"
            fi
        done <<< "$variable_names"
    else
        echo "Error: jq command not found. Please install jq to parse JSON." >&2
        exit 1
    fi
    
    # Build the complete command
    local base_command="anypoint-cli agent-fabric-plugin agent-network project deploy"
    local full_command="$base_command $property_args"
    
    echo "$full_command"
}

# Main execution
ENVIRONMENT="$1"
if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment>" >&2
    echo "Example: $0 dev" >&2
    exit 1
fi

echo "Generating Anypoint CLI command for environment: $ENVIRONMENT" >&2
COMMAND=$(generate_anypoint_command "$ENVIRONMENT")

echo "" >&2
echo "Generated command:" >&2
echo "$COMMAND" >&2
echo "" >&2

# Output the command (this can be captured by Jenkins)
echo "$COMMAND"