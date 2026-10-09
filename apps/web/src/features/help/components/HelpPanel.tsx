import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Drawer, Input, useToast } from "../../../components/ui";
import { PanelCloseButton } from "../../../components/ui/PanelCloseButton";
import { messageFor } from "../../../lib/errorCodes";
import { SUPPORT_EMAIL } from "../../../lib/links";
import { APP_VERSION } from "../../../lib/version";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import { GuideMarkdown } from "../../docs/components/GuideMarkdown";
import * as gettingStartedApi from "../../gettingstarted/api/gettingStartedApi";
import { articlesForPage, HELP_ARTICLES, searchArticles, type HelpArticle } from "../lib/articles";
import type { HelpSection } from "../lib/helpSection";
import { markWhatsNewSeen, WHATS_NEW } from "../lib/whatsNew";

/** How many further articles show before "All articles" opens the rest, so the panel scans at a glance. */
const ARTICLES_SHOWN = 4;

const SHORTCUTS: { keys: string; does: string }[] = [
  { keys: "?", does: "Open Help" },
  { keys: "Esc", does: "Close a menu, dialog or panel" },
  { keys: "⌘ Enter / Ctrl Enter", does: "Save or send, in most forms" },
  { keys: "↑ ↓", does: "Move through the workspace and account menus" },
];

/** Help for the page you're on, the articles, what's new, shortcuts and a way to reach us — in one panel. */
export default function HelpPanel({
  initialSection,
  onClose,
  onNewsSeen,
}: {
  initialSection: HelpSection;
  onClose: () => void;
  onNewsSeen: () => void;
}) {
  const { pathname } = useLocation();
  const [query, setQuery] = useState("");
  const [article, setArticle] = useState<HelpArticle | null>(null);
  const [allArticles, setAllArticles] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);
  const articleTitleRef = useRef<HTMLHeadingElement>(null);
  const lastOpenedSlug = useRef<string | null>(null);
  const forThisPage = useMemo(() => articlesForPage(pathname), [pathname]);
  const others = useMemo(() => HELP_ARTICLES.filter((candidate) => !forThisPage.includes(candidate)), [forThisPage]);
  const results = useMemo(() => searchArticles(query), [query]);

  // Help is search-first. This runs after the drawer's own focus placement, so it wins.
  useEffect(() => {
    if (initialSection === "whatsNew") {
      document.getElementById("help-whats-new")?.scrollIntoView?.();
      return;
    }
    searchRef.current?.focus();
  }, [initialSection]);

  useEffect(() => {
    if (article) {
      articleTitleRef.current?.focus();
      return;
    }
    if (lastOpenedSlug.current) {
      document.querySelector<HTMLElement>(`[data-help-article="${lastOpenedSlug.current}"]`)?.focus();
    }
  }, [article]);

  const openArticle = (next: HelpArticle) => {
    lastOpenedSlug.current = next.slug;
    setArticle(next);
  };

  return (
    <Drawer open onClose={onClose} label="Help">
      <PanelCloseButton onClose={onClose} />
      <div className="border-b border-u-border px-5 pb-4 pt-5">
        {article ? (
          <>
            <button
              type="button"
              onClick={() => setArticle(null)}
              className="mb-1 inline-flex items-center gap-1 rounded-[4px] text-note text-u-accent hover:underline"
            >
              <Icon d={ICONS.arrowLeft} size={13} />
              Help
            </button>
            <h2 ref={articleTitleRef} tabIndex={-1} className="pr-8 text-subhead font-semibold outline-none">
              {article.title}
            </h2>
          </>
        ) : (
          <>
            <h2 className="text-subhead font-semibold">Help</h2>
            <div className="mt-3">
              <Input
                ref={searchRef}
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search help"
                aria-label="Search help"
              />
            </div>
          </>
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4 text-body">
        {article ? (
          <div className="[&_h2]:mt-6 [&_h2]:border-0 [&_h2]:pt-0 [&_h2]:text-lead">
            <GuideMarkdown markdown={article.body ?? ""} onNavigate={onClose} />
          </div>
        ) : query.trim() ? (
          <Section title={results.length ? "Results" : "Nothing found"}>
            {results.length === 0 ? (
              <p className="text-note text-u-text3">
                Try other words, or <SupportLink className="text-u-accent hover:underline">ask Uncava support</SupportLink>.
              </p>
            ) : (
              <ArticleList articles={results} onOpen={openArticle} />
            )}
          </Section>
        ) : (
          <>
            {forThisPage.length > 0 && (
              <Section title="Help for this page">
                <ArticleList articles={forThisPage} onOpen={openArticle} />
              </Section>
            )}
            <Section title={forThisPage.length > 0 ? "More articles" : "Articles"}>
              <ArticleList articles={allArticles ? others : others.slice(0, ARTICLES_SHOWN)} onOpen={openArticle} />
              {!allArticles && others.length > ARTICLES_SHOWN && (
                <button
                  type="button"
                  onClick={() => setAllArticles(true)}
                  className="mt-1 rounded-[4px] text-note font-medium text-u-accent hover:underline"
                >
                  All articles ({others.length})
                </button>
              )}
            </Section>
            <WhatsNew onSeen={onNewsSeen} seenOnArrival={initialSection === "whatsNew"} />
            <Section title="Keyboard shortcuts">
              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
                {SHORTCUTS.map((shortcut) => (
                  <div key={shortcut.keys} className="contents">
                    <dt>
                      <kbd className="rounded border border-u-border bg-u-raised px-1.5 py-0.5 font-mono text-meta text-u-text">
                        {shortcut.keys}
                      </kbd>
                    </dt>
                    <dd className="text-note text-u-text2">{shortcut.does}</dd>
                  </div>
                ))}
              </dl>
            </Section>
            <GettingStartedAgain />
          </>
        )}
      </div>

      <div className="flex items-center justify-between gap-3 border-t border-u-border px-5 py-3">
        <SupportLink className="inline-flex items-center gap-1.5 text-note font-medium text-u-accent hover:underline">
          <Icon d={ICONS.mail} size={14} />
          Contact support
        </SupportLink>
        <span className="font-mono text-meta text-u-text3">Version {APP_VERSION}</span>
      </div>
    </Drawer>
  );
}

/** Read once it has actually been in view — or the panel was opened at it — so the dot means what it says. */
function WhatsNew({ onSeen, seenOnArrival }: { onSeen: () => void; seenOnArrival: boolean }) {
  const ref = useRef<HTMLElement>(null);

  useEffect(() => {
    const markSeen = () => {
      markWhatsNewSeen();
      onSeen();
    };
    if (seenOnArrival || typeof IntersectionObserver === "undefined") {
      if (seenOnArrival) markSeen();
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        markSeen();
        observer.disconnect();
      }
    });
    if (ref.current) observer.observe(ref.current);
    return () => observer.disconnect();
  }, [onSeen, seenOnArrival]);

  return (
    <Section title="What's new" id="help-whats-new" sectionRef={ref}>
      <ul className="space-y-3">
        {WHATS_NEW.map((item) => (
          <li key={item.id}>
            <div className="text-note font-medium text-u-text">{item.title}</div>
            <div className="text-note text-u-text3">{item.body}</div>
          </li>
        ))}
      </ul>
    </Section>
  );
}

