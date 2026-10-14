/**
 * A note's @mentions. The body the server keeps carries them as tokens — `@[Full Name](user:<id>)` or
 * `@[team](team)` — and the server, not the name, decides who is told. The composer shows `@Full Name` while
 * someone types, and the tokens are written only when the note is saved.
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

/** Everyone a body mentions, once each, in the order they first appear. */
export function mentionsIn(body: string): Mention[] {
  const found: Mention[] = [];
  for (const segment of parseNoteBody(body)) {
    if (segment.kind !== "mention") continue;
    if (!found.some((known) => mentionKey(known) === mentionKey(segment.mention))) found.push(segment.mention);
  }
  return found;
}

export function mentionKey(mention: Mention): string {
  return mention.kind === "team" ? "team" : `user:${mention.userId}`;
}

/** What the composer shows for a mention: `@team`, `@Sara Al-Mansour`. */
export function mentionLabel(mention: Mention): string {
  return mention.kind === "team" ? "@team" : `@${cleanName(mention.name)}`;
}

export function mentionToken(mention: Mention): string {
  return mention.kind === "team" ? "@[team](team)" : `@[${cleanName(mention.name)}](user:${mention.userId})`;
}

/** The mentions whose `@Name` the text still holds — one deleted from the words is no longer a mention. */
export function mentionsPresent(text: string, mentions: readonly Mention[]): Mention[] {
  return mentions.filter((mention) => text.includes(mentionLabel(mention)));
}

/** What a note may hold as stored, each mention spelled out as its token (`PersonNote.MAX_BODY`). */
export const NOTE_MAX_STORED = 4000;

export const NOTE_TOO_LONG = "Too long once its mentions are saved. Shorten it a little.";

/** The composer's text as the server keeps it: every picked `@Name` written as its token. */
export function serializeMentions(text: string, mentions: readonly Mention[]): string {
  const longestFirst = [...mentions].sort((a, b) => mentionLabel(b).length - mentionLabel(a).length);
  return longestFirst.reduce(
    (body, mention) => body.split(mentionLabel(mention)).join(mentionToken(mention)),
    text,
  );
}

/** A stored body as the composer shows it, for editing: every token back to its `@Name`. */
export function displayTextOf(body: string): string {
  return parseNoteBody(body)
    .map((segment) => (segment.kind === "text" ? segment.text : mentionLabel(segment.mention)))
    .join("");
}

function cleanName(name: string): string {
  return name.replace(/[[\]\n\r]/g, " ").replace(/\s+/g, " ").trim();
}
