#!/bin/bash

# Release build script for Chocolate CakePHP plugin
# Creates a release with signed artifacts using git worktrees
#
# Usage: ./build-release.sh <branch-name> <version>
# Example: ./build-release.sh feature-xyz 1.2.3

set -euo pipefail

# Configuration
GPG_KEY_ID="${GPG_KEY_ID:-}"  # Set via environment or hardcode here
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKTREE_BASE="${PROJECT_ROOT}/worktree"
RELEASE_DIR="${PROJECT_ROOT}/release"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
error() {
    echo -e "${RED}ERROR: $1${NC}" >&2
    exit 1
}

info() {
    echo -e "${GREEN}INFO: $1${NC}"
}

warn() {
    echo -e "${YELLOW}WARN: $1${NC}"
}

# Cleanup function
cleanup() {
    local exit_code=$?
    if [ -n "${WORKTREE_PATH:-}" ] && [ -d "$WORKTREE_PATH" ]; then
        info "Cleaning up worktree at $WORKTREE_PATH"
        cd "$PROJECT_ROOT"
        git worktree remove "$WORKTREE_PATH" --force || warn "Failed to remove worktree"
    fi
    if [ -n "${RELEASE_BRANCH:-}" ]; then
        if git rev-parse --verify "$RELEASE_BRANCH" >/dev/null 2>&1; then
            info "Removing release branch $RELEASE_BRANCH"
            git branch -D "$RELEASE_BRANCH" || warn "Failed to remove release branch"
        fi
    fi
    if [ -n "${TEMP_EXTRACT_DIR:-}" ] && [ -d "$TEMP_EXTRACT_DIR" ]; then
        info "Cleaning up temp directory at $TEMP_EXTRACT_DIR"
        rm -rf "$TEMP_EXTRACT_DIR"
    fi
    exit $exit_code
}

trap cleanup EXIT INT TERM

# Validation and setup
validate_and_setup() {
    local branch_name="$1"
    local version="$2"

    # Verify we're in project root
    if [ ! -f "$PROJECT_ROOT/gradle.properties" ]; then
        error "Not in project root (gradle.properties not found)"
    fi

    # Validate version format (x.x.x)
    if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        error "Version must be in x.x.x format (e.g., 1.0.0), got: $version"
    fi

    # Validate GPG key is configured
    if [ -z "$GPG_KEY_ID" ]; then
        error "GPG_KEY_ID must be set (export GPG_KEY_ID=your-key-id)"
    fi

    # Verify GPG key exists
    if ! gpg --list-secret-keys "$GPG_KEY_ID" >/dev/null 2>&1; then
        error "GPG key $GPG_KEY_ID not found in keyring"
    fi

    # Create worktree base directory if needed
    if [ ! -d "$WORKTREE_BASE" ]; then
        info "Creating worktree directory: $WORKTREE_BASE"
        mkdir -p "$WORKTREE_BASE"
    fi

    # Create release directory if needed
    if [ ! -d "$RELEASE_DIR" ]; then
        info "Creating release directory: $RELEASE_DIR"
        mkdir -p "$RELEASE_DIR"
    fi

    # Verify source branch exists
    if ! git rev-parse --verify "$branch_name" >/dev/null 2>&1; then
        error "Branch '$branch_name' does not exist"
    fi

    # Set release branch name and worktree path
    RELEASE_BRANCH="release-${version}"
    WORKTREE_PATH="${WORKTREE_BASE}/${RELEASE_BRANCH}"

    # Check if release branch already exists
    if git rev-parse --verify "$RELEASE_BRANCH" >/dev/null 2>&1; then
        error "Release branch '$RELEASE_BRANCH' already exists"
    fi

    # Check if worktree already exists
    if [ -d "$WORKTREE_PATH" ]; then
        error "Worktree already exists at $WORKTREE_PATH - remove it first"
    fi

    info "Validation passed"
}

# Create release branch and worktree
create_worktree() {
    local source_branch="$1"
    local version="$2"

    info "Creating release branch '$RELEASE_BRANCH' from '$source_branch'"
    cd "$PROJECT_ROOT"
    git branch "$RELEASE_BRANCH" "$source_branch"

    info "Creating worktree at $WORKTREE_PATH"
    git worktree add "$WORKTREE_PATH" "$RELEASE_BRANCH"
    info "Worktree created successfully"
}

# Verify version in gradle.properties matches expected version
verify_version() {
    local expected_version="$1"

    info "Verifying version in gradle.properties"
    local gradle_props="$WORKTREE_PATH/gradle.properties"

    if [ ! -f "$gradle_props" ]; then
        error "gradle.properties not found in worktree"
    fi

    local actual_version
    actual_version=$(grep '^pluginVersion' "$gradle_props" | cut -d'=' -f2 | tr -d ' ')

    if [ "$actual_version" != "$expected_version" ]; then
        error "Version mismatch: gradle.properties has '$actual_version' but expected '$expected_version'"
    fi

    info "Version verified: $actual_version"
}

