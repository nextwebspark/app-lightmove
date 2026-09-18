/**
 * Hands a fetched Blob to the browser to save.
 *
 * A download here can never be an `<a href>`: the access token lives in a module variable inside
 * `apiClient` and rides on the `Authorization` header, which a browser navigation does not send, and
 * the refresh cookie is path-scoped to the auth routes — so a plain link to any of these endpoints
 * 401s for every user, every time. Every caller fetches the bytes through `requestBlob` and saves
 * them here.
 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  try {
    const link = window.document.createElement("a");
    link.href = url;
    link.download = fileName;
    // Appended before the click: Firefox starts no download from an anchor outside the document.
    window.document.body.append(link);
    link.click();
    link.remove();
  } finally {
    // Revoked once the click has been handed off; leaving it would pin the blob in memory for the
    // life of the document.
    URL.revokeObjectURL(url);
  }
}

/**
 * A file name a browser and a file system will both accept, built from parts a mandate supplies —
 * its own name, a stage, a date. Anything that is not a letter, digit or dash becomes one dash.
 */
export function fileNameOf(parts: readonly string[], extension: string): string {
  const slug = parts
    .map((part) => part.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, ""))
    .filter((part) => part.length > 0)
    .join("-");
  return `${slug || "uncava"}.${extension}`;
}
