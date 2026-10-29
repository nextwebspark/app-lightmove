# Connect an AI app to Uncava

Uncava runs an MCP server, so an AI app such as Claude, ChatGPT, Cursor or your own agent can read your positions
and the companies and executives they hold, and answer questions about them. For example, you can ask it: "Who are
the finance chiefs at the shortlisted companies on the Group CFO search?" or "Brief me on where the Riyadh COO
mapping stands."

- **Server URL:** your Uncava address followed by `/api/v1/mcp`, which is `https://beta.uncava.com/api/v1/mcp`.
  You can copy it from **Settings → Connected AI apps**.
- **Transport:** Streamable HTTP. The server is stateless and never streams.
- **Read-only:** no tool adds, changes or deletes anything.

## Two ways to sign an app in

| | OAuth (recommended) | API key with MCP access |
|---|---|---|
| **For** | Claude.ai, Claude Desktop, Claude Code, ChatGPT and any app that signs in by itself | Cursor, scripts, the Claude and OpenAI APIs, and anything that sends a fixed header |
| **How** | Give the app the server URL. It opens Uncava, where you sign in, pick the workspace and tick what it may read | Make a key under **Settings → API keys** with `mcp:use` (**AI access**) ticked, and give the app the key as a bearer token |
| **Reaches** | One workspace, the one you picked. It sees only the positions you can open | A personal key sees the positions you can open. A workspace key sees every position in the workspace |
| **Ends** | When you press **Disconnect** in Settings → Connected AI apps, leave the workspace or change your password | When you revoke the key or it expires; a personal key also ends when you leave the workspace |

Both kinds re-check your access on every call, so a seat you lose today is gone from the app on its next call.

**Who may connect:** staff only. A client representative's account cannot connect an app.

## Connect

### Claude.ai and Claude Desktop

1. Open **Settings → Connectors → Add custom connector**.
2. Name it "Uncava" and paste the server URL.
3. Press **Connect**. Uncava opens.
4. Sign in, pick the workspace and choose what Claude may read.

The consent screen shows Claude as **Verified**: Uncava has fetched Claude's own description from `claude.ai`. On a
Team or Enterprise plan, an owner may have to add the connector for the organisation first.

### Claude Code

```bash
claude mcp add --transport http uncava https://beta.uncava.com/api/v1/mcp
```

Then run `/mcp` in Claude Code and choose **Authenticate** on `uncava`. Your browser opens the consent screen, and
Claude Code receives the connection on this machine.

To use a key instead, for example in CI:

```bash
claude mcp add --transport http uncava https://beta.uncava.com/api/v1/mcp \
  --header "Authorization: Bearer $UNCAVA_API_KEY"
```

### ChatGPT

1. Turn on **Settings → Apps → Advanced settings → Developer mode**.
2. Under **Apps**, choose **Create**.
3. Name it "Uncava", paste the server URL and choose **OAuth** authentication.
4. Sign in, pick the workspace and choose what ChatGPT may read.

Developer mode is a ChatGPT setting, and on a Business or Enterprise plan an admin may have to allow it.

### Cursor

Cursor's OAuth sign-in returns to a `cursor://` link, which any installed app can claim, so Uncava refuses it.
Connect Cursor with a key instead. Put this in `~/.cursor/mcp.json`, or in `.cursor/mcp.json` for one project:

```json
{
  "mcpServers": {
    "uncava": {
      "url": "https://beta.uncava.com/api/v1/mcp",
      "headers": { "Authorization": "Bearer ${env:UNCAVA_API_KEY}" }
    }
  }
}
```

Keep the key in your environment rather than in the file, and never commit it.

### The Claude API (MCP connector)

The model calls Uncava from Anthropic's servers, using the key you pass:

