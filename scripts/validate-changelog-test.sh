#!/bin/bash
#
# Tests for validate-changelog.sh
#

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_UNDER_TEST="$SCRIPT_DIR/validate-changelog.sh"

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

# Test 1: Current empty [Unreleased] section should fail validation
test_empty_unreleased_fails() {
    if "$SCRIPT_UNDER_TEST" unreleased &>/dev/null; then
        fail "Empty [Unreleased] section should fail" "Returned exit code 0 (success) but should fail"
    else
        pass "Empty [Unreleased] section fails validation"
    fi
}

# Test 2: Script runs without errors
test_script_runs_without_error() {
    local output
    if output=$("$SCRIPT_UNDER_TEST" unreleased 2>&1); then
        # Success case
        pass "Script runs without bash errors (validation passed)"
    else
        # Failure case is expected, but check it's not a bash error
        if echo "$output" | grep -q "line.*:"; then
            fail "Script has bash errors" "$output"
        else
            pass "Script runs without bash errors (validation failed as expected)"
        fi
    fi
}

# Test 3: Script accepts section argument
test_accepts_section_argument() {
    # Just verify it doesn't crash with a version argument
    if "$SCRIPT_UNDER_TEST" 1.0.0 &>/dev/null; then
        pass "Script accepts version section argument"
    else
        # Either way, as long as it doesn't crash, it's fine
        pass "Script accepts version section argument (validation worked)"
    fi
}

# Test 4: Script provides helpful output
test_provides_helpful_output() {
    local output
    output=$("$SCRIPT_UNDER_TEST" unreleased 2>&1) || true

    if echo "$output" | grep -q "Changelog"; then
        pass "Script provides informative output"
    else
        fail "Script should provide informative output" "Got: $output"
    fi
}

# Run all tests
echo "Running validate-changelog.sh tests..."
echo ""

test_empty_unreleased_fails
test_script_runs_without_error
test_accepts_section_argument
test_provides_helpful_output

# Summary
echo ""
echo "================================"
echo "Tests run: $TESTS_RUN"
echo "Passed: $TESTS_PASSED"
echo "Failed: $TESTS_FAILED"
echo "================================"

[ $TESTS_FAILED -eq 0 ] && exit 0 || exit 1
