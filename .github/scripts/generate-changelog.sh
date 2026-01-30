#!/bin/bash
set -euo pipefail

# Generate changelog and calculate next version based on conventional commits
# Usage: ./generate-changelog.sh [previous_tag] [current_tag]
# Outputs: Markdown changelog to stdout, new version to stderr

# Get tags
CURRENT_TAG="${2:-${GITHUB_REF_NAME:-$(git describe --tags --abbrev=0 2>/dev/null || echo '')}}"
PREVIOUS_TAG="${1:-$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || git tag --sort=-creatordate | sed -n '2p' 2>/dev/null || echo '')}"

# Determine commit range
if [ -n "$PREVIOUS_TAG" ]; then
    COMMIT_RANGE="${PREVIOUS_TAG}..HEAD"
else
    COMMIT_RANGE="HEAD"
fi

# Initialize arrays for each type
declare -a features fixes updates ui_changes refactoring performance

# Version bump flags
HAS_BREAKING=false
HAS_FEAT=false
HAS_PATCH=false

# Parse commits
while IFS= read -r line; do
    [ -z "$line" ] && continue
    
    # Extract commit hash and message
    commit_hash=$(echo "$line" | cut -d'|' -f1)
    commit_msg=$(echo "$line" | cut -d'|' -f2-)
    
    # Skip excluded types and merge commits
    if [[ "$commit_msg" =~ ^(ci|agent|chore|doc|Merge|Revert) ]]; then
        continue
    fi
    
    # Check for breaking change in commit body
    commit_body=$(git log -1 --pretty=format:"%b" "$commit_hash" 2>/dev/null || echo "")
    if [[ "$commit_body" == *"BREAKING CHANGE:"* ]] || [[ "$commit_msg" == *"!:"* ]]; then
        HAS_BREAKING=true
    fi
    
    # Parse conventional commit - using POSIX regex without extended patterns
    if echo "$commit_msg" | grep -qE '^(feat|fix|update|ui|refactor|perf)(\([^)]+\))?!?:[[:space:]]+.+$'; then
        # Extract parts using sed
        type=$(echo "$commit_msg" | sed -E 's/^(feat|fix|update|ui|refactor|perf).*/\1/')
        scope=$(echo "$commit_msg" | sed -E 's/^[^(:]+\(([^)]+)\):.*/\1/' | grep -v "^$commit_msg$" || true)
        desc=$(echo "$commit_msg" | sed -E 's/^[^(:]+(\([^)]+\))?!?:[[:space:]]+//')
        # Clean scope - remove parentheses if present
        if [ -n "$scope" ]; then
            scope="${scope#\(}"
            scope="${scope%\)}"
        fi
        
        # Format entry
        if [ -n "$scope" ]; then
            entry="- **${scope}**: ${desc}"
        else
            entry="- ${desc}"
        fi
        
        # Track version bump type
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
done < <(git log --pretty=format:"%h|%s" "$COMMIT_RANGE" 2>/dev/null || echo "")

# Calculate new version
if [ -n "$PREVIOUS_TAG" ]; then
    # Extract version from previous tag (remove 'v' prefix)
    PREV_VERSION="${PREVIOUS_TAG#v}"
    
    # Parse version components
    if [[ "$PREV_VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        MAJOR="${BASH_REMATCH[1]}"
        MINOR="${BASH_REMATCH[2]}"
        PATCH="${BASH_REMATCH[3]}"
    else
        # Invalid format, default to 0.0.0
        MAJOR=0
        MINOR=0
        PATCH=0
    fi
else
    # No previous tag, start fresh
    MAJOR=0
    MINOR=0
    PATCH=0
fi

# Check if there are any version-bumping commits
HAS_VERSION_BUMP=false
if [ "$HAS_BREAKING" = true ] || [ "$HAS_FEAT" = true ] || [ "$HAS_PATCH" = true ]; then
    HAS_VERSION_BUMP=true
fi

# Apply version bump based on priority: breaking > feat > patch
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

NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"

# Output whether we should skip release and the new version to stderr for workflow capture
echo "skip_release=$([ "$HAS_VERSION_BUMP" = true ] && echo 'false' || echo 'true')" >&2
echo "v${NEW_VERSION}" >&2

# Output changelog to stdout
echo "# Changelog"
echo ""
echo "**Release: v${NEW_VERSION}**"
echo ""

# Output sections only if they have entries
output_section() {
    local title="$1"
    shift
    local arr=("$@")
    
    if [ ${#arr[@]} -gt 0 ]; then
        echo "## ${title}"
        printf "%s\n" "${arr[@]}"
        echo ""
    fi
}

output_section "Features" "${features[@]:-}"
output_section "Fixes" "${fixes[@]:-}"
output_section "Updates" "${updates[@]:-}"
output_section "UI Changes" "${ui_changes[@]:-}"
output_section "Refactoring" "${refactoring[@]:-}"
output_section "Performance" "${performance[@]:-}"

# If no changes found, add a note
# Temporarily disable nounset to check array lengths safely
set +u
if [ ${#features[@]} -eq 0 ] && [ ${#fixes[@]} -eq 0 ] && [ ${#updates[@]} -eq 0 ] && \
   [ ${#ui_changes[@]} -eq 0 ] && [ ${#refactoring[@]} -eq 0 ] && [ ${#performance[@]} -eq 0 ]; then
    echo "*No notable changes in this release.*"
fi
set -u
