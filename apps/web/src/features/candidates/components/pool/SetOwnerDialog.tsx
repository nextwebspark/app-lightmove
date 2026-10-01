import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Avatar } from "../../../../components/ui/Avatar";
import { Button, Modal, useToast } from "../../../../components/ui";
import { useRadioGroupKeys } from "../../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import type { Member } from "../../../workspace/api/types";
import * as poolApi from "../../api/poolApi";

const NOBODY = "nobody";

/**
 * One owner, or nobody, for every person ticked. The owner keeps the relationship; it changes nobody's
 * access. Nothing is chosen on opening: "Nobody" preselected would clear every owner on one press.
 */
export function SetOwnerDialog({
  open,
  onClose,
  personIds,
  staff,
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  personIds: string[];
  staff: Member[];
  onDone?: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  /** `undefined` until a choice is made; `null` is the choice of nobody. */
  const [owner, setOwner] = useState<string | null | undefined>(undefined);
  const target = `${personIds.length} ${personIds.length === 1 ? "person" : "people"}`;

  const saving = useMutation({
    mutationFn: (ownerUserId: string | null) => poolApi.assignOwners(personIds, ownerUserId),
    onSuccess: (_, ownerUserId) => {
      const name = staff.find((member) => member.userId === ownerUserId)?.fullName;
      toast(name ? `${name} now owns ${target}` : `Cleared the owner of ${target}`);
      void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
      onDone?.();
      onClose();
    },
    onError: (error) => toast(messageFor(error)),
  });

  const options = [...staff.map((member) => ({ id: member.userId, member })), { id: null, member: null }];
  const keys = useRadioGroupKeys(
    options.map((option) => option.id ?? NOBODY),
    owner === undefined ? null : (owner ?? NOBODY),
    (value) => setOwner(value === NOBODY ? null : value),
  );

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Set the owner of ${target}`}
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            disabled={owner === undefined}
            loading={saving.isPending}
            onClick={() => owner !== undefined && saving.mutate(owner)}
          >
            Set owner
          </Button>
        </div>
      }
    >
      <p className="mb-3 text-[13px]/[1.55] text-u-text2">
        The owner keeps the relationship across searches. It changes nobody&apos;s access.
      </p>
      <div
        ref={keys.ref}
        role="radiogroup"
        aria-label="Owner"
        onKeyDown={keys.onKeyDown}
        className="flex max-h-[320px] flex-col gap-1 overflow-y-auto"
      >
        {options.map(({ id, member }, index) => (
          <button
            key={id ?? "nobody"}
            type="button"
            role="radio"
            aria-checked={owner === id}
            tabIndex={owner === id || (owner === undefined && index === 0) ? 0 : -1}
            onClick={() => setOwner(id)}
            className={cn(
              "flex items-center gap-2.5 rounded-[6px] px-2 py-1.5 text-start hover:bg-u-raised",
              owner === id && "bg-u-raised",
            )}
          >
            {member ? (
              <Avatar id={member.userId} name={member.fullName} src={member.avatarUrl} size="sm" />
            ) : (
              <span className="grid size-6 place-items-center rounded-full bg-u-raised font-mono text-[11px] text-u-text3">—</span>
            )}
            <span className="flex-1 text-[13px] text-u-text">{member ? member.fullName : "Nobody"}</span>
            {owner === id && <Icon d={ICONS.check} size={13} className="text-u-accent" />}
          </button>
        ))}
      </div>
    </Modal>
  );
}
