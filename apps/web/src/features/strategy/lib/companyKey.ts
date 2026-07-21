/** A company's rebuild-stable identity as one comparable string — the universe key `(source, sourceId)`. */
export function companyKeyOf(company: { source: string; sourceId: string }): string {
  return `${company.source}\u0000${company.sourceId}`;
}
