import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { cn } from "../../lib/cn";
import { currencyChoicesOf, currencyNameOf, useCurrencies, type CurrencyChoiceGroup } from "../../lib/currencies";
import { Icon, ICONS } from "../layout/Icon";
import { Popover } from "./Popover";

type CurrencySelectVariant = "field" | "compact";

const TRIGGER_VARIANT: Record<CurrencySelectVariant, string> = {
  field: "flex w-full gap-2 rounded-[6px] bg-u-raised px-3 py-2.5 text-[13px]",
  compact: "inline-flex gap-2 rounded-[8px] bg-u-sunken px-3 py-1.5 text-body font-semibold",
};

const NONE = "";

/**
 * Every currency picker: a button reading as the code held, opening the suggested currencies and then every other
 * one the server accepts, under a box that searches code and name. A held code no list carries stays offered.
 */
export function CurrencySelect({
  value,
  onChange,
  held = [],
  allowNone = false,
  codesOnly = false,
  invalid,
  variant = "field",
  className,
  "aria-label": ariaLabel = "Currency",
}: {
  value: string;
  onChange: (code: string) => void;
  /** Codes in play beside the value (the brief's, the workspace's), listed first. */
  held?: ReadonlyArray<string | null | undefined>;
  /** Offers "Not set", which picks the empty code. */
  allowNone?: boolean;
  /** The trigger spells the code alone, as the brief's Compensation step does. */
  codesOnly?: boolean;
  invalid?: boolean;
  variant?: CurrencySelectVariant;
  className?: string;
  "aria-label"?: string;
}) {
  const served = useCurrencies();
  const name = value ? currencyNameOf(value, served) : null;

  return (
    <Popover
      label={ariaLabel}
      hasPopup="listbox"
      placement="auto"
      width={300}
      triggerClassName={cn(
        "min-w-0 items-center border text-u-text outline-none transition hover:border-u-text3 focus-visible:border-u-accent",
        TRIGGER_VARIANT[variant],
        invalid ? "border-u-offlimits" : variant === "compact" ? "border-u-border" : "border-u-border-strong",
        className,
      )}
      trigger={(open) => (
        <>
          <span className="flex min-w-0 flex-1 items-baseline gap-2 text-start">
            {value ? (
              <>
                <span className="flex-none font-mono font-semibold">{value}</span>
                {!codesOnly && name && (
                  <>
                    {" "}
                    <span className="min-w-0 truncate font-sans text-u-text3">{name}</span>
                  </>
                )}
              </>
            ) : (
              <span className="truncate text-u-text3">Not set</span>
            )}
          </span>
          <Icon
            d={ICONS.chevronDown}
            size={12}
            className={cn("flex-none text-u-text3 transition-transform", open && "rotate-180")}
          />
        </>
      )}
    >
      {(close) => (
        <CurrencyList
          value={value}
          held={held}
          allowNone={allowNone}
          label={ariaLabel}
          onPick={(code) => {
            close();
            if (code !== value) onChange(code);
          }}
        />
      )}
    </Popover>
  );
}

function CurrencyList({
  value,
  held,
  allowNone,
  label,
  onPick,
}: {
  value: string;
  held: ReadonlyArray<string | null | undefined>;
  allowNone: boolean;
  label: string;
  onPick: (code: string) => void;
}) {
  const listId = useId();
  const served = useCurrencies();
  const [search, setSearch] = useState("");
  const groups = useMemo(
    () => currencyChoicesOf([value, ...held], served, search),
    [value, held, served, search],
  );
  const offered = useMemo(() => {
    const codes = groups.flatMap((group) => group.currencies.map((currency) => currency.code));
    return allowNone && !search.trim() ? [NONE, ...codes] : codes;
  }, [groups, allowNone, search]);
  const [activeCode, setActiveCode] = useState(value);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const active = offered.includes(activeCode) ? activeCode : (offered[0] ?? null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    if (active === null) return;
    const row = listRef.current?.querySelector<HTMLElement>(`#${CSS.escape(optionId(listId, active))}`);
    row?.scrollIntoView?.({ block: "nearest" });
  }, [active, listId]);

  const move = (step: number) => {
    if (offered.length === 0) return;
    const at = active === null ? -1 : offered.indexOf(active);
    setActiveCode(offered[(at + step + offered.length) % offered.length]);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      move(1);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      move(-1);
    } else if (event.key === "Enter") {
      event.preventDefault();
      if (active !== null) onPick(active);
    }
  };

  const row = (code: string, text: string, name?: string) => {
    const isHeld = code === value;
    return (
      <div
        key={code || "none"}
        id={optionId(listId, code)}
        role="option"
        aria-selected={isHeld}
        aria-label={name === undefined ? text : name ? `${code} - ${name}` : code}
        onMouseEnter={() => setActiveCode(code)}
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => onPick(code)}
        className={cn(
          "flex cursor-pointer items-center gap-2.5 rounded-u-control px-2 py-1.5 text-note text-u-text2",
          code === active && "bg-u-raised text-u-text",
          isHeld && "font-medium text-u-text",
        )}
      >
        {name !== undefined ? (
          <>
            <span className="w-9 flex-none font-mono font-semibold text-u-text">{text}</span>
            <span className="min-w-0 flex-1 truncate">{name}</span>
          </>
        ) : (
          <span className="min-w-0 flex-1 truncate text-u-text3">{text}</span>
        )}
        {isHeld && <Icon d={ICONS.check} size={13} className="flex-none text-u-accent" />}
      </div>
    );
  };

  return (
    <div className="flex flex-col gap-1.5">
      <label className="flex items-center gap-2 rounded-u-control border border-u-border-strong bg-u-sunken px-2.5 py-1.5 focus-within:border-u-accent">
        <Icon d={ICONS.search} size={13} className="flex-none text-u-text3" />
        <input
          ref={inputRef}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder="Search by code or name"
          role="combobox"
          aria-label={`Search ${label.toLowerCase()}`}
          aria-expanded
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={active === null ? undefined : optionId(listId, active)}
          className="w-full bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
        />
      </label>

      <div ref={listRef} id={listId} role="listbox" aria-label={label} className="flex flex-col">
        {allowNone && !search.trim() && row(NONE, "Not set")}
        {groups.map((group: CurrencyChoiceGroup) => (
          <div key={group.label} role="group" aria-label={group.label} className="pt-2 first:pt-1">
            <div className="px-2 pb-1 type-micro-label font-mono text-u-text3">{group.label}</div>
            {group.currencies.map((currency) => row(currency.code, currency.code, currency.name))}
          </div>
        ))}
        {offered.length === 0 && <p className="px-2 py-3 text-note text-u-text3">No currency matches</p>}
      </div>
    </div>
  );
}

function optionId(listId: string, code: string): string {
  return `${listId}-${code || "none"}`;
}
