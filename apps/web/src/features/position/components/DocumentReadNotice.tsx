import { Icon, ICONS } from "../../../components/layout/Icon";

/** The one line a document reading leaves behind, and only when it went wrong — success is silent. */
export function DocumentReadNotice({
  message,
  retrying,
  onRetry,
  onDismiss,
}: {
  message: string;
  retrying: boolean;
  onRetry: () => void;
  onDismiss: () => void;
}) {
  return (
    <div
      role="alert"
      className="mb-6 flex flex-wrap items-center gap-2 rounded-[8px] bg-u-signal-tint px-3 py-2 text-note text-u-signal"
    >
      <Icon d={ICONS.warning} size={14} className="flex-none" />
      <span className="min-w-0 flex-1">{message}</span>
      <button
        type="button"
        onClick={onRetry}
        disabled={retrying}
        className="flex-none font-semibold hover:underline disabled:cursor-not-allowed disabled:opacity-60"
      >
        {retrying ? "Reading…" : "Read again"}
      </button>
      <button
        type="button"
        aria-label="Dismiss"
        onClick={onDismiss}
        className="flex-none opacity-70 transition hover:opacity-100"
      >
        <Icon d={ICONS.close} size={12} />
      </button>
    </div>
  );
}