function Section({
  title,
  id,
  sectionRef,
  children,
}: {
  title: string;
  id?: string;
  sectionRef?: React.Ref<HTMLElement>;
  children: ReactNode;
}) {
  return (
    <section ref={sectionRef} id={id} className="mb-6 last:mb-0">
      <h3 className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">{title}</h3>
      {children}
    </section>
  );
}

function ArticleList({ articles, onOpen }: { articles: HelpArticle[]; onOpen: (article: HelpArticle) => void }) {
  const rowClass =
    "flex w-full items-start gap-2.5 rounded-[7px] px-2 py-2 text-left hover:bg-u-raised focus-visible:bg-u-raised focus-visible:outline-none";
  return (
    <ul className="-mx-2">
      {articles.map((article) => (
        <li key={article.slug}>
          {article.href ? (
            <Link to={article.href} target="_blank" rel="noopener noreferrer" className={rowClass}>
              <ArticleRow article={article} external />
            </Link>
          ) : (
            <button type="button" data-help-article={article.slug} onClick={() => onOpen(article)} className={rowClass}>
              <ArticleRow article={article} />
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}

function ArticleRow({ article, external = false }: { article: HelpArticle; external?: boolean }) {
  return (
    <>
      <Icon d={ICONS.fileText} size={15} className="mt-0.5 flex-none text-u-text3" />
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1 text-note font-medium text-u-text">
          {article.title}
          {external && (
            <>
              <Icon d={ICONS.externalLink} size={12} className="flex-none text-u-text3" />
              <span className="sr-only"> (opens in a new tab)</span>
            </>
          )}
        </span>
        <span className="block text-meta text-u-text3">{article.summary}</span>
      </span>
    </>
  );
}

/** A mailto that names the workspace and the version, so a reply can start from the right place. */
function SupportLink({ className, children }: { className?: string; children: ReactNode }) {
  const { user } = useAuth();
  const body = [
    "",
    "",
    "—",
    `Workspace: ${user?.workspace?.name ?? "none"} (${user?.workspace?.id ?? "no id"})`,
    `Version: ${APP_VERSION}`,
  ].join("\n");
  const href = `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent("Help with Uncava")}&body=${encodeURIComponent(body)}`;
  return (
    <a href={href} className={className}>
      {children}
    </a>
  );
}

/** Brings back the Getting started card someone put away — the way back its dismissal toast points to. */
function GettingStartedAgain() {
  const { user } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const isStaff = !!user?.workspace && !isPureClient(user.workspace.roles);
  const { data } = useQuery({
    queryKey: gettingStartedApi.GETTING_STARTED_KEY,
    queryFn: gettingStartedApi.gettingStarted,
    enabled: isStaff,
  });
  const restore = useMutation({
    mutationFn: () => gettingStartedApi.setDismissed(false),
    onSuccess: (fresh) => {
      queryClient.setQueryData(gettingStartedApi.GETTING_STARTED_KEY, fresh);
      toast.success("Getting started is back on My positions.");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  if (!isStaff || !data?.dismissed) return null;

  return (
    <Section title="Getting started">
      <button
        type="button"
        onClick={() => restore.mutate()}
        disabled={restore.isPending}
        className="inline-flex items-center gap-1.5 text-note font-medium text-u-accent hover:underline disabled:opacity-60"
      >
        <Icon d={ICONS.checkCircle} size={14} />
        Show getting started on My positions
      </button>
    </Section>
  );
}
