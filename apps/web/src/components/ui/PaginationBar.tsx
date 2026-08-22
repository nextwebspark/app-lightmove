import { Icon, ICONS } from "../layout/Icon";

/**
 * The mockup's pagination row, wired for real: its own buttons were decorative because every row
 * fitted on one page of seeded data. The universe is 71,822 companies, so they have to work.
 */
export function PaginationBar({
  page,
  size,
  totalCount,
  onPage,
}: {
  page: number;
  size: number;
  totalCount: number;
  onPage: (page: number) => void;
}) {
  const lastPage = Math.max(0, Math.ceil(totalCount / size) - 1);
  const first = totalCount === 0 ? 0 : page * size + 1;
  const last = Math.min((page + 1) * size, totalCount);

  return (
    <div className="flex flex-none items-center gap-4">
      <PageButton
        label="Previous page"
        path={ICONS.back}
        disabled={page === 0}
        onClick={() => onPage(page - 1)}
      />
      <span className="inline-flex items-center gap-2 rounded-[6px] border border-line-soft px-3 py-1.5 font-sans text-[13px] font-semibold text-text">
        {page + 1}
      </span>
      <PageButton
        label="Next page"
        path={ICONS.chevronRight}
        disabled={page >= lastPage}
        onClick={() => onPage(page + 1)}
      />
      <span className="font-sans text-[13px] text-text3">
        {totalCount === 0 ? "0 results" : `${first} - ${last} of ${totalCount.toLocaleString()}`}
      </span>
    </div>
  );
}

function PageButton({
  label,
  path,
  disabled,
  onClick,
}: {
  label: string;
  path: string;
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="grid size-8 place-items-center rounded-[6px] border border-line-soft text-text3 transition hover:border-line hover:text-text disabled:opacity-40 disabled:hover:border-line-soft disabled:hover:text-text3"
    >
      <Icon d={path} size={14} />
    </button>
  );
}
