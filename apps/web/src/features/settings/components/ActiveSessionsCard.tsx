import { Icon, ICONS } from "../../../components/layout/Icon";
import { Skeleton } from "../../../components/ui";
import { formatRelativeTime } from "../../../lib/format";
import type { ActiveSession, DeviceKind } from "../../auth/api/types";

const DEVICE_ICONS: Record<DeviceKind, string> = {
  DESKTOP: ICONS.laptop,
  MOBILE: ICONS.phone,
  TABLET: ICONS.phone,
  UNKNOWN: ICONS.laptop,
};

/**
 * The mockup's active-sessions card: every signed-in device, with the caller's own marked and the
 * rest revocable.
 *
 * A refused read renders as an error and never as an empty list. "You have no other sessions" is a
 * statement of fact about the account's security, and stating it because a request failed is worse
 * than saying nothing.
 */
export function ActiveSessionsCard({
  sessions,
  isLoading,
  isError,
  revokingSessionId,
  isRevokingOthers,
  onRevoke,
  onRevokeOthers,
}: {
  sessions: ActiveSession[];
  isLoading: boolean;
  isError: boolean;
  revokingSessionId: string | null;
  isRevokingOthers: boolean;
  onRevoke: (sessionId: string) => void;
  onRevokeOthers: () => void;
}) {
  const hasOtherSessions = sessions.some((session) => !session.current);

  return (
    <div className="rounded-[10px] border border-line-soft bg-panel2 p-5">
      <div className="mb-3 flex items-center">
        <div className="text-[13px] font-semibold">Active sessions</div>
        {hasOtherSessions && (
          <button
            type="button"
            onClick={onRevokeOthers}
            disabled={isRevokingOthers}
            className="ml-auto rounded-md px-2 py-1 text-xs font-medium text-red hover:bg-red-dim disabled:opacity-50"
          >
            Sign out all others
          </button>
        )}
      </div>

      {isError ? (
        <p role="alert" className="border-t border-line-soft pt-3 font-mono text-[11.5px] text-red">
          Your sessions could not be loaded. Reload the page to try again.
        </p>
      ) : isLoading ? (
        <Skeleton className="h-12 w-full bg-line" />
      ) : (
        sessions.map((session) => (
          <SessionRow
            key={session.id}
            session={session}
            isRevoking={revokingSessionId === session.id}
            onRevoke={() => onRevoke(session.id)}
          />
        ))
      )}
    </div>
  );
}

function SessionRow({
  session,
  isRevoking,
  onRevoke,
}: {
  session: ActiveSession;
  isRevoking: boolean;
  onRevoke: () => void;
}) {
  const meta = [session.ipAddress, formatRelativeTime(session.lastActiveAt)]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className="flex items-center gap-3 border-t border-line-soft py-2.5">
      <Icon d={DEVICE_ICONS[session.deviceKind]} className="shrink-0 text-text3" />

      <div className="min-w-0 flex-1">
        <div className="truncate text-[13px] font-medium">{session.device}</div>
        <div className="mt-px font-mono text-[11px] text-text3">{meta}</div>
      </div>

      {session.current ? (
        <span className="rounded-full bg-sky-dim px-[9px] py-[3px] font-mono text-[10px] font-semibold uppercase tracking-[0.05em] text-sky">
          This device
        </span>
      ) : (
        <button
          type="button"
          onClick={onRevoke}
          disabled={isRevoking}
          className="rounded-[7px] border border-line px-2.5 py-1 text-xs font-medium text-text3 hover:border-red hover:text-red disabled:opacity-50"
        >
          Revoke
        </button>
      )}
    </div>
  );
}
