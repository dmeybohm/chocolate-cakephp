#!/bin/bash
#
# Tests for manage-release-drafts.sh
#

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_UNDER_TEST="$SCRIPT_DIR/manage-release-drafts.sh"

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

# Create mock gh command that records calls
setup_mock_gh() {
    local mock_dir="$1"
    local mock_gh="$mock_dir/gh"

    cat > "$mock_gh" << 'EOF'
#!/bin/bash
# Mock gh command - just record the call and return success
echo "$@" >> "$GH_CALLS_LOG"
# Return mock data for list operation
if [ "$1" = "api" ] && [[ "$2" == *"/releases"* ]] && [[ "$3" != "-X" ]]; then
    echo '[{"name":"Test Release","tag_name":"v1.0.0","id":123,"draft":true}]'
fi
exit 0
EOF
    chmod +x "$mock_gh"
    echo "$mock_gh"
}

# Setup
TEST_DIR=$(mktemp -d)
trap "rm -rf $TEST_DIR" EXIT

export GH_CALLS_LOG="$TEST_DIR/gh_calls.log"
export GITHUB_TOKEN="test-token"
MOCK_GH=$(setup_mock_gh "$TEST_DIR")

# Test 1: Script requires GITHUB_TOKEN
test_requires_github_token() {
    local output
    if output=$(unset GITHUB_TOKEN && "$SCRIPT_UNDER_TEST" list 2>&1); then
        fail "Should require GITHUB_TOKEN" "Succeeded without GITHUB_TOKEN"
    else
        if echo "$output" | grep -q "GITHUB_TOKEN"; then
            pass "Requires GITHUB_TOKEN environment variable"
        else
            fail "Should mention GITHUB_TOKEN in error" "Got: $output"
        fi
    fi
}

# Test 2: List subcommand calls gh api
test_list_subcommand() {
    > "$GH_CALLS_LOG"
    if GH_CMD="$MOCK_GH" "$SCRIPT_UNDER_TEST" list &>/dev/null; then
        if grep -q "api.*releases" "$GH_CALLS_LOG"; then
            pass "List subcommand calls gh api correctly"
        else
            fail "List should call gh api" "Calls: $(cat $GH_CALLS_LOG)"
        fi
    else
        fail "List subcommand failed" "Should succeed with mock"
    fi
}

# Test 3: Clean subcommand calls gh api for deletion
test_clean_subcommand() {
    > "$GH_CALLS_LOG"
    if GH_CMD="$MOCK_GH" "$SCRIPT_UNDER_TEST" clean &>/dev/null; then
        if grep -q "api.*releases" "$GH_CALLS_LOG"; then
            pass "Clean subcommand calls gh api correctly"
        else
            fail "Clean should call gh api" "Calls: $(cat $GH_CALLS_LOG)"
        fi
    else
        fail "Clean subcommand failed" "Should succeed with mock"
    fi
}

# Test 4: Create subcommand validates arguments
test_create_validates_arguments() {
    local output
    output=$(GH_CMD="$MOCK_GH" "$SCRIPT_UNDER_TEST" create 2>&1) || true
    if echo "$output" | grep -q "Usage"; then
        pass "Create subcommand validates arguments"
    else
        fail "Create should show usage when args missing"
    fi
}

# Test 5: Create subcommand requires notes file to exist
test_create_requires_notes_file() {
    local output
    if output=$(GH_CMD="$MOCK_GH" "$SCRIPT_UNDER_TEST" create 1.0.0 /nonexistent/file.md 2>&1); then
        fail "Create should fail with missing notes file" "Succeeded unexpectedly"
    else
        if echo "$output" | grep -q "not found"; then
            pass "Create validates notes file exists"
        else
            fail "Create should mention file not found" "Got: $output"
        fi
    fi
}

# Test 6: Create subcommand works with valid inputs
test_create_with_valid_inputs() {
    local notes_file="$TEST_DIR/notes.md"
    echo "Test release notes" > "$notes_file"

    > "$GH_CALLS_LOG"
    if GH_CMD="$MOCK_GH" "$SCRIPT_UNDER_TEST" create 1.0.0 "$notes_file" &>/dev/null; then
        if grep -q "release create" "$GH_CALLS_LOG"; then
            pass "Create subcommand calls gh release create"
        else
            fail "Create should call gh release create" "Calls: $(cat $GH_CALLS_LOG)"
        fi
    else
        fail "Create subcommand failed with valid inputs"
    fi
}

# Test 7: Invalid subcommand shows error
test_invalid_subcommand() {
    local output
    output=$(GH_CMD="$MOCK_GH" "$SCRIPT_UNDER_TEST" invalid 2>&1) || true
    if echo "$output" | grep -q "Unknown subcommand"; then
        pass "Shows error for invalid subcommand"
    else
        fail "Should show error for invalid subcommand"
    fi
}

# Run all tests
echo "Running manage-release-drafts.sh tests..."
echo ""

test_requires_github_token
test_list_subcommand
test_clean_subcommand
test_create_validates_arguments
test_create_requires_notes_file
test_create_with_valid_inputs
test_invalid_subcommand

# Summary
echo ""
echo "================================"
echo "Tests run: $TESTS_RUN"
echo "Passed: $TESTS_PASSED"
echo "Failed: $TESTS_FAILED"
echo "================================"

[ $TESTS_FAILED -eq 0 ] && exit 0 || exit 1
