---
name: java-spring-development
description: Java Spring Boot development guidelines with best practices for building robust, secure, and maintainable enterprise applications
---

# Java Spring Development Best Practices

## Core Principles

- Write clean, efficient, and well-documented Java code with accurate Spring Boot examples
- Use Spring Boot 3.x with Java 21+ features (records, sealed classes, pattern matching, virtual threads)
- Prefer constructor injection over field injection for better testability
- Follow SOLID principles and RESTful API design patterns
- Design for microservices architecture suitability

## Project Structure

Organize code using the standard layered pattern (singular package names):

```
com.example/
├── controller/     # REST controllers
├── service/        # Business logic
├── repository/     # Data access layer (Spring Data JPA)
├── entity/         # JPA domain entities
├── dto/            # Request/response DTOs (prefer records)
├── config/         # Spring configurations
├── security/       # Auth filters, security config
└── web/            # Cross-cutting web concerns (exception handlers)
```

## Naming Conventions

- **Classes / records / enums**: `PascalCase`; suffix by role — `*Controller`, `*Service`, `*Repository`, `*Config`, `*Exception`, `*Dto` / `*Request` / `*Response`
- **Methods / fields / params**: `camelCase`; Spring Data finders read as queries (`findByIdAndOrgId`, `existsByEmail`)
- **Constants** (`static final`): `UPPER_SNAKE_CASE`
- **Packages**: lowercase, singular layer names — match the existing project layout
- **Config properties**: kebab-case under a namespaced prefix (`app.jwt.expiry-seconds`); bind with `@ConfigurationProperties`
- **Test classes**: `<Unit>Test`; methods describe behavior (`returns403WhenOrgMissing`)

## Dependency Injection

- Use constructor injection for required dependencies
- Leverage `@RequiredArgsConstructor` with Lombok for cleaner code
- Keep constructors simple and avoid logic in them
- Use `@Qualifier` when multiple implementations exist

## REST API Design

- Use appropriate HTTP methods (GET, POST, PUT, DELETE, PATCH)
- Return proper HTTP status codes
- Implement consistent error response format
- Use DTOs to control API contract
- Version APIs when needed

## Data Access

### Spring Data JPA
- Define proper entity relationships (@OneToMany, @ManyToOne, etc.)
- Use lazy loading appropriately to avoid N+1 queries
- Implement pagination for large result sets
- Use query methods and @Query for custom queries
- Never expose entities directly over the API — map to DTOs

### Transactions
- Own transaction boundaries in the service layer with `@Transactional`
- Mark read-only paths `@Transactional(readOnly = true)`
- Keep transactions short; don't call external services (HTTP/LLM) inside them
- Remember `@Transactional` only applies to public methods called through the Spring proxy (no self-invocation)

