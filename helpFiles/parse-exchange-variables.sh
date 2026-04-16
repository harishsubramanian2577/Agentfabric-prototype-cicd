#!/bin/bash

set -e  # Exit on any error

# Function to parse exchange.json variables and generate --property arguments
generate_property_args() {
    local exchange_file="$1"
    local property_args=""
    
    if [ ! -f "$exchange_file" ]; then
        echo "Error: Exchange file $exchange_file not found" >&2
        exit 1
    fi
    
    echo "Parsing variables from: $exchange_file" >&2
    
    # Extract all variable names from the metadata.variables section
    variable_names=$(jq -r '.metadata.variables | keys[]' "$exchange_file" 2>/dev/null || echo "")
    
    if [ -z "$variable_names" ]; then
        echo "No variables found in exchange.json" >&2
        return 0
    fi
    
    # Process each variable
    while IFS= read -r var_name; do
        if [ -n "$var_name" ]; then
            echo "Processing variable: $var_name" >&2
            
            # Get all property names for this variable (url, apiKey, modelName, etc.)
            prop_names=$(jq -r ".metadata.variables[\"$var_name\"] | keys[]" "$exchange_file" 2>/dev/null || echo "")
            
            while IFS= read -r prop_name; do
                if [ -n "$prop_name" ]; then
                    # Get the default value for this property
                    default_value=$(jq -r ".metadata.variables[\"$var_name\"][\"$prop_name\"].default // empty" "$exchange_file" 2>/dev/null)
                    
                    if [ -n "$default_value" ] && [ "$default_value" != "null" ]; then
                        # Format as variable-name.property-name=value
                        property_key="${var_name}.${prop_name}"
                        property_args="$property_args --property \"${property_key}=${default_value}\""
                        echo "Added property: ${property_key}=${default_value}" >&2
                    fi
                fi
            done <<< "$prop_names"
        fi
    done <<< "$variable_names"
    
    echo "$property_args"
}

# Main execution
ENVIRONMENT=${1:-dev}
EXCHANGE_FILE="resources/$ENVIRONMENT/exchange.json"

# If no environment-specific file, use root exchange.json
if [ ! -f "$EXCHANGE_FILE" ]; then
    EXCHANGE_FILE="exchange.json"
fi

echo "Using exchange file: $EXCHANGE_FILE"

# Generate property arguments
PROPERTY_ARGS=$(generate_property_args "$EXCHANGE_FILE")

# Output the complete command
echo ""
echo "Generated Anypoint CLI command with properties:"
echo "anypoint-cli agent-fabric-plugin agent-network project deploy $PROPERTY_ARGS"
echo ""

# Also output just the property arguments for use in Jenkins
echo "Property arguments only:"
echo "$PROPERTY_ARGS"