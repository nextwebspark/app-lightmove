import {
  askServiceWorker,
  type ExtensionReplies,
  type ExtensionRequest,
} from "../../background/extensionMessages";
import { CaptureRefusal } from "./captureRefusal";

/** Asks the worker and throws its refusal as data a screen can switch on — the shape every query and mutation wants. */
export async function askOrThrow<K extends ExtensionRequest["kind"]>(
  request: Extract<ExtensionRequest, { kind: K }>,
): Promise<ExtensionReplies[K]> {
  const result = await askServiceWorker(request);
  if (!result.ok) {
    throw new CaptureRefusal(result.code, result.message);
  }
  return result.value;
}
