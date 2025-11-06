#!/bin/bash
#
# Tests for prepare-release-draft.sh
#

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_UNDER_TEST="$SCRIPT_DIR/prepare-release-draft.sh"

# Test counter
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
pass() {
    echo "✓ $1"
    ((TESTS_PASSED++))
    ((TESTS_RUN++))
}

fail() {
    echo "✗ $1"
    if [ $# -gt 1 ]; then
        echo "  $2"
    fi
    ((TESTS_FAILED++))
    ((TESTS_RUN++))
}

# Setup
TEST_DIR=$(mktemp -d)
trap "rm -rf $TEST_DIR" EXIT

export GITHUB_TOKEN="test-token"

# Create a mock manage-release-drafts.sh for testing
MOCK_MANAGE_SCRIPT="$SCRIPT_DIR/manage-release-drafts.sh.original"
if [ -f "$SCRIPT_DIR/manage-release-drafts.sh" ]; then
    mv "$SCRIPT_DIR/manage-release-drafts.sh" "$MOCK_MANAGE_SCRIPT"
fi

cat > "$SCRIPT_DIR/manage-release-drafts.sh" << 'EOF'
#!/bin/bash
# Mock manage-release-drafts.sh
echo "Mock: $@" >> "$TEST_MANAGE_CALLS"
exit 0
EOF
chmod +x "$SCRIPT_DIR/manage-release-drafts.sh"

# Restore original script after tests
restore_scripts() {
    if [ -f "$MOCK_MANAGE_SCRIPT" ]; then
        mv "$MOCK_MANAGE_SCRIPT" "$SCRIPT_DIR/manage-release-drafts.sh"
    fi
}
trap restore_scripts EXIT

export TEST_MANAGE_CALLS="$TEST_DIR/manage_calls.log"

# Test 1: Script requires --version argument
test_requires_version() {
    local changelog="$TEST_DIR/changelog.md"
    echo "## [Unreleased]" > "$changelog"
    echo "### Added" >> "$changelog"
    echo "- Feature" >> "$changelog"

    local output
    output=$("$SCRIPT_UNDER_TEST" --changelog-file "$changelog" 2>&1) || true
    if echo "$output" | grep -q "version is required"; then
        pass "Requires --version argument"
    else
        fail "Should require --version argument"
    fi
}

# Test 2: Script requires --changelog-file argument
test_requires_changelog_file() {
    local output
    output=$("$SCRIPT_UNDER_TEST" --version 1.0.0 2>&1) || true
    if echo "$output" | grep -q "changelog-file is required"; then
        pass "Requires --changelog-file argument"
    else
        fail "Should require --changelog-file argument"
    fi
}

# Test 3: Script validates changelog file exists
test_validates_file_exists() {
    local output
    output=$("$SCRIPT_UNDER_TEST" --version 1.0.0 --changelog-file /nonexistent.md 2>&1) || true
    if echo "$output" | grep -q "not found"; then
        pass "Validates changelog file exists"
    else
        fail "Should validate file exists"
    fi
}

# Test 4: Script skips when changelog is empty
test_skips_empty_changelog() {
    local changelog="$TEST_DIR/empty.md"
    echo "## [Unreleased]" > "$changelog"

    > "$TEST_MANAGE_CALLS"
    local output
    output=$("$SCRIPT_UNDER_TEST" --version 1.0.0 --changelog-file "$changelog" 2>&1)

    if echo "$output" | grep -q "skipping"; then
        if [ ! -s "$TEST_MANAGE_CALLS" ]; then
            pass "Skips draft creation for empty changelog"
        else
            fail "Should not call manage-release-drafts for empty changelog" "Calls: $(cat $TEST_MANAGE_CALLS)"
        fi
    else
        fail "Should skip empty changelog" "Got: $output"
    fi
}

# Test 5: Dry-run mode doesn't make changes
test_dry_run_mode() {
    local changelog="$TEST_DIR/with_content.md"
    echo "## [Unreleased]" > "$changelog"
    echo "### Added" >> "$changelog"
    echo "- Feature" >> "$changelog"

    > "$TEST_MANAGE_CALLS"
    local output
    output=$("$SCRIPT_UNDER_TEST" --version 1.0.0 --changelog-file "$changelog" --dry-run 2>&1)

    if echo "$output" | grep -q "DRY RUN"; then
        if [ ! -s "$TEST_MANAGE_CALLS" ]; then
            pass "Dry-run mode doesn't make changes"
        else
            fail "Dry-run should not call manage-release-drafts" "Calls: $(cat $TEST_MANAGE_CALLS)"
        fi
    else
        fail "Should show DRY RUN message" "Got: $output"
    fi
}

# Test 6: Script calls manage-release-drafts with valid changelog
test_calls_manage_with_valid_changelog() {
    local changelog="$TEST_DIR/valid.md"
    echo "## [Unreleased]" > "$changelog"
    echo "### Added" >> "$changelog"
    echo "- New feature" >> "$changelog"

    > "$TEST_MANAGE_CALLS"
    if "$SCRIPT_UNDER_TEST" --version 1.0.0 --changelog-file "$changelog" &>/dev/null; then
        if grep -q "clean" "$TEST_MANAGE_CALLS" && grep -q "create" "$TEST_MANAGE_CALLS"; then
            pass "Calls manage-release-drafts clean and create"
        else
            fail "Should call both clean and create" "Calls: $(cat $TEST_MANAGE_CALLS)"
        fi
    else
        fail "Should succeed with valid changelog"
    fi
}

# Run all tests
echo "Running prepare-release-draft.sh tests..."
echo ""

test_requires_version
test_requires_changelog_file
test_validates_file_exists
test_skips_empty_changelog
test_dry_run_mode
test_calls_manage_with_valid_changelog

# Summary
echo ""
echo "================================"
echo "Tests run: $TESTS_RUN"
echo "Passed: $TESTS_PASSED"
echo "Failed: $TESTS_FAILED"
echo "================================"

[ $TESTS_FAILED -eq 0 ] && exit 0 || exit 1
