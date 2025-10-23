#!/bin/bash
# Test Chocolate CakePHP Plugin Translations using VM Options
# This method DOES NOT require installing system locales

set -e

JETBRAINS_CONFIG="$HOME/.config/JetBrains"
VMOPTS_FILE=""

# Find the most recent PhpStorm configuration directory
if [ -d "$JETBRAINS_CONFIG" ]; then
    PHPSTORM_DIR=$(ls -dt "$JETBRAINS_CONFIG"/PhpStorm* 2>/dev/null | head -1)
    if [ -n "$PHPSTORM_DIR" ]; then
        VMOPTS_FILE="$PHPSTORM_DIR/phpstorm64.vmopts"
    fi
fi

if [ -z "$VMOPTS_FILE" ]; then
    echo "Could not find PhpStorm configuration directory."
    echo "Please manually edit your phpstorm64.vmopts file and add:"
    echo ""
    echo "  -Duser.language=<lang> -Duser.country=<COUNTRY>"
    echo ""
    echo "Example: -Duser.language=es -Duser.country=ES"
    echo ""
    echo "You can also edit it via: Help → Edit Custom VM Options..."
    exit 1
fi

# Backup current vmopts if it exists
if [ -f "$VMOPTS_FILE" ]; then
    cp "$VMOPTS_FILE" "$VMOPTS_FILE.backup.$(date +%s)"
    echo "Backed up current VM options to: $VMOPTS_FILE.backup.*"
fi

# Create vmopts directory if it doesn't exist
mkdir -p "$(dirname "$VMOPTS_FILE")"

echo ""
echo "Chocolate CakePHP Translation Tester"
echo "===================================="
echo ""
echo "Select a language to test:"
echo ""
echo "  1. Spanish (Español)"
echo "  2. French (Français)"
echo "  3. German (Deutsch)"
echo "  4. Japanese (日本語)"
echo "  5. Portuguese (Português)"
echo "  6. Danish (Dansk)"
echo "  7. Korean (한국어)"
echo "  8. Chinese (中文)"
echo "  9. English (default)"
echo "  0. Remove language override"
echo ""
read -p "Enter your choice (0-9): " choice

# Remove any existing language settings
if [ -f "$VMOPTS_FILE" ]; then
    sed -i '/^-Duser\.language=/d' "$VMOPTS_FILE"
    sed -i '/^-Duser\.country=/d' "$VMOPTS_FILE"
fi

case $choice in
    1)
        echo "-Duser.language=es" >> "$VMOPTS_FILE"
        echo "-Duser.country=ES" >> "$VMOPTS_FILE"
        LANG_NAME="Spanish"
        ;;
    2)
        echo "-Duser.language=fr" >> "$VMOPTS_FILE"
        echo "-Duser.country=FR" >> "$VMOPTS_FILE"
        LANG_NAME="French"
        ;;
    3)
        echo "-Duser.language=de" >> "$VMOPTS_FILE"
        echo "-Duser.country=DE" >> "$VMOPTS_FILE"
        LANG_NAME="German"
        ;;
    4)
        echo "-Duser.language=ja" >> "$VMOPTS_FILE"
        echo "-Duser.country=JP" >> "$VMOPTS_FILE"
        LANG_NAME="Japanese"
        ;;
    5)
        echo "-Duser.language=pt" >> "$VMOPTS_FILE"
        echo "-Duser.country=PT" >> "$VMOPTS_FILE"
        LANG_NAME="Portuguese"
        ;;
    6)
        echo "-Duser.language=da" >> "$VMOPTS_FILE"
        echo "-Duser.country=DK" >> "$VMOPTS_FILE"
        LANG_NAME="Danish"
        ;;
    7)
        echo "-Duser.language=ko" >> "$VMOPTS_FILE"
        echo "-Duser.country=KR" >> "$VMOPTS_FILE"
        LANG_NAME="Korean"
        ;;
    8)
        echo "-Duser.language=zh" >> "$VMOPTS_FILE"
        echo "-Duser.country=CN" >> "$VMOPTS_FILE"
        LANG_NAME="Chinese"
        ;;
    9)
        echo "-Duser.language=en" >> "$VMOPTS_FILE"
        echo "-Duser.country=US" >> "$VMOPTS_FILE"
        LANG_NAME="English"
        ;;
    0)
        echo "Language override removed."
        echo "PhpStorm will use system default."
        echo ""
        echo "VM options file: $VMOPTS_FILE"
        echo ""
        echo "Please restart PhpStorm for changes to take effect."
        exit 0
        ;;
    *)
        echo "Invalid choice. No changes made."
        exit 1
        ;;
esac

echo ""
echo "✓ VM options updated to use: $LANG_NAME"
echo ""
echo "VM options file: $VMOPTS_FILE"
echo ""
echo "IMPORTANT: You must restart PhpStorm for this change to take effect."
echo ""
echo "After restarting PhpStorm:"
echo "  1. Build the plugin: ./gradlew buildPlugin"
echo "  2. Install from: build/distributions/Chocolate-CakePHP-1.0.0.zip"
echo "  3. Settings → Languages & Frameworks → PHP → Chocolate CakePHP"
echo "  4. Check that UI text is displayed in $LANG_NAME"
echo ""
echo "To restore your original settings, restore from backup or run this script again with option 0."
echo ""
