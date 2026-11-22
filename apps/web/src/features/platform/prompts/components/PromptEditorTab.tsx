import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useForm, type FieldPath } from "react-hook-form";
import { z } from "zod";
import { Button, FormError, Input, Select, TextArea, useToast } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import { cn } from "../../../../lib/cn";
import { formatDate } from "../../../../lib/datetime/format";
import { messageFor } from "../../../../lib/errorCodes";
import * as promptApi from "../api/promptApi";
import type { AiModel, GenerationSettings, PromptDetail, PromptVersionSummary } from "../api/types";
import {
  isRetiringSoon,
  modelOptionLabel,
  parseSetting,
  PROVIDER_LABEL,
  rangeLabel,
  SETTINGS,
  unusableReason,
} from "../lib/promptLabels";
import { PromptPill } from "./PromptPill";

const NOTE_LIMIT = 300;
const DEFAULT_MODEL = "";
const LABEL = "mb-1.5 block type-micro-label font-mono text-u-text3";
const HINT = "mt-1.5 block font-mono text-xs text-u-text3";
const ERROR = "mt-1.5 block font-mono text-xs text-u-offlimits";

/** A setting is held to the ranges of the model that would answer: the chosen one, else the configured one. */
function editorSchema(boundsFor: (modelId: string) => AiModel | null) {
  return z
    .object({
      body: z.string(),
      modelId: z.string(),
      settings: z.object({ temperature: z.string(), thinkingBudget: z.string(), maxOutputTokens: z.string() }),
      changeNote: z.string().trim().min(1, "Add a change note").max(NOTE_LIMIT, `At most ${NOTE_LIMIT} characters`),
    })
    .superRefine((values, context) => {
      const bounds = boundsFor(values.modelId);
      for (const { key, label, unit, integer } of SETTINGS) {
        const typed = parseSetting(values.settings[key], integer);
        const range = bounds?.[key];
        if (typed === "invalid") {
          context.addIssue({
            code: "custom",
            path: ["settings", key],
            message: integer ? `${label} must be a whole number` : `${label} must be a number, with a point for decimals`,
          });
        } else if (typed !== null && range && (typed < range.min || typed > range.max)) {
          context.addIssue({ code: "custom", path: ["settings", key], message: `${label} must be ${rangeLabel(range, unit)}` });
        }
      }
    });
}

type EditorValues = z.infer<ReturnType<typeof editorSchema>>;