```bash
curl https://api.anthropic.com/v1/messages \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -H "anthropic-beta: mcp-client-2025-11-20" \
  -H "content-type: application/json" \
  -d '{
    "model": "'"$MODEL"'",
    "max_tokens": 2000,
    "messages": [{ "role": "user", "content": "Which positions are open, and where does each stand?" }],
    "mcp_servers": [{
      "type": "url",
      "url": "https://beta.uncava.com/api/v1/mcp",
      "name": "uncava",
      "authorization_token": "'"$UNCAVA_API_KEY"'"
    }],
    "tools": [{ "type": "mcp_toolset", "mcp_server_name": "uncava" }]
  }'
```

### The OpenAI Responses API

```json
{
  "model": "<model>",
  "input": "Which positions are open, and where does each stand?",
  "tools": [{
    "type": "mcp",
    "server_label": "uncava",
    "server_url": "https://beta.uncava.com/api/v1/mcp",
    "headers": { "Authorization": "Bearer <your uncava key>" },
    "require_approval": "never"
  }]
}
```

### Agent SDKs and anything else

Any MCP client that speaks Streamable HTTP works. Either let it run the OAuth flow (it finds everything from the
server URL), or have it send `Authorization: Bearer <key>` on every request. For the Claude Agent SDK, for example:

```ts
mcpServers: {
  uncava: {
    type: "http",
    url: "https://beta.uncava.com/api/v1/mcp",
    headers: { Authorization: `Bearer ${process.env.UNCAVA_API_KEY}` },
  },
}
```

To try the server by hand, run the MCP Inspector (`npx @modelcontextprotocol/inspector`), choose **Streamable
HTTP** through its proxy, paste the server URL and add the bearer header. A direct connection from the Inspector's
page is refused, because that page is a browser origin other than Uncava's.

## What it can read

| Tool | Reads | Needs |
|---|---|---|
| `uncava_whoami` | The connection itself: its workspace and scopes. Call it to check a connection works | nothing |
| `uncava_search_positions` | The positions you can read, newest first, optionally by role title. **Start here**: every other tool takes a `positionId` from it | `projects:read` |
| `uncava_get_position` | One position: role, client, type, stage, dates and counts | `projects:read` |
| `uncava_get_position_summary` | Where a position stands: companies at each stage, executives at each status, how many companies have someone mapped, and its dates | `projects:read` |
| `uncava_list_companies` | A position's companies at one stage (in universe, shortlisted or declined) | `companies:read` |
| `uncava_get_company` | One company of a position, with the executives mapped at it | `companies:read` (executives also need `candidates:read`) |
| `uncava_list_candidates` | A position's executives, optionally at one status or one company | `candidates:read` |
| `uncava_search_candidates` | A position's executives whose name, title or employer contains some text, optionally at one stage or status | `candidates:read` |
| `uncava_get_candidate` | One executive: profile, career, education and background | `candidates:read` |
| `uncava_get_universe` | A whole stage in one call: every company with its executives | `companies:read` and `candidates:read` |

Every tool is marked read-only and idempotent. Each one returns structured content matching its declared output
schema, with the same JSON as text for clients that read only text.

### Scopes

| Scope | Reads |
|---|---|
| `projects:read` | Positions: title, client, stage, type, dates and counts |
| `companies:read` | Each position's companies |
| `candidates:read` | Each position's executives: profile, company and status |
| `candidates.contacts:read` | An executive's emails and phone numbers. Personal data |
| `candidates.compensation:read` | An executive's package. Personal data |

You choose these on the consent screen, or on the key. A key also needs `mcp:use`, marked **AI access**, to reach the MCP server. That scope
reads nothing by itself, so a key made for a dashboard never becomes an AI connection unless you say so.

### What never leaves Uncava

- **Personal data needs its own scope.** Without it, `contacts` and `compensation` are `null`.
- **A guess is not a fact.** Seniority, nationality, gender and years of experience are `null` while they are only an
  AI suggestion that nobody has confirmed.
- **Never sent:** notes, AI assessments, who added a row, and custom columns.

