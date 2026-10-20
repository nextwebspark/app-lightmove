/**
 * A note's @mentions. The body the server keeps carries them as tokens — `@[Full Name](user:<id>)` or
 * `@[team](team)` — and the server, not the name, decides who is told.
 */
export type Mention = { kind: "user"; userId: string; name: string } | { kind: "team" };

export type NoteSegment = { kind: "text"; text: string } | { kind: "mention"; mention: Mention };

const TOKEN = /@\[([^\]\n]+)\]\((?:user:([A-Za-z0-9-]+)|(team))\)/g;

export function parseNoteBody(body: string): NoteSegment[] {
  const segments: NoteSegment[] = [];
  let from = 0;
  for (const match of body.matchAll(TOKEN)) {
    const at = match.index ?? 0;
    if (at > from) segments.push({ kind: "text", text: body.slice(from, at) });
    const mention: Mention = match[3]
      ? { kind: "team" }
      : { kind: "user", userId: match[2], name: match[1].trim() };
    segments.push({ kind: "mention", mention });
    from = at + match[0].length;
  }
  if (from < body.length) segments.push({ kind: "text", text: body.slice(from) });
  return segments;
}

export function mentionKey(mention: Mention): string {
  return mention.kind === "team" ? "team" : `user:${mention.userId}`;
}

export function mentionToken(mention: Mention): string {
  return mention.kind === "team" ? "@[team](team)" : `@[${cleanName(mention.name)}](user:${mention.userId})`;
}

/** What a note may hold as stored, its formatting and mention tokens included (`PersonNote.MAX_BODY`). */
export const NOTE_MAX_STORED = 10000;

export const NOTE_TOO_LONG = "Too long to save. Shorten it a little.";

/** A body whose mentions are words again — for a note that is about to mention nobody. */
export function withoutMentionTokens(body: string): string {
  return body.replace(TOKEN, (_token, name: string) => `@${cleanName(name)}`);
}

function cleanName(name: string): string {
  return name.replace(/[[\]\n\r]/g, " ").replace(/\s+/g, " ").trim();
}