const SERVER_FIELDS: readonly FieldPath<EditorValues>[] = [
  "body",
  "modelId",
  "changeNote",
  ...SETTINGS.map(({ key }) => `settings.${key}` as const),
];

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
  const [formError, setFormError] = useState<string | null>(null);
  const modelOf = (modelId: string) => models.find((model) => model.modelId === modelId) ?? null;
  const configured = models.find((model) => model.modelId === detail.defaultModelId) ?? null;
  const boundsFor = (modelId: string) => modelOf(modelId) ?? configured;

  const {
    register,
    handleSubmit,
    watch,
    trigger,
    setError,
    formState: { errors, isSubmitted },
  } = useForm<EditorValues>({
    resolver: zodResolver(editorSchema(boundsFor)),
    defaultValues: {
      body,
      modelId: version.modelId ?? DEFAULT_MODEL,
      settings: {
        temperature: textOf(version.settings.temperature),
        thinkingBudget: textOf(version.settings.thinkingBudget),
        maxOutputTokens: textOf(version.settings.maxOutputTokens),
      },
      changeNote: "",
    },
  });

  const values = watch();
  const chosen = modelOf(values.modelId);
  const bounds = boundsFor(values.modelId);
  const temperature = parseSetting(values.settings.temperature, false);

  const save = useMutation({
    mutationFn: (draft: EditorValues) => {
      const model = modelOf(draft.modelId);
      return promptApi.savePromptVersion(detail.prompt.id, {
        body: draft.body,
        provider: model?.provider ?? null,
        modelId: model?.modelId ?? null,
        settings: settingsOf(draft.settings),
        changeNote: draft.changeNote,
      });
    },
    onSuccess: (updated) => {
      toast.success(`Saved as version ${updated.versions[0].version}. Nothing serves it until it is put live`);
      onSaved(updated);
    },
    onError: (error) => {
      const fields = error instanceof ApiRequestError ? error.fieldErrors : {};
      const unattributed: string[] = [];
      for (const [field, message] of Object.entries(fields)) {
        if (SERVER_FIELDS.includes(field as FieldPath<EditorValues>)) {
          setError(field as FieldPath<EditorValues>, { type: "server", message });
        } else {
          unattributed.push(message);
        }
      }
      const attributedOnly = Object.keys(fields).length > 0 && unattributed.length === 0;
      setFormError(attributedOnly ? null : (unattributed[0] ?? messageFor(error)));
    },
  });

  const submit = handleSubmit((draft) => {
    setFormError(null);
    save.mutate(draft);
  });

  return (
    <div>
      <label className="block">
        <span className={LABEL}>Instructions · Version {version.version}</span>
        <TextArea
          aria-label="Prompt text"
          spellCheck={false}
          invalid={!!errors.body}
          {...register("body")}
          className="min-h-[300px] font-u-num text-xs leading-[1.7] [tab-size:2]"
        />
      </label>
      {errors.body && (
        <span role="alert" className={ERROR}>
          {errors.body.message}
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
            <Select
              aria-label="Model"
              invalid={!!errors.modelId}
              {...register("modelId", {
                // The settings are held to the answering model's ranges, so another model re-checks them.
                onChange: () => {
                  if (isSubmitted) void trigger("settings");
                },
              })}
            >
              <option value={DEFAULT_MODEL}>Default model{configured ? ` (${configured.displayName})` : ""}</option>
              {models.map((model) => (
                <option key={model.modelId} value={model.modelId} disabled={unusableReason(model, detail.needs) !== null}>
                  {PROVIDER_LABEL[model.provider]} · {modelOptionLabel(model, detail.needs)}
                </option>
              ))}
            </Select>
          </label>
          {errors.modelId ? (
            <span role="alert" className={ERROR}>
              {errors.modelId.message}
            </span>
          ) : (
            <span className={HINT}>
              {chosen
                ? `${chosen.status} · retires ${formatDate(chosen.retiresOn)} · same region and data terms as today`
                : values.modelId !== DEFAULT_MODEL
                  ? `${values.modelId} is no longer offered. The default model answers this version.`
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
          {SETTINGS.map(({ key, label, unit, integer }) => {
            const typed = parseSetting(values.settings[key], integer);
            const error = errors.settings?.[key]?.message;
            return (
              <div key={key} className="py-[5px]">
                <div className="flex flex-wrap items-center gap-2.5">
                  <span className="flex-[1_1_130px] text-body text-u-text2">{label}</span>
                  <Input
                    aria-label={label}
                    inputMode={integer ? "numeric" : "decimal"}
                    invalid={!!error}
                    placeholder={textOf(detail.defaults[key])}
                    {...register(`settings.${key}`)}
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
            <Input
              maxLength={NOTE_LIMIT}
              placeholder="What changed and why"
              invalid={!!errors.changeNote}
              {...register("changeNote")}
            />
          </label>
          <Button
            type="button"
            loading={save.isPending}
            disabled={values.changeNote.trim() === ""}
            onClick={() => void submit()}
          >
            Save as new version
          </Button>
        </div>
        {errors.changeNote && (
          <span role="alert" className={ERROR}>
            {errors.changeNote.message}
          </span>
        )}
      </div>
    </div>
  );
}

function textOf(value: number | null): string {
  return value === null ? "" : String(value);
}

/** Only ever given a draft the schema passed, so each setting reads as a number or as not set. */
function settingsOf(texts: EditorValues["settings"]): GenerationSettings {
  const settings: GenerationSettings = { temperature: null, thinkingBudget: null, maxOutputTokens: null };
  for (const { key, integer } of SETTINGS) {
    const typed = parseSetting(texts[key], integer);
    settings[key] = typed === "invalid" ? null : typed;
  }
  return settings;
}
