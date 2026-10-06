import { useQuery } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { Link, Navigate, useLocation } from "react-router-dom";
import { AuthLogo, Button, Spinner } from "../../../components/ui";
import { ChatGptMark, ClaudeMark } from "../../../components/ui/BrandMarks";
import { CheckBox } from "../../../components/ui/FilterCheckRow";
import { useRadioGroupKeys } from "../../../components/ui/useRadioGroupKeys";
import { ApiRequestError } from "../../../lib/apiClient";
import { cn } from "../../../lib/cn";
import { initials } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import type { User } from "../../auth/api/types";
import { homeFor } from "../../auth/homeFor";
import type { ApiKeyScope } from "../../settings/api/types";
import { answerConsent, getConsentContext, storeAuthorizationRequest } from "../api/oauthConsentApi";
import type { ConsentContext, ConsentWorkspace } from "../api/types";
import { returnToClient } from "../lib/clientRedirect";
import {
  CONSENT_SCOPES,
  clientSubtitle,
  grantedSummary,
  initialScopes,
  knownClientMarkOf,
  outcomeOf,
  readClientRequest,
  refusalMessage,
  type ClientRequest,
} from "../lib/consentRequest";

type Outcome =
  | { kind: "allowed"; workspaceName: string; scopes: ApiKeyScope[] }
  | { kind: "denied" }
  | { kind: "notConnected" }
  | { kind: "error"; code: string };

/**
 * Where an AI client's authorize request lands (#701's `consentPage`): sign in, pick the workspace, choose what it may
 * read. The server decides everything again; this page only asks. It never posts to `window.opener` — the AI client
 * that opened it is another origin, and nothing of the session is its to hold.
 */
