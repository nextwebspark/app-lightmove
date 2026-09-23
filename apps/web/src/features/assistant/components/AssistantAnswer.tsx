import ReactMarkdown, { type Components } from "react-markdown";

const ALLOWED = ["p", "ul", "ol", "li", "strong", "em", "br"];

const ELEMENTS: Components = {
  p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
  ul: ({ children }) => <ul className="mb-2 list-disc space-y-1 ps-4 last:mb-0">{children}</ul>,
  ol: ({ children }) => <ol className="mb-2 list-decimal space-y-1 ps-4 last:mb-0">{children}</ol>,
  strong: ({ children }) => <strong className="font-semibold text-text">{children}</strong>,
};

/**
 * The model answers in Markdown. Only paragraphs, lists and emphasis are drawn; anything else keeps
 * its text and loses its formatting, and raw HTML in the answer is never rendered.
 */
export function AssistantAnswer({ text }: { text: string }) {
  return (
    <div className="font-sans text-[13px] leading-[1.6] text-text">
      <ReactMarkdown allowedElements={ALLOWED} unwrapDisallowed components={ELEMENTS}>
        {text}
      </ReactMarkdown>
    </div>
  );
}