# Run tests
run_tests() {
    info "Running tests in worktree"
    cd "$WORKTREE_PATH"

    info "Running verifyPlugin..."
    ./gradlew verifyPlugin

    info "Running script tests..."
    scripts/run-all-tests.sh

    info "All tests passed"
}

# Create source distributions
create_source_distributions() {
    local version="$1"

    info "Creating source distributions"
    cd "$WORKTREE_PATH"

    local tar_name="chocolate-cakephp-${version}-src.tar.gz"
    local zip_name="chocolate-cakephp-${version}-src.zip"

    # Create list of files to exclude
    local exclude_patterns=(
        ".git"
        "build"
        ".gradle"
        ".idea"
        "worktree"
        "release"
        "*.iml"
        ".DS_Store"
    )

    # Build tar exclude arguments
    local tar_excludes=()
    for pattern in "${exclude_patterns[@]}"; do
        tar_excludes+=(--exclude="$pattern")
    done

    # Create tar.gz
    info "Creating $tar_name..."
    tar czf "${RELEASE_DIR}/${tar_name}" "${tar_excludes[@]}" -C "$WORKTREE_PATH" .

    # Create zip
    info "Creating $zip_name..."
    # Zip needs to be created from inside the directory
    (cd "$WORKTREE_PATH" && zip -r "${RELEASE_DIR}/${zip_name}" . \
        -x ".git/*" "build/*" ".gradle/*" ".idea/*" "worktree/*" "release/*" "*.iml" ".DS_Store" \
        >/dev/null)

    info "Source distributions created successfully"
}

# Build plugin
build_plugin() {
    local version="$1"

    info "Building plugin"
    cd "$WORKTREE_PATH"

    ./gradlew buildPlugin

    local plugin_zip="build/distributions/chocolate-cakephp-${version}.zip"
    if [ ! -f "$plugin_zip" ]; then
        error "Plugin zip not found at $plugin_zip"
    fi

    info "Plugin built successfully: $plugin_zip"

    # Copy plugin distribution to release directory
    info "Copying plugin distribution to release directory..."
    cp "$plugin_zip" "${RELEASE_DIR}/"
}

