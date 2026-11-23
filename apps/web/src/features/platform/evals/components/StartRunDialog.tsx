import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button, Field, FormError, LinesSkeleton, Select } from "../../../../components/ui";
import { Modal } from "../../../../components/ui/Modal";
import { ApiRequestError } from "../../../../lib/apiClient";
import { formatDate } from "../../../../lib/datetime/format";
import { messageFor } from "../../../../lib/errorCodes";
import * as promptApi from "../../prompts/api/promptApi";
import type { PromptVersionSummary } from "../../prompts/api/types";
import * as evalApi from "../api/evalApi";
import type { EvalDatasetOverview, EvalRunDetail, EvalRunEstimate, StartEvalRunRequest } from "../api/types";
import { countOf, repeatsReasonLine, tokensLabel } from "../lib/evalLabels";

const SAME_VERSION = "Choose another version to compare it against";

const startRunSchema = z
  .object({
    promptId: z.string().min(1, "Choose a prompt"),
    candidateVersion: z.string().min(1, "Choose the version to score"),
    baselineVersion: z.string().min(1, "Choose the version to compare it against"),
    datasetId: z.string().min(1, "Choose a dataset"),
    split: z.enum(["HOLDOUT", "DEV"]),
  })
  .refine((run) => run.candidateVersion === "" || run.candidateVersion !== run.baselineVersion, {
    path: ["baselineVersion"],
    message: SAME_VERSION,
  });
type StartRunValues = z.infer<typeof startRunSchema>;

const VERSION_FIELDS = ["candidateVersion", "baselineVersion"] as const;

/**
 * Start a run: one version of a prompt against another on one split of a dataset's cases, with what it would
 * take shown first. Only a prompt whose cases can be run is offered.
 */
