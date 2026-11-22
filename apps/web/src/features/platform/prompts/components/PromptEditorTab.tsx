import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Button, FormError, Input, Select, TextArea, useToast } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import { cn } from "../../../../lib/cn";
import { formatDate } from "../../../../lib/datetime/format";
import { messageFor } from "../../../../lib/errorCodes";
import * as promptApi from "../api/promptApi";
import type { AiModel, GenerationSettings, PromptDetail, PromptVersionSummary, SettingKey } from "../api/types";
import { isRetiringSoon, modelOptionLabel, parseSetting, rangeLabel, SETTINGS, unusableReason } from "../lib/promptLabels";
import { PromptPill } from "./PromptPill";

const NOTE_LIMIT = 300;
const DEFAULT_MODEL = "";
const LABEL = "mb-1.5 block type-micro-label font-mono text-u-text3";
const HINT = "mt-1.5 block font-mono text-xs text-u-text3";
const ERROR = "mt-1.5 block font-mono text-xs text-u-offlimits";

type SettingTexts = Record<SettingKey, string>;

/**
 * The editor: one version opened into a draft of its text, model and settings. Saving always makes a new
 * version and never changes what serves. Remount it (by `key`) to open another version.
 */
export function PromptEditorTab({
  detail,
  models,
  version,
  body,
  onSaved,
}: {
  detail: PromptDetail;
  models: AiModel[];
  version: PromptVersionSummary;
  body: string;
  onSaved: (updated: PromptDetail) => void;
}) {
  const toast = useToast();
  const [text, setText] = useState(body);
  const [modelId, setModelId] = useState(version.modelId ?? DEFAULT_MODEL);
  const [settings, setSettings] = useState<SettingTexts>({
    temperature: textOf(version.settings.temperature),
    thinkingBudget: textOf(version.settings.thinkingBudget),
    maxOutputTokens: textOf(version.settings.maxOutputTokens),
  });
  const [note, setNote] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const chosen = models.find((model) => model.modelId === modelId) ?? null;
  const configured = models.find((model) => model.modelId === detail.defaultModelId) ?? null;
  const bounds = chosen ?? configured ?? models[0] ?? null;
  const temperature = parseSetting(settings.temperature);

  const save = useMutation({
    mutationFn: (parsed: GenerationSettings) =>
      promptApi.savePromptVersion(detail.prompt.id, {
        body: text,
        provider: chosen?.provider ?? null,
        modelId: chosen?.modelId ?? null,
        settings: parsed,
        changeNote: note.trim(),
      }),
    onSuccess: (updated) => {
      toast.success(`Saved as version ${updated.versions[0].version}. Nothing serves it until it is put live`);
      onSaved(updated);
    },
    onError: (error) => {
      const fields = error instanceof ApiRequestError ? error.fieldErrors : {};
      setFieldErrors(fields);
      setFormError(Object.keys(fields).length > 0 ? null : messageFor(error));
    },
  });

  const handleSave = () => {
    if (note.trim() === "") {
      toast("Add a change note first");
      return;
    }
    const parsed = parsedSettings(settings, bounds);
    setFormError(null);
    setFieldErrors(parsed.errors);
    if (Object.keys(parsed.errors).length === 0) save.mutate(parsed.values);
  };

  return (
    <div>
      <label className="block">
        <span className={LABEL}>Instructions · Version {version.version}</span>
        <TextArea
          aria-label="Prompt text"
          spellCheck={false}
          value={text}
          invalid={!!fieldErrors.body}
          onChange={(event) => {
            setText(event.target.value);
            setFieldErrors(({ body: _cleared, ...rest }) => rest);
          }}
          className="min-h-[300px] font-u-num text-xs leading-[1.7] [tab-size:2]"
        />
      </label>
      {fieldErrors.body && (
        <span role="alert" className={ERROR}>
          {fieldErrors.body}
        </span>
      )}
      {detail.variables.length > 0 && (
        <span className={HINT}>
          Keep {detail.variables.map((name) => `{${name}}`).join(" and ")} exactly as written. They are filled in when
          the prompt is used.
        </span>
      )}

      <div className="mt-[18px] flex flex-wrap gap-5">
        <div className="min-w-0 flex-[1_1_300px]">
          <label className="block">
            <span className={LABEL}>Model</span>
            <Select aria-label="Model" value={modelId} invalid={!!fieldErrors.modelId} onChange={(event) => setModelId(event.target.value)}>
              <option value={DEFAULT_MODEL}>Default model{configured ? ` (${configured.displayName})` : ""}</option>
              {models.map((model) => (
                <option key={model.modelId} value={model.modelId} disabled={unusableReason(model, detail.needs) !== null}>
                  Vertex AI · {modelOptionLabel(model, detail.needs)}
                </option>
              ))}
            </Select>
          </label>
          {fieldErrors.modelId ? (
            <span role="alert" className={ERROR}>
              {fieldErrors.modelId}
            </span>
          ) : (
            <span className={HINT}>
              {chosen
                ? `${chosen.status} · retires ${formatDate(chosen.retiresOn)} · same region and data terms as today`
                : "Follows the deployment's configured model."}
            </span>
          )}
          {chosen && isRetiringSoon(chosen.retiresInDays) && (
            <span role="alert" className={ERROR}>
              This model retires in {chosen.retiresInDays} days. After that, calls fall back to the default model.
            </span>
          )}
        </div>

        <div className="min-w-0 flex-[1_1_300px]">
          <span className={LABEL}>Settings</span>
          {SETTINGS.map(({ key, label, unit }) => {
            const typed = parseSetting(settings[key]);
            const error = fieldErrors[`settings.${key}`];
            return (
              <div key={key} className="py-[5px]">
                <div className="flex flex-wrap items-center gap-2.5">
                  <span className="flex-[1_1_130px] text-body text-u-text2">{label}</span>
                  <Input
                    aria-label={label}
                    inputMode="decimal"
                    value={settings[key]}
                    invalid={!!error}
                    placeholder={textOf(detail.defaults[key])}
                    onChange={(event) => setSettings((current) => ({ ...current, [key]: event.target.value }))}
                    className="w-[110px] flex-none px-2.5 py-1.5 font-u-num"
                  />
                  <span className="flex flex-[1_1_130px] flex-wrap items-center gap-1.5 text-xs text-u-text3">
                    {bounds && rangeLabel(bounds[key], unit)}
                    {typed === null && <PromptPill tone="plain">Default</PromptPill>}
                    {typeof typed === "number" && typed !== detail.defaults[key] && (
                      <PromptPill tone="warning">Differs from default</PromptPill>
                    )}
                  </span>
                </div>
                {error && (
                  <span role="alert" className={ERROR}>
                    {error}
                  </span>
                )}
              </div>
            );
          })}
          {detail.prompt.kind === "STRUCTURED" && typeof temperature === "number" && temperature > 0 && (
            <span className={cn(HINT, "text-u-accent")}>
              Above 0, answers vary from one call to the next. Run an eval with repeats before putting this live.
            </span>
          )}
        </div>
      </div>

      <div className="mt-5">
        <span className={LABEL}>What the model also receives</span>
        <pre
          aria-label="The message sent with these instructions"
          className="m-0 overflow-x-auto whitespace-pre-wrap rounded-lg border border-dashed border-u-border-strong px-3.5 py-3 font-u-num text-xs leading-[1.7] text-u-text3"
        >
          {detail.userMessageTemplate ??
            "The material the feature sends: built in code, after contact details are removed."}
        </pre>
        <span className={HINT}>
          The application builds this from a workspace's own material, after contact details are removed. It cannot be
          edited here, so an edit never changes which data reaches the model.
        </span>
      </div>

      <div className="mt-5 border-t border-u-border pt-4">
        <FormError message={formError} />
        <div className="flex flex-wrap items-end gap-3">
          <label className="min-w-0 flex-[1_1_320px]">
            <span className={LABEL}>Change note</span>
            <Input value={note} maxLength={NOTE_LIMIT} placeholder="What changed and why" onChange={(event) => setNote(event.target.value)} />
          </label>
          <Button
            type="button"
            loading={save.isPending}
            onClick={handleSave}
            className={cn(note.trim() === "" && "opacity-50")}
          >
            Save as new version
          </Button>
        </div>
      </div>
    </div>
  );
}

function textOf(value: number | null): string {
  return value === null ? "" : String(value);
}

function parsedSettings(
  texts: SettingTexts,
  bounds: AiModel | null,
): { values: GenerationSettings; errors: Record<string, string> } {
  const values: GenerationSettings = { temperature: null, thinkingBudget: null, maxOutputTokens: null };
  const errors: Record<string, string> = {};
  for (const { key, label, unit } of SETTINGS) {
    const typed = parseSetting(texts[key]);
    const range = bounds?.[key];
    if (typed === "invalid" || (typeof typed === "number" && range && (typed < range.min || typed > range.max))) {
      errors[`settings.${key}`] = range ? `${label} must be ${rangeLabel(range, unit)}` : `${label} must be a number`;
    } else {
      values[key] = typed;
    }
  }
  return { values, errors };
}
