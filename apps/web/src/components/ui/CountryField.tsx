import { useCountries } from "../../lib/countries";
import { Input } from "./index";
import { FacetCombobox, type FacetComboboxVariant } from "./FacetCombobox";

/**
 * The country box everywhere, over one served vocabulary so spellings compare across screens (four boxes once gave
 * "AE" beside "United Arab Emirates"). Free text survives; the server keeps an unresolvable spelling as typed.
 */
export function CountryField({
  listId,
  value,
  invalid,
  placeholder,
  variant = "default",
  onChange,
}: {
  /** Unique per rendered field — it wires the input to its own listbox for assistive tech. */
  listId: string;
  value: string;
  invalid?: boolean;
  placeholder?: string;
  variant?: FacetComboboxVariant;
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
        className={
          variant === "uncava"
            ? "rounded-[8px] border border-u-border bg-u-sunken px-3 py-2.5 font-sans text-body text-u-text focus:border-u-accent"
            : undefined
        }
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
      variant={variant}
      onChange={onChange}
    />
  );
}
