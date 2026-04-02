#!/bin/bash
set -euo pipefail

# Generate changelog and calculate the next version based on conventional commits.
# Usage: ./generate-changelog.sh [previous_stable_tag]
# Environment:
#   RELEASE_CHANNEL=stable|dev (default: stable)
#   DEV_RELEASE_NUMBER=<positive integer> required when RELEASE_CHANNEL=dev
# Outputs:
#   - Markdown changelog to stdout
#   - skip_release=true|false and version to stderr

RELEASE_CHANNEL="${RELEASE_CHANNEL:-stable}"
PREVIOUS_TAG="${1:-$(git tag --merged HEAD --sort=-version:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -n1 || echo '')}"

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

# Calculate new base version
if [ -n "$PREVIOUS_TAG" ]; then
    PREV_VERSION="${PREVIOUS_TAG#v}"

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

if [ "$HAS_BREAKING" = true ]; then
    MAJOR=$((MAJOR + 1))
    MINOR=0
    PATCH=0
elif [ "$HAS_FEAT" = true ]; then
    MINOR=$((MINOR + 1))
    PATCH=0
elif [ "$HAS_PATCH" = true ]; then
    PATCH=$((PATCH + 1))
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
echo "# Changelog"
echo ""
echo "**Release: v${NEW_VERSION}**"
echo ""

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

output_section "Features" "${features[@]}"
output_section "Fixes" "${fixes[@]}"
output_section "Updates" "${updates[@]}"
output_section "UI Changes" "${ui_changes[@]}"
output_section "Refactoring" "${refactoring[@]}"
output_section "Performance" "${performance[@]}"

if [ ${#features[@]} -eq 0 ] && [ ${#fixes[@]} -eq 0 ] && [ ${#updates[@]} -eq 0 ] && \
   [ ${#ui_changes[@]} -eq 0 ] && [ ${#refactoring[@]} -eq 0 ] && [ ${#performance[@]} -eq 0 ]; then
    echo "*No notable changes in this release.*"
fi
