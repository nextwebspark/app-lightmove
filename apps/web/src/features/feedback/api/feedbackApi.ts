import { request } from "../../../lib/apiClient";
import type { FeedbackReport, FeedbackResponse } from "./types";

/**
 * Files a report.
 *
 * Multipart, with the typed half as a JSON part beside the images — `@RequestPart` on the server, so
 * the blob must declare `application/json` or Spring reads it as a plain string and the binding fails
 * with a 415 nobody can debug from the browser.
 *
 * Sent anonymously when nobody is signed in, which is the endpoint's whole point: apiClient attaches
 * the bearer token when there is one and simply does not when there is not.
 */
export async function submitFeedback(
  report: FeedbackReport,
  screenshot: Blob | null,
  attachments: File[],
): Promise<FeedbackResponse> {
  const form = new FormData();
  form.append("report", new Blob([JSON.stringify(report)], { type: "application/json" }));

  if (screenshot) {
    // Named from the blob's own type: a large capture is re-encoded as JPEG, and a .png name on JPEG
    // bytes is the sort of small lie that costs someone an afternoon.
    const extension = screenshot.type === "image/jpeg" ? "jpg" : "png";
    form.append("screenshot", screenshot, `screen-capture.${extension}`);
  }
  for (const file of attachments) {
    form.append("attachments", file, file.name);
  }

  return request<FeedbackResponse>("/feedback", { method: "POST", body: form });
}
