#!/bin/bash
#
# Run the CakePHP assumption tests against real, pinned CakePHP releases
# inside containers.
#
# Usage:
#   ./run-cakephp-assumption-tests.sh build [2 3 4 5]
#   ./run-cakephp-assumption-tests.sh install [2 3 4 5]
#   ./run-cakephp-assumption-tests.sh test [2 3 4 5] [-- phpunit args]
#   ./run-cakephp-assumption-tests.sh check-pins
#
# Environment variables:
#   CONTAINER_RUNTIME - Container command to use (default: podman, else docker)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SUITE_DIR="$PROJECT_ROOT/src/test/php"
IMAGE_PREFIX="chocolate-cake-assumptions"
ALL_VERSIONS=(2 3 4 5)

usage() {
    sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2
    exit 2
}

image_name_for() {
    case "$1" in
        2|3) echo "php74" ;;
        4|5) echo "php83" ;;
    esac
}

detect_runtime() {
    if [ -n "${CONTAINER_RUNTIME:-}" ]; then
        RUNTIME="$CONTAINER_RUNTIME"
    elif command -v podman >/dev/null 2>&1; then
        RUNTIME="podman"
    elif command -v docker >/dev/null 2>&1; then
        RUNTIME="docker"
    else
        echo "Neither podman nor docker was found. Install one or set CONTAINER_RUNTIME." >&2
        exit 1
    fi
}

# Rootless Podman maps the container's root to the host user. Other runtimes
# need the host user passed explicitly, or vendor/ ends up owned by root.
run_in_image() {
    local version="$1"
    shift
    local user_args=()
    if [ "$(basename "$RUNTIME")" != "podman" ]; then
        user_args=(--user "$(id -u):$(id -g)" -e HOME=/tmp)
    fi
    local cache_dir="${COMPOSER_CACHE_DIR:-$HOME/.cache/composer}"
    mkdir -p "$cache_dir"
    "$RUNTIME" run --rm \
        ${user_args[@]+"${user_args[@]}"} \
        -v "$SUITE_DIR:/suite:z" \
        -v "$cache_dir:/composer-cache:z" \
        -e COMPOSER_CACHE_DIR=/composer-cache \
        -w "/suite/cake$version" \
        "$IMAGE_PREFIX:$(image_name_for "$version")" \
        "$@"
}

image_exists() {
    "$RUNTIME" image inspect "$IMAGE_PREFIX:$1" >/dev/null 2>&1
}

build_image() {
    echo "==> Building $IMAGE_PREFIX:$1"
    "$RUNTIME" build -t "$IMAGE_PREFIX:$1" -f "$SUITE_DIR/images/$1/Containerfile" "$SUITE_DIR/images/$1"
}

images_for_versions() {
    local version
    for version in "$@"; do
        image_name_for "$version"
    done | sort -u
}

install_version() {
    echo "==> Installing dependencies for CakePHP $1"
    run_in_image "$1" composer install --no-interaction --no-progress
}

parse_versions() {
    VERSIONS=()
    local arg
    for arg in "$@"; do
        case "$arg" in
            2|3|4|5) VERSIONS+=("$arg") ;;
            *) echo "Unknown CakePHP version: $arg" >&2; usage ;;
        esac
    done
    if [ ${#VERSIONS[@]} -eq 0 ]; then
        VERSIONS=("${ALL_VERSIONS[@]}")
    fi
}

cmd_build() {
    parse_versions "$@"
    detect_runtime
    local image
    for image in $(images_for_versions "${VERSIONS[@]}"); do
        build_image "$image"
    done
}

cmd_install() {
    parse_versions "$@"
    detect_runtime
    local image version
    for image in $(images_for_versions "${VERSIONS[@]}"); do
        image_exists "$image" || build_image "$image"
    done
    for version in "${VERSIONS[@]}"; do
        install_version "$version"
    done
}

cmd_test() {
    local version_args=() phpunit_args=()
    while [ $# -gt 0 ]; do
        if [ "$1" = "--" ]; then
            shift
            phpunit_args=("$@")
            break
        fi
        version_args+=("$1")
        shift
    done
    parse_versions ${version_args[@]+"${version_args[@]}"}
    detect_runtime

    local image version failed=()
    for image in $(images_for_versions "${VERSIONS[@]}"); do
        image_exists "$image" || build_image "$image"
    done
    for version in "${VERSIONS[@]}"; do
        [ -d "$SUITE_DIR/cake$version/vendor" ] || install_version "$version"
    done
    for version in "${VERSIONS[@]}"; do
        echo ""
        echo "==================== CakePHP $version ===================="
        if ! run_in_image "$version" vendor/bin/phpunit ${phpunit_args[@]+"${phpunit_args[@]}"}; then
            failed+=("$version")
        fi
    done

    echo ""
    if [ ${#failed[@]} -gt 0 ]; then
        echo "Assumption tests failed on CakePHP: ${failed[*]}" >&2
        return 1
    fi
    echo "Assumption tests passed on CakePHP: ${VERSIONS[*]}"
}

cmd_check_pins() {
    local version composer_pin gradle_pin status=0
    for version in "${ALL_VERSIONS[@]}"; do
        composer_pin="$(sed -n 's/.*"cakephp\/cakephp": *"\([^"]*\)".*/\1/p' "$SUITE_DIR/cake$version/composer.json")"
        gradle_pin="$(grep -A1 -E "^\s*$version to CakeRelease\(" "$PROJECT_ROOT/build.gradle.kts" \
            | grep -oE '"[0-9]+\.[0-9]+\.[0-9]+"' | head -n1 | tr -d '"')"
        if [ -z "$composer_pin" ] || [ "$composer_pin" != "$gradle_pin" ]; then
            echo "CakePHP $version: composer.json has '$composer_pin', build.gradle.kts has '$gradle_pin'" >&2
            status=1
        else
            echo "CakePHP $version: $composer_pin"
        fi
    done
    return $status
}

[ $# -ge 1 ] || usage
command="$1"
shift
case "$command" in
    build) cmd_build "$@" ;;
    install) cmd_install "$@" ;;
    test) cmd_test "$@" ;;
    check-pins) cmd_check_pins ;;
    *) usage ;;
esac
