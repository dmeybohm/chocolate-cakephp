# Translation Testing Guide

This guide explains how to test the Chocolate CakePHP plugin translations on your system.

## Quick Start (Recommended Method)

**No system locale installation required!** Use the VM options method:

```bash
./test-translations-vmopts.sh
```

This script will:
1. Automatically find your IntelliJ IDEA configuration
2. Update VM options to use your selected language
3. Back up your current settings
4. Provide instructions for reverting changes

## Testing Steps

### 1. Build the Plugin

```bash
./gradlew buildPlugin
```

This creates: `build/distributions/Chocolate-CakePHP-1.0.0.zip`

### 2. Configure Language (Using Script)

```bash
./test-translations-vmopts.sh
```

Select a language (1-8) and restart IntelliJ IDEA.

### 3. Install the Plugin

1. Open IntelliJ IDEA
2. Go to **Settings** → **Plugins**
3. Click the **⚙️** (gear icon) → **Install Plugin from Disk...**
4. Navigate to `build/distributions/Chocolate-CakePHP-1.0.0.zip`
5. Click **OK** and restart when prompted

### 4. Verify Translations

#### Settings Pages

Navigate to **Settings** → **Languages & Frameworks** → **PHP** → **Chocolate CakePHP**

Check these elements are translated:
- Page title: "Chocolate CakePHP"
- Section headers: "CakePHP 3+", "CakePHP 2"
- Checkboxes: "Auto-detect CakePHP 3+", "Force enable CakePHP 3+", etc.
- Labels: "App Namespace", "App Directory", "Template Extension"
- Help text below each field
- "Default" buttons

Sub-pages to check:
- **Plugins** tab - Title and help text
- **Data Views** tab - Title and help text

#### Action Menus

In a CakePHP project:
1. Right-click in a controller file
2. Look for "Toggle Controller / View" action (should be translated)

#### Dialog Boxes

Test these scenarios:
1. Try to create a view file
   - Dialog title should be translated
   - Field labels should be translated
2. Try to create a duplicate file
   - Error message should be translated

#### Navigation Popups

When navigating between controllers and views:
- Popup title should be "Select Target to Navigate" (translated)
- Action items should be translated

## Manual VM Options Configuration

If the script doesn't work, manually edit your VM options:

### Find Your VM Options File

Typically located at:
- `~/.config/JetBrains/IntelliJIdea*/idea64.vmopts`

Or via IntelliJ: **Help** → **Edit Custom VM Options...**

### Add Language Override

Add these lines (example for Spanish):

```
-Duser.language=es
-Duser.country=ES
```

### Language Codes

| Language | Language Code | Country Code |
|----------|--------------|--------------|
| Spanish | es | ES |
| French | fr | FR |
| German | de | DE |
| Japanese | ja | JP |
| Portuguese | pt | PT |
| Danish | da | DK |
| Korean | ko | KR |
| Chinese | zh | CN |
| English | en | US |

### Restart IntelliJ

Changes take effect after restart.

## Alternative Method: System Locales (Optional)

If you want to install system locales for other purposes:

### Install Locales on Kubuntu

```bash
# Install locale support
sudo apt-get install language-pack-es   # Spanish
sudo apt-get install language-pack-fr   # French
sudo apt-get install language-pack-de   # German
sudo apt-get install language-pack-ja   # Japanese
sudo apt-get install language-pack-pt   # Portuguese
sudo apt-get install language-pack-da   # Danish
sudo apt-get install language-pack-ko   # Korean
sudo apt-get install language-pack-zh-hans  # Chinese (Simplified)

# Reconfigure locales
sudo dpkg-reconfigure locales
```

### Launch with Environment Variables

```bash
LANG=es_ES.UTF-8 /path/to/idea.sh     # Spanish
LANG=fr_FR.UTF-8 /path/to/idea.sh     # French
LANG=de_DE.UTF-8 /path/to/idea.sh     # German
LANG=ja_JP.UTF-8 /path/to/idea.sh     # Japanese
```

## Reverting to English

### Option 1: Use the Script

```bash
./test-translations-vmopts.sh
# Choose option 0 or 9
```

### Option 2: Manually Remove Lines

Edit your VM options file and remove:
```
-Duser.language=xx
-Duser.country=XX
```

Then restart IntelliJ IDEA.

### Option 3: Restore Backup

The script creates backups like `idea64.vmopts.backup.1234567890`

```bash
cp ~/.config/JetBrains/IntelliJIdea*/idea64.vmopts.backup.* \
   ~/.config/JetBrains/IntelliJIdea*/idea64.vmopts
```

## Troubleshooting

### Translations Not Showing

1. **Verify plugin is installed**: Settings → Plugins → search "Chocolate CakePHP"
2. **Check VM options are set**: Help → About → Copy to clipboard → look for `user.language`
3. **Restart was performed**: Language changes require a full restart
4. **Correct locale code**: Double-check language/country codes match
5. **Resource bundle file exists**: Check `build/distributions/` ZIP contains `messages/ChocolateCakePHPBundle_XX.properties`

### Plugin Not Loading

1. **Check IntelliJ logs**: Help → Show Log in Files
2. **Verify build succeeded**: `./gradlew buildPlugin` completes without errors
3. **Check compatibility**: Plugin requires IntelliJ build 233.15026+

### Partial Translations

Some IntelliJ UI elements (like main menus) are controlled by IntelliJ's own translations, not the plugin. Only plugin-specific text will be translated.

## What Gets Translated

✅ **Plugin settings pages** - All form labels, help text, section titles
✅ **Action menus** - Plugin action names and descriptions
✅ **Dialog boxes** - Titles, field labels, buttons
✅ **Error messages** - All plugin error messages
✅ **Navigation popups** - Popup titles and menu items
✅ **Plugin metadata** - Name and description in plugin manager

❌ **IntelliJ UI** - Main menu, toolbars (controlled by JetBrains)
❌ **System dialogs** - File choosers, etc. (OS-level)

## Reporting Issues

If you find translation issues:

1. Note the language and exact location
2. Take a screenshot if possible
3. Check what the English version says
4. Report in GitHub issues with:
   - Language code
   - Location in UI
   - Expected translation
   - Actual text shown
