#!/bin/bash
#
# Validate that a changelog section has meaningful content
#
# Usage:
#   ./validate-changelog.sh [section]
#
# Arguments:
#   section: Section to validate (default: "unreleased")
#
# Exit codes:
#   0 - Changelog has meaningful content
#   1 - Changelog is empty or only contains markers
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default section
SECTION="${1:-unreleased}"

# Extract the changelog section
CHANGELOG="$("$SCRIPT_DIR/get-changelog-section.sh" "$SECTION")"

# Remove lines containing [Unreleased] or [version] markers and check if non-whitespace content remains
CONTENT=$(echo "$CHANGELOG" | { grep -v '\[.*\]' || true; } | tr -d '[:space:]')

if [[ -n "$CONTENT" ]]; then
    echo "Changelog section '$SECTION' has content"
    exit 0
else
    echo "Changelog section '$SECTION' is empty or contains only markers"
    exit 1
fi
