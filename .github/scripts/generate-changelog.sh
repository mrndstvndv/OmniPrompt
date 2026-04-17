#!/bin/bash
set -euo pipefail

# Generate changelog and calculate the next version based on conventional commits.
# Usage: ./generate-changelog.sh [comparison_tag]
# Environment:
#   RELEASE_CHANNEL=stable|dev (default: stable)
#   DEV_RELEASE_NUMBER=<positive integer> required when RELEASE_CHANNEL=dev
#   VERSION_BASE_TAG=<tag used to calculate the next semantic version>; defaults to comparison_tag
# Outputs:
#   - Markdown changelog to stdout
#   - skip_release=true|false and version to stderr

RELEASE_CHANNEL="${RELEASE_CHANNEL:-stable}"
PREVIOUS_TAG="${1:-$(git tag --merged HEAD --sort=-version:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -n1 || echo '')}"
VERSION_BASE_TAG="${VERSION_BASE_TAG:-$PREVIOUS_TAG}"

if [[ "$RELEASE_CHANNEL" != "stable" && "$RELEASE_CHANNEL" != "dev" ]]; then
    echo "RELEASE_CHANNEL must be 'stable' or 'dev'" >&2
    exit 1
fi

# Determine commit range
if [ -n "$PREVIOUS_TAG" ]; then
    COMMIT_RANGE="${PREVIOUS_TAG}..HEAD"
else
    COMMIT_RANGE="HEAD"
fi

get_bump_level() {
    local commit_range="$1"
    local has_breaking=false
    local has_feat=false
    local has_patch=false

    while IFS= read -r line; do
        [ -z "$line" ] && continue

        local commit_hash
        local commit_msg
        local commit_body
        commit_hash=$(echo "$line" | cut -d'|' -f1)
        commit_msg=$(echo "$line" | cut -d'|' -f3-)

        if [[ "$commit_msg" =~ ^(ci|agent|chore|doc|Merge|Revert) ]]; then
            continue
        fi

        commit_body=$(git log -1 --pretty=format:"%b" "$commit_hash" 2>/dev/null || echo "")
        if [[ "$commit_body" == *"BREAKING CHANGE:"* ]] || [[ "$commit_msg" == *"!:"* ]]; then
            has_breaking=true
        fi

        if echo "$commit_msg" | grep -qE '^(feat|fix|update|ui|refactor|perf)(\([^)]+\))?!?:[[:space:]]+.+$'; then
            case "$(echo "$commit_msg" | sed -E 's/^(feat|fix|update|ui|refactor|perf).*/\1/')" in
                feat)
                    has_feat=true
                    ;;
                fix|update|ui|refactor|perf)
                    has_patch=true
                    ;;
            esac
        fi
    done < <(git log --pretty=format:"%h|%H|%s" "$commit_range" 2>/dev/null || true)

    if [ "$has_breaking" = true ]; then
        echo "breaking"
        return
    fi

    if [ "$has_feat" = true ]; then
        echo "feat"
        return
    fi

    if [ "$has_patch" = true ]; then
        echo "patch"
        return
    fi

    echo "none"
}

# Initialize arrays for each type
features=()
fixes=()
updates=()
ui_changes=()
refactoring=()
performance=()

# Version bump flags
HAS_BREAKING=false
HAS_FEAT=false
HAS_PATCH=false

# Parse commits
while IFS= read -r line; do
    [ -z "$line" ] && continue

    # Extract commit hash (short), full hash, and message
    commit_hash=$(echo "$line" | cut -d'|' -f1)
    commit_hash_full=$(echo "$line" | cut -d'|' -f2)
    commit_msg=$(echo "$line" | cut -d'|' -f3-)

    # Skip excluded types and merge commits
    if [[ "$commit_msg" =~ ^(ci|agent|chore|doc|Merge|Revert) ]]; then
        continue
    fi

    # Check for breaking change in commit body
    commit_body=$(git log -1 --pretty=format:"%b" "$commit_hash" 2>/dev/null || echo "")
    if [[ "$commit_body" == *"BREAKING CHANGE:"* ]] || [[ "$commit_msg" == *"!:"* ]]; then
        HAS_BREAKING=true
    fi

    if echo "$commit_msg" | grep -qE '^(feat|fix|update|ui|refactor|perf)(\([^)]+\))?!?:[[:space:]]+.+$'; then
        type=$(echo "$commit_msg" | sed -E 's/^(feat|fix|update|ui|refactor|perf).*/\1/')
        scope=$(echo "$commit_msg" | sed -E 's/^[^(:]+\(([^)]+)\):.*/\1/' | grep -v "^$commit_msg$" || true)
        desc=$(echo "$commit_msg" | sed -E 's/^[^(:]+(\([^)]+\))?!?:[[:space:]]+//')

        if [ -n "$scope" ]; then
            scope="${scope#\(}"
            scope="${scope%\)}"
        fi

        REPO_URL="https://github.com/${GITHUB_REPOSITORY:-}"
        if [ -n "$REPO_URL" ] && [ "$REPO_URL" != "https://github.com/" ]; then
            commit_link="([${commit_hash}](${REPO_URL}/commit/${commit_hash_full}))"
        else
            commit_link=""
        fi

        if [ -n "$scope" ]; then
            entry="- **${scope}**: ${desc} ${commit_link}"
        else
            entry="- ${desc} ${commit_link}"
        fi

        case "$type" in
            feat)
                HAS_FEAT=true
                features+=("$entry")
                ;;
            fix)
                HAS_PATCH=true
                fixes+=("$entry")
                ;;
            update)
                HAS_PATCH=true
                updates+=("$entry")
                ;;
            ui)
                HAS_PATCH=true
                ui_changes+=("$entry")
                ;;
            refactor)
                HAS_PATCH=true
                refactoring+=("$entry")
                ;;
            perf)
                HAS_PATCH=true
                performance+=("$entry")
                ;;
        esac
    fi
