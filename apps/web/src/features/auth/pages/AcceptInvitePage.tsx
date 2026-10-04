import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo, Notice } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import type { InvitationPreview } from "../api/types";
import { acceptInviteSchema, type AcceptInviteValues } from "../schemas";
import { titleCase } from "../../../lib/format";

/**
 * Where an invitation lands — and, since membership is invitation-only, the only door into an existing
 * workspace.
 *
 * <p>The common arrival is a stranger with a token and no account: they set a name and a password right
 * here and are in immediately, with <b>no email-verification step</b>. The invitation token, mailed only
 * to the invited address, is the proof of the mailbox that verification would otherwise establish, so the
 * account is created already verified. The address is the invitation's, shown read-only — never theirs to
 * choose, because acceptance is bound to the invited address and an account created under a different one
 * would only be refused.
 *
 * <p>Someone who already has an account is handled too: a token arrival whose address already exists is
 * told to log in, and a signed-in invitee accepts with one click.
 */
export function AcceptInvitePage() {
  const [params] = useSearchParams();
  const token = params.get("token");
  const { user, loading } = useAuth();

  // Wait for the boot session-restore to settle. Until it does `user` is null even for a signed-in
  // invitee, and rendering the anonymous create-account form here would flash it in their face — a
  // wrong-account or already-placed user briefly sees a "set your password" screen before it corrects.
  if (loading) {
    return <Centered>Loading…</Centered>;
  }

  if (token) {
    return <TokenArrival token={token} />;
  }

  // No token in the URL: only the server-derived invitations (an already-signed-in invitee) belong here.
  if (user && user.pendingInvitations.length > 0) {
    return <ServerDerivedArrival />;
  }

  return <Dead title="No invitation" detail="This link is missing its token." />;
}

// ── Arrival with a token, from the emailed link ──────────────────────────────

