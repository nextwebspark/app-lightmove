import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Field, LinesSkeleton, Modal, TextArea, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { formatDate, formatShortDate } from "../../../lib/datetime/format";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { useAddressedPanel } from "../../../lib/useAddressedPanel";
import * as subprocessorNoticesApi from "../api/subprocessorNoticesApi";
import type { SubprocessorChange } from "../api/subprocessorNoticesApi";

const CLOSED = "This change has already taken effect, so it can no longer be objected to.";

/**
 * Sub-processor changes (`SettingsDataRetention.dc.html`, #1491): each new sub-processor Uncava announced, 30 days
 * ahead, with Object… until it takes effect. The notice email's link opens the objection itself (`?object=`).
 */
export function SubprocessorChangesCard() {
  const objecting = useAddressedPanel(subprocessorNoticesApi.OBJECT_PARAM);
  const changes = useQuery({
    queryKey: subprocessorNoticesApi.SUBPROCESSOR_CHANGES_KEY,
    queryFn: ({ signal }) => subprocessorNoticesApi.getSubprocessorChanges(signal),
  });
  const opened = changes.data?.find((change) => change.id === objecting.value);

  return (
    <section aria-label="Sub-processor changes" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="text-body font-semibold text-u-text">Sub-processor changes</div>
      <p className="mt-0.5 max-w-[560px] font-mono text-meta text-u-text3">
        Uncava tells you 30 days before a new sub-processor handles your workspace's data. You can object before it
        takes effect.
      </p>

      {changes.isError ? (
        <p className="mt-3.5 text-note text-u-text3">{messageFor(changes.error)}</p>
      ) : changes.isPending ? (
        <div className="mt-3.5">
          <LinesSkeleton />
        </div>
      ) : changes.data.length === 0 ? (
        <p className="mt-3.5 pb-1 pt-4 text-center font-mono text-meta text-u-text3">No changes announced.</p>
      ) : (
        <ul aria-label="Sub-processor changes" className="mt-3">
          {changes.data.map((change, index) => (
            <ChangeRow
              key={change.id}
              change={change}
              isFirst={index === 0}
              onObject={() => objecting.open(change.id)}
            />
          ))}
        </ul>
      )}

      {opened && <ObjectToSubprocessorDialog key={opened.id} change={opened} onClose={objecting.close} />}
    </section>
  );
}

function ChangeRow({ change, isFirst, onObject }: { change: SubprocessorChange; isFirst: boolean; onObject: () => void }) {
  return (
    <li
      aria-label={change.provider}
      className={cn("flex flex-wrap items-center gap-x-4 gap-y-1.5 py-[11px]", !isFirst && "border-t border-u-border")}
    >
      <div className="min-w-0 flex-[1_1_260px]">
        <div className="text-note font-semibold text-u-text">{change.provider}</div>
        <div className="mt-0.5 font-mono text-meta text-u-text3">{change.receives}</div>
      </div>
      <span className={cn("whitespace-nowrap font-mono text-meta", change.inEffect ? "text-u-text3" : "text-u-text2")}>
        {change.inEffect ? `In effect since ${formatDate(change.effectiveOn)}` : `Takes effect ${formatDate(change.effectiveOn)}`}
      </span>
      {!change.inEffect && !change.objection && (
        <Button variant="secondary" size="xs" onClick={onObject} aria-label={`Object to ${change.provider}`}>
          Object…
        </Button>
      )}
      {!change.inEffect && change.objection && (
        <span className="flex flex-none items-center gap-1.5 font-mono text-meta text-u-text2">
          You objected on {formatShortDate(change.objection.objectedAt)} ·
          <button
            type="button"
            onClick={onObject}
            aria-label={`Change the objection to ${change.provider}`}
            className="font-sans font-medium text-u-accent hover:underline"
          >
            Change
          </button>
        </span>
      )}
    </li>
  );
}

/** Object to {provider} (`SettingsDataDialogs.dc.html`, `object-subprocessor` and `object-closed`). */
function ObjectToSubprocessorDialog({ change, onClose }: { change: SubprocessorChange; onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [reason, setReason] = useState(change.objection?.reason ?? "");
  const [isClosed, setClosed] = useState(change.inEffect);
  const [failure, setFailure] = useState<string | null>(null);

  const send = useMutation({
    mutationFn: () => subprocessorNoticesApi.objectToSubprocessorChange(change.id, reason.trim()),
    onSuccess: (updated) => {
      queryClient.setQueryData<SubprocessorChange[]>(subprocessorNoticesApi.SUBPROCESSOR_CHANGES_KEY, (held) =>
        held?.map((existing) => (existing.id === updated.id ? updated : existing)),
      );
      toast.success(`Objection sent. Uncava will contact you about ${change.provider}`);
      onClose();
    },
    onError: (error) => {
      if (codeOf(error) === "SUBPROCESSOR_NOTICE_CLOSED") {
        setClosed(true);
        void queryClient.invalidateQueries({ queryKey: subprocessorNoticesApi.SUBPROCESSOR_CHANGES_KEY });
        return;
      }
      setFailure(messageFor(error));
    },
  });

  const isBlocked = !reason.trim() || isClosed;
  const when = formatDate(change.effectiveOn);

  return (
    <Modal
      open
      onClose={onClose}
      onSubmit={() => {
        if (isBlocked) return;
        setFailure(null);
        send.mutate();
      }}
      title={`Object to ${change.provider}`}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={send.isPending} disabled={isBlocked}>
            Send objection
          </Button>
        </>
      }
    >
      <p className="mb-4 text-body text-u-text2">
        {change.receives}. {isClosed ? `It took effect on ${when}.` : `It takes effect on ${when}.`}
      </p>
      {isClosed ? (
        <div
          role="alert"
          className="rounded-lg border border-u-offlimits/35 bg-u-offlimits-tint px-3 py-2.5 text-note text-u-text2"
        >
          {CLOSED}
        </div>
      ) : (
        <>
          {failure && (
            <p role="alert" className="mb-3 text-note text-u-offlimits">
              {failure}
            </p>
          )}
          <Field
            label="Why do you object?"
            hint="Uncava will contact you about it before the change takes effect."
            action={
              <span className="font-mono text-meta text-u-text3">
                {reason.length} / {subprocessorNoticesApi.OBJECTION_LIMIT}
              </span>
            }
          >
            <TextArea
              rows={5}
              required
              aria-required="true"
              maxLength={subprocessorNoticesApi.OBJECTION_LIMIT}
              value={reason}
              onChange={(event) => setReason(event.target.value.slice(0, subprocessorNoticesApi.OBJECTION_LIMIT))}
            />
          </Field>
        </>
      )}
    </Modal>
  );
}