done < <(git log --pretty=format:"%h|%H|%s" "$COMMIT_RANGE" 2>/dev/null || true)

if [ -n "$VERSION_BASE_TAG" ]; then
    VERSION_BASE_RANGE="${VERSION_BASE_TAG}..HEAD"
else
    VERSION_BASE_RANGE="HEAD"
fi

BASE_BUMP_LEVEL=$(get_bump_level "$VERSION_BASE_RANGE")

# Calculate new base version
if [ -n "$VERSION_BASE_TAG" ]; then
    PREV_VERSION="${VERSION_BASE_TAG#v}"
    PREV_VERSION="${PREV_VERSION%%-dev.*}"

    if [[ "$PREV_VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        MAJOR="${BASH_REMATCH[1]}"
        MINOR="${BASH_REMATCH[2]}"
        PATCH="${BASH_REMATCH[3]}"
    else
        MAJOR=0
        MINOR=0
        PATCH=0
    fi
else
    MAJOR=0
    MINOR=0
    PATCH=0
fi

HAS_VERSION_BUMP=false
if [ "$HAS_BREAKING" = true ] || [ "$HAS_FEAT" = true ] || [ "$HAS_PATCH" = true ]; then
    HAS_VERSION_BUMP=true
fi

case "$BASE_BUMP_LEVEL" in
    breaking)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    feat)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
esac

COMPARISON_VERSION="${PREVIOUS_TAG#v}"
COMPARISON_VERSION="${COMPARISON_VERSION%%-dev.*}"
if [[ "$COMPARISON_VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    COMPARISON_MAJOR="${BASH_REMATCH[1]}"
    COMPARISON_MINOR="${BASH_REMATCH[2]}"
    COMPARISON_PATCH="${BASH_REMATCH[3]}"

    if [ "$COMPARISON_MAJOR" -gt "$MAJOR" ] || \
       { [ "$COMPARISON_MAJOR" -eq "$MAJOR" ] && [ "$COMPARISON_MINOR" -gt "$MINOR" ]; } || \
       { [ "$COMPARISON_MAJOR" -eq "$MAJOR" ] && [ "$COMPARISON_MINOR" -eq "$MINOR" ] && [ "$COMPARISON_PATCH" -gt "$PATCH" ]; }; then
        MAJOR="$COMPARISON_MAJOR"
        MINOR="$COMPARISON_MINOR"
        PATCH="$COMPARISON_PATCH"
    fi
fi

BASE_VERSION="${MAJOR}.${MINOR}.${PATCH}"
NEW_VERSION="$BASE_VERSION"

if [ "$HAS_VERSION_BUMP" = true ] && [ "$RELEASE_CHANNEL" = "dev" ]; then
    if ! [[ "${DEV_RELEASE_NUMBER:-}" =~ ^[0-9]+$ ]] || [ "${DEV_RELEASE_NUMBER}" -lt 1 ]; then
        echo "DEV_RELEASE_NUMBER must be a positive integer for dev releases" >&2
        exit 1
    fi

    NEW_VERSION="${BASE_VERSION}-dev.${DEV_RELEASE_NUMBER}"
fi

# Output whether we should skip release and the new version to stderr for workflow capture
echo "skip_release=$([ "$HAS_VERSION_BUMP" = true ] && echo 'false' || echo 'true')" >&2
echo "v${NEW_VERSION}" >&2

# Output changelog to stdout
output_section() {
    local title="$1"
    shift
    local arr=("$@")

    if [ ${#arr[@]} -eq 0 ]; then
        return
    fi

    echo "## ${title}"
    printf "%s\n" "${arr[@]}"
    echo ""
}

if [ ${#features[@]} -gt 0 ]; then
    output_section "Features" "${features[@]}"
fi

if [ ${#fixes[@]} -gt 0 ]; then
    output_section "Fixes" "${fixes[@]}"
fi

if [ ${#updates[@]} -gt 0 ]; then
    output_section "Updates" "${updates[@]}"
fi

if [ ${#ui_changes[@]} -gt 0 ]; then
    output_section "UI Changes" "${ui_changes[@]}"
fi

if [ ${#refactoring[@]} -gt 0 ]; then
    output_section "Refactoring" "${refactoring[@]}"
fi

if [ ${#performance[@]} -gt 0 ]; then
    output_section "Performance" "${performance[@]}"
fi

if [ ${#features[@]} -eq 0 ] && [ ${#fixes[@]} -eq 0 ] && [ ${#updates[@]} -eq 0 ] && \
   [ ${#ui_changes[@]} -eq 0 ] && [ ${#refactoring[@]} -eq 0 ] && [ ${#performance[@]} -eq 0 ]; then
    echo "*No notable changes in this release.*"
fi
