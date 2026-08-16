import { useQuery } from "@tanstack/react-query";
import { type ReactElement } from "react";
import { Button } from "../../../components/ui";
import * as authApi from "../api/authApi";

/**
 * The "Continue with …" buttons, one per identity provider the server has configured.
 *
 * The mockup's "Continue with SSO" button sat behind a `showSso` prop. Here the list comes from the
 * server's answer to "which providers are actually wired?" — a button that leads nowhere is worse
 * than no button — and it is a list of registration ids rather than a fixed set of flags, so wiring
 * up another provider stays a yml block on the API. An id this file has no mark for still gets a
 * working button, just a generic one.
 */
const PROVIDER_MARKS: Record<string, { label: string; mark: () => ReactElement }> = {
  google: { label: "Google", mark: GoogleMark },
  linkedin: { label: "LinkedIn", mark: LinkedInMark },
};

export function OAuthButtons() {
  // Server state stays in the query cache rather than useState, so Login and Signup share one fetch.
  // What this deployment has configured cannot change while the page is open — hence no refetching.
  const { data } = useQuery({
    queryKey: ["auth", "providers"],
    queryFn: authApi.providers,
    staleTime: Infinity,
    retry: false,
  });
  const providers = data?.providers ?? [];

  if (providers.length === 0) {
    return null;
  }

  return (
    <>
      <div className="my-[18px] flex items-center gap-2.5 font-mono text-[10px] font-medium uppercase tracking-[0.1em] text-text3">
        <span className="h-px flex-1 bg-line-soft" />
        <span>or</span>
        <span className="h-px flex-1 bg-line-soft" />
      </div>

      <div className="flex flex-col gap-2">
        {providers.map((id) => {
          const { label, mark: Mark } = PROVIDER_MARKS[id] ?? { label: titleCase(id), mark: SsoMark };
          return (
            /*
              A full page navigation, not fetch(). This is an OAuth redirect: the browser has to
              actually leave for the provider's consent screen and come back. An XHR would be blocked
              by CORS and could not show the user the provider's own UI even if it were not.
            */
            <Button
              key={id}
              type="button"
              variant="secondary"
              className="w-full"
              onClick={() => {
                window.location.href = `/oauth2/authorization/${id}`;
              }}
            >
              <Mark />
              Continue with {label}
            </Button>
          );
        })}
      </div>
    </>
  );
}

/** A registration id is lowercase and may be hyphenated ("azure-ad"); a button label is neither. */
function titleCase(id: string): string {
  return id
    .split(/[-_]/)
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

/** Google's own four-colour mark. */
function GoogleMark() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.5h6.5a5.6 5.6 0 0 1-2.4 3.6v3h3.9c2.3-2.1 3.5-5.2 3.5-8.8Z" />
      <path fill="#34A853" d="M12 24c3.2 0 5.9-1.1 7.9-2.9l-3.9-3a7.2 7.2 0 0 1-10.7-3.8h-4v3.1A12 12 0 0 0 12 24Z" />
      <path fill="#FBBC05" d="M5.3 14.3a7.1 7.1 0 0 1 0-4.6V6.6h-4a12 12 0 0 0 0 10.8l4-3.1Z" />
      <path fill="#EA4335" d="M12 4.8c1.8 0 3.4.6 4.6 1.8l3.4-3.4A12 12 0 0 0 1.3 6.6l4 3.1A7.2 7.2 0 0 1 12 4.8Z" />
    </svg>
  );
}

/** LinkedIn's mark, in their blue. */
function LinkedInMark() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#0A66C2"
        d="M20.45 20.45h-3.56v-5.57c0-1.33-.03-3.04-1.85-3.04-1.86 0-2.14 1.45-2.14 2.95v5.66H9.35V9h3.41v1.56h.05a3.74 3.74 0 0 1 3.37-1.85c3.6 0 4.27 2.37 4.27 5.46v6.28ZM5.34 7.43a2.07 2.07 0 1 1 0-4.13 2.07 2.07 0 0 1 0 4.13Zm1.78 13.02H3.55V9h3.57v11.45ZM22.22 0H1.77C.79 0 0 .77 0 1.73v20.54C0 23.23.79 24 1.77 24h20.45c.98 0 1.78-.77 1.78-1.73V1.73C24 .77 23.2 0 22.22 0Z"
      />
    </svg>
  );
}

/** For a provider we have no mark for: a padlock, the mockup's own icon for SSO in the abstract. */
function SsoMark() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <rect x="4" y="10" width="16" height="11" rx="2" />
      <path d="M8 10V7a4 4 0 0 1 8 0v3" />
    </svg>
  );
}
