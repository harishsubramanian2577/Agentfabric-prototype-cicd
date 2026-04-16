# Commands to Copy log4j2.xml

## Option 1: If target/broker-mule-app directory already exists
```bash
# Copy log4j2.xml from resources/dev to target/broker-mule-app/src/main/resources/
cp resources/dev/log4j2.xml target/broker-mule-app/src/main/resources/
```

## Option 2: If you need to create the directory structure first
```bash
# Create the directory structure if it doesn't exist
mkdir -p target/broker-mule-app/src/main/resources/

# Then copy the file
cp resources/dev/log4j2.xml target/broker-mule-app/src/main/resources/
```

## Option 3: One-liner with directory creation
```bash
# Create directory and copy in one command
mkdir -p target/broker-mule-app/src/main/resources/ && cp resources/dev/log4j2.xml target/broker-mule-app/src/main/resources/
```

## Option 4: For Jenkins pipeline (Groovy)
```groovy
sh '''
    mkdir -p target/broker-mule-app/src/main/resources/
    cp resources/dev/log4j2.xml target/broker-mule-app/src/main/resources/
'''
```

## Option 5: Environment-aware copy (for different environments)
```bash
# For different environments (dev, stage, prod)
ENVIRONMENT=${1:-dev}
mkdir -p target/broker-mule-app/src/main/resources/
cp resources/$ENVIRONMENT/log4j2.xml target/broker-mule-app/src/main/resources/
```

## Verification
After copying, verify the file was copied correctly:
```bash
# Check if file exists
ls -la target/broker-mule-app/src/main/resources/log4j2.xml

# Compare files to ensure they're identical
diff resources/dev/log4j2.xml target/broker-mule-app/src/main/resources/log4j2.xml