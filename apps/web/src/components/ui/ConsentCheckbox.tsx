import { useId, type ReactNode } from "react";
import { cn } from "../../lib/cn";
import { CheckBox } from "./FilterCheckRow";

/**
 * An unticked box someone must tick to go on — the terms on every signup door, the data processing agreement when a
 * workspace is founded (Signup.dc.html, Login.dc.html). A native checkbox over the drawn square, so the keyboard, a
 * screen reader and a test all meet a real one; a link inside the label opens without toggling it.
 */
export function ConsentCheckbox({
  checked,
  onChange,
  error,
  className,
  children,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** Shown under the box while it is still unticked; the mockups' words are "Tick the box to accept …". */
  error?: string;
  className?: string;
  children: ReactNode;
}) {
  const errorId = useId();
  return (
    <div className={cn("mb-5", className)}>
      <label className="flex cursor-pointer items-start gap-2.5">
        <span className="relative mt-px grid flex-none place-items-center">
          {/* Before the square in the DOM so `peer-*` can reach it: CSS has no previous-sibling selector. */}
          <input
            type="checkbox"
            checked={checked}
            onChange={(event) => onChange(event.target.checked)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            className="peer absolute inset-0 z-10 cursor-pointer opacity-0"
          />
          <span className="rounded-[5px] peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-u-accent">
            <CheckBox checked={checked} invalid={!!error} />
          </span>
        </span>
        <span className="text-[12.5px] leading-[1.55] text-u-text2">{children}</span>
      </label>
      {error && (
        <p id={errorId} role="alert" className="ml-[26px] mt-1.5 font-mono text-[11.5px] text-u-offlimits">
          {error}
        </p>
      )}
    </div>
  );
}
