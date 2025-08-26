#!/bin/bash

# Base64 encoded JavaScript script
JS_script="#JS_SCRIPT_HERE_PLACEHOLDER_BASE64#"

# Check if platform is Android, skip for other platforms
if [ "$CAPACITOR_PLATFORM_NAME" != "android" ]; then
    echo "Platform not equal to android, skipping twilio voice script"
    exit 0
fi

# Check if node exists
if ! command -v node &> /dev/null; then
    echo "ERROR: Node.js is not installed or not found in PATH"
    exit 1
fi

# Check and display Node.js version
NODE_VERSION=$(node --version)
echo "INFO: Using Node.js version: $NODE_VERSION"

# Create a temporary file for the JavaScript script
# Clean up any existing temp files first
rm -f /tmp/capacitor_script_*.js 2>/dev/null || true

# Create a unique temporary file
TMP_JS_FILE=$(mktemp /tmp/capacitor_script_XXXXXX.js)

# Decode the base64 script and write to temporary file
echo "$JS_script" | base64 -d > "$TMP_JS_FILE"

# Check if decoding was successful
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to decode base64 JavaScript script"
    rm -f "$TMP_JS_FILE"
    exit 1
fi

# Execute the JavaScript file and capture output
echo "INFO: Executing JavaScript script..."
node "$TMP_JS_FILE"
SCRIPT_EXIT_CODE=$?

# Print the exit code for reference
echo "INFO: JavaScript script exited with code: $SCRIPT_EXIT_CODE"

# Clean up: remove the temporary file
rm -f "$TMP_JS_FILE"

# Exit with the same code as the JavaScript script
exit $SCRIPT_EXIT_CODE
