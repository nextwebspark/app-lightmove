import { Icon, ICONS } from "../../../components/layout/Icon";

/** A business unit is a part of the org, not a company, so it wears the team glyph rather than a logo. */
export function BusinessUnitGlyph({ size }: { size: 26 | 32 }) {
  return (
    <span
      aria-hidden="true"
      style={{ width: size, height: size }}
      className="grid flex-none place-items-center rounded-md bg-u-raised text-u-text3 ring-1 ring-u-border"
    >
      <Icon d={ICONS.team} size={size === 32 ? 16 : 13} />
    </span>
  );
}
