import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CountryField } from "./CountryField";

const OPTIONS = [
  { value: "Israel", label: "Israel", aliases: ["il"] },
  { value: "United Arab Emirates", label: "United Arab Emirates", aliases: ["ae", "uae", "emirates"] },
  { value: "Ireland", label: "Ireland", aliases: ["ie"] },
];

const catalog = { options: OPTIONS, markets: ["United Arab Emirates"], isPending: false, isError: false };
const countries = vi.fn(() => catalog);

beforeEach(() => countries.mockReturnValue(catalog));

vi.mock("../../lib/countries", () => ({ useCountries: () => countries() }));

function Harness() {
  const [country, setCountry] = useState("");
  return <CountryField listId="test-country" value={country} onChange={setCountry} />;
}

describe("CountryField", () => {
  it("finds a country by the abbreviation people actually type", async () => {
    const onChange = vi.fn();
    render(<CountryField listId="test-country" value="" onChange={onChange} />);

    // The label alone would never match "uae" — the alias is what makes the picker worth having.
    await userEvent.type(screen.getByRole("combobox"), "uae");
    await userEvent.click(await screen.findByRole("option", { name: "United Arab Emirates" }));

    expect(onChange).toHaveBeenCalledWith("United Arab Emirates");
  });

  it("shows a country the vocabulary does not carry rather than clearing it", () => {
    render(<CountryField listId="test-country" value="Atlantis" onChange={vi.fn()} />);

    // Research records places the catalog has never heard of; dropping one would lose a fact.
    expect(screen.getByRole("combobox")).toHaveValue("Atlantis");
  });

  it("keeps a country nobody listed when it is typed", async () => {
    const onChange = vi.fn();
    render(<CountryField listId="test-country" value="" onChange={onChange} />);

    // The field this replaced was free text. A place the catalog misses is still somewhere a
    // researcher met somebody, and the server stores an unresolvable spelling as it was typed.
    await userEvent.type(screen.getByRole("combobox"), "Kosovo");
    await userEvent.tab();

    expect(onChange).toHaveBeenCalledWith("Kosovo");
  });

  it("prefers an exact code over a country that merely contains those letters", async () => {
    const onChange = vi.fn();
    render(<CountryField listId="test-country" value="" onChange={onChange} />);

    // "ae" is the code for the United Arab Emirates and also three letters inside "Israel", which
    // sorts first — so an unranked substring match committed Israel on Enter.
    await userEvent.type(screen.getByRole("combobox"), "ae");
    const options = await screen.findAllByRole("option");
    expect(options[0]).toHaveTextContent("United Arab Emirates");
  });

  it("falls back to a plain box when the vocabulary was refused", async () => {
    countries.mockReturnValue({ ...catalog, options: [], isError: true });
    render(<Harness />);

    // A client representative is gated out of the read that backs the list. Their country is still
    // a fact worth recording, so the control degrades rather than becoming unusable.
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    await userEvent.type(screen.getByRole("textbox"), "Oman");
    expect(screen.getByRole("textbox")).toHaveValue("Oman");
  });
});
