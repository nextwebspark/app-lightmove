import { MCP_GUIDE_PATH } from "../../docs/lib/paths";

const bodies = import.meta.glob<string>("../../../../../../docs/help/*.md", {
  query: "?raw",
  import: "default",
  eager: true,
});

export interface HelpArticle {
  slug: string;
  title: string;
  summary: string;
  /** Pages it is the help for, matched against the path. */
  pages: RegExp[];
  /** Markdown drawn in the panel; null for a guide that is its own page. */
  body: string | null;
  href?: string;
}

const CATALOG: Omit<HelpArticle, "body">[] = [
  { slug: "getting-started", title: "Get your first map", summary: "The first hour, step by step.", pages: [/^\/$/, /^\/all$/] },
  { slug: "words", title: "The words Uncava uses", summary: "Position, universe, executive, candidate and the rest.", pages: [] },
  {
    slug: "filter-the-market",
    title: "Filter the market",
    summary: "Find target companies on Strategy and add them to a position.",
    pages: [/^\/projects\/[^/]+\/strategy/],
  },
  {
    slug: "companies-pages",
    title: "In universe, Shortlisted and Declined",
    summary: "Decide about companies and keep a record of why.",
    pages: [/^\/projects\/[^/]+\/companies/],
  },
  {
    slug: "find-executives",
    title: "Find executives",
    summary: "Let Uncava map the people at your target companies.",
    pages: [/^\/projects\/[^/]+\/companies\/universe/],
  },
  {
    slug: "import-spreadsheet",
    title: "Import a spreadsheet",
    summary: "Bring a long list you already have into a position.",
    pages: [/^\/projects\/[^/]+\/companies/],
  },
  {
    slug: "chrome-extension",
    title: "Uncava Capture for Chrome",
    summary: "Add companies and executives straight from LinkedIn.",
    pages: [/^\/projects\/[^/]+\/companies/, /^\/candidates/],
  },
  {
    slug: "outreach-mailbox",
    title: "Outreach and your mailbox",
    summary: "Connect your mailbox and send sequences that stop on a reply.",
    pages: [/^\/projects\/[^/]+\/outreach/],
  },
  {
    slug: "ai-apps",
    title: "Connect Claude, ChatGPT or Cursor",
    summary: "Ask an AI app about your positions.",
    pages: [/^\/settings\/ai-apps/],
    href: MCP_GUIDE_PATH,
  },
];

export const HELP_ARTICLES: HelpArticle[] = CATALOG.map((article) => ({
  ...article,
  body: article.href ? null : (bodies[`../../../../../../docs/help/${article.slug}.md`] ?? ""),
}));

export function articlesForPage(pathname: string): HelpArticle[] {
  return HELP_ARTICLES.filter((article) => article.pages.some((page) => page.test(pathname))).slice(0, 3);
}

/** Every word of the query somewhere in the article's title, summary or text. */
export function searchArticles(query: string): HelpArticle[] {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (words.length === 0) return [];
  return HELP_ARTICLES.filter((article) => {
    const text = `${article.title} ${article.summary} ${article.body ?? ""}`.toLowerCase();
    return words.every((word) => text.includes(word));
  });
}