export function StartRunDialog({
  datasets,
  onClose,
  onStarted,
}: {
  datasets: EvalDatasetOverview[];
  onClose: () => void;
  onStarted: (run: EvalRunDetail) => void;
}) {
  const runnable = useMemo(() => datasets.filter((dataset) => dataset.importable), [datasets]);
  const prompts = useMemo(
    () => [...new Map(runnable.map((dataset) => [dataset.promptId, dataset.promptName])).entries()],
    [runnable],
  );
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    watch,
    formState: { errors },
  } = useForm<StartRunValues>({
    resolver: zodResolver(startRunSchema),
    mode: "onChange",
    defaultValues: {
      promptId: runnable[0]?.promptId ?? "",
      candidateVersion: "",
      baselineVersion: "",
      datasetId: runnable[0]?.id ?? "",
      split: "HOLDOUT",
    },
  });
  const values = watch();
  const promptDatasets = runnable.filter((dataset) => dataset.promptId === values.promptId);
  const dataset = promptDatasets.find((candidate) => candidate.id === values.datasetId);

  const prompt = useQuery({
    queryKey: promptApi.PROMPT_KEY(values.promptId),
    queryFn: ({ signal }) => promptApi.getPrompt(values.promptId, signal),
    enabled: values.promptId !== "",
  });
  const versions = prompt.data?.versions ?? [];
  const candidates = versions.filter((version) => !version.live);

  // The versions are the prompt's, which arrives after the dialog opens and again when another prompt is chosen.
  useEffect(() => {
    if (!prompt.data || prompt.data.prompt.id !== values.promptId) return;
    const listed = prompt.data.versions;
    if (!listed.some((version) => String(version.version) === values.candidateVersion)) {
      setValue("candidateVersion", String(listed.find((version) => !version.live)?.version ?? ""));
    }
    if (!listed.some((version) => String(version.version) === values.baselineVersion)) {
      setValue("baselineVersion", String(prompt.data.prompt.liveVersion));
    }
  }, [prompt.data, values.promptId, values.candidateVersion, values.baselineVersion, setValue]);

  const request = requestOf(values);
  const estimate = useQuery({
    queryKey: evalApi.EVAL_ESTIMATE_KEY(request ?? { datasetId: "", split: "HOLDOUT", candidateVersion: 0 }),
    queryFn: ({ signal }) => evalApi.estimateRun(request as StartEvalRunRequest, signal),
    enabled: request !== null,
    staleTime: 0,
  });

  const start = useMutation({
    mutationFn: (run: StartEvalRunRequest) => evalApi.startRun(run),
    onSuccess: onStarted,
    onError: (error) => {
      const fields = error instanceof ApiRequestError ? error.fieldErrors : {};
      const named = VERSION_FIELDS.filter((field) => fields[field]);
      named.forEach((field) => setError(field, { type: "server", message: fields[field] }));
      setFormError(named.length > 0 ? null : messageFor(error));
    },
  });

  const submit = handleSubmit((submitted) => {
    const run = requestOf(submitted);
    if (!run) return;
    setFormError(null);
    start.mutate(run);
  });

  const cannotStart =
    request === null || !estimate.data || estimate.data.tooLarge || estimate.data.cases === 0 || start.isPending;

  return (
    <Modal
      open
      onClose={onClose}
      onSubmit={() => void submit()}
      title="Start a run"
      className="md:w-[520px]"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={start.isPending} disabled={cannotStart}>
            Start run
          </Button>
        </>
      }
    >
      <p className="mb-4 text-body text-u-text2">
        Both versions answer the same cases, through the same path the feature uses, each on its own model and
        settings.
      </p>
      <FormError message={formError ?? (prompt.isError ? messageFor(prompt.error) : null)} />

      {runnable.length === 0 ? (
        <p className="text-body text-u-text2">No prompt has cases that can be run yet.</p>
      ) : (
        <div className="grid grid-cols-1 gap-x-3.5 md:grid-cols-2">
          <div className="md:col-span-2">
            <Field label="Prompt" error={errors.promptId?.message}>
              <Select
                {...register("promptId", {
                  onChange: (event) => {
                    const first = runnable.find((candidate) => candidate.promptId === event.target.value);
                    setValue("datasetId", first?.id ?? "");
                    setValue("candidateVersion", "");
                    setValue("baselineVersion", "");
                  },
                })}
              >
                {prompts.map(([promptId, promptName]) => (
                  <option key={promptId} value={promptId}>
                    {promptName}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Field
            label="Candidate"
            error={errors.candidateVersion?.message}
            hint={prompt.data && candidates.length === 0 ? "Save a version of this prompt first" : undefined}
          >
            <Select aria-label="Candidate version" invalid={!!errors.candidateVersion} {...register("candidateVersion")}>
              {candidates.length === 0 && <option value="" />}
              {candidates.map((version) => (
                <option key={version.version} value={version.version}>
                  {versionOption(version)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Against" error={errors.baselineVersion?.message}>
            <Select aria-label="Baseline version" invalid={!!errors.baselineVersion} {...register("baselineVersion")}>
              {versions.length === 0 && <option value="" />}
              {[...versions.filter((version) => version.live), ...candidates].map((version) => (
                <option key={version.version} value={version.version}>
                  {versionOption(version)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Dataset" error={errors.datasetId?.message}>
            <Select {...register("datasetId")}>
              {promptDatasets.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Cases">
            <Select aria-label="Split" {...register("split")}>
              <option value="HOLDOUT">Holdout · {countOf(dataset?.holdout ?? 0, "case")}</option>
              <option value="DEV">Dev · {countOf(dataset?.dev ?? 0, "case")}</option>
            </Select>
          </Field>
        </div>
      )}

      {request !== null && (
        <div className="rounded-lg border border-u-border bg-u-sunken px-3.5 py-3">
          {estimate.isError ? (
            <FormError message={messageFor(estimate.error)} />
          ) : !estimate.data ? (
            <LinesSkeleton lines={2} />
          ) : (
            <EstimateBox estimate={estimate.data} />
          )}
        </div>
      )}
      <p className="mt-3 font-mono text-xs text-u-text3">
        Only a run on the holdout cases counts when a version is put live.
      </p>
    </Modal>
  );
}

function EstimateBox({ estimate }: { estimate: EvalRunEstimate }) {
  if (estimate.cases === 0) {
    return <p role="alert" className="text-body text-u-text2">That dataset has no cases in that split.</p>;
  }
  return (
    <>
      <div className="flex flex-wrap items-baseline justify-between gap-2.5">
        <span className="text-body font-semibold">
          About {countOf(estimate.calls, "call")} · {tokensLabel(estimate.tokens)}
        </span>
        <span className="font-mono text-xs text-u-text3">around {countOf(estimate.minutes, "minute")}</span>
      </div>
      <p className="mt-1.5 text-xs text-u-text2">
        {[
          `${countOf(estimate.cases, "case")} × 2 versions × ${countOf(estimate.repeats, "repeat")}.`,
          repeatsReasonLine(estimate.repeatsReason, estimate.repeats),
          `The run counts toward the AI budget and stops at ${countOf(estimate.callCap, "call")}.`,
        ]
          .filter(Boolean)
          .join(" ")}
      </p>
      {estimate.tooLarge && (
        <p role="alert" className="mt-1.5 text-xs font-medium text-u-offlimits">
          That is more calls than one run may make. Choose a split with fewer cases.
        </p>
      )}
    </>
  );
}

/** "v10 · 19 Nov 2026", or "v7 · live" for the version serving. */
function versionOption(version: PromptVersionSummary): string {
  return `v${version.version} · ${version.live ? "live" : formatDate(version.createdAt)}`;
}

/** The run the form describes, once it names two different versions and a dataset. */
function requestOf(values: StartRunValues): StartEvalRunRequest | null {
  const candidateVersion = Number(values.candidateVersion);
  const baselineVersion = Number(values.baselineVersion);
  if (!values.datasetId || !candidateVersion || !baselineVersion || candidateVersion === baselineVersion) return null;
  return { datasetId: values.datasetId, split: values.split, candidateVersion, baselineVersion };
}
