# Settings → Integrations: how an own app's keys should look, and what the current build gets wrong

Research for the redesign drawn in `claude-design/Settings.dc.html` (`?section=integrations&integrationsState=…`:
`overview`, `own-new`, `own-saved`, `replacing`, `verify-failed`, `expiring`). The app now draws it
(`IntegrationCard`, `OwnAppSetupForm`, `SavedOwnApp`), over the API described at the end of this page.

## How established products do it

| Product | Pattern |
|---|---|
| [Clerk](https://clerk.com/docs/guides/configure/auth-strategies/social-connections/google) | Shared development credentials by default. **Use custom credentials** reveals Client ID and Client Secret, with the redirect URI to copy into Google. |
| [Auth0](https://auth0.com/docs/authenticate/identity-providers/social-identity-providers/devkeys.md) | The same, called developer keys, with a standing warning to replace them with your own before production. |
| [Supabase](https://supabase.com/docs/guides/auth/social-login/auth-google) | An Enable switch per provider, then Client ID, Client Secret, and the callback URL to copy. |
| [Vercel, sensitive variables](https://vercel.com/academy/optimize-your-vercel-account/sensitive-env-vars) | Write-only. The row reads "•••• (cannot be read)"; editing means **replacing**, never revealing. |
| [Google Cloud console](https://developers.googleblog.com/usability-and-safety-updates-to-google-auth-platform/) (June 2025) | Client secrets are hashed. The full value is shown once, at creation; after that it is **masked, with the last few characters as an identifier**. Rotation keeps the old and new secrets valid side by side. |
| GitHub, Stripe, [Microsoft Entra](https://learn.microsoft.com/fr-ca/entra/identity-platform/how-to-add-credentials) | A secret is shown once and regenerated if lost. Entra secrets last 24 months at most (Microsoft recommends under 12); Google and Zoom secrets do not expire. |
| [Nango](https://nango.dev/docs/guides/auth/auth-guide), [WorkOS](https://workos.com/docs/admin-portal) | **Test connection / Test sign-in**: the keys are proven before anyone relies on them. |

## The pattern the mockup draws

- **A closed card per provider, with a status pill**: `Shared app`, `Not set up`, `Own app · Verified`,
  `Expires in 12 days`, `Secret expired`, `Keys refused`. The summary line repeats the essentials:
  `Your own app · 3f2a9c71…8f05 · secret ••••Jx4q`.
- **First set-up follows the provider console's order**: (1) register an app there, (2) add this redirect
  URI, (3) grant these scopes / API permissions (with **Copy all**), (4) paste its keys.
- **Fields use the console's own names**. Microsoft's are *Application (client) ID*, *Directory (tenant) ID* and
  *Client secret value*, so nobody pastes the Secret ID or the Object ID by mistake.
- **The secret is masked while typed, with a Show toggle.** Autofill is off (`autocomplete="off"`, plus
  `data-1p-ignore` and `data-lpignore`) so no password manager offers a generated password or stores the secret.
  The hint under the field says when the console shows the value only once.
- **Client checks that catch the common pastes**:
  - Leading and trailing whitespace is trimmed; whitespace inside the secret is refused.
  - A Google client ID must end in `.apps.googleusercontent.com`; a Microsoft one must be a GUID.
  - A GUID pasted as a Microsoft secret is the Secret ID: "Paste the Value column instead".
  - A tenant of `common`, `organizations` or `consumers` is refused.
  - A Microsoft expiry in the past, or more than 24 months out, is refused.
  - Expiry is asked for Microsoft only.
- **Save and verify**: the provider is asked first, and a refusal saves nothing ("Microsoft refused these keys
  (invalid_client). Nothing was saved.").
- **A saved app is read-only rows, never an empty password box**. The client ID can be copied. The secret
  shows `••••••••••••Jx4q · saved 12 Sep 2026 by Yara Haddad` with **Replace**; replacing opens only that row
  (plus the new expiry), and the old secret keeps working until the new one is saved. Under the rows: a
  verification line with **Verify again**, the redirect URI and scopes folded away, and the count of mailboxes
  connected through the app.
- **Leaving an own app says what it costs.** "Return to the shared app" and "Use a different app" both name the
  mailboxes that must connect again, and say the keys are deleted here *and* from Recall.ai's copies.

## Security review of the current build

What already meets the standard:
- **Storage**: Tink envelope encryption (AES-256-GCM, a data key per value) under a Secret Manager keyset
  (`core/crypto`). The associated data binds each ciphertext to its workspace and to
  `integration-client-secret:<PROVIDER>`, so a copied value will not decrypt elsewhere.
- **No read-back**: the API is write-only (`secretSet` alone); `UpdateWorkspaceIntegrationRequest`, `OwnAppKeys`
  and `ProviderCredentials` redact the secret in `toString`; validation logs name fields, never values; the
  audit event records `secretChanged`, never the value.
- **Gates**: every route is `WORKSPACE_MANAGE` on the caller's own workspace. Client and tenant IDs are held to
  URL-safe characters, and the tenant goes into the token URL as one encoded path segment.
- **Rules**: a new client ID needs its own secret; the shared mode refuses keys; Microsoft's admin-consent
  return is checked against an HMAC `state`.

Findings, most severe first. Findings 1–7 are fixed; 8 is open.

1. **Recall.ai keeps the old keys.** `RecallCalendars.reconcile` sends the app's client ID and secret to Recall
   only when a mailbox connects or when calendar sync changes, and `WorkspaceIntegrationService` publishes
   nothing when an app changes. Two consequences:
   - **Rotating the secret** (what the expiry email asks for) leaves every Recall calendar on the old one, and
     they stop syncing once it is deleted in the console.
   - **"Return to the shared app"** deletes the keys from Uncava, as its dialog promises, but every Recall
     calendar still holds the firm's own client secret and keeps reading through that app.

   *Fixed*: new keys for the same app publish `ProviderAppKeysReplaced`, on which `RecallCalendars` hands each of
   the workspace's calendars at that provider the new ones once the save commits. A change of app (including
   "Return to the shared app") withdraws the mailboxes connected through the old one, which releases their
   calendars (finding 2). A failed update at Recall is logged and not retried, as a reconnect's is.
2. **Mailboxes connected through the old app are never told.** A refresh token belongs to the client it was
   issued to, and nothing records which app a mailbox connected through. After a change of app, the next
   refresh presents the old token with the new app's keys. If the provider answers `invalid_grant`, the mailbox
   goes to `ERROR` ("reconnect"), which is acceptable. If it answers `invalid_client` / `unauthorized_client`,
   `OAuthProviderTokenClient` reads that as `ProviderAppUnavailable`: the mailbox stays `ACTIVE` and sends wait
   an hour, again and again, with nobody told. Which answer each provider gives was not checked against live
   accounts.

   *Fixed*: V168 records the connecting client (`app_client_id`) on direct mailboxes and Zoom accounts. A change of
   app withdraws, in the same transaction, every live connection made through the app being left
   (`ReplacedAppConnections`; a row from before V168 counts as the app until now). The read carries
   `connectionsThroughApp`, which the form and the return dialog state before anyone saves.
3. **Keys are saved without being checked.** A typo surfaces only when a consultant's send or connect fails.

   *Fixed*: Save and verify asks the token endpoint before anything is kept (`ProviderTokenClient.checkKeys`, outside
   any transaction). Microsoft: `client_credentials` with Graph's `/.default`, which checks ID, secret and tenant
   together; a refused client (`invalid_client`, `unauthorized_client`, any 401: `AADSTS7000215`, `7000222`, `700016`)
   or an unknown directory (`AADSTS90002`, `900023`) refuses the keys, and any other refusal is no answer about them.
   Google and Zoom: a code nobody issued, where refusing the client means the keys are wrong and refusing anything else
   means the client authenticated first, as a token endpoint does before it reads the code (RFC 6749 §4.1.3). A
   refusal is `422 INTEGRATION_KEYS_REFUSED` with the provider's code as `providerError` (Entra's `AADSTS…` rule where
   it names one); no answer is `502 INTEGRATION_PROVIDER_UNREACHABLE`; neither writes anything. **Verify again** is
   `POST /workspace/integrations/{provider}/verify` (`WORKSPACE_MANAGE`, audited `keysChecked`), which keeps the answer
   either way. Both spend a budget of ten checks a minute per workspace on each instance (`OwnAppKeyCheck`, spent
   before the call whatever it answers). A mailbox or Zoom refresh the provider refuses as an app records the same
   refusal (`OwnAppKeyHealth`), so the card reads "Keys refused" without anyone pressing anything, and the next refresh
   it answers clears it — a write only while a refusal is on record, read off the credentials the refresh resolved.
4. **The secret is not trimmed** (`ownAppSchema` and `WorkspaceIntegrationService`). A trailing newline or space
   from a paste is stored as part of the secret, and only the provider notices. No Google, Entra or Zoom secret
   contains whitespace.

   *Fixed*, without trimming (the codebase's rule: whitespace inside a secret is the secret): a secret carrying any
   space, tab, line break or control character is refused, client and server, before anything is stored.
5. **`autocomplete="new-password"` on the secret field** (`OwnAppForm`). Chrome offers to *suggest a strong
   password* there, which would replace the real secret, and password managers offer to save the firm's client
   secret into a personal vault.

   *Fixed*: `autocomplete="off"` with `data-1p-ignore`, `data-lpignore` and `data-bwignore`.
6. **The tenant ID accepts `common`, `organizations`, `consumers` and `..`.** The pattern `[A-Za-z0-9.-]*`
   lets an own app's sign-in go to Microsoft's multi-tenant endpoints rather than the firm's own directory.
   It stays on Microsoft's host, so this is hardening only.

   *Fixed*: a GUID or a domain with at least one dot, client and server. A Google client ID must also end in
   `.apps.googleusercontent.com`, a Microsoft one must be a GUID, and a GUID pasted as a Microsoft secret (the Secret
   ID) is refused.
7. **Secret expiry is free-form and asked of every provider.** A past date is accepted, so is one more than
   24 months out, and Google and Zoom secrets do not expire at all.

   *Fixed*: asked for Microsoft only (a date sent for Google or Zoom is refused), and held to today through
   +24 months unless it is the date already on record, so an app whose secret lapsed can still be corrected.
8. **Admin-consent `state` is static and replayable** (`ProviderAppSetup.adminConsentState`): one HMAC per
   workspace, with no nonce or expiry and not bound to the tenant. Anyone who has seen the link can make an
   admin's browser record "Approved" for any tenant. The record is informational and gates nothing.

   *Fix*: add a nonce and an expiry to the state if it ever gates something.

## What the screen reads from the API

All built (V169):
- `secretHint`: the secret's last four characters, written with the ciphertext on save — none for a secret under 16
  characters, which four would give away — and read from the ciphertext for a secret saved before V169, never
  written back. Beside it `secretSavedAt` and `secretSavedBy` (a name), null for a secret saved before.
- `keysCheckedAt` and `keysRefusedWith`: when the provider last answered, and its code when it refused (finding 3).
  An own app never checked reads as **Own app**, the one pill the mockup does not draw.
- `connectionsThroughApp`, for the footer and the dialogs (findings 1 and 2).
- ~~Findings 1–7~~ (done; `docs/validation-matrix.md` updated).
