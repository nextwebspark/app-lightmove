import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, FormError, Logo, Notice } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { pendingInvite } from "../pendingInvite";
import { titleCase } from "../../../lib/format";

/**
 * Where an invitation lands — and, since membership is invitation-only, the only door into an
 * existing workspace.
 *
 * <p>Two arrivals share this page:
 *
 * <ul>
 *   <li><b>With a token</b> — the emailed link, possibly in a browser with no session at all. The
 *       anonymous preview says what is being offered, and the token rides through signup in
 *       sessionStorage.
 *   <li><b>Without a token</b> — an invitee routed here by the server-derived
 *       {@code user.pendingInvitation}, typically after verifying in a fresh tab where the emailed
 *       token's sessionStorage does not exist. Their verified, matching address is the proof the
 *       token existed to give, so acceptance needs no token at all.
 * </ul>
 *
 * <p>The email is not theirs to choose. The invitation is addressed to a person; acceptance checks the
 * account's address against it, and an invitee who signs up with a different one gets refused for a
 * mismatch nobody ever showed them. So signup is handed the invited address and pins it.
 */
export function AcceptInvitePage() {
  const [params] = useSearchParams();
  const token = params.get("token");
  const { user } = useAuth();

  if (token) {
    return <TokenArrival token={token} />;
  }

  // No token in the URL: only the server-derived invitation can put someone here usefully.
  if (user?.pendingInvitation) {
    return <ServerDerivedArrival />;
  }

  return <Dead title="No invitation" detail="This link is missing its token." />;
}

// ── Arrival 1: the emailed link, token in hand ───────────────────────────────

function TokenArrival({ token }: { token: string }) {
  const { user, reload } = useAuth();
  const navigate = useNavigate();

  const [accepting, setAccepting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    data: invitation,
    isLoading,
    error: previewError,
  } = useQuery({
    queryKey: ["invitation", token],
    queryFn: () => authApi.previewInvitation(token),
    retry: false,
  });

  // Kept for the round trip through signup and email verification, both of which are full page loads
  // that would drop it. The address travels with it: signup pins its email field to the invited one,
  // because an account created under a different address is one acceptance will refuse.
  useEffect(() => {
    if (invitation) {
      pendingInvite.remember({ token, email: invitation.email });
    }
  }, [token, invitation]);

  if (isLoading) {
    return <Centered>Checking your invitation…</Centered>;
  }

  if (previewError) {
    const code = previewError instanceof ApiRequestError ? previewError.code : null;
    pendingInvite.clear();

    return code === "INVITATION_EXPIRED" ? (
      <Dead
        title="This invitation has expired"
        detail="Invitations are valid for seven days. Ask an admin to send you another."
      />
    ) : (
      <Dead
        title="This invitation is no longer valid"
        detail="It may have already been used, or been revoked. Ask an admin to send you another."
      />
    );
  }

  if (!invitation) {
    return null;
  }

  const accept = async () => {
    setAccepting(true);
    setError(null);
    try {
      await authApi.acceptInvitation(token);
      pendingInvite.clear();
      // Mints a token carrying the new workspace claim, then re-reads the user. Without the refresh the
      // token in memory still says they belong to nothing, and the workspace they just joined would
      // refuse them.
      await reload();
      navigate("/", { replace: true });
    } catch (err) {
      setError(
        err instanceof ApiRequestError ? err.problem.detail : "Could not accept the invitation.",
      );
      setAccepting(false);
    }
  };

  return (
    <Shell
      workspaceName={invitation.workspaceName}
      subtitle={`${invitation.inviterName ? `${invitation.inviterName} invited you` : "You were invited"} as ${titleCase(invitation.role)}`}
      error={error}
    >
      <Body
        invitation={{ email: invitation.email, workspaceName: invitation.workspaceName }}
        user={user}
        accepting={accepting}
        onAccept={() => void accept()}
      />
    </Shell>
  );
}

// ── Arrival 2: no token, the server knows the invitation ─────────────────────

