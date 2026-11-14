import { useState } from "react";
import { Link } from "react-router-dom";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import type { RecentApproach } from "../api/types";
import { approachLines } from "../lib/recentApproach";

/**
 * Going ahead with someone a teammate recently approached, or who is in process on another position: the lines,
 * and a box to tick saying so before the action goes. A position the reader cannot open arrives unnamed.
 */
export function RecentApproachConfirm({
  open,
  fullName,
  approach,
  confirmLabel,
  onConfirm,
  onClose,
}: {
  open: boolean;
  fullName: string;
  approach: RecentApproach;
  confirmLabel: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const [isUnderstood, setIsUnderstood] = useState(false);
  const handleClose = () => {
    setIsUnderstood(false);
    onClose();
  };
  return (
    <ConfirmDialog
      open={open}
      title={`${fullName} was recently approached`}
      confirmLabel={confirmLabel}
      tone="primary"
      confirmDisabled={!isUnderstood}
      onConfirm={() => {
        setIsUnderstood(false);
        onConfirm();
      }}
      onClose={handleClose}
    >
      <ul className="flex flex-col gap-1 text-note">
        {approachLines(approach).map((line, index) => (
          <li key={index} className="flex items-start gap-2">
            <span aria-hidden className="mt-1.5 size-1.5 flex-none rounded-full bg-u-signal" />
            {line.href ? (
              <Link to={line.href} target="_blank" rel="noreferrer" className="text-u-text hover:underline">
                {line.text}
              </Link>
            ) : (
              <span>{line.text}</span>
            )}
          </li>
        ))}
      </ul>
      <label className="mt-1 flex cursor-pointer items-start gap-2.5 rounded-u-card border border-u-border p-2.5">
        <input
          type="checkbox"
          checked={isUnderstood}
          onChange={(event) => setIsUnderstood(event.target.checked)}
          className="mt-0.5 accent-u-accent-solid"
        />
        <span className="text-body text-u-text">
          I have read this and want to go ahead with {fullName}
        </span>
      </label>
    </ConfirmDialog>
  );
}
