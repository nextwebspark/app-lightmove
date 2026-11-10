import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, FormError, Input, LinesSkeleton, Modal } from "../../../components/ui";
import { GoogleMark } from "../../../components/ui/BrandMarks";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import type { AccountDeletionPreview, AccountWorkspace, HandoverPosition } from "../../auth/api/types";
import { rememberAccountDeleted } from "../../auth/accountDeletedNotice";
import { messageForOAuthError } from "../../auth/oauthErrors";
import { startOAuthSignIn } from "../../auth/oauthPopup";
import { listOfNames, providerLabel } from "../lib/accountDeletion";

/**
 * Delete my account (`Settings.dc.html`, `profile`). The only admin of a workspace, or the only lead of a live
 * position, is shown what to hand over first, since nobody else could. Everyone else confirms as they would change how
 * they sign in: the password, with a code where two-factor is on, or — an account with no password — a sign-in moments
 * ago, which the provider's popup gives them here. The server asks all of it again.
 */
export function DeleteAccountModal({ onClose }: { onClose: () => void }) {
  const preview = useQuery({
    queryKey: authApi.ACCOUNT_DELETION_KEY,
    queryFn: authApi.accountDeletionPreview,
    staleTime: 0,
  });

  const blocked = !!preview.data && (preview.data.soleAdminOf.length > 0 || preview.data.soleLeadOf.length > 0);

  if (preview.isError) {
    return (
      <Modal open onClose={onClose} title="Delete your account?" className="md:w-[480px]">
        <FormError message={messageFor(preview.error)} />
      </Modal>
    );
  }
  if (!preview.data) {
    return (
      <Modal open onClose={onClose} title="Delete your account?" className="md:w-[480px]">
        <LinesSkeleton />
      </Modal>
    );
  }
  if (blocked) {
    return <HandOverFirst preview={preview.data} onClose={onClose} />;
  }
  return <ConfirmDeletion preview={preview.data} onRecheck={() => void preview.refetch()} onClose={onClose} />;
}

function HandOverFirst({ preview, onClose }: { preview: AccountDeletionPreview; onClose: () => void }) {
  const { user, switchWorkspace } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  // A workspace other than this session's is reached by switching into it first: the roster and the position's team
  // are read in the workspace the session is in.
  const handleGo = async (workspaceId: string, path: string) => {
    setError(null);
    try {
      if (user?.workspace?.id !== workspaceId) {
        await switchWorkspace(workspaceId);
      }
      onClose();
      navigate(path);
    } catch (switchError) {
      setError(messageFor(switchError));
    }
  };

  const onlyAdminLine =
    preview.soleAdminOf.length === 1
      ? "You're the only admin of the workspace below. Make someone else an admin there, or delete the workspace, then come back."
      : "You're the only admin of the workspaces below. Make someone else an admin in each, or delete it, then come back.";

  return (
    <Modal
      open
      onClose={onClose}
      title="Hand over first"
      className="md:w-[480px]"
      footer={<Button onClick={onClose}>OK</Button>}
    >
      <FormError message={error} />
      {preview.soleAdminOf.length > 0 && (
        <>
          <p className="mb-3 text-body text-u-text2">{onlyAdminLine}</p>
          <ul className="mb-4 rounded-u-chip border border-u-border bg-u-surface px-3 py-0.5">
            {preview.soleAdminOf.map((workspace) => (
              <HandoverWorkspaceRow
                key={workspace.id}
                workspace={workspace}
                onMakeAdmin={() => void handleGo(workspace.id, "/team")}
                onDeleteWorkspace={() => void handleGo(workspace.id, "/settings/general")}
              />
            ))}
          </ul>
        </>
      )}
      {preview.soleLeadOf.length > 0 && (
        <>
          <p className="mb-3 text-body text-u-text2">
            You're the only lead of the {preview.soleLeadOf.length === 1 ? "position" : "positions"} below. Seat
            another lead on each, then come back.
          </p>
          <ul className="rounded-u-chip border border-u-border bg-u-surface px-3 py-0.5">
            {preview.soleLeadOf.map((position) => (
              <HandoverPositionRow
                key={position.projectId}
                position={position}
                onOpenTeam={() => void handleGo(position.workspaceId, `/projects/${position.projectId}/team`)}
              />
            ))}
          </ul>
        </>
      )}
    </Modal>
  );
}

