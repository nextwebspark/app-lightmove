#!/usr/bin/env bash
# PreToolUse hook: nothing lands on main (or master) from a Claude session — no PR merged into it, no
# `git merge` run while it is checked out, no push to it. Merging main INTO a feature branch, and PRs
# into any other base, are allowed. Fail-closed: a PR whose base cannot be read is treated as main.

input=$(cat)
cmd=$(jq -r '.tool_input.command // empty' <<<"$input" 2>/dev/null)
cwd=$(jq -r '.cwd // empty' <<<"$input" 2>/dev/null)
[ -n "$cmd" ] || exit 0

protected='^(main|master)$'

deny() {
  jq -n --arg reason "$1" \
    '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$reason}}'
  exit 0
}

# The directory a `cd <dir> &&` prefix moves into, else the session's own.
workdir=$(sed -nE 's/.*(^|[;&|[:space:]])cd[[:space:]]+("[^"]+"|[^[:space:];&|]+).*/\2/p' <<<"$cmd" | tail -1 | tr -d '"')
workdir=${workdir:-$cwd}
[ -d "$workdir" ] || workdir=$cwd
workdir=${workdir/#\~/$HOME}

pr_base() {
  (cd "${workdir:-.}" 2>/dev/null && gh pr view $1 --json baseRefName -q .baseRefName 2>/dev/null)
}

# gh pr merge [<number> | <url> | <branch>]
if grep -Eq '(^|[;&|[:space:]])gh[[:space:]]+pr[[:space:]]+merge\b' <<<"$cmd"; then
  target=$(sed -nE 's/.*gh[[:space:]]+pr[[:space:]]+merge[[:space:]]+([^-[:space:]][^[:space:];&|]*).*/\1/p' <<<"$cmd")
  base=$(pr_base "$target")
  if [ -z "$base" ] || grep -Eq "$protected" <<<"$base"; then
    deny "Merging a PR into ${base:-main (base unreadable)} is blocked — merges to main are done by the user"
  fi
fi

# gh api …/pulls/<n>/merge
api_pr=$(sed -nE 's/.*gh[[:space:]]+api[^;&|]*pulls\/([0-9]+)\/merge.*/\1/p' <<<"$cmd")
if [ -n "$api_pr" ]; then
  base=$(pr_base "$api_pr")
  if [ -z "$base" ] || grep -Eq "$protected" <<<"$base"; then
    deny "Merging PR #$api_pr into ${base:-main (base unreadable)} through the API is blocked — merges to main are done by the user"
  fi
fi

current=$(git -C "${workdir:-.}" rev-parse --abbrev-ref HEAD 2>/dev/null)

# git merge (not merge-base / merge-file / merge-tree) while main is checked out
if grep -Eq '(^|[;&|[:space:]])git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+merge([[:space:]]|$)' <<<"$cmd"; then
  if grep -Eq "$protected" <<<"$current"; then
    deny "git merge on $current is blocked — merges to main are done by the user"
  fi
fi

# git push to main: an explicit main/master refspec, or a bare push while main is checked out
if grep -Eq '(^|[;&|[:space:]])git[[:space:]]+push\b' <<<"$cmd"; then
  push_args=$(sed -nE 's/.*git[[:space:]]+push([^;&|]*).*/\1/p' <<<"$cmd")
  if grep -Eq '(^|[[:space:]:+])(refs/heads/)?(main|master)([[:space:]]|$)' <<<"$push_args"; then
    deny "Pushing to main is blocked — changes reach main through a PR the user merges"
  fi
  refspecs=$(awk '{n=0; for (i=1;i<=NF;i++) if ($i !~ /^-/) n++; print n}' <<<"$push_args")
  if [ "${refspecs:-0}" -le 1 ] && grep -Eq "$protected" <<<"$current"; then
    deny "Pushing from $current is blocked — changes reach main through a PR the user merges"
  fi
fi

exit 0
