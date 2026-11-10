import { request } from "../../../lib/apiClient";

/**
 * A consultant's email templates and the workspace's shared ones (`docs/email-templates.md`). A template's body is the
 * notes editor's Markdown, its variables written as a sequence writes them (`{{firstName}}`, `{{firstName|there}}`);
 * the server fills them for each executive when the email is written.
 */

export const EMAIL_TEMPLATES_KEY = ["email-templates"] as const;

export interface EmailTemplateOwner {
  userId: string;
  fullName: string | null;
  avatarUrl: string | null;
}

export interface EmailTemplate {
  id: string;
  name: string;
  subject: string | null;
  /** Markdown, variables kept. */
  body: string;
  /** The body's words alone, variables kept, for a sequence step. */
  plainBody: string;
  shared: boolean;
  mine: boolean;
  /** The owner's, or an admin's on a shared one. */
  canEdit: boolean;
  owner: EmailTemplateOwner;
  updatedAt: string;
  version: number;
}

export interface EmailTemplates {
  templates: EmailTemplate[];
  /** How many one person may keep. */
  limit: number;
  bookingLinkOffered: boolean;
}

export interface EmailTemplateContent {
  name: string;
  subject: string | null;
  body: string;
}

export function listEmailTemplates(signal?: AbortSignal): Promise<EmailTemplates> {
  return request<EmailTemplates>("/me/email-templates", { signal });
}

export function createEmailTemplate(content: EmailTemplateContent & { shared: boolean }): Promise<EmailTemplate> {
  return request<EmailTemplate>("/me/email-templates", { method: "POST", body: content });
}

export function updateEmailTemplate(
  id: string,
  content: EmailTemplateContent & { version: number },
): Promise<EmailTemplate> {
  return request<EmailTemplate>(`/me/email-templates/${id}`, { method: "PUT", body: content });
}

export function shareEmailTemplate(id: string, shared: boolean, version: number): Promise<EmailTemplate> {
  return request<EmailTemplate>(`/me/email-templates/${id}/sharing`, { method: "PUT", body: { shared, version } });
}

export function duplicateEmailTemplate(id: string): Promise<EmailTemplate> {
  return request<EmailTemplate>(`/me/email-templates/${id}/duplicate`, { method: "POST" });
}

export function deleteEmailTemplate(id: string, version: number): Promise<void> {
  return request<void>(`/me/email-templates/${id}?version=${version}`, { method: "DELETE" });
}