Names, titles and summaries in a result are data people or providers recorded. The server tells the model to treat
them as data and never as instructions.

## Paging and size

- **Answer shape:** lists answer `concise` rows by default. Pass `response_format: "detailed"` for the whole record.
- **Page size:** lists take `limit`, which is 25 by default and at most 100.
- **Next page:** a list that has more rows answers `nextCursor`. Pass it back as `cursor`.
- **Size cap:** an answer is capped at about 90,000 characters, roughly 25,000 tokens. A page that would pass the cap
  is cut short and carries a `notice`, and its `nextCursor` resumes at the first row left out.
- **`uncava_get_universe`:** it refuses a stage too large for one answer. Page through `uncava_list_companies` and
  `uncava_list_candidates` instead.

## Limits

- **Calls:** each connection or key may make 120 calls a minute. Past that, the server answers `429` with
  `Retry-After`.
- **Request size:** a request body is capped at 64 KB. Past that, the answer is `413`.
- **Tokens:** an OAuth access token lives for an hour. The app refreshes it by itself, and each refresh token is good
  for one use within 30 days.

## When something goes wrong

| What you see | Why | What to do |
|---|---|---|
| `401` with `WWW-Authenticate: Bearer resource_metadata=…` | No credential, or one that has ended (disconnected, revoked, expired, or you lost access) | Reconnect the app, or use a live key |
| `403 insufficient_scope` naming scopes | An OAuth connection called a tool it was not granted | The app asks you to grant it; or reconnect and tick it |
| A tool result marked as an error: "This connection lacks …" | A key called a tool it lacks the scope for | Make a key with that scope |
| A tool result marked as an error: "No position … is readable through this connection" | The position is in another workspace, or you have no seat on it | Call `uncava_search_positions` to see what you can read |
| `403` from a browser page | The page's `Origin` is not Uncava's own | Call the server from an app, not a web page on another site |
| `429` | Too many calls | Wait for `Retry-After` |

A tool never answers with an internal error message. A failure is always one plain sentence the model can act on.

## Disconnect

- **One app:** go to **Settings → Connected AI apps** and press **Disconnect**. The app stops at its next call.
- **All of a person's apps:** an admin can disconnect them from **All** on the same page.
- **Every app at once:** changing or resetting your password disconnects every app on your account, in every
  workspace.

## For Uncava maintainers

- **Kill switch:** the repository variable `MCP_ENABLED` (`deploy.yml`) turns on the MCP server and its
  authorization server. Off, every MCP and OAuth route answers 404. Existing grants are kept and work again once it
  is turned back on.
- **Signing key:** MCP access tokens are signed by their own RSA key, separate from the session's. It lives in Secret
  Manager as `lightmove-mcp-jwt-private-key` and `lightmove-mcp-jwt-public-key`, and is made once, by hand, as
  `ops/gcp/bootstrap.sh` describes. Replacing it refuses every access token already issued at once. Apps then
  refresh, since refresh tokens are stored hashes rather than signed tokens, and carry on.
- **The tool contract is a snapshot.** `McpToolContractTest` diffs `tools/list` against `docs/mcp/tools.json`.
  After a deliberate change, run `UPDATE_MCP_TOOLS_SNAPSHOT=true ./mvnw test -Dtest=McpToolContractTest` in
  `apps/api` and commit the file with the change. The variable fails the test under `CI`.
- **Evals:** `ops/eval/mcp/` asks Claude Code realistic questions against a local stack, and checks which tools it
  picks and how many calls it makes. Run it after changing a tool's description. It is not part of `./mvnw test`.
- **Settings:** `lightmove.mcp.*` holds the token lifetimes, the budgets, the size caps, the verified client
  prefixes (`MCP_VERIFIED_CLIENT_ID_PREFIXES`) and the extra browser origins (`MCP_ALLOWED_ORIGINS`; the
  deployment's own origin is always allowed).
