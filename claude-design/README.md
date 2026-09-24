# claude-design

HTML mockups of every Uncava screen, in Claude Design's `dc` format. They are the UI source of truth:
read the relevant file before building or changing a screen. Each one mirrors what `apps/web` (or
`apps/extension`) renders today, so a change designed here can be ported straight into the code.

Open a file in a browser over HTTP (`npx serve claude-design`, then `/Workspace.dc.html`), or import the
folder into claude.ai/design and edit it there. `support.js` is the Claude Design runtime; leave it as is.

## Screens

| File | Routes it draws | States (`data-props` knobs, also `?param=` in the URL) |
|---|---|---|
| `Login.dc.html` | `/login`, `/forgot-password`, `/auth/reset-password`, `/auth/verify`, `/auth/callback`, `/auth/accept-invite` | `screen`, `showProviders`, `error` |
| `Signup.dc.html` | `/signup`, `/signup/verify-email`, `/signup/workspace`, `/signup/invite` | `step`, `emailTaken`, `reentered`, `linkSent` |
| `Workspace.dc.html` | `/`, `/all`, `/team`, not-found | `view`, `empty`, `pureClient`, `loading`, `drawerOpen`, `modalOpen`, `inviteOpen` |
| `Clients.dc.html` | `/clients` | `empty`, `loading`, `drawerOpen`, `modalOpen`, `mandateModalOpen` |
| `Project.dc.html` | `/projects/:id/companies/{universe,shortlisted,declined}`, `/reports`, `/team`, `/candidates`, `/outreach` | `page`, `view`, `drawer`, `dialog`, `importStep`, `empty`, `clientRep`, `chapter`, `reportDrawer`, `teamModal`, `canManage` |
| `Position.dc.html` | `/projects/:id` (the brief) | `step`, `published`, `readBack`, `documentAttached`, `provenanceOpen`, `readNotice`, `saving` |
| `Strategy.dc.html` | `/projects/:id/strategy` | `filtersOpen`, `openFilter`, `selectedCount`, `saveMenuOpen`, `drawerOpen`, `columnsOpen` |
| `Settings.dc.html` | `/settings/*` incl. Templates and Template library | `section`, `superAdmin`, `isAdmin`, `importOpen`, `deleteOpen`, `providerOnly` |
| `Assistant.dc.html` | the docked assistant over Strategy | `panelOpen`, `context`, `turnState` |
| `Extension.dc.html` | the Chrome popup + `/extension/connect` | `screen` |

## The format

```
<head> … <script src="./support.js"></script> </head>
<x-dc>
  <helmet> fonts + one <style> with the token block + theme script </helmet>
  <div data-screen-label="…"> markup </div>
  <template id="__bundler_thumbnail"> card thumbnail </template>
</x-dc>
<script type="text/x-dc" data-dc-script data-props="…"> class Component extends DCLogic { … renderVals() } </script>
```

- The component takes its name from the file name. Screens link to each other with plain relative links.
- Markup is inline `style` only, plus `style-hover` / `style-focus`, `{{ value }}`, `<sc-if>` and
  `<sc-for>`. No classes. Every value the markup reads comes from `renderVals()`.
- Seed data lives in each file's constructor. The whole folder shares one cast (Meridian Search
  Partners; the Meridian Energy Group CFO mandate), so the screens agree with each other.

## Tokens

Every helmet links `uncava-tokens.css`, the UNCAVA palette (`--u-*`), and still aliases the old app
names onto it (`--bg: var(--u-bg)`, `--amber: var(--u-accent)`, …) so the mockups' markup reads as
before. The code has one palette only: `apps/web/src/styles/tokens.css` and the extension's
`theme.css` declare the `--color-u-*` values and nothing else, so a screen ported from a mockup writes
`bg-u-surface` where the helmet says `var(--panel)`, `text-u-accent` where it says `var(--amber)` or
`var(--sky)`.

Dark mode is a `dark` class on `<body>`, stored in `localStorage['lm-theme']`, the same switch as the
app, and dark is the default until someone picks light. A token change belongs in `tokens.css` and in every helmet at once.

## Brand

The product is Uncava. The mark is the rhombus over an isometric cube, drawn inline in `currentColor`
(geometry from `apps/web/public/brand/uncava-app-icon-*.svg`). The wordmark is "Uncava", uppercase,
letter-spaced. The amber tile with an initial is the workspace's own mark, not the product's.
A few template-library strings in `Settings.dc.html` still say "LightMove" because the app's code
still does. They change when the code does.
