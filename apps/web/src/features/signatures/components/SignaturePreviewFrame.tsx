import { useCallback, useState } from "react";
import { cn } from "../../../lib/cn";

/**
 * A signature's HTML as a mail client shows it, in a sandboxed frame: no script ever runs there, and nothing in it
 * reaches the app. Same-origin only so the frame can be measured to its content's height.
 */
export function SignaturePreviewFrame({
  html,
  dark = false,
  title,
  className,
}: {
  html: string;
  /** A mail client's dark mode: the text the signature leaves uncoloured turns light. */
  dark?: boolean;
  title: string;
  className?: string;
}) {
  const [height, setHeight] = useState(80);
  const document = `<!doctype html><html><head><meta charset="utf-8"><base target="_blank"></head><body style="margin:0;padding:16px 18px;background:${
    dark ? "#1f1f1f" : "#ffffff"
  };color:${dark ? "#e3e3e3" : "#1f2328"}">${html}</body></html>`;

  const measure = useCallback((frame: HTMLIFrameElement) => {
    const body = frame.contentDocument?.body;
    if (body) setHeight(Math.max(48, body.scrollHeight));
  }, []);

  return (
    <iframe
      title={title}
      sandbox="allow-same-origin"
      srcDoc={document}
      onLoad={(event) => measure(event.currentTarget)}
      style={{ height }}
      className={cn("block w-full rounded-lg border border-u-border", className)}
    />
  );
}
