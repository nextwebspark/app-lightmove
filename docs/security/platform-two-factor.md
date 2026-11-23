# Platform two-factor and step-up (#1313)

What `/api/v1/platform/**` asks of a super admin beyond the role. The rationale lives in the `lightmove-domain`
skill ("A platform role sits above every tenant and inside none"); this is the rule and how to run it.

## The rule

With `lightmove.platform.require-two-factor` on (`PLATFORM_REQUIRE_TWO_FACTOR`, off by default):

| Route | Asked, in this order | Refusal |
|---|---|---|
| every platform route | any platform action at all, before the request body is read (`PlatformRouteGate`) | `404 NOT_FOUND` |
| every platform route | the platform action | `404 NOT_FOUND` |
| every platform route | an enabled two-factor factor | `403 PLATFORM_TWO_FACTOR_REQUIRED` |
| high-impact routes (`@RequireRecentTwoFactor`: a credit grant, an invoiced subscription, putting a prompt version live or rolling one back, changing or resetting an application variable) | a code entered within `lightmove.platform.step-up-window` (`PLATFORM_STEP_UP_WINDOW`, 10 minutes) | `403 PLATFORM_STEP_UP_REQUIRED` |

- **The action is always first.** Anyone without it is answered the 404 they always were, so nothing tells a
  stranger the surface exists or that it asks for two-factor.
- **The code is given at `POST /api/v1/platform/step-up`** — an authenticator code or a recovery code, verified by
  `MfaCodeVerifier` under the factor's row lock, with sign-in's replay guard.
- **When it was entered is a row** (`app_lm_platform_step_up`, V234), never a token claim: a token cannot assert it
  and every instance agrees. It is the person's, not the session's, so a code entered in one browser opens the
  window in that person's other sessions until it closes.
- **A code older than the factor does not count**, so turning two-factor off and on carries no window across.
- **Every pass and failure is audited** (`PLATFORM_STEP_UP_PASSED`, `PLATFORM_STEP_UP_FAILED`); a recovery code
  also records `MFA_RECOVERY_CODE_USED`.
- **A new platform route takes `@RequirePlatformPermission` or `@RequireRecentTwoFactor`.**
  `PlatformTwoFactorIntegrationTest` fails on a route under `/api/v1/platform/` with neither.

With the flag off none of this runs: the role alone decides, as before #1313.

## Guessing a code

A wrong step-up code does **not** feed the account lockout. It is braked by the rate limiter alone
(`RateLimitGuard.checkPlatformStepUp`: `mfa-change-attempts-per-hour`, 10 an hour per account and per address,
counted apart from two-factor changes), the same brake the in-session two-factor changes have (turn off, new
recovery codes). The lockout guards the sign-in door, and a caller here is already signed in: locking the account
would not end the session doing the guessing.

So the flag depends on the limiter: **never run `PLATFORM_REQUIRE_TWO_FACTOR=true` with
`AUTH_RATE_LIMIT_ENABLED=false`.** `deploy.yml` sets the limiter on unless the `RATE_LIMIT_ENABLED` repository
variable says otherwise.

## Turning it on in an environment

1. `MFA_ENABLED=true` and a keyset (`lightmove.crypto.keyset`) — nobody can enrol without them.
2. `AUTH_RATE_LIMIT_ENABLED` is not `false`.
3. Every super admin of that environment has enrolled (Settings → Security). One who has not is refused every
   platform route the moment the flag is on, and the console tells them to set it up.
4. Set the `PLATFORM_REQUIRE_TWO_FACTOR` repository variable to `true` and deploy.

To turn it off, unset the variable and deploy. No data changes either way; the step-up rows are simply not read.