function ServerDerivedArrival() {
  const { user, reload } = useAuth();
  const navigate = useNavigate();

  const [accepting, setAccepting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const invitation = user!.pendingInvitation!;

  const accept = async () => {
    setAccepting(true);
    setError(null);
    try {
      await authApi.acceptPendingInvitation();
      pendingInvite.clear();
      await reload();
      navigate("/", { replace: true });
    } catch (err) {
      setError(
        err instanceof ApiRequestError ? err.problem.detail : "Could not accept the invitation.",
      );
      setAccepting(false);
    }
  };

  // Only an unplaced, self-matching user carries pendingInvitation, so the wrong-account and
  // already-placed states of the token path cannot occur here. Unverified still can.
  if (!user!.emailVerified) {
    return (
      <Shell workspaceName={invitation.workspaceName} subtitle="one step left" error={null}>
        <Notice>
          Confirm {user!.email} first — check your inbox for the verification link. We'll bring you
          back here.
        </Notice>
      </Shell>
    );
  }

  return (
    <Shell
      workspaceName={invitation.workspaceName}
      subtitle={`You were invited as ${titleCase(invitation.role)}`}
      error={error}
    >
      <Notice>You'll be in as soon as you accept.</Notice>

      <Button className="w-full" loading={accepting} onClick={() => void accept()}>
        Accept invitation
      </Button>
    </Shell>
  );
}

/**
 * The five ways someone can arrive with a token, and what each of them actually needs next.
 *
 * <p>Every branch but the last is a dead end unless it is spelled out — "sign in" is useless advice to
 * someone signed in as the wrong person, and an accept button is a lie to someone who cannot yet use it.
 */
function Body({
  invitation,
  user,
  accepting,
  onAccept,
}: {
  invitation: { email: string; workspaceName: string };
  user: ReturnType<typeof useAuth>["user"];
  accepting: boolean;
  onAccept: () => void;
}) {
  const { signOut } = useAuth();

  // 1. Nobody is signed in. They almost certainly have no account — this is the common case.
  if (!user) {
    return (
      <>
        <Notice>Your account is your seat — you'll be in as soon as you accept.</Notice>

        <Button className="w-full" onClick={() => (window.location.href = "/signup")}>
          Create your account
        </Button>

        <p className="mt-4 text-[12.5px] text-text2">
          Already have one?{" "}
          <Link to="/login" className="text-sky hover:underline">
            Sign in
          </Link>
        </p>
      </>
    );
  }

  // 2. Signed in as somebody else. A forwarded invitation must not admit the person it was forwarded
  //    to, so there is nothing to do here but change who you are.
  if (user.email.toLowerCase() !== invitation.email.toLowerCase()) {
    return (
      <>
        <Notice>
          This invitation is for {invitation.email}, but you are signed in as {user.email}.
        </Notice>

        <Button variant="secondary" className="w-full" onClick={() => void signOut()}>
          Sign out and switch account
        </Button>
      </>
    );
  }

  // 3. Right person, unproven address. Possession of the link is not proof of the mailbox — someone who
  //    saw it over a shoulder has the token and not the inbox.
  if (!user.emailVerified) {
    return (
      <Notice>
        Confirm {user.email} first — check your inbox for the verification link. We'll bring you back
        here.
      </Notice>
    );
  }

  // 4. Already in a workspace. One person, one workspace.
  if (user.workspace) {
    return (
      <Notice>
        You already belong to {user.workspace.name}. Leave it before joining another.
      </Notice>
    );
  }

  // 5. Ready.
  return (
    <>
      <Notice>You'll be in as soon as you accept.</Notice>

      <Button className="w-full" loading={accepting} onClick={onAccept}>
        Accept invitation
      </Button>
    </>
  );
}

function Shell({
  workspaceName,
  subtitle,
  error,
  children,
}: {
  workspaceName: string;
  subtitle: string;
  error: string | null;
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />

      <Card className="w-[440px] max-w-[94vw] text-center [animation-delay:60ms]">
        <h1 className="text-[19px] font-semibold leading-tight">Join {workspaceName}</h1>
        <p className="mb-6 mt-1 font-mono text-xs text-text3">{subtitle}</p>

        <FormError message={error} />

        {children}
      </Card>
    </div>
  );
}

function Dead({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />

      <Card className="w-[420px] max-w-[94vw] text-center [animation-delay:60ms]">
        <h1 className="text-[19px] font-semibold">{title}</h1>
        <p className="mb-6 mt-2 text-[13px] leading-relaxed text-text2">{detail}</p>

        <Link to="/login" className="text-[12.5px] font-medium text-sky hover:underline">
          Back to sign in
        </Link>
      </Card>
    </div>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6">
      <Logo />
      <p className="font-mono text-xs text-text3">{children}</p>
    </div>
  );
}
