import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { Button } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { cn } from "../../../lib/cn";
import { initials } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import type { User } from "../../auth/api/types";
import { homeFor } from "../../auth/homeFor";
import type { ApiKeyScope } from "../../settings/api/types";
import { answerConsent, getConsentContext, storeAuthorizationRequest } from "../api/oauthConsentApi";
import type { ConsentContext, ConsentWorkspace } from "../api/types";
import { ClientMark } from "../components/ClientMark";
import { ConsentFrame, ConsentLoading } from "../components/ConsentFrame";
import { ErrorOutcome, OutcomeView, RefusedOutcome, VerifyEmailFirst } from "../components/ConsentOutcomes";
import { ScopeChoices } from "../components/ScopeChoices";
import { WorkspacePicker } from "../components/WorkspacePicker";
import { returnToClient } from "../lib/clientRedirect";
import {
  CONSENT_SCOPES,
  clientSubtitle,
  consentOutcomeOf,
  initialScopes,
  readClientRequest,
  type ClientRequest,
  type ConsentOutcome,
} from "../lib/consentRequest";

type AuthorizeRequest = Extract<ClientRequest, { kind: "request" }>;

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
        <ConsentLoading />
      </ConsentFrame>
    );
  }
  if (!user) {
    // The request rides the return-to, so signing in — by password or a provider — lands back here with it intact.
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (!user.emailVerified) {
    return (
      <ConsentFrame>
        <VerifyEmailFirst />
      </ConsentFrame>
    );
  }
  if (!user.workspace) {
    return <Navigate to={homeFor(user)} replace />;
  }

  return <ConsentForm clientRequest={clientRequest} user={user} />;
}

function ConsentForm({ clientRequest, user }: { clientRequest: AuthorizeRequest; user: User }) {
  const { clientId, redirectUri, scope } = clientRequest;
  const context = useQuery({
    queryKey: ["oauth", "consent-context", clientId, redirectUri, scope],
    queryFn: () => getConsentContext(clientId, redirectUri, scope),
    retry: false,
    staleTime: Infinity,
  });
  const [outcome, setOutcome] = useState<ConsentOutcome | null>(null);

  if (outcome) {
    return (
      <ConsentFrame>
        <OutcomeView outcome={outcome} clientName={context.data?.clientName ?? "The app"} />
      </ConsentFrame>
    );
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
        <ConsentLoading />
      </ConsentFrame>
    );
  }

  const { workspaces } = context.data;
  const eligible = workspaces.filter((workspace) => workspace.eligible);
  if (eligible.length === 0) {
    const current = workspaces.find((workspace) => workspace.id === user.workspace?.id) ?? workspaces[0];
    return (
      <ConsentFrame>
        <RefusedOutcome
          clientName={context.data.clientName}
          workspaceName={current?.name ?? user.workspace?.name ?? "this workspace"}
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
  clientRequest: AuthorizeRequest;
  user: User;
  eligible: ConsentWorkspace[];
  onOutcome: (outcome: ConsentOutcome) => void;
}) {
  const { signOut } = useAuth();
  const [ticked, setTicked] = useState<ApiKeyScope[]>(() => initialScopes(context.requestedScopes));
  const [workspaceId, setWorkspaceId] = useState<string>(
    () => eligible.find((workspace) => workspace.id === user.workspace?.id)?.id ?? eligible[0].id,
  );
  const [pressed, setPressed] = useState<"allow" | "deny" | null>(null);
  const workspace = eligible.find((candidate) => candidate.id === workspaceId) ?? eligible[0];

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
      onOutcome(consentOutcomeOf(redirectUri, choice, workspace.name, granted));
      returnToClient(redirectUri);
    } catch (error) {
      onOutcome({ kind: "error", code: codeOf(error) });
    }
  };

  return (
    <>
      <div className="flex items-center gap-3">
        <ClientMark client={context} />
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
        <WorkspacePicker workspaces={eligible} value={workspace.id} onChange={setWorkspaceId} />
      </div>

      <div className="mt-[18px]">
        <span className="type-micro-label mb-1.5 block font-mono text-u-text3">What it can read</span>
        <ScopeChoices requested={context.requestedScopes} ticked={ticked} onToggle={toggle} />
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

function codeOf(error: unknown): string {
  return error instanceof ApiRequestError ? error.code : "server_error";
}
