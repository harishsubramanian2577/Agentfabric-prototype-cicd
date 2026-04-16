#!/bin/bash

# One-liner approach for Jenkins or command line
# This generates the property arguments directly from exchange.json

ENVIRONMENT=${1:-dev}
EXCHANGE_FILE="resources/$ENVIRONMENT/exchange.json"

# If environment-specific file doesn't exist, use root exchange.json
if [ ! -f "$EXCHANGE_FILE" ]; then
    EXCHANGE_FILE="exchange.json"
fi

# One-liner to extract all variables and their default values as --property arguments
PROPERTY_ARGS=$(jq -r '
    .metadata.variables | 
    to_entries[] | 
    . as $var | 
    .value | 
    to_entries[] | 
    select(.value.default != null and .value.default != "") | 
    "--property \"" + ($var.key + "." + .key) + "=" + (.value.default | tostring) + "\""
' "$EXCHANGE_FILE" | tr '\n' ' ')

echo "Generated command:"
echo "anypoint-cli agent-fabric-plugin agent-network project deploy $PROPERTY_ARGS"

# Uncomment to execute:
# anypoint-cli agent-fabric-plugin agent-network project deploy $PROPERTY_ARGS