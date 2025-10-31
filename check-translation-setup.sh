#!/bin/bash
# Diagnostic script to check translation setup

echo "==================================="
echo "Translation Setup Diagnostic Tool"
echo "==================================="
echo ""

# Check if plugin is built
echo "1. Checking if plugin is built..."
if [ -f "build/distributions/chocolate-cakephp-1.0.0.zip" ]; then
    echo "   ✓ Plugin ZIP exists: build/distributions/chocolate-cakephp-1.0.0.zip"
    SIZE=$(ls -lh build/distributions/chocolate-cakephp-1.0.0.zip | awk '{print $5}')
    echo "   ✓ Size: $SIZE"
else
    echo "   ✗ Plugin ZIP NOT found!"
    echo "   → Run: ./gradlew buildPlugin"
    exit 1
fi

# Check if translations are in the JAR
echo ""
echo "2. Checking if translations are in the plugin..."
TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"
unzip -q ~/projects/chocolate-cakephp/build/distributions/chocolate-cakephp-1.0.0.zip
BUNDLE_COUNT=$(unzip -l chocolate-cakephp/lib/instrumented-chocolate-cakephp-1.0.0.jar | grep "messages/ChocolateCakePHPBundle" | wc -l)
cd - > /dev/null
rm -rf "$TEMP_DIR"

if [ "$BUNDLE_COUNT" -ge 9 ]; then
    echo "   ✓ Found $BUNDLE_COUNT resource bundle files"
    echo "     (1 base English + 8 translations)"
else
    echo "   ✗ Only found $BUNDLE_COUNT resource bundle files"
    echo "   → Expected 9 files (base + 8 languages)"
fi

# Check PhpStorm config
echo ""
echo "3. Checking PhpStorm configuration..."
PHPSTORM_DIRS=$(ls -dt ~/.config/JetBrains/PhpStorm* 2>/dev/null)

if [ -z "$PHPSTORM_DIRS" ]; then
    echo "   ✗ No PhpStorm configuration found"
    echo "   → Have you run PhpStorm at least once?"
else
    LATEST_DIR=$(echo "$PHPSTORM_DIRS" | head -1)
    echo "   ✓ Found PhpStorm config: $LATEST_DIR"

    VMOPTS_FILE="$LATEST_DIR/phpstorm64.vmopts"
    if [ -f "$VMOPTS_FILE" ]; then
        echo "   ✓ VM options file exists: $VMOPTS_FILE"

        if grep -q "user.language" "$VMOPTS_FILE"; then
            LANG=$(grep "user.language" "$VMOPTS_FILE" | cut -d= -f2)
            COUNTRY=$(grep "user.country" "$VMOPTS_FILE" | cut -d= -f2)
            echo "   ✓ Language setting found: $LANG-$COUNTRY"
        else
            echo "   ⚠ No language override set (will use system default)"
        fi
    else
        echo "   ⚠ VM options file doesn't exist yet"
        echo "     → Create via: Help → Edit Custom VM Options..."
    fi
fi

# Check system locale
echo ""
echo "4. System locale information..."
echo "   Current LANG: $LANG"
echo "   Current LC_ALL: $LC_ALL"

# Instructions
echo ""
echo "==================================="
echo "Next Steps"
echo "==================================="
echo ""
echo "To test translations:"
echo ""
echo "1. In PhpStorm: Help → Edit Custom VM Options..."
echo "2. Add these lines:"
echo "     -Duser.language=es"
echo "     -Duser.country=ES"
echo "3. Restart PhpStorm"
echo "4. Install plugin from: build/distributions/chocolate-cakephp-1.0.0.zip"
echo "5. Check Settings → Languages & Frameworks → PHP → Chocolate CakePHP"
echo ""
echo "See test-translations-manual.md for detailed instructions."
echo ""
