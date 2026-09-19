#!/usr/bin/env bash
# PreToolUse hook: commits and pushes to a working branch run unattended; only a push that
# would land on a protected branch (main/master) asks, and a force push stays blocked.
# Falls through to the normal permission rules when the command is not a plain git chain,
# and asks when a push target cannot be read off the command line.

set -f   # a bare word like HEAD~1 or a refspec must never be glob-expanded while parsing

cmd=$(jq -r '.tool_input.command // empty' 2>/dev/null)

case "$cmd" in
  *"git push"*|*"git commit"*) ;;
  *) exit 0 ;;
esac

decide() {
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"%s","permissionDecisionReason":"%s"}}\n' "$1" "$2"
  exit 0
}

# Quoted spans (commit messages above all) are blanked before the command is split on shell
# operators, so an "&&" inside a message is not mistaken for the start of a second command.
scrubbed=$(printf '%s' "$cmd" | sed "s/'[^']*'/__Q__/g; s/\"[^\"]*\"/__Q__/g")
segments=$(printf '%s' "$scrubbed" | sed 's/||/\n/g; s/&&/\n/g; s/[;|]/\n/g')

protected='^(main|master)$'
safe_verbs='^(push|commit|add|status|diff|log|show|branch|fetch|remote|rev-parse|config|stash|restore|switch|checkout|tag)$'

# Prints one line per branch this push would write to, or DENY:/ASK: when the flags alone settle it.
push_targets() {
  local -a refs=()
  local remote='' tok
  while [ $# -gt 0 ]; do
    tok=$1
    shift
    case "$tok" in
      -f|--force|--force-with-lease*|--force-if-includes)
        echo "DENY:Force push — blocked by project policy"; return ;;
      --all|--mirror)
        echo "ASK:Push of every branch — could land on a protected branch"; return ;;
      -o|--push-option|--receive-pack|--exec|--repo) shift ;;
      -*) ;;
      *) if [ -z "$remote" ]; then remote=$tok; else refs+=("$tok"); fi ;;
    esac
  done

  if [ ${#refs[@]} -eq 0 ]; then
    git -C "${CLAUDE_PROJECT_DIR:-.}" rev-parse --abbrev-ref HEAD 2>/dev/null
    return
  fi
  local ref dest
  for ref in "${refs[@]}"; do
    dest=${ref#+}
    dest=${dest##*:}
    printf '%s\n' "${dest#refs/heads/}"
  done
}

while IFS= read -r segment; do
  set -- $segment
  [ $# -eq 0 ] && continue
  [ "$1" = "cd" ] && continue
  [ "$1" = "git" ] || exit 0   # anything else in the chain — let the normal rules judge it
  shift
  while [ $# -gt 0 ] && [ "${1#-}" != "$1" ]; do shift; done   # git's own -C/-c style flags
  verb=$1
  [ -n "$verb" ] || exit 0
  printf '%s' "$verb" | grep -Eq "$safe_verbs" || exit 0
  [ "$verb" = "push" ] || continue

  shift
  case "$*" in *__Q__*) decide ask "Push target is quoted and cannot be read — approve it by hand" ;; esac
  targets=$(push_targets "$@")
  case "$targets" in
    DENY:*) decide deny "${targets#DENY:}" ;;
    ASK:*)  decide ask "${targets#ASK:}" ;;
    '')     decide ask "Cannot determine the branch this push lands on" ;;
  esac
  while IFS= read -r branch; do
    if printf '%s' "$branch" | grep -Eq "$protected"; then
      decide ask "Push to protected branch '$branch' — needs explicit approval"
    fi
  done <<< "$targets"
done <<< "$segments"

decide allow "Commit/push on a working branch — no approval needed"
