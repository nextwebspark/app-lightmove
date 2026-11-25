import { useQuery } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { FormError, LinesSkeleton } from "../../../../components/ui";
import { Drawer } from "../../../../components/ui/Drawer";
import { PanelCloseButton } from "../../../../components/ui/PanelCloseButton";
import { formatInstantDateTime } from "../../../../lib/datetime/format";
import { messageFor } from "../../../../lib/errorCodes";
import * as variableApi from "../api/variableApi";
import type { ApplicationVariable } from "../api/types";
import { valueInWords } from "../lib/variableLabels";

/** History of one variable: every change, newest first. Never edited or removed. */
export function VariableHistoryDrawer({ variable, onClose }: { variable: ApplicationVariable; onClose: () => void }) {
  const { data: changes, isPending, isError, error } = useQuery({
    queryKey: variableApi.VARIABLE_HISTORY_KEY(variable.key),
    queryFn: ({ signal }) => variableApi.getVariableHistory(variable.key, signal),
  });

  return (
    <Drawer open onClose={onClose} label={`History of ${variable.label}`}>
      <PanelCloseButton onClose={onClose} />
      <div className="flex-none border-b border-u-border px-[18px] py-4 pr-12">
        <h2 className="text-lead font-semibold">{variable.label}</h2>
        <div className="mt-0.5 break-all font-u-num text-xs text-u-text3">{variable.key}</div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-[18px] pb-[18px] pt-1.5">
        {isPending ? (
          <div className="pt-3">
            <LinesSkeleton lines={4} />
          </div>
        ) : isError ? (
          <div className="pt-3">
            <FormError message={messageFor(error)} />
          </div>
        ) : (
          <>
            <ul>
              {changes.map((change, index) => (
                <li key={`${change.changedAt}-${index}`} className="border-b border-u-border py-3.5">
                  <div className="flex items-center gap-2 font-u-num text-body font-medium text-u-text">
                    <span className="text-u-text3">{change.oldValue}</span>
                    <Icon d={ICONS.arrowRight} size={13} className="text-u-text3" />
                    <span className="sr-only">to</span>
                    <span>{change.newValue}</span>
                  </div>
                  <p className="mt-1.5 text-body text-u-text2">{change.note}</p>
                  <div className="mt-1.5 text-xs text-u-text3">
                    {change.changedBy ?? "Former member"} · {formatInstantDateTime(change.changedAt)}
                  </div>
                </li>
              ))}
            </ul>
            <div className="pt-3.5 text-xs text-u-text3">
              Shipped default: {valueInWords(variable.defaultValue, variable.unit)}
            </div>
          </>
        )}
      </div>
    </Drawer>
  );
}
