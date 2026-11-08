import type { SignatureLayout } from "../api/signaturesApi";

/**
 * A layout drawn small, with the person's own name, for the gallery: a sketch of the arrangement rather than the
 * signature itself, which the editor's preview draws once a layout is picked.
 */
export function LayoutSketch({ layout, name, title }: { layout: SignatureLayout; name: string; title: string | null }) {
  const role = title ?? "Your title";
  const photo = (
    <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-full bg-u-accent-tint font-mono text-meta font-semibold text-u-accent">
      {initialsOf(name)}
    </span>
  );
  const logo = <span aria-hidden="true" className="h-5 w-12 shrink-0 rounded-u-control bg-u-border-strong" />;

  return (
    <div aria-hidden="true" className="h-24 overflow-hidden rounded-u-chip border border-u-border bg-u-surface p-3 text-meta text-u-text2">
      {layout === "CUSTOM" ? (
        <>
          <div>Kind regards,</div>
          <div className="mt-1 font-semibold text-u-text">{name}</div>
          <div className="mt-1 italic text-u-text3">Write it your own way</div>
        </>
      ) : layout === "MINIMAL" ? (
        <div className="pt-6">
          <span className="font-semibold text-u-text">{name}</span> · {role} · +971 …
        </div>
      ) : (
        <div className="flex items-center gap-2.5">
          {(layout === "PHOTO" || layout === "PHOTO_LOGO") && photo}
          <div className="min-w-0 flex-1">
            <div className="truncate font-semibold text-u-text">{name}</div>
            <div className="truncate">{role}</div>
            <div className="truncate text-u-text3">+971 … · LinkedIn</div>
            {layout === "LOGO_BANNER" && <div className="mt-1.5 border-t-2 border-u-text pt-1.5">{logo}</div>}
          </div>
          {layout === "PHOTO_LOGO" && <div className="border-l border-u-border pl-2.5">{logo}</div>}
        </div>
      )}
    </div>
  );
}

function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}
