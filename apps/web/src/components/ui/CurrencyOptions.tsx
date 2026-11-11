import { currencyGroupsOf, currencyOptionLabel, useCurrencies } from "../../lib/currencies";

/**
 * A currency `<select>`'s options, wherever one is drawn: the codes already in play and the suggested nine
 * first, then every other currency the server accepts. A held code no list carries stays offered, since an
 * unmatched select posts blank.
 */
export function CurrencyOptions({
  held = [],
  codesOnly = false,
}: {
  held?: ReadonlyArray<string | null | undefined>;
  /** A compact select spells the code alone ("AED"), as the brief's Compensation step does. */
  codesOnly?: boolean;
}) {
  const served = useCurrencies();
  const { suggested, others } = currencyGroupsOf(held, served);
  const servedNames = new Map(served.map((currency) => [currency.code, currency.name]));
  const labelOf = (code: string) => (codesOnly ? code : currencyOptionLabel(code, servedNames.get(code)));
  if (others.length === 0) {
    return (
      <>
        {suggested.map((code) => (
          <option key={code} value={code}>
            {labelOf(code)}
          </option>
        ))}
      </>
    );
  }
  return (
    <>
      <optgroup label="Suggested">
        {suggested.map((code) => (
          <option key={code} value={code}>
            {labelOf(code)}
          </option>
        ))}
      </optgroup>
      <optgroup label="All currencies">
        {others.map((currency) => (
          <option key={currency.code} value={currency.code}>
            {labelOf(currency.code)}
          </option>
        ))}
      </optgroup>
    </>
  );
}
