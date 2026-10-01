#!/usr/bin/env bash
# Fails when a workflow uses an action that is not pinned to the full SHA of a commit.
#
# A tag such as @v4 is a pointer its owner can move. On 19 March 2026 someone moved 76 tags of trivy-action, and every
# workflow that used them ran a credential stealer (CVE-2026-33634). A 40-character SHA cannot be moved. Dependabot
# bumps the SHA, and the version in the comment next to it, when a new release comes out.
#
# What is not an action from a repository (a local "./..." action or a "docker://...@sha256:" image) is left out.
set -euo pipefail

workflows="${1:-.github/workflows}"
unpinned=0

while IFS= read -r match; do
  file="${match%%:*}"
  rest="${match#*:}"
  line="${rest%%:*}"
  ref="$(sed -E 's/\r$//; s/^[[:space:]]*(-[[:space:]]*)?uses:[[:space:]]*//; s/[[:space:]]+#.*$//; s/^["'\'']//; s/["'\'']$//' <<< "${rest#*:}")"
  case "$ref" in
    ./*|docker://*@sha256:*) continue ;;
  esac
  if [[ ! "$ref" =~ ^[^@[:space:]]+@[0-9a-f]{40}$ ]]; then
    echo "::error file=${file},line=${line}::${ref} is not pinned to a commit SHA"
    unpinned=$((unpinned + 1))
  fi
done < <(grep -rnE '^[[:space:]]*(-[[:space:]]*)?uses:' "$workflows" --include='*.yml' --include='*.yaml')

if (( unpinned > 0 )); then
  echo "${unpinned} action(s) not pinned to a commit SHA."
  exit 1
fi
echo "Every action is pinned to a commit SHA."