### Database Migrations
- Use Flyway or Liquibase for schema migrations, **or** manage the schema externally with `ddl-auto=none` (this project's approach — schema owned outside the app, no migration tool)
- Version migration scripts properly
- Never modify existing migrations
- Test migrations in development before production

## Security

### Spring Security
- Implement authentication and authorization properly
- Use BCrypt for password encoding
- Configure CORS appropriately
- Protect endpoints based on roles/permissions
- Use HTTPS in production

### Secure Coding
- Validate all user inputs
- Sanitize data to prevent injection attacks
- Avoid exposing sensitive information in responses
- Use parameterized queries

## Testing

### Unit Testing
- Use JUnit 5 for unit tests
- Mock dependencies with Mockito
- Test business logic thoroughly
- Follow Given-When-Then pattern

### Integration Testing
- Use @SpringBootTest for integration tests
- Use MockMvc for web layer testing
- Test database operations with test containers
- Test security configurations

## Performance

### Caching
- Use Spring Cache abstraction
- Configure appropriate cache providers (Redis, Caffeine)
- Set proper TTL for cached data
- Implement cache eviction strategies

### Async Processing
- Use @Async for non-blocking operations
- Configure thread pools appropriately
- Handle exceptions in async methods
- Consider using reactive patterns for high concurrency

## Logging and Monitoring

### Logging
- Use SLF4J with Logback
- Log at appropriate levels
- Include correlation IDs for tracing
- Avoid logging sensitive data

### Monitoring
- Use Spring Boot Actuator for health and metrics
- Export metrics to monitoring systems
- Set up proper health checks
- Monitor application performance

## API Documentation

- Use Springdoc OpenAPI for API documentation
- Document all endpoints with descriptions
- Include request/response examples
- Keep documentation up to date with code

---

# LightMove specifics

Everything below is this repository's own law — it overrides the generic guidance above where they
disagree. Boot version here is **4.1** (not 3.x), Java 21, Maven, Jackson 3.

## Stack notes — Boot 4 renamed the starters

Most tutorials online are for Boot 3 and will not compile:

| Boot 3 | Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
| `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |

Also: **Jackson 3** (`tools.jackson.*`, not `com.fasterxml.*` — the old jars are still on the classpath
and will compile, then fail at runtime with "no ObjectMapper bean"). Spring Security 7 enables CSRF for
APIs by default. `authorizeRequests()` is gone; use `authorizeHttpRequests()`.

## Architecture

**Two tiers, every module laid out by type.** A shared **`core/`** holds the concerns every feature
reuses; each business feature is its sibling. Both use the same type-subpackages, a module keeping only
the ones it needs. The actual tree:

```
core/
  security/                # the whole auth domain
    constant/   TokenPurpose, UserStatus
    model/      User, UserIdentity, VerificationToken, AuthPrincipal,
                EmailVerifiedEvent, SignupCommand, AuthenticatedSession
    repository/ UserRepository, UserIdentityRepository, VerificationTokenRepository
    service/    AuthService, VerificationService, PasswordPolicy,
                OAuth2LoginSuccessHandler, OAuth2LoginFailureHandler,
                LoginErrorRedirector, ProviderQuirkAwareRequestResolver,
                CurrentUser, ClientIpResolver
    config/     SecurityConfig
    controller/ AuthController, AuthResponseAssembler
    dto/        AuthDtos
    jwt/        JwtConfig, JwtPrincipalConverter, RsaKeyProvider          (flat concern pkg)
    token/      RefreshToken, RefreshTokenRepository, TokenService, TokenPair,
                RevokeReason, RefreshCookieFactory, Tokens                (flat concern pkg)
    rbac/       Role, Action, RoleRepository, ActionRepository, RoleScope,
                WorkspaceRole, ProjectRole, WorkspaceAction, ProjectAction,
                RbacService, WorkspaceAccess, ProjectAccess,
                WorkspaceAuth, ProjectAuth                                (flat concern pkg)
  email/       model/(EmailMessage)  service/(EmailSender, EmailAddressValidator, …)  config/
  audit/       constant/(AuditEventType, AuditOutcome)  model/(AuditEvent)  repository/  service/
  error/       constant/(ErrorCode)  model/(ApiException)  service/(Problems)
               handler/(GlobalExceptionHandler, ProblemAccessDeniedHandler)
  ratelimit/   service/(RateLimiter, Bucket4jRateLimiter, RateLimitGuard)
  persistence/ model/(BaseEntity)
  logging/     service/(CorrelationId, CorrelationIdFilter)
  config/      LightMoveProperties (root record) + one *Settings record per branch,
               SpaResourceConfig                          (cross-cutting; no type split)

workspace/                 # feature template — project / strategy / candidate copy this
  constant/   MemberStatus, WorkspaceStatus, InvitationStatus
  model/      Workspace, WorkspaceMember, PendingOnboarding, Invitation,
              CreateWorkspaceCommand, InviteCommand
  repository/ service/ controller/ dto/(WorkspaceDtos)
```

**What goes in each subpackage** (a module includes only the ones it needs):

| subpackage | holds |
|---|---|
| `constant` | **all enums** and fixed constant values |
| `model` | entities, domain events, internal command/result records — **no enums, no HTTP payloads** |
| `dto` | HTTP request/response records only |
| `repository` | Spring Data interfaces |
| `service` | business logic and its interfaces (`EmailSender`, `RateLimiter` live here) |
| `controller` | `@RestController` classes (`@RestControllerAdvice` handlers go in `error/handler`) |
| `config` | `@Configuration` classes and `*Settings` config records |

**Flat concern packages** are the one exception to type-only grouping: inside `core/security`, `jwt/`,
`token/` and `rbac/` group everything for their concern regardless of type — so `RefreshToken` (an
entity) and `RevokeReason` (an enum) live in `token/`, and `Role` (an entity) next to `WorkspaceAction`
(an enum) in `rbac/`. This applies only to those three. Role enums live in `core/security/rbac`, not in
the features — they are catalog mirrors, and both tiers' access services need them. Invitations are
part of `workspace` (membership), not their own feature.

**Dependency rule:** features depend on `core`, never on each other's internals. `core` does not depend
on a feature — the deliberate exceptions are `AuthResponseAssembler` (`core/security/controller`), which
reads workspace repositories to build the `/me` response (`AuthDtos.UserResponse` embedding
`WorkspaceDtos.WorkspaceSummary` is the same seam), and the `rbac/` access services, which read the
workspace/project repositories because authorisation is answered from membership rows. One
feature→feature seam is sanctioned: `project`'s `StrategyService` calls `company`'s
`CompanyQueryService.refsByKeys` to resolve strategy-list company snapshots at write time — the
universe lookup lives with the universe rather than being duplicated SQL in `project`, and the seam
is a public service method plus the `company/model` records it returns, never `company` internals.
A second seam is sanctioned for client representatives: `project`'s `ClientRepresentativeService`
calls `workspace`'s `InvitationService.onboardClientRepresentative` to grant membership (a representative
is a CLIENT-role workspace member, and membership is the workspace's to grant). That call chooses the
path: an email that is **already an active member** gains the `CLIENT` role on their existing membership
plus an informational email — no invite, because a user is unique to a workspace and this person is in;
a **stranger** gets the ordinary invitation, and *acceptance* flows back as a
`ClientRepresentativeAcceptedEvent` the project side listens for — so `workspace` announces the accept in
primitives and never depends on `project` (mirrors `EmailVerifiedEvent`). Attaching a representative to a
mandate is a plain project seat (`ProjectService.attachRepresentative`), no seam. This is a deliberate
trade of the old ports/adapters layering for a uniform, type-based shape, so
`EmailSender`/`RateLimiter` are plain `service` interfaces rather than declared ports.

Ports worth knowing: `EmailSender` (`core/email/service`; `LogEmailSender` prints the verification link to
the console — the default, so a fresh clone is fully testable with no provider account; `ResendEmailSender`
for prod) and `RateLimiter` (`core/ratelimit/service`; in-memory Bucket4j — swap for Redis before running
more than one instance).

## Conventions

- Java: constructor injection only. `record` for DTOs. Immutable where you can be.
- **Names carry intent.** No abbreviations, single letters (except loop indices), or vague names
  (`data`, `info`, `tmp`, `doStuff`, `handle`, `flag`). Methods read as verbs (`resolveWorkspaceId`),
  booleans as predicates (`isVerified`), classes/enums as nouns. If a name needs a comment to explain
  what it holds, rename it.
- **Every type name must read standalone** — in an import, a constructor parameter, autocomplete.
  This is a general rule for *any* type anywhere in the codebase (records, enums, DTOs, nested
  classes), not just config: if a name only makes sense through its enclosing path
  (`Something.Inner`), or collides with an unrelated type elsewhere, rename it so the simple name
  alone carries the meaning. Prefer one top-level type per file over nesting; nest only when the
  inner type is meaningless outside its owner *and* its name still reads unambiguously.
  *Example that shipped:* config records named `Jwt`/`Web`/`Oauth` inside `LightMoveProperties` —
  `Auth.Jwt` collided with the unrelated `JwtConfig` (issue #53); they became one `*Settings` record
  per file in `core/config`.
- **Config records specifically:** `LightMoveProperties` is the root record only; a new config branch
  is a new `*Settings` file in `core/config`, never a nested record. Renaming a record never breaks
  binding — yml keys come from component names, not Java type names.
- **Lombok.** `@RequiredArgsConstructor` for constructor injection, `@Slf4j` for the logger. Entities
  use `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + selective `@Setter`, and **never**
  `@Data`, `@EqualsAndHashCode`, or `@Builder` — `BaseEntity` explains why identity equality is
  hand-written. A hand-written constructor is allowed **only** when it *derives* a value (e.g. a nested
  config record, `this.config = properties.auth()`); one that contains nothing but `this.x = x`
  assignments is dead weight, however many dependencies it takes — use `@RequiredArgsConstructor`.
  Config is `lombok.config` at the module root.
- Errors: RFC 9457 `ProblemDetail`, produced centrally in `GlobalExceptionHandler`. The frontend
  switches on `code`, never on `detail`. `ApiException` has **two message channels**: the constructors
  take an *internal* detail that reaches the log and never the response (so a rule may quote the
  request), while `userFacing` / `withField` opt a **fixed** sentence into the body — the latter as
  `fieldErrors`, the same shape Bean Validation produces. Never hand `userFacing` anything interpolated
  from input.
- Comments explain *why*, not *what*. Every class carries a class-level doc; the inline comments that
  document shipped bugs (the traps below) are load-bearing and must not be stripped. If a line needs a
  comment to say what it *does*, rename something.

## Traps this codebase has already fallen into

Each of these shipped, looked correct, and did nothing. They are all covered by tests now — don't
reintroduce them.

- **`@Async` / `@Transactional` are proxy-based.** A method calling another method *on itself* bypasses
  the proxy and the annotations are inert. `AuditService` delegates to a separate `AuditEventWriter`
  bean for exactly this reason.
- **Spring rolls back on any unchecked exception, including `ApiException`.** `login()` and `rotate()`
  are `@Transactional(noRollbackFor = ApiException.class)`, because otherwise the failed-login counter
  and the token-family revocation are rolled straight back out — silently disabling account lockout and
  refresh-token theft detection entirely.
- **Spring Security loads the CSRF token lazily.** An endpoint that returns 204 without calling
  `csrfToken.getToken()` writes no cookie, so the SPA has nothing to echo back and every refresh 401s.
  See `AuthController.csrf`.
- **`@DefaultValue("")` on a `List<String>` binds to `[""]`, not `[]`.** Treating that as "the operator
  supplied an override" emptied the consumer-domain blocklist and let Gmail signups through.
- **Every auth route needs `JwtPrincipalConverter`.** With Spring's default converter the principal is a
  raw `Jwt`, `CurrentUser` finds no `AuthPrincipal`, and the endpoint 401s on a valid token.
- **BCrypt measures the password in bytes; `String.length()` counts characters.** 41 accented characters
  is 83 bytes: it passed a 72-*character* policy and then threw inside `encode`, 500ing signup, password
  reset and invited signup alike. `PasswordPolicy` measures UTF-8 bytes, and the message no longer
  promises a character count it cannot keep.
- **Bean Validation runs before the service ever sees the request.** Jakarta `@Email` rejected a pasted
  address with a trailing space while the normaliser that would have trimmed it sat one layer down,
  unreached. Address fields carry `@JsonDeserialize(converter = EmailAddressNormaliser.class)` so
  canonicalisation happens at binding — never do this to a password, where trimming changes the secret.
- **A revoked refresh token is not automatically a stolen one.** Branch on `RevokeReason`: `ROTATED` and
  `REUSE_DETECTED` are theft, `LOGOUT` and `PASSWORD_CHANGED` are how a session is *supposed* to end.
  Testing only `isRevoked()` declared theft on every ordinary logout — alarming the user and firing the
  one alert meant to page a human, which made it worthless. `ROTATED` must stay theft: that is the
  actual attack signature.
- **A caught `NamingException` is not proof of an outage.** The MX check swallowed `NameNotFoundException`
  — the resolver's answer that the domain does not exist — as "inconclusive" and let it through, so the
  typo'd domain it exists to catch was the case it passed. Fail open on timeouts, not on answers.
- **Deleting before deciding.** `materialise` deleted the held wizard, *then* checked whether it was
  usable, so the path that refused it was also the path that destroyed it. Decide first, delete after.
- **`OAuth2AuthorizationRequest.from()` carries the rendered URI across.** Rebuilding a request to
  drop `code_challenge` produced a clean parameter map and a redirect URL that still carried it,
  because `authorizationRequestUri` is a field copied verbatim. Build the request field by field so
  `build()` renders the URI from the parameters you actually kept.
- **`String.valueOf(x)` where `x` comes from a generic `<T> T` getter binds to the `char[]` overload.**
  `OAuth2AuthorizationRequest.getAttribute` is generic, so inference picks `valueOf(char[])` and the
  call dies at runtime with a `ClassCastException` — a 500 on every authorisation request. Assign to
  a `String` first. (A non-generic getter returning `Object`, like `HttpServletRequest.getAttribute`
  or `Map.get`, is safe: that binds `valueOf(Object)`.)
- **`Set.copyOf(…).contains(null)` throws.** An immutable set answers a null lookup with a
  `NullPointerException` rather than `false`, so a nullable key needs its own guard.
- **`@ConditionalOnBean` on user configuration silently never matches.** A resolver bean conditioned
  on `ClientRegistrationRepository` was never created — the repository is auto-configured *after*
  user config — so Spring used its default and the override looked inert. Build such things where the
  bean is already in hand.
- **Javadoc binds to the next declaration, whatever the block says.** A reorder left the
  `email_verified`-trust doc sitting on `usablePictureUrl` while `emailProvenBy` — the
  account-takeover-relevant method it described — shipped with no comment at all. Move a method and
  its doc block as one unit; two stacked blocks above one declaration means one of them is orphaned.
- **The OAuth login handlers run inside the security filter chain, where `GlobalExceptionHandler`
  does not exist.** An exception escaping `OAuth2LoginSuccessHandler` is a raw container error page,
  not a ProblemDetail. Every path through those handlers must end in a redirect to the SPA —
  `LoginErrorRedirector` owns the redirect, and the catch covers `Exception`, not just
  `ApiException` (the concurrent double-callback race throws `DataIntegrityViolationException`).
