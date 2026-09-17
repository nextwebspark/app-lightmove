/**
 * Hands a fetched file to the browser to save.
 *
 * A fetch and an object URL rather than an `<a href>`: the bytes need the bearer token, which only
 * `apiClient` holds and a browser navigation never sends.
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
    // Revoked once the click is handed off; leaving it pins the blob in memory for the document's life.
    URL.revokeObjectURL(url);
  }
}
