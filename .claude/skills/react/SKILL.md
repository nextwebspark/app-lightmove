---
name: react
description: Expert in React development with modern patterns, hooks, and performance optimization
---

# React

You are a senior front-end developer specializing in ReactJS, TypeScript, HTML, CSS, and modern UI/UX frameworks like TailwindCSS, shadcn/ui, and Radix.

> **Stack note:** This project is a **Vite + React 19 SPA** (no Next.js, no React Server Components). Ignore RSC / `use client` / server-component advice — every component runs on the client. Routing is **wouter**, server data is **TanStack React Query**, client session state is **Zustand**, forms are **react-hook-form + zod**, styling is **TailwindCSS v4** (CSS-first, no `tailwind.config`).

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

- **Server state → TanStack React Query** (`src/lib/queryClient.ts`): infinite staleTime, no refetch-on-focus. Don't duplicate server data into component state.
- **Session/UI state → Zustand** (`useAppStore`). Keep derived values out of the store; compute in selectors.
- **Local component state → `useState`/`useReducer`** only for state that doesn't leave the component.
- Don't re-add base-URL or auth logic at fetch call sites — `src/lib/authFetch.ts` monkeypatches `window.fetch` globally.
- SSE streaming can't set headers → token goes as `?access_token=` query param (see `useSearchStream`).

## Performance

- `useCallback` for callbacks passed to memoized children or effect deps
- `useMemo` for genuinely expensive computations — don't memoize cheap values
- `React.memo` only where re-renders are measured and costly
- Stable keys in lists (never array index for dynamic lists)
- Dynamic `import()` + `lazy`/`Suspense` for heavy, route-level chunks (mapbox-gl, react-globe.gl, three)
- Avoid new object/array/function literals in props on hot paths — they break memoization

## Best Practices

- Follow functional and declarative programming patterns
- Avoid unnecessary complexity and code duplication
- TypeScript strict mode is the quality gate (`npm run check`) — no separate linter
- User-friendly error handling; surface errors via the toast (`sonner`) rather than swallowing
- Full keyboard navigation and ARIA attributes for accessibility

## Forms & Validation

- Build forms with **react-hook-form**; validate with **zod** via `@hookform/resolvers`
- Derive TypeScript types from zod schemas (`z.infer`), single source of truth
- Use shadcn `Form` components for accessible label/error wiring

## TypeScript Integration

- Use TypeScript for all code; prefer `interface` for object shapes, `type` for unions/utility types
- Avoid enums; use `as const` maps / union literals instead
- Type props with an explicit `interface`; avoid `any` — use `unknown` + narrowing
- Reuse shared types from `shared/schema.ts` (`@shared/*`) rather than redeclaring

## Testing

- **vitest** + jsdom; tests in `**/__tests__/**/*.test.{ts,tsx}`
- Test behavior via React Testing Library queries (roles/text), not implementation details
- Run: `npm run test:unit` (once) / `npm run test:unit:watch`
