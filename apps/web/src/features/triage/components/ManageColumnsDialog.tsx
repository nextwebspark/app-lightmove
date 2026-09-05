import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Input, Select } from "../../../components/ui";
import { Modal } from "../../../components/ui/Modal";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import * as customColumnsApi from "../../customcolumns/api/customColumnsApi";
import type {
  CustomColumn,
  CustomColumnTarget,
  CustomColumnType,
} from "../../customcolumns/api/types";

const TYPE_LABELS: Record<CustomColumnType, string> = {
  text: "Text",
  number: "Number",
  date: "Date",
  boolean: "Yes / no",
};

const TARGET_LABELS: Record<CustomColumnTarget, string> = {
  company: "Company",
  candidate: "Person",
};

const LABEL = "font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3";

const ROW_BUTTON =
  "rounded-[7px] border border-line px-2 py-[5px] text-[11.5px] font-medium transition disabled:opacity-50";

/**
 * The columns this mandate has added to its own grid: rename one, change what it holds, move it,
 * hide it, or remove it.
 *
 * <p>Adding a column here as well as through an import matters more than it looks: a consultant who
 * wants somewhere to record a fact should not have to invent a spreadsheet to get a column for it.
 *
 * <p>Hide and Remove are deliberately both offered and worded differently. Hiding takes a column off
 * the grid and keeps every value in it; removing takes the definition and leaves the values on the
 * rows, so defining it again under the same name brings the data back. Neither destroys anything,
 * which is why neither asks for confirmation.
 */