function HandoverWorkspaceRow({
  workspace,
  onMakeAdmin,
  onDeleteWorkspace,
}: {
  workspace: AccountWorkspace;
  onMakeAdmin: () => void;
  onDeleteWorkspace: () => void;
}) {
  return (
    <li className="flex flex-wrap items-center gap-2.5 py-2.5">
      <span
        aria-hidden="true"
        className="grid size-7 flex-none place-items-center rounded-u-control bg-u-accent-solid font-mono text-meta font-bold text-white"
      >
        {workspace.name.charAt(0).toUpperCase()}
      </span>
      <div className="min-w-0 flex-1">
        <div className="truncate text-body font-medium text-u-text">{workspace.name}</div>
        <div className="font-mono text-meta text-u-text3">You're its only admin</div>
      </div>
      <Button size="xs" variant="ghost" className="text-u-accent" onClick={onMakeAdmin}>
        Make an admin
      </Button>
      <Button size="xs" variant="ghost" className="text-u-offlimits" onClick={onDeleteWorkspace}>
        Delete workspace
      </Button>
    </li>
  );
}

function HandoverPositionRow({ position, onOpenTeam }: { position: HandoverPosition; onOpenTeam: () => void }) {
  return (
    <li className="flex flex-wrap items-center gap-2.5 py-2.5">
      <div className="min-w-0 flex-1">
        <div className="truncate text-body font-medium text-u-text">{position.title}</div>
        <div className="font-mono text-meta text-u-text3">{position.workspaceName}</div>
      </div>
      <Button size="xs" variant="ghost" className="text-u-accent" onClick={onOpenTeam}>
        Seat a lead
      </Button>
    </li>
  );
}

