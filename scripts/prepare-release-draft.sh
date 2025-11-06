#!/bin/bash
#
# Prepare a GitHub release draft
#
# This script orchestrates the complete release draft preparation:
# 1. Validates changelog has content
# 2. Cleans old draft releases
# 3. Creates new draft release
#
# Usage:
#   ./prepare-release-draft.sh --version <version> --changelog-file <file>
#   ./prepare-release-draft.sh --version <version> --changelog-file <file> --dry-run
#
# Environment variables:
#   GITHUB_TOKEN - Required for GitHub API access
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Parse arguments
VERSION=""
CHANGELOG_FILE=""
DRY_RUN=false

while [ $# -gt 0 ]; do
    case "$1" in
        --version)
            VERSION="$2"
            shift 2
            ;;
        --changelog-file)
            CHANGELOG_FILE="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        *)
            echo "Error: Unknown argument '$1'" >&2
            echo "Usage: $0 --version <version> --changelog-file <file> [--dry-run]" >&2
            exit 1
            ;;
    esac
done

# Validate required arguments
if [ -z "$VERSION" ]; then
    echo "Error: --version is required" >&2
    exit 1
fi

if [ -z "$CHANGELOG_FILE" ]; then
    echo "Error: --changelog-file is required" >&2
    exit 1
fi

if [ ! -f "$CHANGELOG_FILE" ]; then
    echo "Error: Changelog file '$CHANGELOG_FILE' not found" >&2
    exit 1
fi

# Step 1: Validate changelog has content
echo "Validating changelog content..."
if cat "$CHANGELOG_FILE" | grep -v '\[Unreleased\]' | grep -v '\[.*\]: https://' | tr -d '[:space:]' | grep -q .; then
    echo "✓ Changelog has content"
else
    echo "Changelog is empty or contains only markers - skipping draft release creation"
    exit 0
fi

if [ "$DRY_RUN" = true ]; then
    echo ""
    echo "[DRY RUN] Would perform the following actions:"
    echo "  1. Clean old draft releases"
    echo "  2. Create draft release v$VERSION with changelog from $CHANGELOG_FILE"
    echo ""
    echo "Run without --dry-run to actually create the release draft"
    exit 0
fi

# Step 2: Clean old draft releases
echo "Cleaning old draft releases..."
"$SCRIPT_DIR/manage-release-drafts.sh" clean

# Step 3: Create new draft release
echo "Creating new draft release..."
"$SCRIPT_DIR/manage-release-drafts.sh" create "$VERSION" "$CHANGELOG_FILE"

echo ""
echo "✓ Release draft preparation complete!"
