import type { ReactNode } from "react";

/** The record full page's frame: the record beside its Details rail. */
export function RecordPageLayout({
  header,
  body,
  footer,
  rail,
}: {
  header: ReactNode;
  body: ReactNode;
  footer: ReactNode;
  rail: ReactNode;
}) {
  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col">
      <div className="flex min-h-0 flex-1">
        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          {header}
          {body}
          {footer}
        </div>
        {rail}
      </div>
    </div>
  );
}
