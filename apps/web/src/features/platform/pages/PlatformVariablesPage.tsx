import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, CardsSkeleton, Input, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { changeSetting, listSettings, PLATFORM_SETTINGS_KEY, resetSetting } from "../api/platformConfigApi";
import type { PlatformSetting, PlatformSettingGroup } from "../api/types";
import { ConfigRefreshButton } from "../components/ConfigRefreshButton";

const GROUPS: { group: PlatformSettingGroup; label: string }[] = [
  { group: "SOURCING", label: "Find executives" },
  { group: "FAIR_USE", label: "Fair use per seat, each month" },
  { group: "TRIAL", label: "Trial" },
  { group: "EXPORT", label: "Exports" },
  { group: "MCP", label: "Connected AI apps" },
  { group: "ASSISTANT", label: "Assistant" },
];

/**
 * Settings → Platform → Variables, a super admin's: the allowlisted limits and model settings every workspace runs
 * on, each changed, saved and reset in place. The server decides what fits; this page shows what it said.
 */
export function PlatformVariablesPage() {
  const settings = useQuery({ queryKey: PLATFORM_SETTINGS_KEY, queryFn: ({ signal }) => listSettings(signal) });

  return (
    <>
      <PageHeader
        title="Variables"
        subtitle="Limits and model settings every workspace runs on. A changed value applies everywhere within 30 seconds, or at once with Refresh. Only the values listed here can be changed from this page."
        action={<ConfigRefreshButton page="settings" config={settings.data?.config} />}
      />

      {settings.isError ? (
        <p className="text-body text-u-text3">{messageFor(settings.error)}</p>
      ) : settings.isPending ? (
        <CardsSkeleton />
      ) : (
        GROUPS.map(({ group, label }) => {
          const rows = settings.data.settings.filter((setting) => setting.group === group);
          if (rows.length === 0) return null;
          return (
            <section key={group} className="mb-4" aria-label={label}>
              <h2 className="type-micro-label mb-2 px-0.5 font-mono text-u-text3">{label}</h2>
              <ul className="divide-y divide-u-border rounded-u-card border border-u-border bg-u-surface px-4">
                {rows.map((setting) => (
                  <VariableRow key={setting.key} setting={setting} />
                ))}
              </ul>
            </section>
          );
        })
      )}

      <p className="mt-1 max-w-[680px] font-mono text-note text-u-text3">
        Keys, secrets and sign-in settings are never listed here. Reset goes back to the value Uncava started with.
      </p>
    </>
  );
}

function VariableRow({ setting }: { setting: PlatformSetting }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(setting.value);
  const [fieldError, setFieldError] = useState<string | null>(null);

  const refresh = () => void queryClient.invalidateQueries({ queryKey: PLATFORM_SETTINGS_KEY });

  const saving = useMutation({
    mutationFn: () => changeSetting(setting.key, draft.trim()),
    onSuccess: () => {
      setEditing(false);
      toast(`${setting.label} saved`);
      refresh();
    },
    onError: (error) => {
      const named = error instanceof ApiRequestError ? error.fieldErrors[setting.key] : undefined;
      if (named) setFieldError(named);
      else toast.error(messageFor(error));
    },
  });

  const resetting = useMutation({
    mutationFn: () => resetSetting(setting.key),
    onSuccess: () => {
      toast(`${setting.label} is back to its default`);
      refresh();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const startEditing = () => {
    setDraft(setting.value);
    setFieldError(null);
    setEditing(true);
  };

  return (
    <li className="flex flex-wrap items-start gap-3 py-3">
      <div className="min-w-[220px] flex-1">
        <div className="text-body font-medium text-u-text">{setting.label}</div>
        <div className="mt-0.5 font-mono text-meta text-u-text3">{setting.key}</div>
        <p className="mt-1 max-w-[460px] text-note text-u-text2">{setting.description}</p>
      </div>

      <div className="w-full sm:w-[220px]">
        {editing ? (
          <form
            id={`variable-${setting.key}`}
            onSubmit={(event) => {
              event.preventDefault();
              if (draft.trim()) saving.mutate();
            }}
          >
            <Input
              value={draft}
              onChange={(event) => {
                setDraft(event.target.value);
                setFieldError(null);
              }}
              onKeyDown={(event) => event.key === "Escape" && setEditing(false)}
              aria-label={setting.label}
              invalid={!!fieldError}
              maxLength={200}
              autoFocus
              className="py-1.5"
            />
            <span aria-live="polite" className="mt-1.5 block font-mono text-meta">
              {fieldError ? (
                <span className="text-u-offlimits">{fieldError}</span>
              ) : (
                <span className="text-u-text3">{rangeOf(setting)}</span>
              )}
            </span>
          </form>
        ) : (
          <>
            <div className="font-mono text-body font-medium text-u-text">{shown(setting, setting.value)}</div>
            <div className="mt-0.5 font-mono text-meta text-u-text3">Default {shown(setting, setting.defaultValue)}</div>
            {setting.overridden && (
              <div className="mt-0.5 font-mono text-meta text-u-accent">{changedBy(setting)}</div>
            )}
          </>
        )}
      </div>

      {/* Each button keyed: unkeyed, React reused Edit's node as Save, and the click that opened the editor went
          on to submit the form it now named — the old value, sent before anything was typed. */}
      <span className="ms-auto flex gap-1 sm:w-[150px] sm:justify-end">
        {editing ? (
          <>
            <Button key="cancel" type="button" variant="ghost" size="xs" onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button
              key="save"
              type="submit"
              form={`variable-${setting.key}`}
              variant="secondary"
              size="xs"
              disabled={!draft.trim()}
              loading={saving.isPending}
            >
              Save
            </Button>
          </>
        ) : (
          <>
            {setting.overridden && (
              <Button
                key="reset"
                type="button"
                variant="ghost"
                size="xs"
                title="Use the default again"
                loading={resetting.isPending}
                onClick={() => resetting.mutate()}
              >
                Reset
              </Button>
            )}
            <Button key="edit" type="button" variant="ghost" size="xs" onClick={startEditing}>
              Edit
            </Button>
          </>
        )}
      </span>
    </li>
  );
}

const NUMBER = new Intl.NumberFormat("en-GB");

function shown(setting: PlatformSetting, value: string): string {
  if (setting.type !== "INT" && setting.type !== "LONG") return value;
  const number = Number(value);
  return Number.isFinite(number) ? NUMBER.format(number) : value;
}

function rangeOf(setting: PlatformSetting): string {
  switch (setting.type) {
    case "MODEL_NAME":
      return "A Gemini model name, such as gemini-3.5-flash";
    case "DURATION":
      return `From ${setting.min}d to ${setting.max}d`;
    default:
      return `From ${NUMBER.format(setting.min)} to ${NUMBER.format(setting.max)}`;
  }
}

function changedBy(setting: PlatformSetting): string {
  const changed = setting.updatedByName ? `Changed by ${setting.updatedByName}` : "Changed";
  const on = formatInstantDate(setting.updatedAt);
  return on ? `${changed} · ${on}` : changed;
}
