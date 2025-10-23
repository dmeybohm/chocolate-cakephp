# Manual Translation Testing Instructions

The automated script may not work for all PhpStorm versions. Here's how to manually test translations:

## Step 1: Build and Install the Plugin

```bash
./gradlew buildPlugin
```

Then in PhpStorm:
1. **Settings** → **Plugins**
2. Click **⚙️** (gear icon) → **Install Plugin from Disk...**
3. Select `build/distributions/chocolate-cakephp-1.0.0.zip`
4. Click **OK** and **Restart** PhpStorm

## Step 2: Set the Language via Help Menu

This is the MOST RELIABLE method:

1. In PhpStorm, go to **Help** → **Edit Custom VM Options...**
2. If prompted to create the file, click **Create**
3. Add these two lines at the end of the file:

   **For Spanish:**
   ```
   -Duser.language=es
   -Duser.country=ES
   ```

   **For French:**
   ```
   -Duser.language=fr
   -Duser.country=FR
   ```

   **For German:**
   ```
   -Duser.language=de
   -Duser.country=DE
   ```

   **For Japanese:**
   ```
   -Duser.language=ja
   -Duser.country=JP
   ```

   **For Portuguese:**
   ```
   -Duser.language=pt
   -Duser.country=PT
   ```

   **For Danish:**
   ```
   -Duser.language=da
   -Duser.country=DK
   ```

   **For Korean:**
   ```
   -Duser.language=ko
   -Duser.country=KR
   ```

   **For Chinese:**
   ```
   -Duser.language=zh
   -Duser.country=CN
   ```

4. **Save** the file
5. **Restart** PhpStorm

## Step 3: Verify Translations

After restarting PhpStorm with the language set:

1. Go to **Settings** → **Languages & Frameworks** → **PHP** → **Chocolate CakePHP**
2. Check that the UI text is in your selected language:
   - Section titles should be translated
   - Checkbox labels should be translated
   - Help text should be translated
   - Button labels should be translated

3. Open a CakePHP project and:
   - Right-click in a controller file
   - Look for the "Toggle Controller / View" action (should be translated)

4. Try creating a view file:
   - Dialog titles should be translated
   - Field labels should be translated
   - Try creating a duplicate file - error should be translated

## Troubleshooting

### I don't see translations after restarting

**Check 1: Verify VM options were saved**
1. Go to **Help** → **About PhpStorm**
2. Click **Copy and Close**
3. Paste into a text editor
4. Search for `-Duser.language` - it should show your language code

**Check 2: Verify plugin is installed**
1. **Settings** → **Plugins**
2. Search for "Chocolate CakePHP"
3. Should show version 1.0.0

**Check 3: Rebuild the plugin**
Sometimes caching issues occur. Try:
```bash
./gradlew clean buildPlugin
```
Then reinstall the plugin.

**Check 4: Clear PhpStorm caches**
1. **File** → **Invalidate Caches...**
2. Check all boxes
3. Click **Invalidate and Restart**

### Still seeing English

Some things that WON'T be translated:
- The plugin NAME in the plugin manager (always "Chocolate CakePHP")
- The plugin DESCRIPTION in the plugin manager
- PhpStorm's own UI (menu bar, toolbars, etc.)
- System dialogs

Only the Chocolate CakePHP plugin's OWN UI elements will be translated:
- Settings pages
- Dialog boxes created by the plugin
- Actions added by the plugin
- Error messages from the plugin

### Reverting to English

1. **Help** → **Edit Custom VM Options...**
2. Remove (or comment out with #) the lines:
   ```
   -Duser.language=xx
   -Duser.country=XX
   ```
3. Save and restart PhpStorm

## Alternative: Test with a Fresh PhpStorm Instance

If you want to test without affecting your main PhpStorm:

```bash
# Create a test configuration directory
export PHPSTORM_VM_OPTIONS=/tmp/test-phpstorm.vmopts

# Create VM options file with Spanish
cat > /tmp/test-phpstorm.vmopts <<EOF
-Duser.language=es
-Duser.country=ES
EOF

# Launch PhpStorm with this config
phpstorm.sh
```

Then install the plugin in this instance.

## Quick Language Reference

| Language | -Duser.language | -Duser.country |
|----------|----------------|----------------|
| Spanish | es | ES |
| French | fr | FR |
| German | de | DE |
| Japanese | ja | JP |
| Portuguese | pt | PT |
| Danish | da | DK |
| Korean | ko | KR |
| Chinese (Simplified) | zh | CN |
| English | en | US |
