import { Icon } from "../../../components/layout/Icon";
import { externalUrl, linkLabel } from "../lib/externalUrl";

/** A link the warehouse doesn't hold — or holds in a form that can't safely be an href — keeps its
 *  slot as a dimmed glyph, so the icons stay in the same place down the column. */
export function ExternalLink({
  url,
  icon,
  label,
}: {
  url: string | null;
  icon: string;
  label: string;
}) {
  const href = externalUrl(url);
  if (!href) {
    return <Icon d={icon} size={14} className="text-line" aria-hidden="true" />;
  }
  return (
    <a
      href={href}
      target="_blank"
      // Without `noopener` the opened tab can navigate this one via window.opener.
      rel="noopener noreferrer"
      title={linkLabel(href)}
      aria-label={label}
      className="flex text-text3 hover:text-amber"
    >
      <Icon d={icon} size={14} />
    </a>
  );
}
