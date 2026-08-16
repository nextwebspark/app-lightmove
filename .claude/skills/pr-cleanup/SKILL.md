---
name: pr-cleanup
description: Clean up a pull request after review — collect the review comments and related issues, fix only what belongs to this PR, verify, commit in logical units, and reply on every review thread. Use when asked to address PR feedback, clean a PR, or respond to review comments. Usage - /pr-cleanup <PR number>
---

# PR cleanup

Given a PR number `N`, work through review feedback with strict scope discipline.

## 1. Collect

```bash
gh pr view N --json title,files,comments,reviews
gh api repos/{owner}/{repo}/pulls/N/comments   # inline review comments, with their ids
gh issue list --limit 20                        # sweep open issues for overlap
```

Read every inline comment and the review body. Note each comment's `id` — replies target it.

## 2. Scope rule (the heart of this skill)

Fix the inline review comments, plus any refactor whose **whole** scope sits inside the PR's own
changed-file set. Everything else stays out, even when open issues touch files the PR modified:

- The reviewer's own split is the guide: **inline comments = this branch; separately-filed issues =
  repo-wide, their own PR later.**
- A partial repo-wide refactor (e.g. splitting 2 of 8 DTO files) is worse than none — it leaves the
  repo half-converted. Take an issue only when its entire scope fits the PR's diff.
- A file the PR is *introducing* is fair game for issue-driven additions (e.g. an untested new page
  flagged by a coverage issue): that slice of the issue belongs to this PR; the rest stays with the
  issue.

## 3. Fix and verify

Make the changes. Then verify with this repo's suites — **ask before running anything** (the user's
stack may be live; Flyway at boot hits the shared dev DB):

```bash
cd apps/api && ./mvnw test     # needs Docker (Testcontainers)
cd apps/web && npx vitest
cd apps/web && npm run build   # the real frontend typecheck
```

## 4. Commit — with approval

**Never commit or push without asking first; one approval covers one commit/push.** Present the file
list and proposed messages, then commit in logical units (review fixes / tests / refactor), adding
`Closes #N` where a change fully resolves a filed issue.

## 5. Reply on every thread

1–2 lines each, naming the fixing commit:

```bash
gh api repos/{owner}/{repo}/pulls/N/comments/{comment_id}/replies -f body="Fixed in <sha> — <what changed>."
```

## 6. Report

Summarise per comment: fixed (commit), or why out of scope (which issue keeps it).