function TokenArrival({ token }: { token: string }) {
  const { user } = useAuth();

  const {
    data: invitation,
    isLoading,
    error: previewError,
  } = useQuery({
    queryKey: ["invitation", token],
    queryFn: () => authApi.previewInvitation(token),
    retry: false,
  });

  if (isLoading) {
    return <Centered>Checking your invitation…</Centered>;
  }

  if (previewError) {
    const code = previewError instanceof ApiRequestError ? previewError.code : null;
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

  // Nobody signed in — the common case. Create the invited account right here; they are in on submit.
  if (!user) {
    return <AcceptSignupForm token={token} invitation={invitation} />;
  }

  // Signed in already, so they had an account: the wrong-account / already-placed / ready states.
  return (
    <Shell workspaceName={invitation.workspaceName} subtitle={invitedAs(invitation)} error={null}>
      <SignedInBody
        invitation={{ email: invitation.email, workspaceName: invitation.workspaceName }}
        token={token}
      />
    </Shell>
  );
}

/**
 * The invitee sets a name and a password (twice) and is in — no verification round-trip. The email is
 * the invitation's, shown read-only. An address that already has an account is sent to log in instead.
 */
function AcceptSignupForm({ token, invitation }: { token: string; invitation: InvitationPreview }) {
  const { acceptInviteSignup } = useAuth();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [alreadyRegistered, setAlreadyRegistered] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<AcceptInviteValues>({
    resolver: zodResolver(acceptInviteSchema),
    defaultValues: { fullName: "", password: "", confirmPassword: "" },
  });

  const onSubmit = async (values: AcceptInviteValues) => {
    setFormError(null);
    setAlreadyRegistered(false);
    try {
      await acceptInviteSignup(token, values.fullName, values.password);
      navigate("/", { replace: true });
    } catch (error) {
      // The one failure with a way forward: this address already has an account, so log in and accept
      // from there. The address prefills the login form — and deliberately nothing else is revealed.
      if (error instanceof ApiRequestError && error.code === "EMAIL_ALREADY_REGISTERED") {
        setAlreadyRegistered(true);
        return;
      }
      setFormError(messageFor(error));
    }
  };

  return (
    <Shell workspaceName={invitation.workspaceName} subtitle={invitedAs(invitation)} error={formError}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="text-left">
        <Field label="Work email" hint="The address your invitation was sent to.">
          <Input type="email" value={invitation.email} readOnly className="cursor-not-allowed text-u-text3" />
        </Field>

        <Field label="Full name" error={errors.fullName?.message}>
          <Input
            autoComplete="name"
            autoFocus
            placeholder="Yara Haddad"
            invalid={!!errors.fullName}
            {...register("fullName")}
          />
        </Field>

        <Field
          label="Password"
          error={errors.password?.message}
          hint="Use at least 8 characters, with one number."
        >
          <Input
            type="password"
            autoComplete="new-password"
            placeholder="8+ characters"
            invalid={!!errors.password}
            {...register("password")}
          />
        </Field>

        <Field label="Confirm password" error={errors.confirmPassword?.message}>
          <Input
            type="password"
            autoComplete="new-password"
            placeholder="Re-enter your password"
            invalid={!!errors.confirmPassword}
            {...register("confirmPassword")}
          />
        </Field>

        {alreadyRegistered ? (
          <Notice>
            {invitation.email} already has an account.{" "}
            <Link
              to="/login"
              state={{ email: invitation.email }}
              className="font-medium text-u-accent hover:underline"
            >
              Log in to accept →
            </Link>
          </Notice>
        ) : (
          <Button type="submit" loading={isSubmitting} className="mt-1 w-full">
            Accept &amp; join
          </Button>
        )}
      </form>

      <p className="mt-4 text-[11.5px] leading-relaxed text-u-text3">
        By continuing you agree to the{" "}
        <a href="/terms" className="text-u-accent hover:underline">
          Terms
        </a>{" "}
        and{" "}
        <a href="/privacy" className="text-u-accent hover:underline">
          Privacy Policy
        </a>
        .
      </p>
    </Shell>
  );
}

// ── Arrival with no token: an already-signed-in invitee, routed here by the server ───────────────────

function ServerDerivedArrival() {
  const { user, acceptAndSwitch } = useAuth();
  const navigate = useNavigate();

  const [accepting, setAccepting] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const invitations = user!.pendingInvitations;
  const invitation = invitations[0];

  const accept = async (invitationId: string) => {
    setAccepting(invitationId);
    setError(null);
    try {
      await acceptAndSwitch(() => authApi.acceptInvitationById(invitationId));
      navigate("/", { replace: true });
    } catch (err) {
      setError(messageFor(err));
      setAccepting(null);
    }
  };

  // An unverified existing account that was then invited. It still has to prove its address the ordinary
  // way — this path never created it, so the invite token never vouched for it.
  if (!user!.emailVerified) {
    return (
      <Shell workspaceName={invitation.workspaceName} subtitle="one step left" error={null}>
        <Notice>
          Confirm {user!.email} first — open the link in your inbox, and we&rsquo;ll bring you back
          here.
        </Notice>
      </Shell>
    );
  }

  if (invitations.length > 1) {
    return (
      <Shell workspaceName="a workspace" subtitle="You have more than one invitation" error={error}>
        <div className="flex flex-col gap-2 text-left">
          {invitations.map((candidate) => (
            <div key={candidate.id} className="flex items-center gap-3 rounded-lg border border-u-border p-3">
              <div className="min-w-0 flex-1">
                <div className="truncate text-body font-medium">{candidate.workspaceName}</div>
                <div className="font-mono text-meta text-u-text3">as {titleCase(candidate.role)}</div>
              </div>
              <Button
                className="!px-3.5 !py-[6px] !text-note"
                loading={accepting === candidate.id}
                disabled={accepting !== null && accepting !== candidate.id}
                onClick={() => void accept(candidate.id)}
              >
                Accept
              </Button>
            </div>
          ))}
        </div>
      </Shell>
    );
  }

  return (
    <Shell
      workspaceName={invitation.workspaceName}
      subtitle={`${invitation.inviterName ? `${invitation.inviterName} invited you` : "You were invited"} as ${titleCase(invitation.role)}`}
      error={error}
    >
      <Notice>You'll be in as soon as you accept.</Notice>

      <Button className="w-full" loading={accepting !== null} onClick={() => void accept(invitation.id)}>
        Accept invitation
      </Button>
    </Shell>
  );
}

/**
 * The states for someone already signed in when they open a token link — they had an account. A fresh
 * invitee never reaches here (they have no session and get the form above).
 */
function SignedInBody({
  invitation,
  token,
}: {
  invitation: { email: string; workspaceName: string };
  token: string;
}) {
  const { user, acceptAndSwitch, signOut } = useAuth();
  const navigate = useNavigate();
  const [accepting, setAccepting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Signed in as somebody else. A forwarded invitation must not admit the person it was forwarded to,
  // so there is nothing to do here but change who you are.
  if (user!.email.toLowerCase() !== invitation.email.toLowerCase()) {
    return (
      <>
        <Notice>
          This invitation is for {invitation.email}, but you are signed in as {user!.email}.
        </Notice>

        <Button variant="secondary" className="w-full" onClick={() => void signOut()}>
          Sign out and switch account
        </Button>
      </>
    );
  }

  const accept = async () => {
    setAccepting(true);
    setError(null);
    try {
      // The switch mints a token carrying the joined workspace's claim. Without it the token in memory
      // still names wherever they were — or nothing — and the workspace they just joined refuses them.
      await acceptAndSwitch(() => authApi.acceptInvitation(token));
      navigate("/", { replace: true });
    } catch (err) {
      setError(messageFor(err));
      setAccepting(false);
    }
  };

  // Right person, ready to accept.
  return (
    <>
      <FormError message={error} />
      <Notice>You'll be in as soon as you accept.</Notice>

      <Button className="w-full" loading={accepting} onClick={() => void accept()}>
        Accept invitation
      </Button>
    </>
  );
}

// ── Presentational ───────────────────────────────────────────────────────────

/** "Alok invited you as Member", or "You were invited as Member" when the inviter is unknown. */
function invitedAs(invitation: InvitationPreview): string {
  const who = invitation.inviterName ? `${invitation.inviterName} invited you` : "You were invited";
  return `${who} as ${titleCase(invitation.role)}`;
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
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <Logo />

      <Card className="w-[440px] max-w-[94vw] text-center [animation-delay:60ms]">
        <h1 className="text-[19px] font-semibold leading-tight">Join {workspaceName}</h1>
        <p className="mb-6 mt-1 font-mono text-xs text-u-text3">{subtitle}</p>

        <FormError message={error} />

        {children}
      </Card>
    </div>
  );
}

function Dead({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <Logo />

      <Card className="w-[420px] max-w-[94vw] text-center [animation-delay:60ms]">
        <h1 className="text-[19px] font-semibold">{title}</h1>
        <p className="mb-6 mt-2 text-[13px] leading-relaxed text-u-text2">{detail}</p>

        <Link to="/login" className="text-[12.5px] font-medium text-u-accent hover:underline">
          Back to sign in
        </Link>
      </Card>
    </div>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 p-4 sm:p-6">
      <Logo />
      <p className="font-mono text-xs text-u-text3">{children}</p>
    </div>
  );
}
