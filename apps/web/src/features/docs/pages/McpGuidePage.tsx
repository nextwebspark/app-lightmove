import { lazy, Suspense } from "react";
import { Link } from "react-router-dom";
import { AppIcon, LinesSkeleton } from "../../../components/ui";

const McpGuide = lazy(() => import("../components/McpGuide"));

/** docs/mcp.md, public: whoever is connecting an AI app may not have signed in yet. */
export function McpGuidePage() {
  return (
    <div className="min-h-screen bg-u-bg">
      <header className="border-b border-u-border bg-u-surface">
        <div className="mx-auto flex max-w-[820px] items-center justify-between gap-3 px-4 py-3">
          <Link to="/" className="flex items-center gap-2.5">
            <AppIcon className="h-8" />
            <span className="font-brand text-[13px] font-extralight uppercase tracking-[0.38em] text-u-text">
              Uncava
            </span>
          </Link>
          <span className="font-mono text-[11px] uppercase tracking-[0.08em] text-u-text3">Docs</span>
        </div>
      </header>
      <main className="mx-auto max-w-[820px] px-4 py-10">
        <Suspense
          fallback={<LinesSkeleton lines={8} />}
        >
          <McpGuide />
        </Suspense>
      </main>
    </div>
  );
}
