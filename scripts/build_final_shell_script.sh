#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Step A: Read the JavaScript file and convert it to base64
echo "Step A: Converting JavaScript file to base64..."
JS_FILE="$SCRIPT_DIR/remove_twilio_voice_from_capacitor_plugins.js"

if [ ! -f "$JS_FILE" ]; then
    echo "ERROR: JavaScript file not found at: $JS_FILE"
    exit 1
fi

JS_BASE64=$(base64 -i "$JS_FILE")
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to encode JavaScript file to base64"
    exit 1
fi

echo "✓ JavaScript file encoded to base64"

# Step B: Read the shell script template and replace the placeholder
echo "Step B: Replacing placeholder in shell script template..."
SHELL_TEMPLATE="$SCRIPT_DIR/find_node_and_exec_js_script.sh"

if [ ! -f "$SHELL_TEMPLATE" ]; then
    echo "ERROR: Shell script template not found at: $SHELL_TEMPLATE"
    exit 1
fi

# Replace the placeholder with the base64 encoded JavaScript
MODIFIED_SHELL_SCRIPT=$(sed "s/#JS_SCRIPT_HERE_PLACEHOLDER_BASE64#/$JS_BASE64/g" "$SHELL_TEMPLATE")
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to replace placeholder in shell script"
    exit 1
fi

echo "✓ Placeholder replaced with base64 JavaScript"

# Step C: Base64 encode the modified shell script
echo "Step C: Encoding modified shell script to base64..."
SHELL_SCRIPT_BASE64=$(echo "$MODIFIED_SHELL_SCRIPT" | base64)
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to encode modified shell script to base64"
    exit 1
fi

echo "✓ Modified shell script encoded to base64"

# Step D: Generate the final command and update package.json
echo "Step D: Generating final command and updating package.json..."

# Create the final command that will decode and execute the script
FINAL_COMMAND="echo '$SHELL_SCRIPT_BASE64' | base64 -d | bash"

# Path to the package.json file (go up one directory from scripts)
PACKAGE_JSON_PATH="$SCRIPT_DIR/../package.json"

if [ ! -f "$PACKAGE_JSON_PATH" ]; then
    echo "ERROR: package.json not found at: $PACKAGE_JSON_PATH"
    exit 1
fi

# Check if Node.js is available for JSON manipulation
if ! command -v node &> /dev/null; then
    echo "ERROR: Node.js is required to update package.json"
    exit 1
fi

# Create a temporary file with the command to avoid shell escaping issues
TEMP_COMMAND_FILE=$(mktemp /tmp/command_XXXXXX.txt)
echo "$FINAL_COMMAND" > "$TEMP_COMMAND_FILE"

# Update package.json using Node.js
node -e "
const fs = require('fs');
const packageJsonPath = '$PACKAGE_JSON_PATH';
const commandFile = '$TEMP_COMMAND_FILE';

try {
    const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
    const command = fs.readFileSync(commandFile, 'utf8').trim();
    
    // Ensure scripts object exists
    if (!packageJson.scripts) {
        packageJson.scripts = {};
    }
    
    // Add or update the capacitor:sync:after script
    packageJson.scripts['capacitor:sync:after'] = command;
    
    // Write back to file with proper formatting (2 spaces)
    fs.writeFileSync(packageJsonPath, JSON.stringify(packageJson, null, 2) + '\n');
    
    console.log('✓ package.json updated successfully');
} catch (error) {
    console.error('ERROR: Failed to update package.json:', error.message);
    process.exit(1);
}
"

# Clean up the temporary command file
rm -f "$TEMP_COMMAND_FILE"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to update package.json"
    exit 1
fi

# Copy to clipboard (macOS) as well
echo "$FINAL_COMMAND" | pbcopy
if [ $? -ne 0 ]; then
    echo "WARNING: Failed to copy command to clipboard (but package.json was updated)"
else
    echo "✓ Final command also copied to clipboard!"
fi

echo ""
echo "SUCCESS: Build completed successfully!"
echo "----------------------------------------"
echo "✓ JavaScript converted to base64"
echo "✓ Shell script template updated"
echo "✓ Complete script encoded to base64"
echo "✓ package.json updated with 'capacitor:sync:after' script"
echo "✓ Command copied to clipboard"
echo "----------------------------------------"
echo ""
echo "The 'capacitor:sync:after' script in package.json now contains:"
echo "$FINAL_COMMAND"
echo ""
echo "This script will automatically run after 'npx cap sync' and will:"
echo "  1. Decode and execute the embedded shell script"
echo "  2. The shell script will decode and run the JavaScript"
echo "  3. The JavaScript will remove @capgo/capacitor-twilio-voice from capacitor.plugins.json"

