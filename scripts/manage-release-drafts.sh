#!/bin/bash
#
# Manage GitHub draft releases
#
# Usage:
#   ./manage-release-drafts.sh list
#   ./manage-release-drafts.sh clean
#   ./manage-release-drafts.sh create <version> <notes-file>
#
# Environment variables:
#   GITHUB_TOKEN - Required for GitHub API access
#   GH_CMD - Override gh command (for testing, default: "gh")
#

set -euo pipefail

# Allow overriding gh command for testing
GH_CMD="${GH_CMD:-gh}"

# Check for required environment variable
if [ -z "${GITHUB_TOKEN:-}" ]; then
    echo "Error: GITHUB_TOKEN environment variable is required" >&2
    exit 1
fi

# Get subcommand
if [ $# -lt 1 ]; then
    echo "Usage: $0 <list|clean|create> [args...]" >&2
    exit 1
fi

SUBCOMMAND="$1"
shift

case "$SUBCOMMAND" in
    list)
        # List all draft releases
        $GH_CMD api repos/{owner}/{repo}/releases \
            --jq '.[] | select(.draft == true) | {name, tag_name, id}'
        ;;

    clean)
        # Delete all draft releases
        echo "Removing old release drafts..."
        $GH_CMD api repos/{owner}/{repo}/releases \
            --jq '.[] | select(.draft == true) | .id' \
            | xargs -I '{}' $GH_CMD api -X DELETE repos/{owner}/{repo}/releases/{}
        echo "Draft releases cleaned"
        ;;

    create)
        # Create a new draft release
        if [ $# -ne 2 ]; then
            echo "Usage: $0 create <version> <notes-file>" >&2
            exit 1
        fi

        VERSION="$1"
        NOTES_FILE="$2"

        if [ ! -f "$NOTES_FILE" ]; then
            echo "Error: Notes file '$NOTES_FILE' not found" >&2
            exit 1
        fi

        echo "Creating draft release v$VERSION..."
        $GH_CMD release create "v$VERSION" \
            --draft \
            --title "v$VERSION" \
            --notes-file "$NOTES_FILE"

        echo "Draft release created: v$VERSION"
        ;;

    *)
        echo "Error: Unknown subcommand '$SUBCOMMAND'" >&2
        echo "Valid subcommands: list, clean, create" >&2
        exit 1
        ;;
esac
