const fs = require('fs');
const path = require('path');

// Get the CAPACITOR_ROOT_DIR environment variable
const capacitorRootDir = process.env.CAPACITOR_ROOT_DIR;

if (!capacitorRootDir) {
  console.error('ERROR: CAPACITOR_ROOT_DIR environment variable is not set');
  process.exit(1);
}

// Construct the path to capacitor.plugins.json
const pluginsJsonPath = path.join(capacitorRootDir, 'android/app/src/main/assets/capacitor.plugins.json');

// Check if the file exists
if (!fs.existsSync(pluginsJsonPath)) {
  console.error(`ERROR: capacitor.plugins.json file not found at: ${pluginsJsonPath}`);
  process.exit(1);
}

try {
  // Read the current file content to preserve formatting
  const fileContent = fs.readFileSync(pluginsJsonPath, 'utf8');

  // Parse the JSON
  const plugins = JSON.parse(fileContent);

  // Check if it's an array
  if (!Array.isArray(plugins)) {
    console.error('ERROR: capacitor.plugins.json does not contain a valid array');
    process.exit(1);
  }

  // Find the index of the @capgo/capacitor-twilio-voice plugin
  const twilioPluginIndex = plugins.findIndex((plugin) => plugin.pkg === '@capgo/capacitor-twilio-voice');

  if (twilioPluginIndex === -1) {
    console.log('INFO: @capgo/capacitor-twilio-voice plugin not found in capacitor.plugins.json');
    process.exit(0);
  }

  // Remove the plugin
  plugins.splice(twilioPluginIndex, 1);

  // Determine the indentation style from the original file
  const lines = fileContent.split('\n');
  let indentChar = '\t'; // Default to tabs
  let indentSize = 1;

  // Look for indentation in the file
  for (const line of lines) {
    if (line.match(/^\s+/)) {
      const match = line.match(/^(\s+)/);
      if (match) {
        const indent = match[1];
        if (indent.includes('\t')) {
          indentChar = '\t';
          indentSize = 1;
        } else {
          indentChar = ' ';
          indentSize = indent.length;
        }
        break;
      }
    }
  }

  // Convert back to JSON with proper formatting
  const jsonString = JSON.stringify(plugins, null, indentChar.repeat(indentSize));

  // Write the updated JSON back to the file
  fs.writeFileSync(pluginsJsonPath, jsonString + '\n', 'utf8');

  console.log('SUCCESS: @capgo/capacitor-twilio-voice plugin removed from capacitor.plugins.json');
} catch (error) {
  console.error(`ERROR: Failed to process capacitor.plugins.json: ${error.message}`);
  process.exit(1);
}
