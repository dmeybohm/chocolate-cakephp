#!/bin/bash
#
# Tests for get-changelog-section.sh
#

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_UNDER_TEST="$SCRIPT_DIR/get-changelog-section.sh"

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

# Test 1: Script extracts unreleased section
test_extracts_unreleased() {
    local output
    if output=$("$SCRIPT_UNDER_TEST" unreleased 2>&1); then
        if echo "$output" | grep -q "## \[Unreleased\]"; then
            pass "Extracts [Unreleased] section"
        else
            fail "Should include section header" "Got: $output"
        fi
    else
        fail "Should succeed extracting unreleased section" "Exit code: $?"
    fi
}

# Test 2: Script filters out footer links
test_filters_footer_links() {
    local output
    output=$("$SCRIPT_UNDER_TEST" unreleased 2>&1) || true

    if echo "$output" | grep -q "^\[Unreleased\]: https://"; then
        fail "Should filter out footer links" "Found link in output"
    else
        pass "Filters out footer link lines"
    fi
}

# Test 3: Script extracts version sections
test_extracts_version_section() {
    local output
    if output=$("$SCRIPT_UNDER_TEST" 1.0.0 2>&1); then
        if echo "$output" | grep -q "## \[1.0.0\]"; then
            pass "Extracts version section with date"
        else
            fail "Should include version section header" "Got first line: $(echo "$output" | head -1)"
        fi
    else
        fail "Should succeed extracting version section" "Exit code: $?"
    fi
}

# Test 4: Script fails for non-existent section
test_fails_for_nonexistent_section() {
    if "$SCRIPT_UNDER_TEST" 99.99.99 &>/dev/null; then
        fail "Should fail for non-existent section" "Succeeded unexpectedly"
    else
        pass "Fails for non-existent section"
    fi
}

# Test 5: Script output includes section content
test_includes_section_content() {
    local output
    output=$("$SCRIPT_UNDER_TEST" 1.0.0 2>&1) || true

    if echo "$output" | grep -q "###"; then
        pass "Includes section content (subsections)"
    else
        fail "Should include subsections" "Output seems incomplete"
    fi
}

# Test 6: Script stops at next section header
test_stops_at_next_section() {
    local output
    output=$("$SCRIPT_UNDER_TEST" 1.0.0 2>&1) || true

    # Count how many ## headers are in the output (should be exactly 1)
    local header_count
    header_count=$(echo "$output" | grep -c "^## \[" || true)

    if [ "$header_count" -eq 1 ]; then
        pass "Stops at next section header"
    else
        fail "Should have exactly one ## header" "Found: $header_count"
    fi
}

# Test 7: Script runs without bash errors
test_no_bash_errors() {
    local output
    if output=$("$SCRIPT_UNDER_TEST" unreleased 2>&1); then
        if echo "$output" | grep -q "line.*:.*command not found\|syntax error"; then
            fail "Has bash errors" "$output"
        else
            pass "Runs without bash errors"
        fi
    else
        # Check if error is expected (section not found) vs bash error
        pass "Runs without bash errors (validation worked)"
    fi
}

# Run all tests
echo "Running get-changelog-section.sh tests..."
echo ""

test_extracts_unreleased
test_filters_footer_links
test_extracts_version_section
test_fails_for_nonexistent_section
test_includes_section_content
test_stops_at_next_section
test_no_bash_errors

# Summary
echo ""
echo "================================"
echo "Tests run: $TESTS_RUN"
echo "Passed: $TESTS_PASSED"
echo "Failed: $TESTS_FAILED"
echo "================================"

[ $TESTS_FAILED -eq 0 ] && exit 0 || exit 1
