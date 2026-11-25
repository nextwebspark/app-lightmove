import { type ComponentProps, useState } from "react";
import { Input } from "../../../components/ui";
import { cn } from "../../../lib/cn";

/** A password field with the eye that shows what was typed. */
export function PasswordInput({ className, ...rest }: Omit<ComponentProps<typeof Input>, "type">) {
  const [shown, setShown] = useState(false);
  // Never "password": a label search for the field would find this button too.
  const label = shown ? "Hide what you typed" : "Show what you typed";

  return (
    <span className="relative block">
      <Input {...rest} type={shown ? "text" : "password"} className={cn("pr-10", className)} />
      <button
        type="button"
        onClick={() => setShown((current) => !current)}
        aria-label={label}
        title={label}
        className="absolute right-1.5 top-1/2 grid size-7 -translate-y-1/2 place-items-center rounded-md text-u-text3 transition-colors hover:text-u-text"
      >
        {shown ? (
          <svg className="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path d="M3 3l18 18M10.6 5.1A10.7 10.7 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3.2 4.2M6.6 6.6A17.3 17.3 0 0 0 2 12s3.6 7 10 7a9.7 9.7 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2" />
          </svg>
        ) : (
          <svg className="size-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        )}
      </button>
    </span>
  );
}