export function OAuthConsentPage() {
  const { user, loading } = useAuth();
  const location = useLocation();
  const clientRequest = useMemo(() => readClientRequest(location.search), [location.search]);

  if (clientRequest.kind === "error") {
    return (
      <ConsentFrame>
        <ErrorOutcome code={clientRequest.code} />
      </ConsentFrame>
    );
  }
  if (loading) {
    return (
      <ConsentFrame>
        <Loading />
      </ConsentFrame>
    );
  }
  if (!user) {
    // The request rides the return-to, so signing in — by password or a provider — lands back here with it intact.
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (!user.emailVerified || !user.workspace) {
    return <Navigate to={homeFor(user)} replace />;
  }

  return <ConsentForm clientRequest={clientRequest} user={user} />;
}

function ConsentForm({
  clientRequest,
  user,
}: {
  clientRequest: Extract<ClientRequest, { kind: "request" }>;
  user: User;
}) {
  const { clientId, redirectUri, scope } = clientRequest;
  const context = useQuery({
    queryKey: ["oauth", "consent-context", clientId, redirectUri, scope],
    queryFn: () => getConsentContext(clientId, redirectUri, scope),
    retry: false,
    staleTime: Infinity,
  });
  const [outcome, setOutcome] = useState<Outcome | null>(null);

  if (outcome) {
    return <ConsentFrame>{outcomeView(outcome, context.data)}</ConsentFrame>;
  }
  if (context.isError) {
    return (
      <ConsentFrame>
        <ErrorOutcome code={codeOf(context.error)} />
      </ConsentFrame>
    );
  }
  if (!context.data) {
    return (
      <ConsentFrame>
        <Loading />
      </ConsentFrame>
    );
  }

  const eligible = context.data.workspaces.filter((workspace) => workspace.eligible);
  if (eligible.length === 0) {
    return (
      <ConsentFrame>
        <RefusedOutcome
          clientName={context.data.clientName}
          workspaceName={context.data.workspaces[0]?.name ?? user.workspace?.name ?? "this workspace"}
        />
      </ConsentFrame>
    );
  }

  return (
    <ConsentFrame>
      <ConsentAsk
        context={context.data}
        clientRequest={clientRequest}
        user={user}
        eligible={eligible}
        onOutcome={setOutcome}
      />
    </ConsentFrame>
  );
}

function ConsentAsk({
  context,
  clientRequest,
  user,
  eligible,
  onOutcome,
}: {
  context: ConsentContext;
  clientRequest: Extract<ClientRequest, { kind: "request" }>;
  user: User;
  eligible: ConsentWorkspace[];
  onOutcome: (outcome: Outcome) => void;
}) {
  const { signOut } = useAuth();
  const offered = CONSENT_SCOPES.filter(({ scope }) => context.requestedScopes.includes(scope));
  const [ticked, setTicked] = useState<ApiKeyScope[]>(() => initialScopes(context.requestedScopes));
  const [workspaceId, setWorkspaceId] = useState<string>(
    () => eligible.find((workspace) => workspace.id === user.workspace?.id)?.id ?? eligible[0].id,
  );
  const [pressed, setPressed] = useState<"allow" | "deny" | null>(null);
  const workspace = eligible.find((candidate) => candidate.id === workspaceId) ?? eligible[0];
  const radioKeys = useRadioGroupKeys(
    eligible.map(({ id }) => id),
    workspaceId,
    setWorkspaceId,
  );

  const toggle = (scope: ApiKeyScope) =>
    setTicked((current) => (current.includes(scope) ? current.filter((each) => each !== scope) : [...current, scope]));

  const answer = async (choice: "allow" | "deny") => {
    if (pressed) return;
    setPressed(choice);
    const granted =
      choice === "allow" ? CONSENT_SCOPES.map(({ scope }) => scope).filter((each) => ticked.includes(each)) : [];
    try {
      const stored = await storeAuthorizationRequest(clientRequest.params, workspace.id);
      const { redirectUri } =
        "redirectUri" in stored ? stored : await answerConsent(clientRequest.clientId, stored.state, granted);
      const result = outcomeOf(redirectUri);
      onOutcome(
        result === "granted"
          ? { kind: "allowed", workspaceName: workspace.name, scopes: granted }
          : result === "denied" && choice === "deny"
            ? { kind: "denied" }
            : { kind: "notConnected" },
      );
      returnToClient(redirectUri);
    } catch (error) {
      onOutcome({ kind: "error", code: codeOf(error) });
    }
  };

  return (
    <>
      <div className="flex items-center gap-3">
        <ClientMark context={context} />
        <div className="min-w-0">
          <h1 className="text-[17px] font-semibold leading-[1.35] text-u-text">
            {context.clientName} wants to read your Uncava data
          </h1>
          <div className="mt-1 flex flex-wrap items-center gap-1.5 font-mono text-[12px] text-u-text3">
            <span>{clientSubtitle(context)}</span>
            <span
              className={cn(
                "inline-flex rounded-full px-[7px] py-px font-mono text-[9px] font-semibold uppercase tracking-[0.06em]",
                context.verified ? "bg-u-direct-tint text-u-direct" : "bg-u-signal-tint text-u-signal",
              )}
            >
              {context.verified ? "Verified" : "Unverified"}
            </span>
          </div>
        </div>
      </div>

      {!context.verified && (
        <div
          role="note"
          className="mt-4 rounded-lg border border-u-signal bg-u-signal-tint px-3 py-2.5 text-[12.5px] leading-[1.55] text-u-text"
        >
          <div className="font-semibold">Uncava can't confirm who made this app</div>
          <div className="mt-0.5 text-u-text2">
            It registered itself and will send you back to{" "}
            <code className="font-mono text-[12px] font-medium text-u-text">{context.redirectHost}</code>. Allow it
            only if you set it up yourself or your firm told you to.
          </div>
        </div>
      )}

      <div className="mt-[18px] flex items-center gap-2.5 rounded-lg border border-u-border bg-u-raised px-3 py-2.5">
        <span className="grid size-7 flex-none place-items-center rounded-full bg-u-accent-tint font-mono text-[11px] font-semibold text-u-accent">
          {initials(user.fullName)}
        </span>
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-medium text-u-text">{user.fullName}</div>
          <div className="truncate font-mono text-[11.5px] text-u-text3">{user.email}</div>
        </div>
        <button
          type="button"
          onClick={() => void signOut()}
          className="flex-none text-[12px] font-medium text-u-accent hover:underline"
        >
          Not you?
        </button>
      </div>

      <div className="mt-[18px]">
        <span className="type-micro-label mb-1.5 block font-mono text-u-text3">Workspace</span>
        {eligible.length === 1 ? (
          <div className="flex items-center gap-2.5 rounded-lg border border-u-border bg-u-raised px-3 py-2.5">
            <WorkspaceMark name={workspace.name} />
            <span className="text-[13px] font-medium text-u-text">{workspace.name}</span>
          </div>
        ) : (
          <>
            <div
              role="radiogroup"
              aria-label="Workspace"
              ref={radioKeys.ref}
              onKeyDown={radioKeys.onKeyDown}
              className="rounded-lg border border-u-border bg-u-raised"
            >
              {eligible.map((option, index) => {
                const isChosen = option.id === workspaceId;
                return (
                  <button
                    key={option.id}
                    type="button"
                    role="radio"
                    aria-checked={isChosen}
                    tabIndex={isChosen ? 0 : -1}
                    onClick={() => setWorkspaceId(option.id)}
                    className={cn(
                      "flex w-full items-center gap-2.5 px-3 py-2.5 text-start hover:bg-u-surface",
                      index > 0 && "border-t border-u-border",
                    )}
                  >
                    <span
                      className={cn(
                        "size-3.5 flex-none rounded-full bg-u-surface",
                        isChosen ? "border-4 border-u-accent-solid" : "border border-u-border-strong",
                      )}
                    />
                    <WorkspaceMark name={option.name} />
                    <span className="min-w-0 flex-1 text-[13px] font-medium text-u-text">{option.name}</span>
                  </button>
                );
              })}
            </div>
            <span className="mt-1.5 block font-mono text-[11px] leading-[1.5] text-u-text3">
              One workspace per connection. Connect again to add another.
            </span>
          </>
        )}
      </div>

      <div className="mt-[18px]">
        <span className="type-micro-label mb-1.5 block font-mono text-u-text3">What it can read</span>
        <div className="rounded-lg border border-u-border bg-u-raised">
          {offered.map(({ scope, label, note, personalData }, index) => {
            const isTicked = ticked.includes(scope);
            return (
              <button
                key={scope}
                type="button"
                role="checkbox"
                aria-checked={isTicked}
                onClick={() => toggle(scope)}
                className={cn(
                  "flex w-full items-start gap-2.5 px-3 py-2.5 text-start hover:bg-u-surface",
                  index > 0 && "border-t border-u-border",
                )}
              >
                <span className="mt-0.5">
                  <CheckBox checked={isTicked} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-1.5">
                    <span className="text-[13px] font-medium text-u-text">{label}</span>
                    {personalData && (
                      <span className="rounded-full bg-u-offlimits-tint px-[7px] py-px font-mono text-[9px] font-semibold uppercase tracking-[0.06em] text-u-offlimits">
                        Personal data
                      </span>
                    )}
                  </span>
                  <span className="mt-0.5 block font-mono text-[11px] leading-[1.5] text-u-text3">{note}</span>
                </span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="mt-3.5 rounded-lg border border-u-border bg-u-raised px-3 py-2.5 font-mono text-[11.5px] leading-[1.55] text-u-text3">
        Read-only: it can't add, change or delete anything. It reaches only the positions you can open today, and loses
        them when you do. Disconnect it any time in Settings → Connected AI apps.
      </div>

      <div className="mt-5 flex gap-2">
        <Button
          variant="secondary"
          className="flex-1"
          loading={pressed === "deny"}
          disabled={pressed !== null}
          onClick={() => void answer("deny")}
        >
          Deny
        </Button>
        <Button
          className="flex-1"
          loading={pressed === "allow"}
          disabled={pressed !== null || ticked.length === 0}
          onClick={() => void answer("allow")}
        >
          Allow
        </Button>
      </div>
      <p className="mt-3 text-center font-mono text-[11.5px] text-u-text3">
        Either way you go back to {context.redirectHost}.
      </p>
    </>
  );
}

function outcomeView(outcome: Outcome, context: ConsentContext | undefined): ReactNode {
  const clientName = context?.clientName ?? "The app";
  switch (outcome.kind) {
    case "allowed":
      return (
        <div className="text-center">
          <span className="mx-auto grid size-10 place-items-center rounded-full bg-u-direct-tint text-u-direct">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          </span>
          <h1 className="mt-4 text-lg font-semibold text-u-text">{clientName} is connected</h1>
          <p className="mt-2 text-sm leading-relaxed text-u-text2">
            It can now read {grantedSummary(outcome.scopes)} in {outcome.workspaceName}. You can close this window.
          </p>
          <p className="mt-3.5 text-xs text-u-text3">
            It appears in Settings → Connected AI apps, where you can disconnect it at any time.
          </p>
        </div>
      );
    case "denied":
      return (
        <div className="text-center">
          <h1 className="text-lg font-semibold text-u-text">Not connected</h1>
          <p className="mt-2 text-sm leading-relaxed text-u-text2">
            {clientName} was told no and can read nothing. You can close this window.
          </p>
        </div>
      );
    case "notConnected":
      return (
        <div className="text-center">
          <h1 className="text-lg font-semibold text-u-text">Not connected</h1>
          <p className="mt-2 text-sm leading-relaxed text-u-text2">
            Uncava couldn't connect {clientName}, and it can read nothing. Start the connection again from the app.
          </p>
        </div>
      );
    case "error":
      return <ErrorOutcome code={outcome.code} />;
  }
}

function RefusedOutcome({ clientName, workspaceName }: { clientName: string; workspaceName: string }) {
  return (
    <div className="text-center">
      <h1 className="text-lg font-semibold text-u-text">AI apps are for your firm's staff</h1>
      <p className="mt-2 text-sm leading-relaxed text-u-text2">
        You're signed in as a client contact of {workspaceName}, so {clientName} can't connect on your behalf. The
        positions shared with you stay open in Uncava as usual.
      </p>
      <Link
        to="/"
        className="mt-5 inline-flex items-center justify-center rounded-[6px] border border-u-border-strong bg-u-surface px-3.5 py-2.5 text-[13.5px] font-medium text-u-text2 hover:text-u-text"
      >
        Back to Uncava
      </Link>
    </div>
  );
}

function ErrorOutcome({ code }: { code: string }) {
  return (
    <div className="text-center">
      <h1 className="text-lg font-semibold text-u-text">This connection can't go ahead</h1>
      <p role="alert" className="mt-2 text-sm leading-relaxed text-u-offlimits">
        {refusalMessage(code)}
      </p>
      <p className="mt-3.5 text-xs text-u-text3">
        Nothing was shared, and you were not sent anywhere. Start the connection again from the app.
      </p>
    </div>
  );
}

function ConsentFrame({ children }: { children: ReactNode }) {
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

function Loading() {
  return (
    <div className="flex items-center justify-center gap-2 py-6 font-mono text-xs text-u-text3">
      <Spinner />
      Loading…
    </div>
  );
}

/**
 * Claude's and ChatGPT's own marks where the server verified them; another verified app's published logo; a letter
 * otherwise. A self-registered app always gets the letter: it could name anyone's logo, and its image URL would be
 * fetched from this page.
 */
function ClientMark({ context }: { context: ConsentContext }) {
  const [isBroken, setIsBroken] = useState(false);
  const known = knownClientMarkOf(context);
  const logo = context.verified && !isBroken ? httpsOrNull(context.logoUri) : null;
  return (
    <span
      aria-hidden="true"
      data-mark={known ?? (logo ? "logo" : "letter")}
      className={cn(
        "grid size-11 flex-none place-items-center overflow-hidden rounded-[10px] text-lg font-bold",
        context.verified
          ? "border border-u-border-strong bg-u-raised text-u-text"
          : "border border-dashed border-u-signal bg-u-signal-tint text-u-signal",
      )}
    >
      {known === "claude" ? (
        <ClaudeMark size={26} />
      ) : known === "chatgpt" ? (
        <ChatGptMark size={26} />
      ) : logo ? (
        <img
          src={logo}
          alt=""
          referrerPolicy="no-referrer"
          className="size-full object-contain p-1.5"
          onError={() => setIsBroken(true)}
        />
      ) : (
        context.clientName.trim().charAt(0).toUpperCase() || "?"
      )}
    </span>
  );
}

function httpsOrNull(uri: string | null): string | null {
  if (!uri) return null;
  try {
    return new URL(uri).protocol === "https:" ? uri : null;
  } catch {
    return null;
  }
}

function WorkspaceMark({ name }: { name: string }) {
  return (
    <span
      aria-hidden="true"
      className="grid size-[22px] flex-none place-items-center rounded-[5px] bg-u-accent-solid font-mono text-[10px] font-bold text-white"
    >
      {name.trim().charAt(0).toUpperCase()}
    </span>
  );
}

function codeOf(error: unknown): string {
  return error instanceof ApiRequestError ? error.code : "server_error";
}
