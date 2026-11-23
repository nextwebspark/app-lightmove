import { request } from "../../../../lib/apiClient";
import type {
  EvalCalibrationPairs,
  EvalCaseImportResult,
  EvalDatasetDetail,
  EvalDatasetOverview,
  EvalRunCases,
  EvalRunDetail,
  EvalRunEstimate,
  EvalRunOverview,
  JudgePick,
  StartEvalRunRequest,
} from "./types";

/** Platform → Evals: datasets of synthetic cases, and the runs that score one prompt version against another. */
export const EVAL_DATASETS_KEY = ["platform-evals", "datasets"] as const;
export const EVAL_DATASET_KEY = (datasetId: string) => ["platform-evals", "datasets", datasetId] as const;
export const EVAL_PAIRS_KEY = (datasetId: string) => ["platform-evals", "datasets", datasetId, "pairs"] as const;
export const EVAL_RUNS_KEY = ["platform-evals", "runs"] as const;
export const EVAL_RUN_KEY = (runId: string) => ["platform-evals", "runs", runId] as const;
export const EVAL_RUN_CASES_KEY = (runId: string) => ["platform-evals", "runs", runId, "cases"] as const;
export const EVAL_ESTIMATE_KEY = (run: StartEvalRunRequest) => ["platform-evals", "estimate", run] as const;

const ROOT = "/platform/evals";
const datasetUrl = (datasetId: string) => `${ROOT}/datasets/${encodeURIComponent(datasetId)}`;
const runUrl = (runId: string) => `${ROOT}/runs/${encodeURIComponent(runId)}`;

export function listDatasets(signal?: AbortSignal): Promise<EvalDatasetOverview[]> {
  return request<EvalDatasetOverview[]>(`${ROOT}/datasets`, { signal });
}

export function getDataset(datasetId: string, signal?: AbortSignal): Promise<EvalDatasetDetail> {
  return request<EvalDatasetDetail>(datasetUrl(datasetId), { signal });
}

/** Reads the file and answers with what an import would do. Writes nothing. */
export function previewCaseImport(datasetId: string, file: File): Promise<EvalCaseImportResult> {
  return request<EvalCaseImportResult>(`${datasetUrl(datasetId)}/cases/import/preview`, {
    method: "POST",
    body: formOf(file),
  });
}

/** Sends the same file again and adds its new cases: all of them, or none if any row is at fault. */
export function commitCaseImport(datasetId: string, file: File): Promise<EvalCaseImportResult> {
  return request<EvalCaseImportResult>(`${datasetUrl(datasetId)}/cases/import/commit`, {
    method: "POST",
    body: formOf(file),
  });
}

export function retireCase(caseId: string): Promise<EvalDatasetDetail> {
  return request<EvalDatasetDetail>(`${ROOT}/cases/${encodeURIComponent(caseId)}/retire`, { method: "POST" });
}

/** The pairs a person labels for the dataset's rubric. Empty until labelling has been started. */
export function getRubricPairs(datasetId: string, signal?: AbortSignal): Promise<EvalCalibrationPairs> {
  return request<EvalCalibrationPairs>(`${datasetUrl(datasetId)}/rubric/pairs`, { signal });
}

/** Draws the pairs the first time; after that it answers the same ones. */
export function startLabelling(datasetId: string): Promise<EvalCalibrationPairs> {
  return request<EvalCalibrationPairs>(`${datasetUrl(datasetId)}/rubric/pairs`, { method: "POST" });
}

/** One pick for every criterion of the pair's rubric, by criterion key. */
export function labelPair(pairId: string, picks: Record<string, JudgePick>): Promise<void> {
  return request<void>(`${ROOT}/rubric-pairs/${encodeURIComponent(pairId)}/labels`, { method: "PUT", body: { picks } });
}

/** Starts a check of the judge against the labels, and answers the dataset with the check going. */
export function checkJudge(datasetId: string): Promise<EvalDatasetDetail> {
  return request<EvalDatasetDetail>(`${datasetUrl(datasetId)}/rubric/check`, { method: "POST" });
}

export function listRuns(signal?: AbortSignal): Promise<EvalRunOverview[]> {
  return request<EvalRunOverview[]>(`${ROOT}/runs`, { signal });
}

/** What a start with the same body would take. Asks the model nothing. */
export function estimateRun(run: StartEvalRunRequest, signal?: AbortSignal): Promise<EvalRunEstimate> {
  return request<EvalRunEstimate>(`${ROOT}/runs/estimate`, { method: "POST", body: run, signal });
}

export function startRun(run: StartEvalRunRequest): Promise<EvalRunDetail> {
  return request<EvalRunDetail>(`${ROOT}/runs`, { method: "POST", body: run });
}

export function getRun(runId: string, signal?: AbortSignal): Promise<EvalRunDetail> {
  return request<EvalRunDetail>(runUrl(runId), { signal });
}

export function getRunCases(runId: string, signal?: AbortSignal): Promise<EvalRunCases> {
  return request<EvalRunCases>(`${runUrl(runId)}/cases`, { signal });
}

export function cancelRun(runId: string): Promise<EvalRunDetail> {
  return request<EvalRunDetail>(`${runUrl(runId)}/cancel`, { method: "POST" });
}

function formOf(file: File): FormData {
  const form = new FormData();
  form.append("file", file);
  return form;
}
