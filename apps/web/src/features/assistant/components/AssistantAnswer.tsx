import ReactMarkdown, { type Components } from "react-markdown";

const ALLOWED = ["p", "ul", "ol", "li", "strong", "em", "br"];

const ELEMENTS: Components = {
  p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
  ul: ({ children }) => <ul className="mb-2 list-disc space-y-1 ps-4 last:mb-0">{children}</ul>,
  ol: ({ children }) => <ol className="mb-2 list-decimal space-y-1 ps-4 last:mb-0">{children}</ol>,
  strong: ({ children }) => <strong className="font-semibold text-u-text">{children}</strong>,
};

/**
 * The model answers in Markdown. Only paragraphs, lists and emphasis are drawn; anything else keeps
 * its text and loses its formatting, and raw HTML in the answer is never rendered.
 *
 * <p>While the answer is `streaming`, a bold the model has opened but not yet closed is closed here,
 * so a half-written name reads bold at once rather than as raw asterisks that turn bold later.
 */
export function AssistantAnswer({ text, streaming = false }: { text: string; streaming?: boolean }) {
  return (
    <div className="font-sans text-[13px] leading-[1.6] text-u-text">
      <ReactMarkdown allowedElements={ALLOWED} unwrapDisallowed components={ELEMENTS}>
        {streaming ? withOpenBoldClosed(text) : text}
      </ReactMarkdown>
    </div>
  );
}

function withOpenBoldClosed(text: string): string {
  const markers = text.match(/\*\*/g)?.length ?? 0;
  if (markers % 2 === 0) return text;
  return text.endsWith("**") ? text.slice(0, -2) : `${text}**`;
}
