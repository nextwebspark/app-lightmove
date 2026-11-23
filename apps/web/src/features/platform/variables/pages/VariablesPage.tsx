import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Button, FormError, LinesSkeleton, useToast } from "../../../../components/ui";
import { formatInstantDateTime } from "../../../../lib/datetime/format";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { PlatformTwoFactorRequired } from "../../components/PlatformTwoFactorRequired";
import { PromptPill } from "../../prompts/components/PromptPill";
import * as variableApi from "../api/variableApi";
import type { ApplicationVariable } from "../api/types";
import { ResetVariableDialog } from "../components/ResetVariableDialog";
import { VariableEditForm } from "../components/VariableEditForm";
import { VariableHistoryDrawer } from "../components/VariableHistoryDrawer";
import { groupsOf, valueInWords, valueLabel } from "../lib/variableLabels";

type OpenPanel = { kind: "edit" | "reset" | "history"; key: string } | null;

/** Platform → Variables: the allowlisted values a super admin changes without a release. */
export default function VariablesPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [open, setOpen] = useState<OpenPanel>(null);

  const { data: variables, isPending, isError, error } = useQuery({
    queryKey: variableApi.VARIABLES_KEY,
    queryFn: ({ signal }) => variableApi.listVariables(signal),
  });

  if (codeOf(error) === "PLATFORM_TWO_FACTOR_REQUIRED") return <PlatformTwoFactorRequired />;

  const opened = (kind: NonNullable<OpenPanel>["kind"]) =>
    open?.kind === kind ? variables?.find((variable) => variable.key === open.key) : undefined;
  const resetting = opened("reset");
  const historyOf = opened("history");

  const store = (updated: ApplicationVariable) => {
    queryClient.setQueryData<ApplicationVariable[]>(variableApi.VARIABLES_KEY, (held) =>
      held?.map((variable) => (variable.key === updated.key ? updated : variable)),
    );
    void queryClient.invalidateQueries({ queryKey: variableApi.VARIABLE_HISTORY_KEY(updated.key) });
    setOpen(null);
  };

  return (
    <div>
      <PageHeader
        title="Variables"
        subtitle="Values the application runs on, changed here without a release. A value with no change runs on its shipped default. A change reaches every instance within seconds."
      />

      {isPending ? (
        <LinesSkeleton lines={8} />
      ) : isError ? (
        <FormError message={messageFor(error)} />
      ) : (
        <>
          {groupsOf(variables).map((group) => (
            <section
              key={group.name}
              aria-label={group.name}
              className="mb-4 rounded-lg border border-u-border-strong bg-u-surface px-[18px] pb-1.5 pt-1"
            >
              <div className="flex flex-wrap items-baseline gap-2.5 border-b border-u-border pb-3 pt-3.5">
                <h2 className="text-lead font-semibold">{group.name}</h2>
                <span className="font-u-num text-xs text-u-text3">{group.keyPattern}</span>
              </div>
              <ul>
                {group.variables.map((variable) => (
                  <VariableRow
                    key={variable.key}
                    variable={variable}
                    isEditing={open?.kind === "edit" && open.key === variable.key}
                    onOpen={(kind) => setOpen({ kind, key: variable.key })}
                    onCancel={() => setOpen(null)}
                    onSaved={(updated) => {
                      store(updated);
                      toast.success(`${updated.label} is now ${valueInWords(updated.value, updated.unit)} on every instance`);
                    }}
                  />
                ))}
              </ul>
            </section>
          ))}
          <p className="mx-0.5 mt-3 max-w-[680px] text-xs text-u-text3">
            Only the values listed here can be changed. Keys, secrets and sign-in protections are never on the list.
          </p>
        </>
      )}

      {resetting && (
        <ResetVariableDialog
          variable={resetting}
          onClose={() => setOpen(null)}
          onReset={(updated) => {
            store(updated);
            toast.success(`${updated.label} is back on its default`);
          }}
        />
      )}
      {historyOf && <VariableHistoryDrawer variable={historyOf} onClose={() => setOpen(null)} />}
    </div>
  );
}

function VariableRow({
  variable,
  isEditing,
  onOpen,
  onCancel,
  onSaved,
}: {
  variable: ApplicationVariable;
  isEditing: boolean;
  onOpen: (kind: NonNullable<OpenPanel>["kind"]) => void;
  onCancel: () => void;
  onSaved: (updated: ApplicationVariable) => void;
}) {
  return (
    <li aria-label={variable.label} className="border-b border-u-border py-3.5 last:border-b-0">
      <div className="flex flex-wrap items-start gap-x-5 gap-y-2.5">
        <div className="min-w-0 flex-[1_1_320px]">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-body font-semibold text-u-text">{variable.label}</span>
            {variable.changed && <PromptPill tone="warning">Changed</PromptPill>}
          </div>
          <div className="mt-0.5 text-xs text-u-text2">{variable.description}</div>
          <div className="mt-0.5 break-all font-u-num text-xs text-u-text3">{variable.key}</div>
        </div>
        {!isEditing && (
          <>
            <div className="w-[84px] flex-none">
              <span className="type-micro-label block font-mono text-u-text3">Now</span>
              <span className="font-u-num text-body text-u-text">{valueLabel(variable.value, variable.unit)}</span>
            </div>
            <div className="w-[84px] flex-none">
              <span className="type-micro-label block font-mono text-u-text3">Default</span>
              <span className="font-u-num text-body text-u-text3">
                {valueLabel(variable.defaultValue, variable.unit)}
              </span>
            </div>
            <div className="min-w-0 flex-[0_0_150px]">
              <span className="type-micro-label block font-mono text-u-text3">Last changed</span>
              {variable.lastChangedAt ? (
                <>
                  <span className="block text-xs text-u-text2">{variable.lastChangedBy ?? "Former member"}</span>
                  <span className="block text-xs text-u-text3">{formatInstantDateTime(variable.lastChangedAt)}</span>
                </>
              ) : (
                <span className="block text-xs text-u-text2">Never changed</span>
              )}
            </div>
            <div className="ms-auto flex flex-[0_0_190px] items-center justify-end gap-0.5">
              {variable.lastChangedAt && (
                <Button
                  type="button"
                  variant="ghost"
                  size="xs"
                  aria-label={`History of ${variable.label}`}
                  onClick={() => onOpen("history")}
                >
                  History
                </Button>
              )}
              {variable.changed && (
                <Button
                  type="button"
                  variant="ghost"
                  size="xs"
                  aria-label={`Reset ${variable.label}`}
                  onClick={() => onOpen("reset")}
                >
                  Reset
                </Button>
              )}
              <Button
                type="button"
                variant="secondary"
                size="xs"
                aria-label={`Edit ${variable.label}`}
                onClick={() => onOpen("edit")}
              >
                Edit
              </Button>
            </div>
          </>
        )}
      </div>
      {isEditing && <VariableEditForm variable={variable} onCancel={onCancel} onSaved={onSaved} />}
    </li>
  );
}
