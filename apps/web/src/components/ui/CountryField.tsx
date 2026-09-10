import { useCountries } from "../../lib/countries";
import { Input } from "./index";
import { FacetCombobox } from "./FacetCombobox";

/**
 * The country box, wherever one is asked for — a company's, an executive's, a client's.
 *
 * <p>One control over one served vocabulary, because a country typed on one screen is compared with a
 * country typed on another: the Strategy filter matches the stored spelling exactly, the report groups
 * by it and the map's country branch is it. Four screens with four boxes produced "AE" beside "United
 * Arab Emirates" in one column.
 *
 * <p>Free text survives, deliberately. The vocabulary is the world, but a researcher may still record
 * a place it does not name, and the server keeps an unresolvable spelling as it was typed rather than
 * dropping a fact nobody asked us to drop.
 */
export function CountryField({
  listId,
  value,
  invalid,
  placeholder,
  onChange,
}: {
  /** Unique per rendered field — it wires the input to its own listbox for assistive tech. */
  listId: string;
  value: string;
  invalid?: boolean;
  placeholder?: string;
  onChange: (country: string) => void;
}) {
  const { options, isError } = useCountries();

  // A refused vocabulary must not become a box nobody can type into. The sector field beside this one
  // has degraded to a plain input since it shipped, for the same reason: a client representative is
  // gated out of the reads that back both lists, and their country is still a fact worth recording.
  if (isError) {
    return (
      <Input
        value={value}
        invalid={invalid}
        placeholder={placeholder ?? "United Arab Emirates"}
        onChange={(event) => onChange(event.target.value)}
      />
    );
  }

  return (
    <FacetCombobox
      listId={listId}
      noun="countries"
      value={value}
      options={options}
      allowFreeText
      invalid={invalid}
      placeholder={placeholder}
      onChange={onChange}
    />
  );
}
