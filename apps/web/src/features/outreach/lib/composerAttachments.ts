import { formatBytes } from "../../candidates/lib/documents";
import type { AttachmentLimits } from "../api/conversationApi";

/** A file the composer will send: uploaded from this computer, or a version of the person's own document. */
export type ComposerAttachment =
  | { kind: "upload"; key: string; file: File; fileName: string; sizeBytes: number }
  | { kind: "document"; key: string; versionId: string; fileName: string; sizeBytes: number };

export function uploadOf(file: File): ComposerAttachment {
  return { kind: "upload", key: `upload:${file.name}:${file.size}:${file.lastModified}`, file, fileName: file.name, sizeBytes: file.size };
}

export function documentOf(versionId: string, fileName: string, sizeBytes: number): ComposerAttachment {
  return { kind: "document", key: `document:${versionId}`, versionId, fileName, sizeBytes };
}

/** Added once: the same upload or document picked twice is still one file. */
export function withAttachment(current: readonly ComposerAttachment[], added: ComposerAttachment): ComposerAttachment[] {
  return current.some((attachment) => attachment.key === added.key) ? [...current] : [...current, added];
}

/** Why the server would refuse these files, in its own order: how many, each one's size, then all of them together. */
export function attachmentProblemOf(attachments: readonly ComposerAttachment[], limits: AttachmentLimits): string | null {
  if (attachments.length > limits.maxFiles) return `One email can carry up to ${limits.maxFiles} files.`;
  const oversized = attachments.find((attachment) => attachment.sizeBytes > limits.maxFileBytes);
  if (oversized) return `${oversized.fileName} is larger than ${formatBytes(limits.maxFileBytes)}, the most one file can be.`;
  const total = attachments.reduce((sum, attachment) => sum + attachment.sizeBytes, 0);
  if (total > limits.maxTotalBytes) return `These files come to ${formatBytes(total)}. Keep them to ${formatBytes(limits.maxTotalBytes)} together.`;
  return null;
}

export function totalBytesOf(attachments: readonly ComposerAttachment[]): number {
  return attachments.reduce((sum, attachment) => sum + attachment.sizeBytes, 0);
}