function ConfirmDeletion({
  preview,
  onRecheck,
  onClose,
}: {
  preview: AccountDeletionPreview;
  onRecheck: () => void;
  onClose: () => void;
}) {
  const { user, forgetDeletedAccount, adoptRestoredSession, completeSecondFactor } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState("");
  const [code, setCode] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  const usesPassword = preview.proof === "PASSWORD";
  const ready = usesPassword
    ? currentPassword !== "" && (!preview.codeRequired || code.trim() !== "")
    : preview.signedInRecently;

  const handleDelete = async () => {
    if (!ready || deleting) return;
    setDeleting(true);
    setFormError(null);
    setFieldErrors({});
    try {
      await authApi.deleteAccount(
        usesPassword ? { currentPassword, code: preview.codeRequired ? code.trim() : undefined } : {},
      );
      rememberAccountDeleted();
      forgetDeletedAccount();
      navigate("/login", { replace: true });
    } catch (error) {
      setDeleting(false);
      if (error instanceof ApiRequestError) {
        if (Object.keys(error.fieldErrors).length > 0) {
          setFieldErrors(error.fieldErrors);
          return;
        }
        // Something changed since the preview — a sign-in grew old, an admin left — so it is read again.
        if (error.code === "ACCOUNT_SIGN_IN_AGAIN" || error.code === "ACCOUNT_HANDOVER_REQUIRED") onRecheck();
      }
      setFormError(messageFor(error));
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      onSubmit={() => void handleDelete()}
      title="Delete your account?"
      className="md:w-[480px]"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" variant="danger" loading={deleting} disabled={!ready}>
            Delete my account
          </Button>
        </>
      }
    >
      <FormError message={formError} />
      <ul className="mb-4 list-disc ps-4.5 text-body text-u-text2">
        <li>
          {preview.workspaces.length > 0
            ? `You leave ${listOfNames(preview.workspaces.map((workspace) => workspace.name))} now, and are signed out everywhere.`
            : "You are signed out everywhere."}
        </li>
        <li>Your sign-in, profile, sessions, API keys and connected AI apps are deleted.</li>
        <li>The notes, candidates and positions you worked on stay with each workspace, shown as a former member's.</li>
      </ul>

      {usesPassword ? (
        <>
          <Field label="Password" error={fieldErrors.currentPassword}>
            <Input
              type="password"
              autoComplete="current-password"
              placeholder="Enter your password to confirm"
              value={currentPassword}
              invalid={!!fieldErrors.currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </Field>
          {preview.codeRequired && (
            <Field label="Code from your app, or a recovery code" error={fieldErrors.code}>
              <Input
                autoComplete="one-time-code"
                spellCheck={false}
                placeholder="123456 or ABCD-EFGH-IJKL-MNOP"
                value={code}
                invalid={!!fieldErrors.code}
                onChange={(event) => setCode(event.target.value)}
              />
            </Field>
          )}
        </>
      ) : (
        <SignInAgain
          preview={preview}
          email={user?.email ?? ""}
          onSignedIn={async (signedInAs) => {
            // A popup can sign in as whoever the provider holds; this dialog must never go on to delete them.
            if (signedInAs.id !== user?.id) {
              onClose();
              navigate("/", { replace: true });
              return;
            }
            onRecheck();
          }}
          adoptRestoredSession={adoptRestoredSession}
          completeSecondFactor={completeSecondFactor}
        />
      )}
    </Modal>
  );
}

/** An account with no password shows it is them by signing in again, in the provider's popup, from here. */
function SignInAgain({
  preview,
  email,
  onSignedIn,
  adoptRestoredSession,
  completeSecondFactor,
}: {
  preview: AccountDeletionPreview;
  email: string;
  onSignedIn: (signedInAs: { id: string }) => Promise<void>;
  adoptRestoredSession: () => Promise<{ id: string } | null>;
  completeSecondFactor: (code: string) => Promise<{ id: string }>;
}) {
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [owesCode, setOwesCode] = useState(false);
  const [code, setCode] = useState("");
  const abandon = useRef<(() => void) | null>(null);
  useEffect(() => () => abandon.current?.(), []);

  const handleSignIn = (providerId: string) => {
    abandon.current?.();
    setError(null);
    setPending(providerId);
    abandon.current = startOAuthSignIn(providerId, {
      onSuccess: () => {
        void (async () => {
          const adopted = await adoptRestoredSession();
          setPending(null);
          if (!adopted) {
            setError(messageForOAuthError("OAUTH_FAILED"));
            return;
          }
          await onSignedIn(adopted);
        })();
      },
      onSecondFactor: () => {
        setPending(null);
        setOwesCode(true);
      },
      onError: (errorCode) => {
        setPending(null);
        setError(messageForOAuthError(errorCode));
      },
      onCancel: () => setPending(null),
    });
  };

  const handleCode = async () => {
    setError(null);
    try {
      const signedIn = await completeSecondFactor(code.trim());
      setOwesCode(false);
      await onSignedIn(signedIn);
    } catch (codeError) {
      setError(messageFor(codeError));
    }
  };

  if (preview.signedInRecently) {
    return (
      <div className="flex items-center gap-2 rounded-u-chip border border-u-border bg-u-surface px-3.5 py-3 text-note text-u-text2">
        <Icon d={ICONS.check} size={15} className="flex-none text-u-direct" />
        Signed in again as {email} just now.
      </div>
    );
  }

  return (
    <div className="rounded-u-chip border border-u-border bg-u-surface px-3.5 py-3">
      <FormError message={error} />
      {owesCode ? (
        <>
          <Field label="Code from your app, or a recovery code">
            <Input
              autoComplete="one-time-code"
              spellCheck={false}
              placeholder="123456"
              value={code}
              onChange={(event) => setCode(event.target.value)}
            />
          </Field>
          <Button type="button" size="sm" variant="secondary" disabled={code.trim() === ""} onClick={() => void handleCode()}>
            Verify
          </Button>
        </>
      ) : preview.signInProviders.length === 0 ? (
        <p className="text-note text-u-text2">
          There is no password to type. Sign out and back in, then delete your account within 10 minutes.
        </p>
      ) : (
        <>
          <p className="text-note text-u-text2">
            You sign in with {listOfNames(preview.signInProviders.map(providerLabel), "or")}, so there is no password
            to type. Sign in again to show it's you.
          </p>
          <div className="mt-2.5 flex flex-wrap gap-2">
            {preview.signInProviders.map((providerId) => (
              <Button
                key={providerId}
                type="button"
                size="sm"
                variant="secondary"
                loading={pending === providerId}
                disabled={pending !== null && pending !== providerId}
                onClick={() => handleSignIn(providerId)}
              >
                {providerId === "google" && <GoogleMark />}
                Sign in with {providerLabel(providerId)} again
              </Button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
