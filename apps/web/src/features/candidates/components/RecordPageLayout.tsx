import type { ReactNode } from "react";

/** The record full page's frame: the record bar across, then the record beside its Details rail. */
export function RecordPageLayout({
  bar,
  header,
  body,
  footer,
  rail,
}: {
  bar: ReactNode;
  header: ReactNode;
  body: ReactNode;
  footer: ReactNode;
  rail: ReactNode;
}) {
  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col">
      {bar}
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
