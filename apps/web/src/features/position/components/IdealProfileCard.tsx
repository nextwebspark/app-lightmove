import { SectionHeading } from "./fields";

/** The narrative — drafted from the brief by the template library, edited freely. */
export function IdealProfileCard({
  narrative,
  onChange,
}: {
  narrative: string | null;
  onChange: (value: string) => void;
}) {
  return (
    <div className="mb-[22px]">
      <SectionHeading title="Ideal Profile" aside="drafted from the brief — edit freely" />
      <textarea
        value={narrative ?? ""}
        onChange={(e) => onChange(e.target.value)}
        className="min-h-[88px] w-full resize-y rounded-[10px] border border-line-soft bg-panel2 px-4 py-3.5 text-[13.5px] leading-[1.65] text-text2 outline-none transition focus:border-sky"
      />
    </div>
  );
}
