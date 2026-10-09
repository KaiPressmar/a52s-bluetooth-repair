#!/usr/bin/env bash
# Cuts release branches and patch releases. Pushing to release/X.Y triggers the signed release
# workflow (.github/workflows/release.yml). Branch model and full checklist: RELEASING.md.
#
#   scripts/release.sh cut X.Y.0                  create release/X.Y from origin/main
#   scripts/release.sh patch X.Y.Z <commit>...    cherry-pick fixes from main onto release/X.Y
#
# Options: --dry-run (prepare and show everything, push nothing), --remote <name> (default origin).
set -euo pipefail

REMOTE=origin
DRY_RUN=false

die() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

usage() {
    sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-1}"
}

changelog_has() { # <ref> <version>
    git show "$1:CHANGELOG.md" | grep -Eq "^## ${2//./\\.}( |$)"
}

changelog_section() { # <ref> <version>: the '## version' section including its heading
    git show "$1:CHANGELOG.md" | awk -v v="$2" '
        /^##? / { if (found) exit; if ($1 == "##" && $2 == v) found = 1 }
        found { print }'
}

remote_branch_exists() { git ls-remote --exit-code --heads "$REMOTE" "$1" >/dev/null 2>&1; }
tag_exists() { git ls-remote --exit-code --tags "$REMOTE" "refs/tags/$1" >/dev/null 2>&1; }

push() { # <src> <dst ref>
    if $DRY_RUN; then
        info "dry run: would push $1 to $REMOTE $2"
    else
        git push "$REMOTE" "$1:$2"
    fi
}

cut() {
    local version="$1"
    [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.0$ ]] || die "cut needs a new minor or major version X.Y.0, got '$version'"
    local line="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"
    local branch="release/$line"
    local main="$REMOTE/main"

    git fetch --quiet --tags "$REMOTE"
    remote_branch_exists "$branch" && die "$branch already exists; use 'patch' for X.Y.Z releases"
    tag_exists "v$version" && die "tag v$version already exists"
    [ "$(git show "$main:RELEASE_VERSION" | tr -d '[:space:]')" = "$version" ] \
        || die "RELEASE_VERSION on $main is not $version; merge the 'Prepare v$version' PR first"
    changelog_has "$main" "$version" \
        || die "CHANGELOG.md on $main has no '## $version' section; merge the 'Prepare v$version' PR first"

    info "Cutting $branch from $main ($(git rev-parse --short "$main"))"
    push "$main" "refs/heads/$branch"
    $DRY_RUN || info "Pushed $branch: the release workflow now publishes v$version"
}

patch() {
    local version="$1"; shift
    [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([1-9][0-9]*)$ ]] || die "patch needs X.Y.Z with Z > 0, got '$version'"
    [ "$#" -gt 0 ] || die "name the commits from main to cherry-pick (fixes land on main first)"
    local line="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"
    local branch="release/$line"
    local main="$REMOTE/main"

    git fetch --quiet --tags "$REMOTE"
    remote_branch_exists "$branch" || die "$branch does not exist; cut it first"
    tag_exists "v$version" && die "tag v$version already exists"
    changelog_has "$main" "$version" \
        || die "CHANGELOG.md on $main has no '## $version' section; add it on main first"
    local commit
    for commit in "$@"; do
        git merge-base --is-ancestor "$commit" "$main" 2>/dev/null \
            || die "$commit is not on $main; fixes land on main first, then get cherry-picked"
        git merge-base --is-ancestor "$commit" "$REMOTE/$branch" \
            && die "$commit is already on $branch"
    done

    local worktree
    worktree="$(mktemp -d "${TMPDIR:-/tmp}/release-$line.XXXXXX")"
    git worktree add --quiet --detach "$worktree" "$REMOTE/$branch"
    info "Preparing v$version in $worktree"
    (
        cd "$worktree"
        for commit in "$@"; do
            git cherry-pick -x "$commit" || {
                echo "error: cherry-pick of $commit failed. Resolve it in $worktree, then finish by hand" >&2
                echo "       (see RELEASING.md) or run 'git worktree remove --force $worktree' to abandon." >&2
                exit 1
            }
        done
        # Insert the release's changelog section from main above the newest release on this line.
        SECTION="$(changelog_section "$main" "$version")" awk '
            !done && /^## / { print ENVIRON["SECTION"]; print ""; done = 1 }
            { print }' CHANGELOG.md > CHANGELOG.md.new
        mv CHANGELOG.md.new CHANGELOG.md
        echo "$version" > RELEASE_VERSION
        git add CHANGELOG.md RELEASE_VERSION
        git commit --quiet -m "Release v$version"
        git log --oneline "$REMOTE/$branch..HEAD"
        push HEAD "refs/heads/$branch"
    ) || exit 1
    git worktree remove --force "$worktree"
    $DRY_RUN || info "Pushed $branch: the release workflow now publishes v$version"
}

args=()
while [ "$#" -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=true ;;
        --remote) REMOTE="${2:?--remote needs a name}"; shift ;;
        -h|--help) usage 0 ;;
        *) args+=("$1") ;;
    esac
    shift
done
set -- "${args[@]}"
[ "$#" -ge 2 ] || usage

git rev-parse --git-dir >/dev/null 2>&1 || die "run inside the repository"
command="$1"; shift
case "$command" in
    cut) [ "$#" -eq 1 ] || usage; cut "$1" ;;
    patch) patch "$@" ;;
    *) usage ;;
esac
