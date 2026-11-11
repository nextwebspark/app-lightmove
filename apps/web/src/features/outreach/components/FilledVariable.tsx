import type { RenderedPart } from "../lib/sequenceTokens";

/**
 * One rendered part as a reader sees it: a variable with nothing on file red where it falls, a fallback standing in
 * underlined, anything else its words. The opener, the link and the signature are the caller's to draw.
 */
export function FilledVariable({ part, forWhom }: { part: RenderedPart; forWhom?: string | null }) {
  if (part.isBlank) {
    return (
      <span
        title={`Nothing on file for ${forWhom ?? "this executive"}: it goes out blank`}
        className="font-mono text-u-offlimits"
      >
        {`{{${part.token}}}`}
      </span>
    );
  }
  if (part.isFallback) {
    return (
      <span
        title={`Nothing on file for ${forWhom ?? "this executive"}: the fallback stands in for {{${part.token}}}`}
        className="underline decoration-dotted underline-offset-2"
      >
        {part.text}
      </span>
    );
  }
  return <>{part.text}</>;
}
