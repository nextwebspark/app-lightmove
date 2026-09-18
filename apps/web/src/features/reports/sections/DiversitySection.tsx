import { useState } from "react";
import type { ReportDiversity } from "../api/types";
import { ChartEmpty } from "../components/ChartEmpty";
import { GenderPyramid } from "../components/GenderPyramid";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { LockedBenchmarkCard } from "../components/LockedBenchmarkCard";
import { NationalityDonut } from "../components/NationalityDonut";
import { NationalityDots } from "../components/NationalityDots";
import { ReportCard } from "../components/ReportCard";
import { Figure, ReportSection } from "../components/ReportSection";
import { ReportSelect } from "../components/ReportSelect";
import {
  ALL_LEVELS_FILTER,
  ALL_NATIONALITIES_FILTER,
  diversityStats,
  feasibility,
  genderStats,
  GCC_NATIONALS_FILTER,
  levelFilterOptions,
  nationalityFilterOptions,
} from "../lib/diversityStats";
import { percent } from "../lib/figures";
import { memberOf } from "../lib/nationalityWording";
import { useCountUp } from "../lib/useCountUp";

/** Who is in the mapped pool, by nationality and by gender where one was recorded. */
export function DiversitySection({ eyebrow, diversity }: { eyebrow: string; diversity: ReportDiversity }) {
  const [nationality, setNationality] = useState(ALL_NATIONALITIES_FILTER);
  const [level, setLevel] = useState(ALL_LEVELS_FILTER);
  const stats = diversityStats(diversity);
  const gender = genderStats(diversity);
  const fit = feasibility(diversity, stats, { nationality, level });
  const qualifying = useCountUp(fit.qualifying);
  const share = useCountUp(percent(fit.qualifying, fit.scope));
  const requirement =
    nationality === GCC_NATIONALS_FILTER
      ? "a GCC national"
      : nationality === ALL_NATIONALITIES_FILTER
        ? "any nationality"
        : memberOf(nationality);
  const levelLabel = level === ALL_LEVELS_FILTER ? "any level" : level;

  return (
    <ReportSection
      eyebrow={eyebrow}
      question="What does the mapped talent pool actually look like?"
      findingLabel="At these settings"
      finding={
        <>
          {gender.recorded > 0 && (
            <>
              Women are <Figure>{gender.femalePct}% of the recorded pool</Figure>
              {gender.thinnest && (
                <>
                  {" "}
                  and thinnest at{" "}
                  <Figure>
                    {gender.thinnest.level} ({gender.thinnest.femalePct}%)
                  </Figure>
                </>
              )}
              .{" "}
            </>
          )}
          {stats.largest ? (
            <>
              <Figure>{stats.nationalityCount} nationalities</Figure> are represented and{" "}
              {stats.largestPct > 50 ? (
                <>
                  <Figure>{stats.largest.nationality}</Figure> holds a majority at {stats.largestPct}%
                </>
              ) : (
                <>
                  none holds a majority — {stats.largest.nationality} is the largest at {stats.largestPct}%
                </>
              )}
              . GCC nationals are <Figure>{stats.gccPct}%</Figure> of the pool.
            </>
          ) : (
            <>Nobody has a nationality on file yet, so there is no mix to report.</>
          )}
        </>
      }
      lede={
        <>
          Gender is shown <b>aggregate-only</b> — counted where a researcher recorded it, never inferred from a name, and
          no individual is identifiable. Nationality is shown in full because it is public, objective data with real
          regulatory weight in the GCC (Nitaqat, Emiratisation).
        </>
      }
    >
      <KpiTileRow>
        <KpiTile
          tone="lead"
          label="Female, overall"
          value={gender.recorded === 0 ? "—" : gender.femalePct}
          unit={gender.recorded === 0 ? undefined : "%"}
          sub={gender.recorded === 0 ? "no gender recorded yet" : `${gender.female} of ${gender.recorded} recorded`}
        />
        <KpiTile
          label={gender.thinnest ? `Female — ${gender.thinnest.level}` : "Female — thinnest level"}
          value={gender.thinnest ? gender.thinnest.femalePct : "—"}
          unit={gender.thinnest ? "%" : undefined}
          tone="alarm"
          sub={
            gender.thinnest
              ? `the thinnest level · ${gender.thinnest.recorded} recorded`
              : gender.recorded > 0
                ? "nobody recorded has a level yet"
                : "nothing recorded yet"
          }
        />
        <KpiTile
          label="Nationalities represented"
          value={stats.nationalityCount}
          sub={
            stats.largest
              ? `largest: ${stats.largest.nationality} (${stats.largestPct}%)${diversity.unknownNationality > 0 ? ` · ${diversity.unknownNationality} unknown` : ""}`
              : "none on file"
          }
        />
        <KpiTile
          tone="positive"
          label="GCC nationals, overall"
          value={stats.gccPct}
          unit="%"
          sub={`${diversity.gccNationals} of ${stats.total} with a nationality on file`}
        />
      </KpiTileRow>

      <ReportCard
        title="Nationality feasibility checker"
        caption="how many mapped executives qualify against a nationality requirement"
        action={
          <>
            <ReportSelect aria-label="Nationality requirement" value={nationality} onChange={(e) => setNationality(e.target.value)}>
              {nationalityFilterOptions(diversity).map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </ReportSelect>
            <ReportSelect aria-label="Seniority level" value={level} onChange={(e) => setLevel(e.target.value)}>
              {levelFilterOptions(diversity).map((l) => (
                <option key={l} value={l}>
                  {l}
                </option>
              ))}
            </ReportSelect>
          </>
        }
        note={
          <>
            A client requiring <b>{requirement}</b> at {levelLabel} can realistically draw from <b>{fit.qualifying}</b> of
            the {fit.scope} executives mapped in that scope.
          </>
        }
      >
        <KpiTileRow columns={2} className="mt-3.5">
          <KpiTile
            tone="lead"
            label="Qualifying executives"
            value={Math.round(qualifying)}
            unit={`/${fit.scope}`}
            sub={`${nationality} · ${level === ALL_LEVELS_FILTER ? "all levels" : level}`}
          />
          <KpiTile
            label="Share of this scope"
            value={Math.round(share)}
            unit="%"
            sub={`of ${level === ALL_LEVELS_FILTER ? "everyone with a level on file" : `${level} executives`}`}
          />
        </KpiTileRow>
        <div className="mb-3 mt-5 text-xs text-u-text3">
          {nationality} — where they sit by level · one square per executive
        </div>
        <NationalityDots feasibility={fit} />
      </ReportCard>

      <ReportCard
        title="Nationality mix"
        caption={`full breakdown, n=${stats.total} with a nationality on file`}
        note={
          <>
            GCC nationals are {diversity.gccNationals} of {stats.total} ({stats.gccPct}%) — the figure a localisation
            quota is measured against, not the lens this view leads with.
          </>
        }
      >
        <NationalityDonut rows={diversity.nationalities} total={stats.total} largest={stats.largest} />
      </ReportCard>

      <ReportCard
        title="Gender through the seniority pipeline"
        caption={
          gender.recorded === 0
            ? "no gender recorded on this mandate yet"
            : `${gender.femalePct}% female of ${gender.recorded} recorded · aggregate only, no individual identifiable`
        }
        note={
          gender.recorded === 0 ? (
            <>
              Gender is recorded on an executive's profile and is never guessed from a name, so this
              chart stays empty until somebody records one. Set it in the Background section of a
              profile and this fills in.
            </>
          ) : (
            <>
              Bar <b>length</b> is scaled to headcount, not share, so a small level is not drawn the
              size of a big one. Every share divides by the executives <b>recorded</b> at that level
              {gender.unrecorded > 0
                ? `, not by its headcount — ${gender.unrecorded} across the map have no gender on file`
                : ""}
              .
              {gender.recordedWithoutLevel > 0 &&
                ` ${gender.recordedWithoutLevel} recorded on executives with no seniority level count toward the overall share and sit on no bar.`}
            </>
          )
        }
      >
        {gender.recorded === 0 ? (
          <ChartEmpty>Nobody on this mandate has a gender recorded.</ChartEmpty>
        ) : (
          <GenderPyramid stats={gender} />
        )}
      </ReportCard>

      <LockedBenchmarkCard>
        <b>Cross-mandate diversity benchmark — not built.</b> Every figure
        above is this mandate's own pool, so nothing here says whether that mix is normal for the
        sector and seniority. Comparing across mandates is a later piece of work.
      </LockedBenchmarkCard>
    </ReportSection>
  );
}
