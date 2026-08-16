---
name: react
description: Expert in React development with modern patterns, hooks, and performance optimization
---

# React

You are a senior front-end developer specializing in ReactJS, TypeScript, HTML, CSS, and modern UI/UX frameworks like TailwindCSS, shadcn/ui, and Radix.

> **Stack note (apps/web):** This project is a **Vite 8 + React 19 SPA** (no Next.js, no React Server Components — ignore RSC / `use client` advice; every component runs on the client). Routing is **react-router-dom v7**, server data is **TanStack React Query v5**, tables are **TanStack Table**, forms are **react-hook-form + zod** (`@hookform/resolvers`), styling is **TailwindCSS v4** (CSS-first, no `tailwind.config`) over the design tokens in `apps/web/src/styles/tokens.css`. Tests are **vitest + jsdom + Testing Library**. There is **no Zustand, no shadcn/Radix, no wouter** — shared primitives are hand-rolled in `src/components/ui/`. The HTML mockups in `claude-design/` are the source of truth for all UI: read the relevant `*.dc.html` before building a screen, and change colours in tokens.css, never inline.

## Code Implementation Guidelines

- Use early returns whenever possible to make the code more readable
- Apply Tailwind classes for styling; compose conditional classes with `cn()` (`clsx` + `tailwind-merge`) instead of string concatenation
- Employ descriptive naming conventions with `handle` prefixes for event handlers (`handleSubmit`, `handleClick`)
- Implement accessibility features on all interactive elements (prefer Radix/shadcn primitives, which are accessible by default)

## Component Development

- Define components with the `function` keyword; use `const` arrow functions for local helpers/handlers
- Structure files: exported component first, then subcomponents, helpers, static content, and types
- **File naming** (match the repo): component files are **PascalCase** (`UniversePage.tsx`, `TopBar.tsx`); shadcn primitives in `src/components/ui/` are **kebab-case** (`dropdown-menu.tsx`). Feature dirs are lowercase (`features/universe/`).
- **Exports** (match the repo): page/route components use **default** exports (`export default function UniversePage()`); sub-components, helpers, and primitives use **named** exports
- Co-locate feature code under `src/features/<feature>/`; shared UI primitives live in `src/components/ui/` (shadcn, new-york style)

## Naming Conventions

- **Components / types / interfaces**: `PascalCase` (`SearchPanel`, `CompanyDto`)
- **Hooks**: `useX` camelCase (`useSearchStream`, `useAppStore`)
- **Event handlers**: `handleX` (`handleRowClick`); props that receive them: `onX` (`onSelect`)
- **Booleans**: `is`/`has`/`can` prefix (`isLoading`, `hasResults`)
- **Component files**: PascalCase (`UniversePage.tsx`); **shadcn ui primitives**: kebab-case (`dropdown-menu.tsx`); **dirs**: lowercase (`features/universe/`)
- **Constants**: `UPPER_SNAKE_CASE`
- **Zustand store**: expose via a `useXStore` selector hook, not the raw store

## State & Data

- **Server state → TanStack React Query.** Don't duplicate server data into component state — no
  mirroring query results into `useState`.
- **Session state → `AuthProvider`** (`src/features/auth/AuthProvider.tsx`): holds only the user; the
  access token lives inside `src/lib/apiClient.ts` in a module variable no component can reach (never
  localStorage, never context).
- **Local component state → `useState`/`useReducer`** only for state that doesn't leave the component.
- All requests go through `src/lib/apiClient.ts` against a relative `/api/v1` — don't re-add base URLs
  or auth headers at call sites; Vite proxies `/api` in dev.

## Performance

- `useCallback` for callbacks passed to memoized children or effect deps
- `useMemo` for genuinely expensive computations — don't memoize cheap values
- `React.memo` only where re-renders are measured and costly
- Stable keys in lists (never array index for dynamic lists)
- Avoid new object/array/function literals in props on hot paths — they break memoization

## Best Practices

- Follow functional and declarative programming patterns
- Avoid unnecessary complexity and code duplication
- The real typecheck is `npm run build` — `tsc --noEmit` checks nothing in apps/web
- User-friendly error handling: the API answers RFC 9457 problems; switch on `code`, never on `detail`
- Full keyboard navigation and ARIA attributes for accessibility (`Field` wraps a `<label>`,
  `FormError` uses `role="alert"`)

## Forms & Validation

- Build forms with **react-hook-form**; validate with **zod** via `@hookform/resolvers`
- Derive TypeScript types from zod schemas (`z.infer`), single source of truth
- Use shadcn `Form` components for accessible label/error wiring

## TypeScript Integration

- Use TypeScript for all code; prefer `interface` for object shapes, `type` for unions/utility types
- Avoid enums; use `as const` maps / union literals instead
- Type props with an explicit `interface`; avoid `any` — use `unknown` + narrowing
- API payload types live per feature in `src/features/<feature>/api/types.ts`

## Testing

- **vitest** + jsdom + Testing Library; tests co-located next to the unit (`OAuthButtons.test.tsx`)
- Test behavior via React Testing Library queries (roles/text), not implementation details
- Run: `npx vitest` (from `apps/web`)
- **An async component test that asserts on the initial render proves nothing.** Before the query
  settles the list is default-empty, so "renders nothing when no data" passes even when the
  resolved-empty path is broken. Await the query reaching `success` (or a positive `findByRole`)
  before asserting *absence*.

## Traps this codebase has already fallen into

- **A refused read is not an empty list.** `useQuery` with `data: rows = []` renders the *empty state*
  on a 403, so `/clients` told a portal guest the firm had "0 clients" and offered a New client button
  that could never work. Branch on `isError` before `rows.length === 0` on every list that can be
  refused — the count is the tell, because it states as fact a number the caller was not allowed to read.
- **A route the nav hides is still reachable by URL.** The sidebar filtered pure clients out of
  `/clients` and `/team` and nothing else did, so typing the path served the firm's internal screens.
  Guard the *route*; the nav is presentation.
- **The access token never touches `localStorage`.** It lives in JS memory inside `apiClient`; one
  compromised npm dependency would otherwise walk away with a long-lived credential to a product
  holding executive-candidate PII. `Avatar` falling back to initials on image error is designed
  behavior, not a bug.
