import type { StarterLayout } from "../lib/signatureDraft";

/**
 * A starting point drawn small, with the person's own name, for the gallery: a sketch of what the editor opens on,
 * which they then change however they like.
 */
export function LayoutSketch({ layout, name, title }: { layout: StarterLayout; name: string; title: string | null }) {
  const role = title ?? "Your title";
  const logo = <span aria-hidden="true" className="mt-1.5 block h-4 w-12 rounded-u-control bg-u-border-strong" />;

  return (
    <div aria-hidden="true" className="h-28 overflow-hidden rounded-u-chip border border-u-border bg-u-surface p-3 text-meta text-u-text2">
      {layout === "CUSTOM" ? (
        <>
          <div>Kind regards,</div>
          <div className="mt-1 font-semibold text-u-text">{name}</div>
          <div className="mt-1 italic text-u-text3">Write it your own way</div>
        </>
      ) : (
        <>
          {layout === "PHOTO_LOGO" && (
            <span className="mb-1 grid size-7 place-items-center rounded-full bg-u-accent-tint font-mono text-eyebrow font-semibold text-u-accent">
              {initialsOf(name)}
            </span>
          )}
          <div className="truncate font-semibold text-u-text">{name}</div>
          <div className="truncate">{role}</div>
          <div className="truncate text-u-text3">+971 … · LinkedIn</div>
          {logo}
        </>
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
