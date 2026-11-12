import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { PipelineCatalog } from "./types";

/** The workspace's own stages and statuses — the ones a person is filed and moved at. */
export function readPipeline(api: LightMoveApiClient): Promise<PipelineCatalog> {
  return api.request<PipelineCatalog>("/workspace/pipeline");
}
