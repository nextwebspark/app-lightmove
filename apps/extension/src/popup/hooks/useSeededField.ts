import { useCallback, useEffect, useRef, useState } from "react";

/**
 * A form field seeded from the page read, then owned by whoever types in it.
 *
 * The rule this replaced could not tell a *typed* value from a *seeded* one — it kept whatever was
 * already there — so the first wrong seed became permanent and the correct read that followed a
 * moment later was refused. Tracking the edit explicitly is what lets a late-but-right answer land.
 */
export function useSeededField(seed: string | null, pageKey: string | null) {
  const [value, setValue] = useState("");
  const [hasBeenEdited, setHasBeenEdited] = useState(false);
  const seededFor = useRef<string | null>(null);

  useEffect(() => {
    // Another page: everything goes, edits included. The previous person's name must never linger
    // over someone else's profile, and `pageKey` — not the raw address — is what "another page"
    // means, so a tracking parameter or a `/details/experience` detour is the same page.
    if (seededFor.current !== pageKey) {
      seededFor.current = pageKey;
      setHasBeenEdited(false);
      setValue(seed ?? "");
      return;
    }
    // The same page answering again. A read that found something replaces a field nobody has touched;
    // it never overwrites an edit, and an empty answer never wipes a name an earlier read found.
    if (!hasBeenEdited && seed) {
      setValue(seed);
    }
    // `hasBeenEdited` is deliberately not a dependency: an edit must not re-run the seeding it just
    // locked out.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageKey, seed]);

  const edit = useCallback((next: string) => {
    // Only ever from a keystroke, never from the seeding above — including clearing a field by hand,
    // which is an edit and must not be refilled by the next read.
    setHasBeenEdited(true);
    setValue(next);
  }, []);

  // `hasBeenEdited` is what the screens lock on: a field still holding exactly what the page said is
  // read data and is shown as such, while one the consultant had to type stays theirs to type in.
  return { value, edit, hasBeenEdited };
}
