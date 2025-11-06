#!/bin/bash
#
# Extract sections from CHANGELOG.md
#
# Usage:
#   ./get-changelog-section.sh unreleased
#   ./get-changelog-section.sh 1.0.0
#
# Outputs the content of the specified section in markdown format,
# excluding the section header and footer links.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHANGELOG_FILE="${SCRIPT_DIR}/../CHANGELOG.md"

if [ ! -f "$CHANGELOG_FILE" ]; then
    echo "Error: CHANGELOG.md not found at $CHANGELOG_FILE" >&2
    exit 1
fi

if [ $# -ne 1 ]; then
    echo "Usage: $0 <section>" >&2
    echo "  section: 'unreleased' or a version number like '1.0.0'" >&2
    exit 1
fi

SECTION="$1"

# Normalize section name
if [ "$SECTION" = "unreleased" ]; then
    SECTION_PATTERN="^## \[Unreleased\]"
else
    # Version sections have dates like: ## [1.0.0] - 2025-10-31
    SECTION_PATTERN="^## \[$SECTION\]"
fi

# Extract the section content
# This awk script:
# 1. Finds the section header (possibly with date suffix)
# 2. Prints lines until the next ## header
# 3. Filters out footer link lines (lines starting with [version]:)
# 4. Prints the section header itself
awk -v pattern="$SECTION_PATTERN" '
    BEGIN { in_section = 0; found = 0 }

    # Found the target section header (with or without date)
    $0 ~ pattern {
        in_section = 1
        found = 1
        print $0
        next
    }

    # Found another section header - stop processing
    in_section && /^## \[/ {
        in_section = 0
        next
    }

    # Skip footer link lines
    /^\[.*\]: https?:\/\// {
        next
    }

    # Print lines within the section
    in_section {
        print $0
    }

    END {
        if (!found) {
            print "Error: Section matching pattern \"" pattern "\" not found in CHANGELOG.md" > "/dev/stderr"
            exit 1
        }
    }
' "$CHANGELOG_FILE"
