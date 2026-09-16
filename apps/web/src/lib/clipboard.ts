/**
 * Copies a value to the clipboard and says whether it landed. False rather than a throw where there
 * is no clipboard to write to — a non-secure context, a browser that refused, jsdom — so a caller
 * can tell the user "couldn't copy" instead of nothing.
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
