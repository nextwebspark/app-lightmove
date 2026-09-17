// tailwind.config.js — UNCAVA
module.exports = {
  darkMode: ["class", '[data-theme="dark"]'],
  theme: {
    extend: {
      fontFamily: {
        sans: ["Geist", "Helvetica Neue", "Arial", "sans-serif"],
        mono: ["JetBrains Mono", "ui-monospace", "monospace"],
        brand: ["Montserrat", "sans-serif"]
      },
      colors: {
        bg: "var(--u-bg)",
        surface: "var(--u-surface)",
        raised: "var(--u-raised)",
        sunken: "var(--u-sunken)",
        hairline: "var(--u-border)",
        ink: { DEFAULT: "var(--u-text)", 2: "var(--u-text-2)", 3: "var(--u-text-3)" },
        accent: { DEFAULT: "var(--u-accent)", solid: "var(--u-accent-solid)", tint: "var(--u-accent-tint)" },
        signal: "var(--u-signal)",
        direct: "var(--u-direct)",
        adjacent: "var(--u-adjacent)",
        inferred: "var(--u-inferred)",
        offlimits: "var(--u-offlimits)",
        chart: {
          1: "var(--u-chart-1)", 2: "var(--u-chart-2)", 3: "var(--u-chart-3)",
          4: "var(--u-chart-4)", 5: "var(--u-chart-5)", 6: "var(--u-chart-6)"
        }
      },
      borderRadius: { control: "6px", chip: "8px", card: "12px", modal: "16px" },
      boxShadow: { e1: "var(--u-e1)", e2: "var(--u-e2)", e3: "var(--u-e3)" },
      transitionTimingFunction: { u: "cubic-bezier(.2,.8,.2,1)", overshoot: "cubic-bezier(.34,1.56,.64,1)" }
    }
  }
};