# Extract JARs and sign everything
extract_and_sign() {
    local version="$1"

    info "Extracting JARs and signing artifacts"

    # Create temp directory for extraction
    TEMP_EXTRACT_DIR=$(mktemp -d)

    local plugin_zip="$WORKTREE_PATH/build/distributions/chocolate-cakephp-${version}.zip"

    # Extract plugin zip
    info "Extracting plugin zip..."
    unzip -q "$plugin_zip" -d "$TEMP_EXTRACT_DIR"

    # Find the JARs
    local jar_dir="$TEMP_EXTRACT_DIR/chocolate-cakephp/lib"
    if [ ! -d "$jar_dir" ]; then
        error "JARs directory not found in extracted plugin"
    fi

    local jars=($(find "$jar_dir" -name "*.jar"))
    if [ ${#jars[@]} -ne 2 ]; then
        error "Expected 2 JARs but found ${#jars[@]}"
    fi

    info "Found ${#jars[@]} JARs to sign"

    # Create temp directory for signatures
    local sig_temp_dir=$(mktemp -d)

    # Sign each JAR
    for jar in "${jars[@]}"; do
        local jar_name=$(basename "$jar")
        info "Signing $jar_name..."
        gpg --default-key "$GPG_KEY_ID" --detach-sign --armor \
            --output "${sig_temp_dir}/${jar_name}.sig" "$jar"
    done

    # Sign source distributions
    local tar_name="chocolate-cakephp-${version}-src.tar.gz"
    local zip_name="chocolate-cakephp-${version}-src.zip"

    info "Signing source tar.gz..."
    gpg --default-key "$GPG_KEY_ID" --detach-sign --armor \
        --output "${sig_temp_dir}/${tar_name}.sig" "${RELEASE_DIR}/${tar_name}"

    info "Signing source zip..."
    gpg --default-key "$GPG_KEY_ID" --detach-sign --armor \
        --output "${sig_temp_dir}/${zip_name}.sig" "${RELEASE_DIR}/${zip_name}"

    # Get git commit information
    cd "$WORKTREE_PATH"
    local commit_hash=$(git rev-parse HEAD)
    local short_hash=$(git rev-parse --short HEAD)

    # Create README.md with release information
    info "Creating README.md with release information..."
    cat > "${sig_temp_dir}/README.md" <<EOF
# Chocolate CakePHP Release Signatures

**Version:** ${version}
**Git Commit:** ${commit_hash}
**Short Hash:** ${short_hash}

## Signed Files

This archive contains GPG signatures for the following release artifacts:

EOF

    # List all signature files
    for sig in "${sig_temp_dir}"/*.sig; do
        if [ -f "$sig" ]; then
            echo "- $(basename "$sig")" >> "${sig_temp_dir}/README.md"
        fi
    done

    cat >> "${sig_temp_dir}/README.md" <<EOF

## Verification

To verify a signature, use:

\`\`\`bash
gpg --verify <signature-file> <original-file>
\`\`\`

For example:
\`\`\`bash
gpg --verify chocolate-cakephp-${version}.tar.gz.sig chocolate-cakephp-${version}.tar.gz
\`\`\`

The signatures were created with GPG key ID: ${GPG_KEY_ID}
EOF

    # Create signatures zip
    local sig_zip="chocolate-cakephp-${version}.signatures.zip"
    info "Creating signatures archive..."
    (cd "$sig_temp_dir" && zip -r "${RELEASE_DIR}/${sig_zip}" . >/dev/null)

    # Store signature paths for verification
    SIGNATURE_FILES=("${sig_temp_dir}"/*)

    # Cleanup signature temp dir
    rm -rf "$sig_temp_dir"

    info "All artifacts signed successfully"
}

# Verify all signatures
verify_signatures() {
    local version="$1"

    info "Verifying GPG signatures..."

    # Extract signatures zip to temp directory
    local verify_temp_dir=$(mktemp -d)
    unzip -q "${RELEASE_DIR}/chocolate-cakephp-${version}.signatures.zip" -d "$verify_temp_dir"

    local verification_failed=0

    # Verify JARs
    local plugin_zip="$WORKTREE_PATH/build/distributions/chocolate-cakephp-${version}.zip"
    local jar_extract_dir=$(mktemp -d)
    unzip -q "$plugin_zip" -d "$jar_extract_dir"

    local jar_dir="$jar_extract_dir/chocolate-cakephp/lib"
    local jars=($(find "$jar_dir" -name "*.jar"))

    for jar in "${jars[@]}"; do
        local jar_name=$(basename "$jar")
        local sig_file="$verify_temp_dir/${jar_name}.sig"

        if [ -f "$sig_file" ]; then
            info "Verifying signature for $jar_name..."
            if ! gpg --verify "$sig_file" "$jar" >/dev/null 2>&1; then
                error "Signature verification failed for $jar_name"
                verification_failed=1
            fi
        else
            error "Signature file not found for $jar_name"
            verification_failed=1
        fi
    done

    rm -rf "$jar_extract_dir"

    # Verify source distributions
    local tar_name="chocolate-cakephp-${version}-src.tar.gz"
    local zip_name="chocolate-cakephp-${version}-src.zip"

    info "Verifying signature for $tar_name..."
    if ! gpg --verify "$verify_temp_dir/${tar_name}.sig" "${RELEASE_DIR}/${tar_name}" >/dev/null 2>&1; then
        error "Signature verification failed for $tar_name"
        verification_failed=1
    fi

    info "Verifying signature for $zip_name..."
    if ! gpg --verify "$verify_temp_dir/${zip_name}.sig" "${RELEASE_DIR}/${zip_name}" >/dev/null 2>&1; then
        error "Signature verification failed for $zip_name"
        verification_failed=1
    fi

    # Cleanup
    rm -rf "$verify_temp_dir"

    if [ $verification_failed -eq 1 ]; then
        error "Signature verification failed"
    fi

    info "All signatures verified successfully!"
}

# Print summary report
print_report() {
    local version="$1"

    info "Release build complete!"
    echo ""
    echo "Created files in ${RELEASE_DIR}:"
    ls -lh "${RELEASE_DIR}"/chocolate-cakephp-${version}* | awk '{print "  " $9 " (" $5 ")"}'
    echo ""
    echo "Files ready for release:"
    echo "  - chocolate-cakephp-${version}.zip (plugin distribution)"
    echo "  - chocolate-cakephp-${version}-src.tar.gz (source)"
    echo "  - chocolate-cakephp-${version}-src.zip (source)"
    echo "  - chocolate-cakephp-${version}.signatures.zip (GPG signatures)"
}

# Main script
main() {
    if [ $# -ne 2 ]; then
        error "Usage: $0 <branch-name> <version>"
    fi

    local branch_name="$1"
    local version="$2"

    info "Building release for branch '$branch_name' version '$version'"

    # Step 1: Validation and setup
    validate_and_setup "$branch_name" "$version"

    # Step 2: Create release branch and worktree
    create_worktree "$branch_name" "$version"

    # Step 3: Verify version
    verify_version "$version"

    # Step 4: Run tests
    run_tests

    # Step 5: Create source distributions
    create_source_distributions "$version"

    # Step 6: Build plugin
    build_plugin "$version"

    # Step 7: Extract JARs and sign everything
    extract_and_sign "$version"

    # Step 8: Verify signatures
    verify_signatures "$version"

    # Step 9: Print report
    print_report "$version"
}

main "$@"
