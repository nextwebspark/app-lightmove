/**
 * Where a position's pipeline lives: In universe, read as its Pipeline view. `params` ride along, so a link can open
 * an executive's drawer there (`candidate`, then `note`, `section` or `tab`).
 */
export function positionPipelinePath(
  projectId: string,
  params: Record<string, string> | URLSearchParams = {},
): string {
  const query = new URLSearchParams({ view: "pipeline" });
  new URLSearchParams(params).forEach((value, key) => {
    // The old page's own List | Board and stage pills have no place on the board view.
    if (key !== "view" && key !== "stage") query.append(key, value);
  });
  return `/projects/${projectId}/companies/universe?${query}`;
}
