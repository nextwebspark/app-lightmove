import ReactMarkdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";

const ELEMENTS: Components = {
  h1: ({ children }) => (
    <h1 className="mb-4 text-[26px] font-semibold leading-[1.25] tracking-[-0.01em] text-u-text">{children}</h1>
  ),
  h2: ({ children }) => (
    <h2 className="mb-3 mt-10 border-t border-u-border pt-6 text-[19px] font-semibold text-u-text">{children}</h2>
  ),
  h3: ({ children }) => <h3 className="mb-2 mt-7 text-[15px] font-semibold text-u-text">{children}</h3>,
  p: ({ children }) => <p className="mb-3 text-u-text2">{children}</p>,
  ul: ({ children }) => <ul className="mb-3 list-disc space-y-1.5 ps-5 text-u-text2">{children}</ul>,
  ol: ({ children }) => <ol className="mb-3 list-decimal space-y-1.5 ps-5 text-u-text2">{children}</ol>,
  strong: ({ children }) => <strong className="font-semibold text-u-text">{children}</strong>,
  a: ({ href, children }) =>
    href && opensElsewhere(href) ? (
      <a href={href} target="_blank" rel="noopener noreferrer" className="font-medium text-u-accent hover:underline">
        {children}
      </a>
    ) : (
      <a href={href} className="font-medium text-u-accent hover:underline">
        {children}
      </a>
    ),
  pre: ({ children }) => (
    <pre className="mb-4 overflow-x-auto rounded-lg border border-u-border bg-u-raised px-4 py-3 font-mono text-[12.5px] leading-[1.6] text-u-text [&_code]:bg-transparent [&_code]:p-0">
      {children}
    </pre>
  ),
  code: ({ children }) => (
    <code className="rounded bg-u-raised px-1 py-px font-mono text-[12.5px] text-u-text">{children}</code>
  ),
  table: ({ children }) => (
    <div className="mb-4 overflow-x-auto rounded-lg border border-u-border">
      <table className="w-full border-collapse text-left text-[13px]">{children}</table>
    </div>
  ),
  th: ({ children }) => (
    <th className="border-b border-u-border bg-u-raised px-3 py-2 align-bottom font-semibold text-u-text">
      {children}
    </th>
  ),
  td: ({ children }) => (
    <td className="border-b border-u-border px-3 py-2 align-top text-u-text2 [tr:last-child_&]:border-b-0">
      {children}
    </td>
  ),
};

function opensElsewhere(href: string): boolean {
  try {
    const target = new URL(href, window.location.href);
    return /^https?:$/.test(target.protocol) && target.origin !== window.location.origin;
  } catch {
    return false;
  }
}

/** A guide's Markdown, tables included. Raw HTML in it is never rendered. */
export function GuideMarkdown({ markdown }: { markdown: string }) {
  return (
    <div className="text-[14px] leading-[1.65]">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={ELEMENTS}>
        {markdown}
      </ReactMarkdown>
    </div>
  );
}