export function ManageColumnsDialog({
  open,
  projectId,
  columns,
  onClose,
}: {
  open: boolean;
  projectId: string;
  columns: readonly CustomColumn[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [newLabel, setNewLabel] = useState("");
  const [newTarget, setNewTarget] = useState<CustomColumnTarget>("candidate");
  const [newType, setNewType] = useState<CustomColumnType>("text");
  const [renaming, setRenaming] = useState<{ id: string; label: string } | null>(null);

  const refresh = () =>
    void queryClient.invalidateQueries({
      queryKey: customColumnsApi.CUSTOM_COLUMNS_KEY(projectId),
    });

  const define = useMutation({
    mutationFn: () =>
      customColumnsApi.defineCustomColumn(projectId, {
        target: newTarget,
        label: newLabel.trim(),
        dataType: newType,
      }),
    onSuccess: (column) => {
      refresh();
      setNewLabel("");
      toast(`${column.label} added to the grid`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  const update = useMutation({
    mutationFn: ({ id, ...payload }: { id: string; label?: string; hidden?: boolean }) =>
      customColumnsApi.updateCustomColumn(projectId, id, payload),
    onSuccess: () => {
      refresh();
      setRenaming(null);
    },
    onError: (error) => toast(messageFor(error)),
  });

  const reorder = useMutation({
    mutationFn: (columnIds: string[]) => customColumnsApi.reorderCustomColumns(projectId, columnIds),
    onSuccess: refresh,
    onError: (error) => toast(messageFor(error)),
  });

  const remove = useMutation({
    mutationFn: (id: string) => customColumnsApi.deleteCustomColumn(projectId, id),
    onSuccess: () => {
      refresh();
      toast("Column removed. The values stay on the rows.");
    },
    onError: (error) => toast(messageFor(error)),
  });

  /**
   * Moves one column and sends the whole resulting order. Scoped to its own grid, because the two
   * halves are ordered independently and the server refuses a request mixing them.
   */
  const move = (column: CustomColumn, offset: number) => {
    const siblings = columns.filter((other) => other.target === column.target);
    const from = siblings.findIndex((other) => other.id === column.id);
    const to = from + offset;
    if (from < 0 || to < 0 || to >= siblings.length) return;
    const next = [...siblings];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    reorder.mutate(next.map((other) => other.id));
  };

  return (
    <Modal open={open} onClose={onClose} title="Columns on this mandate" className="md:w-[620px]">
      <div className="flex flex-col gap-4">
        {columns.length === 0 ? (
          <p className="font-sans text-[13px] text-text3">
            This mandate has only the built-in columns. Add one below, or import a spreadsheet and any
            column it carries that we do not have becomes one.
          </p>
        ) : (
          <ul className="flex flex-col rounded-lg border border-line-soft">
            {columns.map((column) => (
              <li
                key={column.id}
                className="flex flex-wrap items-center gap-2 border-b border-line-soft px-2.5 py-2 last:border-b-0"
              >
                {renaming?.id === column.id ? (
                  <Input
                    autoFocus
                    aria-label={`New name for ${column.label}`}
                    value={renaming.label}
                    onChange={(event) => setRenaming({ id: column.id, label: event.target.value })}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        update.mutate({ id: column.id, label: renaming.label.trim() });
                      }
                      // Stopped so the key that leaves a rename does not also close the dialog.
                      if (event.key === "Escape") {
                        event.stopPropagation();
                        setRenaming(null);
                      }
                    }}
                    onBlur={() => setRenaming(null)}
                    className="min-w-[160px] flex-1"
                  />
                ) : (
                  <span className="min-w-0 flex-1">
                    <span
                      className={`block truncate font-sans text-[13px] ${
                        column.hidden ? "text-text3 line-through" : "text-text"
                      }`}
                    >
                      {column.label}
                    </span>
                    <span className="mt-px block font-mono text-[11px] text-text3">
                      {TARGET_LABELS[column.target]} · {TYPE_LABELS[column.dataType]}
                    </span>
                  </span>
                )}

                <span className="flex flex-none gap-1.5">
                  <button
                    type="button"
                    aria-label={`Move ${column.label} earlier`}
                    onClick={() => move(column, -1)}
                    disabled={reorder.isPending}
                    className={`${ROW_BUTTON} text-text2 hover:border-text3 hover:text-text`}
                  >
                    <Icon d={ICONS.chevronDown} size={13} className="rotate-180" />
                  </button>
                  <button
                    type="button"
                    aria-label={`Move ${column.label} later`}
                    onClick={() => move(column, 1)}
                    disabled={reorder.isPending}
                    className={`${ROW_BUTTON} text-text2 hover:border-text3 hover:text-text`}
                  >
                    <Icon d={ICONS.chevronDown} size={13} />
                  </button>
                  <button
                    type="button"
                    onClick={() => setRenaming({ id: column.id, label: column.label })}
                    className={`${ROW_BUTTON} text-text2 hover:border-text3 hover:text-text`}
                  >
                    Rename
                  </button>
                  <button
                    type="button"
                    onClick={() => update.mutate({ id: column.id, hidden: !column.hidden })}
                    disabled={update.isPending}
                    className={`${ROW_BUTTON} text-text2 hover:border-text3 hover:text-text`}
                  >
                    {column.hidden ? "Show" : "Hide"}
                  </button>
                  <button
                    type="button"
                    onClick={() => remove.mutate(column.id)}
                    disabled={remove.isPending}
                    className={`${ROW_BUTTON} text-red hover:border-red`}
                  >
                    Remove
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}

        <div className="rounded-lg border border-line-soft p-2.5">
          <p className={`mb-2 ${LABEL}`}>Add a column</p>
          <div className="flex flex-wrap gap-2">
            <Input
              aria-label="New column name"
              placeholder="e.g. Ethnicity"
              value={newLabel}
              onChange={(event) => setNewLabel(event.target.value)}
              className="min-w-[160px] flex-1"
            />
            <Select
              aria-label="What the new column is about"
              value={newTarget}
              onChange={(event) => setNewTarget(event.target.value as CustomColumnTarget)}
              className="w-auto flex-none"
            >
              <option value="candidate">About the person</option>
              <option value="company">About the company</option>
            </Select>
            <Select
              aria-label="Kind of value in the new column"
              value={newType}
              onChange={(event) => setNewType(event.target.value as CustomColumnType)}
              className="w-auto flex-none"
            >
              {(Object.keys(TYPE_LABELS) as CustomColumnType[]).map((type) => (
                <option key={type} value={type}>
                  {TYPE_LABELS[type]}
                </option>
              ))}
            </Select>
            <Button
              onClick={() => define.mutate()}
              loading={define.isPending}
              disabled={newLabel.trim().length === 0}
              className="flex-none"
            >
              Add
            </Button>
          </div>
        </div>

        <div className="flex justify-end">
          <Button variant="secondary" onClick={onClose}>
            Done
          </Button>
        </div>
      </div>
    </Modal>
  );
}
