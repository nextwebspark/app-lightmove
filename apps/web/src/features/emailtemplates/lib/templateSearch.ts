import type { EmailTemplate } from "../api/emailTemplatesApi";

/** One rule for every template search: its name, subject, words and owner. */
export function matchesTemplateSearch(template: EmailTemplate, query: string): boolean {
  const needle = query.trim().toLowerCase();
  return (
    !needle ||
    [template.name, template.subject ?? "", template.plainBody, template.owner.fullName ?? ""]
      .join(" ")
      .toLowerCase()
      .includes(needle)
  );
}

/** The caller's own first, each group in the order the server sent it. */
export function mineFirst(templates: readonly EmailTemplate[]): EmailTemplate[] {
  return [...templates].sort((a, b) => Number(b.mine) - Number(a.mine));
}
