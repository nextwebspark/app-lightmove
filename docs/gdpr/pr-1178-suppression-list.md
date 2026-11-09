# PR #1178: the suppression list (#920, part 1)

Part of GDPR epic #917 (section D of #1080). Base: `main`. Migration: **V195**.
Stacked above it: #1179 (erasure), #1180 (restrict processing), #1185/#1186 (retention), #1188/#1190 (offboarding, account deletion).

## What it does

A workspace gets a **suppression list**: the people no door may file again. A person goes on it for one of three
reasons:

| Reason | Who writes it |
|---|---|
| `erased` | the erasure itself (#1179); the API refuses it from an admin |
| `objected` | an admin, when a person objects to being processed |
| `manual` | an admin, for any other reason (the default) |

A person is recognised by their **LinkedIn profile** or an **email address**, never by phone or name, the same two
keys `PersonMatcher` uses. Once a key is on the list:

- the add form, the browser plugin and a spreadsheet row are refused with `409 PERSON_SUPPRESSED`;
- mapping someone the workspace already knows is refused when **that person's own** profile or any address they
  hold is listed, whatever the request carries;
- an edit to someone already filed is refused only if it **adds** a suppressed profile or address. The keys a person
  already holds never block their own edits;
- Find executives, People Search and the Candidates page's Add to position leave the person out without filing
  them. The first two don't fetch their photo either.

The list is API-only for now. Its screen (Settings → Data & retention) is drawn in #1177 and arrives with #1186.

## How it does it

### Storage: keyed hashes, never the key

`app_lm_workspace_suppression` (V195) holds one row per key:

| Column | Meaning |
|---|---|
| `kind` | `PROFILE_SLUG` or `EMAIL` |
| `value_hash` | HMAC-SHA-256 of `workspaceId:kind:normalisedKey` under the suppression keyset, unique per workspace and kind |
| `key_id` | the keyset key the hash was taken under |
| `reason` | `ERASED`, `OBJECTED` or `MANUAL` |
| `note` | an admin's own words, at most 500 characters |
| `created_by`, `created_at` | who added it, and when |

`SuppressionKey` normalises each key exactly the way the matcher reads a person:

- a profile is the slug `LinkedInUrls.profileSlugOrNull` takes, lower-cased;
- an email is its ledger key, `CandidateContact.keyOf(EMAIL, …)`.

So the list recognises exactly the people the matcher would find. Because the workspace id is part of the hash, one
person suppressed by two firms gives two unrelated hashes. No read returns a slug or an address.

The hash is keyed (`core/crypto`'s `KeyedHasher`, a Tink PRF keyset of HMAC-SHA-256 keys at
`lightmove.crypto.suppression-keyset`, mounted from the `lightmove-suppression-keyset` secret). Without the key, a copy
of the table cannot be tested against a guessed address or slug.

- **Rotation:** add a key to the keyset and promote it. New entries are written under the new primary, and a lookup
  asks every key's hash, so entries written under the old key still match.
- **No keyset:** the admin's add is refused with `SUPPRESSION_UNAVAILABLE` (503), and a workspace with no entries
  files as before.
- **Fail closed:** a workspace holding an entry whose `key_id` the keyset does not hold is refused with
  `SUPPRESSION_UNAVAILABLE` at every door. That covers a deployment that lost its keyset, or a key deleted too early.
  Answering "not listed" there would let every suppressed person back in.

Adding a key that is already listed changes nothing (`INSERT … ON CONFLICT DO NOTHING`): the first reason and note
stay as the record.

The table has forced row-level security from its first migration (`ENABLE`, `FORCE`, an `app_lm_tenant` policy over
`app_lm_tenant_visible(workspace_id)`), so `TenantTableGuard` accepts it at boot.

### The gate, asked at every door

The doors depend on `SuppressionGate`, which only reads. The admin routes and the erasure write through
`SuppressionListService`, and both find entries through `SuppressionEntries`, which owns the hashing and the
fail-closed check. The gate asks two kinds of question.

About the keys a request carries:

- `refuseSuppressed(workspaceId, linkedinUrl, emails)` throws `PERSON_SUPPRESSED`;
- `suppressedProfileSlugsOf(workspaceId, slugs)` takes a whole page of hits and answers in one query, with every
  spelling of a suppressed slug (`Jane-Doe` and `jane-doe` alike).

About a person the workspace already holds, by the profile and addresses stored on them:

- `refuseSuppressedPerson(workspaceId, person)` throws `PERSON_SUPPRESSED`;
- `suppressedPeopleOf(workspaceId, people)` answers for any number of people in one query.

| Door | Where it asks | What happens |
|---|---|---|
| Add form, plugin, spreadsheet | `CandidateService.add` | refused with `PERSON_SUPPRESSED`, before a person is matched or founded |
| Researched filing (plugin research, People Search, Find executives) | `CandidateService.addResearched` | refused before an employer is filed |
| Any filing that maps a known person | `CandidateService.file` → `refuseSuppressedPerson` | refused by the person's own keys. This covers a name-only add answered with `existingPersonId`, and a capture matched by profile to someone suppressed by email. A refusal in `addResearched` (`REQUIRES_NEW`) also rolls back the employer it filed just before |
| Candidates page → Add to position | `CandidateService.mapFromPool` → `suppressedPeopleOf` | suppressed people are skipped, and the response counts them as `suppressed`. The SPA's toast says who was left out |
| Profile edit | `CandidateService.replace` → `refuseSuppressedAdditions` | refused only for a profile slug it changes to |
| Contact section save | `CandidateService.replaceContacts` → `refuseSuppressedAdditions` | refused only for an address the person did not already hold |
| Find executives | `ExecutiveSourcingWorker.usable()` | suppressed hits dropped before photos are fetched; one suppressed mid-run (refused by `addResearched`) is counted as skipped, like `CANDIDATE_ALREADY_MAPPED` |
| People Search: filing ticked people | `PeopleSearchFiling.add` | counted as skipped |
| People Search: reading a page | `StrategyPeopleService.pagesOf` | left off the page as it is read, the way people at declined companies already are |

On People Search, the bought page itself is never narrowed: it is cached under its own query for every workspace,
so only this workspace's read of it filters.

### Admin API

All three routes require `WORKSPACE_MANAGE`:

- `GET /api/v1/workspace/suppressions` lists the entries: kind, reason, note, who added it, when.
- `POST /api/v1/workspace/suppressions` takes `linkedinUrl` and/or `email`, `reason` (`manual` or `objected`) and
  `note`, and answers with the entries holding its keys: `201` when any was new, `200` when all were already
  listed. A link pasted without `https://` is accepted.
- `DELETE /api/v1/workspace/suppressions/{id}` lifts an entry, `204`. From then on the person can be filed again.

Both writes are audited, `PERSON_SUPPRESSION_ADDED` and `PERSON_SUPPRESSION_REMOVED`. The audit metadata records
the kinds, the reason and how many entries were new, never the key. Both events are phrased in the customer audit
log ("Added someone to the suppression list") under its Settings category.

### Wording and docs

- `PERSON_SUPPRESSED`: added to `ErrorCode`, the SPA's `errorCodes.ts` and the plugin's refusal copy. The message
  is "This person is on your workspace's suppression list, so they can't be added."
- `SUPPRESSION_UNAVAILABLE` (503): added to `ErrorCode` and the SPA's `errorCodes.ts`.
- `MapPeopleToPositionResponse` gains `suppressed`. The Add to position dialog's toast (`addedMessageOf`) reads, for
  example, "Added 2 people to CFO. 1 person is on your workspace's suppression list and was left out".
- "Suppression list" added to `docs/glossary.md`, with blocklist and blacklist as the words not to use.
- Three rows added to `docs/validation-matrix.md`.
- `CLAUDE.md` updated.

### The merge with main (2026-11-06)

`main` squash-merged row-level security (#1173) as V184 and V189–V192. This branch had carried its own earlier
copies of that work as V171–V173. The merge takes main's row-level security whole and re-applies only the
suppression diff, so the PR's diff against `main` is these 26 files alone.

The migration moved from V174 to **V193**. There was one code conflict, in `replaceContacts`: the merge keeps
main's primary-contact arguments and runs the suppression check before them.

### Review fixes (2026-11-07)

- The plain SHA-256 became an HMAC under the suppression keyset, with `key_id` per row (V195 edited in place: it had
  run nowhere but tests since the renumbering).
- `PersonSuppressionService` was split into `SuppressionGate` (the doors' reads), `SuppressionListService` (the admin
  routes, the audit, and `suppress` for #1179) and `SuppressionEntries`.
- `suppressedProfileSlugsOf` returns every spelling of a suppressed slug. An edit's own-slug comparison ignores case.
- `add` reads its entries back in one query, and answers `200` when nothing was new.
- `deploy.yml` mounts the keyset behind `SUPPRESSION_LIST_ENABLED`.

### The second merge with main (2026-11-07)

`main` took V193 and V194 for email signatures (#1157), so the suppression migration moved again, from V193 to
**V195**. The only conflict was the migration list in `CLAUDE.md`; both entries are kept, in version order.

## Testing

- **Integration tests:** `PersonSuppressionIntegrationTest` (8 tests). It covers:
  - only hashes are stored, and nothing readable comes back in a response;
  - every door refuses the person however the key is spelled (case, `www.`, a trailing slash, an uppercased
    address);
  - an edit is refused only for what it adds;
  - lifting an entry lets the person be filed again;
  - one firm's list never refuses another firm's filing;
  - validation, and that only an admin may use the routes.
- **Two more tests in that class:**
  - someone the workspace knows is refused by their own keys: once by `existingPersonId`, once by a plugin capture
    matched by profile while suppressed by email;
  - Add to position leaves a suppressed person out and counts them.
- **Added after review:** the stored hash is not the plain SHA-256 and carries the dev key's id; a repeat add answers
  `200`; an entry under a key the keyset lacks refuses the add form with `SUPPRESSION_UNAVAILABLE`.
- **Unit tests:**
  - `AddToPositionDialog.test.ts` covers the toast's wording.
  - `SuppressionKeyTest` pins the normalisation, the hash input and two hashes under the dev keyset.
  - `TinkKeyedHasherTest` checks that a hash is keyed and survives a rotation.
  - `SuppressionEntriesTest` covers the old-key match, the fail-closed refusals and the primary key on a write.
  - `SuppressionGateTest` covers mixed-case slugs.
- **Added cases** in `ExecutiveSourcingIntegrationTest` and `StrategyPeopleSearchIntegrationTest`: a suppressed hit
  is never filed and leaves no person behind; a suppressed person drops off the page that is read back; ticking them
  files nobody.
- **Rerun after the merge:** the suppression, People Search, Find executives, contacts, `TenantTableGuard` and
  `TenantResolvers` suites (54 tests), plus the web error-code coverage test. All pass.
- **`/verify` after the merge**, on a fresh database: migrations ran V1→V193 and the API booted. A driven flow then
  passed all 15 checks:
  - signup, a workspace and a position;
  - a suppression entry added and listed;
  - the add form and the plugin refused, by profile and by email;
  - the Contact section refused a suppressed address, then accepted it once the entry was lifted.

## Risks

Ranked from most to least serious.

### Fixed before review: two filing paths skipped the list

The first draft checked only the keys **the request carried**. Two paths could map a suppressed person that way:

- the Candidates page's Add to position (`mapFromPool`);
- a name-only add answered with `existingPersonId`.

A plugin capture matched by profile to someone suppressed by email also got through.

All three now ask about the person's own stored keys (see the doors table above), and each has a test.

### 1. The keyset is now what protects the list (medium, operational)

Since review the hashes are keyed, so reading the table alone no longer lets anyone test a guessed address or slug.
The keyset is the thing to keep:

- `lightmove-suppression-keyset` must exist before `SUPPRESSION_LIST_ENABLED` is set.
- A key may leave the keyset only once no entry uses it (`key_id`); before that, the workspaces holding those
  entries are refused at every door.

Under GDPR a keyed hash of an email is still pseudonymous personal data: the privacy notice and the record of
processing should say the list is kept.

### 2. The list depends on the matcher's normalisation forever (medium)

Every stored hash depends on how `LinkedInUrls.profileSlugOrNull` and `CandidateContact.keyOf` read a key today. If
either changes, for example in case handling, percent-decoding or email aliasing, existing entries silently stop
matching and the list lets people through. Nothing can rebuild the hashes from what is stored.

**Mitigation, built:** `SuppressionKeyTest` pins the normalisation of known inputs and their hashes under the dev
keyset, so a change to either reader fails CI instead of failing open.

### 3. Vendor slugs are hashed without percent-decoding (low)

`suppressedProfileSlugsOf` hashes the vendor's raw `linkedinId`, while an admin's URL goes through `LinkedInUrls`,
which percent-decodes. If a vendor ever sends a non-ASCII slug still encoded, the pre-filter misses it. The person
would show on a People Search page, and Find executives would fetch their photo. The filing itself is still refused,
because `addResearched` checks the full URL. So the miss is a display and a photo fetch, not a filing.

### 4. No lock between adding an entry and a filing (low)

A suppression committed while a filing is in flight can lose the race, so that one filing goes through. This is
acceptable for an admin action, but worth knowing if erasure (#1179) relies on it. Erasure deletes the person in the
same flow, which closes most of the window; #1179 should account for it.

### 5. People Search still pays for suppressed people (low)

A suppressed person on a bought page is billed and cached, then hidden as the page is read. The free count
(`…/people/count`) still includes them, so the count can be higher than what the page shows. The same is already
true for people at declined companies.

### 6. The refusal tells staff that someone is listed (low, by design)

`PERSON_SUPPRESSED` tells the staff member trying to add someone that the person is suppressed, though not why.
That is the point of the error, and it is only ever said inside the same workspace. It should still be a conscious
choice: the alternative is a generic refusal, which would leave researchers guessing.

### 7. The migration was renumbered (operational)

Any database that applied this branch's earlier `V174__workspace_suppression.sql` or `V193__workspace_suppression.sql` will fail Flyway validation on
boot, with "applied migration not resolved locally". That includes a shared dev database reached through
`npm run dev:cloud` with this branch checked out. Such a database needs its history row repaired, or a reset.

The PRs stacked on this one (#1179 onward) still number their migrations V175–V180 and carry the old row-level
security copies. Each needs the same merge from `main`, with its migrations renumbered from V196.

### 8. Not done in this PR

- No screen: until #1186, an admin can only use the API.
- The rest of #920 is not here, so this PR does not close it:
  - `processing_restricted`, the gating of the AI and enrichment workers, and the Restrict processing toggle are
    #1180;
  - the cross-workspace suppression question waits for counsel.
- Adding an entry does nothing to people already filed. They stay where they are, and only filing them again is
  blocked. Removing them is #1179's job, and restricting them is #1180's.
- Name and phone are never keys, deliberately: the matcher never matches on them. A person typed in by name alone,
  with no profile and no email, is not recognised.
