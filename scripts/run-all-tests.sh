#!/bin/bash
#
# Run all test scripts in the scripts directory
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Find all test scripts
TEST_SCRIPTS=()
while IFS= read -r -d '' script; do
    TEST_SCRIPTS+=("$script")
done < <(find "$SCRIPT_DIR" -name "*-test.sh" -type f -print0 | sort -z)

if [ ${#TEST_SCRIPTS[@]} -eq 0 ]; then
    echo "No test scripts found in $SCRIPT_DIR"
    exit 1
fi

echo "========================================"
echo "Running all test scripts"
echo "========================================"
echo ""

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
FAILED_SCRIPTS=()

# Run each test script
for test_script in "${TEST_SCRIPTS[@]}"; do
    script_name=$(basename "$test_script")
    echo "----------------------------------------"
    echo "Running: $script_name"
    echo "----------------------------------------"

    if "$test_script"; then
        echo -e "${GREEN}✓ $script_name passed${NC}"
        ((PASSED_TESTS++))
    else
        echo -e "${RED}✗ $script_name failed${NC}"
        ((FAILED_TESTS++))
        FAILED_SCRIPTS+=("$script_name")
    fi

    ((TOTAL_TESTS++))
    echo ""
done

# Summary
echo "========================================"
echo "Test Summary"
echo "========================================"
echo "Total test scripts: $TOTAL_TESTS"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"

if [ $FAILED_TESTS -gt 0 ]; then
    echo -e "Failed: ${RED}$FAILED_TESTS${NC}"
    echo ""
    echo "Failed scripts:"
    for script in "${FAILED_SCRIPTS[@]}"; do
        echo -e "  ${RED}✗${NC} $script"
    done
    echo ""
    exit 1
else
    echo -e "Failed: $FAILED_TESTS"
    echo ""
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
