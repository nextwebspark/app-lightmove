import type { ReactNode } from "react";
import { AuthLogo, Spinner } from "../../../components/ui";

/** The consent screen's card under the Uncava lockup, on every state. */
export function ConsentFrame({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-u-bg px-4 py-8">
      <div className="w-full max-w-[460px]">
        <div className="mb-[22px] flex justify-center">
          <AuthLogo />
        </div>
        <div className="animate-fade-up rounded-2xl border border-u-border-strong bg-u-surface p-7 shadow-u-e3">
          {children}
        </div>
      </div>
    </div>
  );
}

export function ConsentLoading() {
  return (
    <div role="status" aria-label="Loading" className="flex items-center justify-center py-6 text-u-text3">
      <Spinner />
    </div>
  );
}
