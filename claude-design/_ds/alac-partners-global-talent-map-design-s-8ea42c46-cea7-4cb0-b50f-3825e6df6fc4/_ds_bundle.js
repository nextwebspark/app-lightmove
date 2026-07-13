/* @ds-bundle: {"format":4,"namespace":"ALACPartnersGlobalTalentMapDesignSystem_8ea42c","components":[],"sourceHashes":{"ui_kits/talent-map/accounts.jsx":"74abc830388d","ui_kits/talent-map/app.jsx":"3294a8d391a0","ui_kits/talent-map/auth.jsx":"535653c931db","ui_kits/talent-map/chrome.jsx":"9fe231c9f2e1","ui_kits/talent-map/cookies.jsx":"c6c06bd6d956","ui_kits/talent-map/crm.jsx":"8f4cfe1166e5","ui_kits/talent-map/data.jsx":"1acf4a38b9af","ui_kits/talent-map/home.jsx":"a843dec86936","ui_kits/talent-map/landing.jsx":"14e133a41f9e","ui_kits/talent-map/mandates.jsx":"e42939d8697a","ui_kits/talent-map/mapview.jsx":"f36ead0b1865","ui_kits/talent-map/panel.jsx":"828090e99b3d","ui_kits/talent-map/primitives.jsx":"e2cfb7aacb67","ui_kits/talent-map/projects.jsx":"31dc4706033b","ui_kits/talent-map/search-wizard.jsx":"77b10a28e2a2","ui_kits/talent-map/settings.jsx":"0acda02d1dd9","ui_kits/talent-map/settings_org.jsx":"7cefcf9efb2f","ui_kits/talent-map/sourcing-card.jsx":"d5218ecd638e","ui_kits/talent-map/sourcing-criteria.jsx":"22ca90f35826","ui_kits/talent-map/sourcing-flyover.jsx":"6b538bdcc7b7","ui_kits/talent-map/sourcing-sidebar.jsx":"a2a16b5a81af","ui_kits/talent-map/table-config.jsx":"fb719c51fa16","ui_kits/talent-map/table-modals.jsx":"cf91b7c1eb07","ui_kits/talent-map/tableview.jsx":"859295659d55","ui_kits/talent-map/universe-chat-old-version.jsx":"2363bdb721cd","ui_kits/talent-map/universe-chat.jsx":"2363bdb721cd","ui_kits/talent-map/universe-filters-old-version.jsx":"9b1c6cc7ecd2","ui_kits/talent-map/universe-filters.jsx":"9b1c6cc7ecd2","ui_kits/talent-map/universe-old-version.jsx":"6cbaa27b083c","ui_kits/talent-map/universe-selected-old-version.jsx":"eea952943c62","ui_kits/talent-map/universe-selected.jsx":"eea952943c62","ui_kits/talent-map/universe.jsx":"74262982896a","ui_kits/talent-map/views.jsx":"e14720623b86","ui_kits/talent-map/worklist.jsx":"81a0d20855b4","ui_kits/talent-map/workspace-screens.jsx":"dc2a6e21c3e2"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.ALACPartnersGlobalTalentMapDesignSystem_8ea42c = window.ALACPartnersGlobalTalentMapDesignSystem_8ea42c || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// ui_kits/talent-map/accounts.jsx
try { (() => {
/* global React, Icon, Button, Avatar, cx, formatAge, AvailPill, StatusPill, dueMeta,
   TM_ACCOUNTS, TM_ACCOUNT_TYPE_META, TM_STRENGTH_META, tmAccountFirmographics, tmAccountPeople */
// ── Accounts (Companies) — CRM directory + the account record ────────────────

const MANDATE_STATUS_META = {
  'Active': {
    fg: '#1d4ed8',
    bg: 'rgba(37,99,235,.10)'
  },
  'Placed': {
    fg: 'var(--success-fg, #15803d)',
    bg: 'var(--success-bg, rgba(5,150,105,.10))'
  },
  'Pitching': {
    fg: '#b45309',
    bg: 'rgba(245,158,11,.12)'
  },
  'On hold': {
    fg: 'var(--muted-foreground)',
    bg: 'var(--muted)'
  },
  'Lost': {
    fg: '#b91c1c',
    bg: 'rgba(220,38,38,.10)'
  }
};
function AccountTypePill({
  type,
  size
}) {
  const m = TM_ACCOUNT_TYPE_META[type] || TM_ACCOUNT_TYPE_META['Source'];
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: m.bg,
      color: m.fg,
      gap: 5,
      fontSize: size === 'lg' ? 11.5 : 10.5,
      padding: size === 'lg' ? '3px 10px' : '2px 8px'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: m.icon,
    size: size === 'lg' ? 12 : 11
  }), type);
}
function StrengthMeter({
  strength,
  showLabel
}) {
  const m = TM_STRENGTH_META[strength] || TM_STRENGTH_META['Dormant'];
  return /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 7
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      gap: 2,
      alignItems: 'flex-end'
    }
  }, [1, 2, 3].map(i => /*#__PURE__*/React.createElement("span", {
    key: i,
    style: {
      width: 4,
      height: 4 + i * 3,
      borderRadius: 1,
      background: i <= m.v ? m.fg : 'color-mix(in srgb, var(--muted-foreground) 25%, transparent)'
    }
  }))), showLabel && /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      color: m.fg,
      fontWeight: 600
    }
  }, strength));
}
function companyInitials(name) {
  return name.replace(/[^a-zA-Z ]/g, '').split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase();
}

// ── Account avatar (square, neutral — distinguishes companies from people) ───
function AccountAvatar({
  name,
  size = 38
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      width: size,
      height: size,
      borderRadius: size > 30 ? 9 : 7,
      flexShrink: 0,
      background: 'var(--ink)',
      color: '#fff',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontSize: size * 0.34,
      fontWeight: 700,
      letterSpacing: '.01em'
    }
  }, companyInitials(name));
}

// ─────────────────────────────────────────────────────────────────────────────
// Accounts directory
// ─────────────────────────────────────────────────────────────────────────────
function AccountsScreen({
  onSelectAccount
}) {
  const [q, setQ] = React.useState('');
  const [filterType, setFilterType] = React.useState('');
  const [filterOwner, setFilterOwner] = React.useState('');
  const [sort, setSort] = React.useState({
    key: 'name',
    dir: 'asc'
  });
  const accounts = TM_ACCOUNTS || [];
  const types = ['Client', 'Prospect', 'Source', 'Off-limits'];
  const owners = [...new Set(accounts.map(a => a.owner))].sort();
  const rows = accounts.map(a => {
    const people = tmAccountPeople(a);
    const activeMandates = a.mandates.filter(m => m.status === 'Active' || m.status === 'Pitching').length;
    const placements = a.mandates.filter(m => m.status === 'Placed').length;
    return {
      ...a,
      peopleCount: people.length,
      activeMandates,
      placements
    };
  });
  const filtered = rows.filter(a => {
    if (q && !a.name.toLowerCase().includes(q.toLowerCase())) return false;
    if (filterType && a.type !== filterType) return false;
    if (filterOwner && a.owner !== filterOwner) return false;
    return true;
  });
  const sorted = [...filtered].sort((a, b) => {
    let av, bv;
    if (sort.key === 'name') {
      av = a.name.toLowerCase();
      bv = b.name.toLowerCase();
    } else if (sort.key === 'type') {
      av = a.type;
      bv = b.type;
    } else if (sort.key === 'strength') {
      av = (TM_STRENGTH_META[a.strength] || {}).v || 0;
      bv = (TM_STRENGTH_META[b.strength] || {}).v || 0;
    } else if (sort.key === 'mandates') {
      av = a.activeMandates;
      bv = b.activeMandates;
    } else if (sort.key === 'people') {
      av = a.peopleCount;
      bv = b.peopleCount;
    } else if (sort.key === 'lastActivity') {
      av = a.lastActivityDays;
      bv = b.lastActivityDays;
    } else {
      av = a[sort.key];
      bv = b[sort.key];
    }
    if (av < bv) return sort.dir === 'asc' ? -1 : 1;
    if (av > bv) return sort.dir === 'asc' ? 1 : -1;
    return 0;
  });
  const toggleSort = key => setSort(s => s.key === key ? {
    key,
    dir: s.dir === 'asc' ? 'desc' : 'asc'
  } : {
    key,
    dir: 'asc'
  });
  const SortH = ({
    k,
    children,
    right
  }) => /*#__PURE__*/React.createElement("button", {
    className: cx('tm-sorth', right && 'is-right'),
    onClick: () => toggleSort(k)
  }, children, sort.key === k && /*#__PURE__*/React.createElement(Icon, {
    name: sort.dir === 'asc' ? 'ChevronUp' : 'ChevronDown',
    size: 12
  }));
  const stat = (label, val, tone) => /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__v",
    style: tone ? {
      color: tone
    } : null
  }, val), /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__l"
  }, label));
  const clients = accounts.filter(a => a.type === 'Client').length;
  const prospects = accounts.filter(a => a.type === 'Prospect').length;
  const activeMands = accounts.reduce((n, a) => n + a.mandates.filter(m => m.status === 'Active').length, 0);
  const offLimits = accounts.filter(a => a.offLimits).length;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "CRM"), /*#__PURE__*/React.createElement("h1", {
    className: "tm-pscreen__title"
  }, "Companies")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-search-field"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 15,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    placeholder: "Search companies\u2026",
    value: q,
    onChange: e => setQ(e.target.value)
  })), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Type",
    options: types,
    value: filterType,
    onChange: setFilterType
  }), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Owner",
    options: owners,
    value: filterOwner,
    onChange: setFilterOwner
  }), /*#__PURE__*/React.createElement(Button, {
    onClick: () => window.showToast && window.showToast('New company — coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 16
  }), "New company"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stats"
  }, stat('Companies', accounts.length), stat('Clients', clients, 'var(--success-fg, #15803d)'), stat('Prospects', prospects, '#1d4ed8'), stat('Active mandates', activeMands), stat('Off-limits', offLimits, offLimits ? '#b91c1c' : null)), /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-acctable__head"
  }, /*#__PURE__*/React.createElement(SortH, {
    k: "name"
  }, "Company"), /*#__PURE__*/React.createElement(SortH, {
    k: "type"
  }, "Type"), /*#__PURE__*/React.createElement("span", null, "Owner"), /*#__PURE__*/React.createElement(SortH, {
    k: "strength"
  }, "Relationship"), /*#__PURE__*/React.createElement(SortH, {
    k: "mandates",
    right: true
  }, "Mandates"), /*#__PURE__*/React.createElement(SortH, {
    k: "people",
    right: true
  }, "People"), /*#__PURE__*/React.createElement(SortH, {
    k: "lastActivity",
    right: true
  }, "Last activity")), sorted.map(a => {
    const fg = tmAccountFirmographics(a.name);
    return /*#__PURE__*/React.createElement("div", {
      key: a.id,
      className: "tm-acctable__row",
      onClick: () => onSelectAccount && onSelectAccount(a)
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-acctable__name"
    }, /*#__PURE__*/React.createElement(AccountAvatar, {
      name: a.name,
      size: 30
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontWeight: 600,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, a.name), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: 'var(--muted-foreground)',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, fg.sector, " \xB7 ", fg.city))), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(AccountTypePill, {
      type: a.type
    })), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("span", {
      className: "tm-pl__av",
      style: {
        width: 24,
        height: 24,
        fontSize: 10
      }
    }, a.owner)), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(StrengthMeter, {
      strength: a.strength,
      showLabel: true
    })), /*#__PURE__*/React.createElement("span", {
      className: "tm-r",
      style: {
        fontVariantNumeric: 'tabular-nums'
      }
    }, a.activeMandates > 0 ? /*#__PURE__*/React.createElement("span", {
      style: {
        fontWeight: 600
      }
    }, a.activeMandates, /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)',
        fontWeight: 400
      }
    }, " active")) : a.placements > 0 ? /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)'
      }
    }, a.placements, " placed") : /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)'
      }
    }, "\u2014")), /*#__PURE__*/React.createElement("span", {
      className: "tm-r",
      style: {
        fontVariantNumeric: 'tabular-nums',
        fontWeight: 600
      }
    }, a.peopleCount || /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)',
        fontWeight: 400
      }
    }, "\u2014")), /*#__PURE__*/React.createElement("span", {
      className: "tm-r",
      style: {
        color: 'var(--muted-foreground)',
        fontSize: 12
      }
    }, formatAge(a.lastActivityDays)));
  }), sorted.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable__empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 20,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, "No companies match your filters.")))));
}

// ─────────────────────────────────────────────────────────────────────────────
// Account record
// ─────────────────────────────────────────────────────────────────────────────
const ACC_ACT_META = {
  note: {
    icon: 'StickyNote',
    fg: 'var(--muted-foreground)',
    bg: 'var(--muted)'
  },
  call: {
    icon: 'Phone',
    fg: '#1d4ed8',
    bg: 'rgba(37,99,235,.10)'
  },
  email: {
    icon: 'Mail',
    fg: '#7c3aed',
    bg: 'rgba(124,58,237,.10)'
  },
  meeting: {
    icon: 'Calendar',
    fg: 'var(--success-fg,#15803d)',
    bg: 'var(--success-bg,rgba(5,150,105,.10))'
  },
  mandate: {
    icon: 'Briefcase',
    fg: '#b45309',
    bg: 'rgba(245,158,11,.12)'
  }
};
function tmBuildAccountActivity(account) {
  const seed = account.id.split('').reduce((s, c) => s + c.charCodeAt(0), 0);
  const owner = account.owner;
  const out = [];
  if (account.mandates.length) {
    const m = account.mandates[0];
    out.push({
      id: account.id + '-aa0',
      type: 'mandate',
      body: `Mandate “${m.role}” — ${m.stageNote.toLowerCase()}.`,
      author: owner,
      ageDays: account.lastActivityDays
    });
  }
  const tmpl = {
    Client: ['Client review call — confirmed shortlist timing.', 'Sent weekly status report to sponsor.', 'Agreed interview panel dates.'],
    Prospect: ['BD call — walked through capability deck.', 'Sent proposal and fee schedule.', 'Coffee with HR Director; positive signals.'],
    Source: ['Mapped two new commercial leaders here.', 'Catch-up with CHRO on market trends.', 'Refreshed org chart from latest filings.'],
    'Off-limits': ['Logged off-limits exclusion period.', 'Courtesy check-in with former sponsor.']
  }[account.type] || ['Logged a note.'];
  const types = ['call', 'email', 'meeting', 'note'];
  tmpl.forEach((body, i) => out.push({
    id: account.id + '-aa' + (i + 1),
    type: types[(seed + i) % types.length],
    body,
    author: [owner, 'OK', 'SM', 'FO'][(seed + i) % 4],
    ageDays: account.lastActivityDays + (i + 1) * 6 + seed % 5
  }));
  return out;
}
function tmBuildAccountTasks(account) {
  const seed = account.id.split('').reduce((s, c) => s + c.charCodeAt(0), 0);
  const byType = {
    Client: [['Send weekly status report', 0], ['Schedule final panel', 3]],
    Prospect: [['Chase proposal decision', -1], ['Send capability deck', 2]],
    Source: [['Refresh org map', 5], ['Re-engage CHRO', 9]],
    'Off-limits': [['Diarise exclusion lapse', 14]]
  }[account.type] || [['Follow up', 2]];
  return byType.map(([title, due], i) => ({
    id: account.id + '-at' + i,
    title,
    dueDays: due,
    assignee: [account.owner, 'OK', 'SM'][(seed + i) % 3],
    done: false
  }));
}
function AccRow({
  icon,
  label,
  value,
  mono
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 13
  }), label), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val",
    style: mono ? {
      fontVariantNumeric: 'tabular-nums'
    } : null
  }, value));
}
function AccountDetail({
  accountId,
  onBack,
  onOpenContact,
  onGoToPipeline,
  onOpenMandate
}) {
  const account = (window.TM_ACCOUNTS || []).find(a => a.id === accountId);
  if (!account) return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      padding: 48,
      color: 'var(--muted-foreground)'
    }
  }, "Company not found."));
  const fg = tmAccountFirmographics(account.name);
  const people = tmAccountPeople(account);
  const [mandates, setMandates] = React.useState(() => account.mandates.map(m => ({
    ...m
  })));
  const [activities, setActivities] = React.useState(() => tmBuildAccountActivity(account));
  const [tasks, setTasks] = React.useState(() => tmBuildAccountTasks(account));
  const [taskOpen, setTaskOpen] = React.useState(false);
  const [taskDraft, setTaskDraft] = React.useState('');
  const [taskDue, setTaskDue] = React.useState(0);
  const ownerName = {
    LH: 'Layla Hassan',
    OK: 'Omar Khalil',
    SM: 'Sara Mitchell',
    FO: 'Farah Obeid'
  }[account.owner] || account.owner;
  const sinceLabel = account.sinceDays >= 365 ? `${(account.sinceDays / 365).toFixed(1)} yrs` : `${Math.round(account.sinceDays / 30)} mo`;
  const logActivity = type => {
    setActivities(prev => [{
      id: 'aa-' + Date.now(),
      type,
      body: {
        note: 'Note added.',
        call: 'Logged a call.',
        email: 'Logged an email.',
        meeting: 'Logged a meeting.'
      }[type],
      author: account.owner,
      ageDays: 0
    }, ...prev]);
    window.showToast && window.showToast('Activity logged');
  };
  const addTask = () => {
    const v = taskDraft.trim();
    if (!v) return;
    setTasks(ts => [...ts, {
      id: 'at-' + Date.now(),
      title: v,
      dueDays: taskDue,
      assignee: account.owner,
      done: false
    }]);
    setTaskDraft('');
    setTaskDue(0);
    setTaskOpen(false);
    window.showToast && window.showToast('Follow-up added');
  };
  const toggleTask = id => setTasks(ts => ts.map(t => t.id === id ? {
    ...t,
    done: !t.done
  } : t));
  const sortedTasks = [...tasks].sort((a, b) => a.done - b.done || a.dueDays - b.dueDays);
  const openTasks = tasks.filter(t => !t.done).length;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__head",
    style: {
      paddingBottom: account.offLimits ? 16 : 22,
      marginBottom: account.offLimits ? 14 : 28,
      borderBottom: account.offLimits ? 'none' : '1px solid var(--border)'
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn",
    style: {
      width: 30,
      height: 30,
      flexShrink: 0
    },
    onClick: onBack,
    title: "Back to companies"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowLeft",
    size: 16
  })), /*#__PURE__*/React.createElement(AccountAvatar, {
    name: account.name,
    size: 44
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 20,
      fontWeight: 700,
      lineHeight: 1.2,
      display: 'flex',
      alignItems: 'center',
      gap: 9
    }
  }, account.name, /*#__PURE__*/React.createElement(AccountTypePill, {
    type: account.type,
    size: "lg"
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      color: 'var(--muted-foreground)',
      marginTop: 3,
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      flexWrap: 'wrap'
    }
  }, /*#__PURE__*/React.createElement("span", null, fg.sector), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 3
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 12
  }), fg.city, ", ", fg.country), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Globe",
    size: 12
  }), account.website))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginLeft: 'auto',
      display: 'flex',
      gap: 8,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement(StrengthMeter, {
    strength: account.strength,
    showLabel: true
  }), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    onClick: () => logActivity('note')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 14
  }), "Log activity"), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: () => window.showToast && window.showToast('New mandate — coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Briefcase",
    size: 14
  }), "New mandate"))), account.offLimits && /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      padding: '9px 14px',
      borderRadius: 10,
      marginBottom: 22,
      background: 'rgba(220,38,38,.07)',
      border: '1px solid rgba(220,38,38,.18)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldAlert",
    size: 15,
    color: "#b91c1c"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12.5,
      color: '#b91c1c',
      fontWeight: 500
    }
  }, "Off-limits \u2014 ", account.offLimits.reason, ". Executives here must not be approached", account.offLimits.untilDays ? ` for ${Math.round(account.offLimits.untilDays / 30)} more months.` : '.')), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__grid"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 18
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Firmographics"), /*#__PURE__*/React.createElement(AccRow, {
    icon: "Tag",
    label: "Sector",
    value: fg.sector
  }), /*#__PURE__*/React.createElement(AccRow, {
    icon: "MapPin",
    label: "HQ",
    value: `${fg.city}, ${fg.country}`
  }), /*#__PURE__*/React.createElement(AccRow, {
    icon: "DollarSign",
    label: "Revenue",
    value: fg.revenue,
    mono: true
  }), /*#__PURE__*/React.createElement(AccRow, {
    icon: "Users",
    label: "Employees",
    value: fg.employees,
    mono: true
  }), /*#__PURE__*/React.createElement(AccRow, {
    icon: "Landmark",
    label: "Ownership",
    value: account.ownership
  }), /*#__PURE__*/React.createElement(AccRow, {
    icon: "Globe",
    label: "Website",
    value: account.website
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Heart",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Relationship"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserCircle",
    size: 13
  }), "Owner"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val",
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 7
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__av",
    style: {
      width: 20,
      height: 20,
      fontSize: 9
    }
  }, account.owner), ownerName)), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Activity",
    size: 13
  }), "Strength"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val"
  }, /*#__PURE__*/React.createElement(StrengthMeter, {
    strength: account.strength,
    showLabel: true
  }))), /*#__PURE__*/React.createElement(AccRow, {
    icon: "Clock",
    label: "Known for",
    value: sinceLabel
  }), /*#__PURE__*/React.createElement(AccRow, {
    icon: "GitBranch",
    label: "Source",
    value: account.source
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldCheck",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Compliance"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Tag",
    size: 13
  }), "Account type"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val"
  }, /*#__PURE__*/React.createElement(AccountTypePill, {
    type: account.type
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Ban",
    size: 13
  }), "Off-limits"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val",
    style: {
      color: account.offLimits ? '#b91c1c' : 'var(--muted-foreground)'
    }
  }, account.offLimits ? account.offLimits.reason : 'None — open to approach')), account.offLimits && account.offLimits.untilDays && /*#__PURE__*/React.createElement(AccRow, {
    icon: "CalendarClock",
    label: "Lapses in",
    value: `${Math.round(account.offLimits.untilDays / 30)} months`
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Key facts"), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '12px 16px',
      fontSize: 13,
      lineHeight: 1.55,
      color: 'var(--foreground)'
    }
  }, account.keyFacts))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 18
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Briefcase",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Mandates", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 8,
      fontSize: 12,
      fontWeight: 700,
      color: 'var(--foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, mandates.length), /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    style: {
      marginLeft: 'auto'
    },
    onClick: () => window.showToast && window.showToast('New mandate — coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "New mandate"))), mandates.length === 0 ? /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '18px 16px',
      fontSize: 13,
      color: 'var(--muted-foreground)'
    }
  }, "No mandates yet. ", account.type === 'Source' ? 'This company is a talent source, not a client.' : account.type === 'Prospect' ? 'Win the pitch to open the first mandate.' : '') : mandates.map(m => {
    const sm = MANDATE_STATUS_META[m.status] || MANDATE_STATUS_META['On hold'];
    const canonical = (window.tmMandatesForAccount ? window.tmMandatesForAccount(account.id) : []).find(x => x.role === m.role);
    const openIt = () => canonical && onOpenMandate ? onOpenMandate(canonical.id) : onGoToPipeline && onGoToPipeline();
    return /*#__PURE__*/React.createElement("div", {
      key: m.id,
      className: "tm-acc-mandate",
      onClick: openIt
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 13,
        fontWeight: 600,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        display: 'flex',
        alignItems: 'center',
        gap: 6
      }
    }, m.role, canonical && /*#__PURE__*/React.createElement(Icon, {
      name: "ArrowUpRight",
      size: 12,
      color: "var(--muted-foreground)"
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: 'var(--muted-foreground)',
        marginTop: 2,
        display: 'flex',
        gap: 8,
        flexWrap: 'wrap'
      }
    }, /*#__PURE__*/React.createElement("span", null, m.stageNote), m.placed && /*#__PURE__*/React.createElement("span", {
      style: {
        display: 'inline-flex',
        alignItems: 'center',
        gap: 3
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "UserCheck",
      size: 11
    }), m.placed))), /*#__PURE__*/React.createElement("div", {
      style: {
        textAlign: 'right',
        flexShrink: 0
      }
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-pill",
      style: {
        background: sm.bg,
        color: sm.fg,
        fontSize: 10.5
      }
    }, m.status), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: 'var(--muted-foreground)',
        marginTop: 4,
        fontVariantNumeric: 'tabular-nums'
      }
    }, m.fee)));
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "People", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 8,
      fontSize: 12,
      fontWeight: 700,
      color: 'var(--foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, people.length)), people.length === 0 ? /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '18px 16px',
      fontSize: 13,
      color: 'var(--muted-foreground)'
    }
  }, "No people tracked at this company yet.") : people.map((p, i) => {
    const clickable = !!p.contactId;
    return /*#__PURE__*/React.createElement("div", {
      key: i,
      className: cx('tm-acc-person', clickable && 'is-link'),
      onClick: () => clickable && onOpenContact && onOpenContact(p.contactId)
    }, /*#__PURE__*/React.createElement(Avatar, {
      name: p.name,
      size: 28,
      tone: p.isClientSide ? 'neutral' : 'primary'
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 13,
        fontWeight: 500,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, p.name), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11.5,
        color: 'var(--muted-foreground)',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, p.title)), /*#__PURE__*/React.createElement("span", {
      className: cx('tm-acc-relation', p.isClientSide ? 'is-client' : 'is-cand')
    }, p.relation), clickable && /*#__PURE__*/React.createElement(Icon, {
      name: "ChevronRight",
      size: 15,
      color: "var(--muted-foreground)"
    }));
  })), account.appearances.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "In searches", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 8,
      fontSize: 12,
      fontWeight: 700,
      color: 'var(--foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, account.appearances.length)), account.appearances.map((ap, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: "tm-acc-appear",
    onClick: onGoToPipeline
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 7,
      height: 7,
      borderRadius: '50%',
      background: ap.role === 'Source' ? '#7c3aed' : '#b45309',
      flexShrink: 0
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12.5,
      fontWeight: 500,
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, ap.project)), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, ap.role), /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: 'var(--muted)',
      color: 'var(--muted-foreground)',
      fontSize: 10.5
    }
  }, ap.mapped, " mapped")))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "CheckSquare",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Follow-ups", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 8,
      fontSize: 12,
      fontWeight: 700,
      color: openTasks ? 'var(--foreground)' : 'var(--muted-foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, openTasks), /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    style: {
      marginLeft: 'auto'
    },
    onClick: () => setTaskOpen(o => !o)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "Add"))), taskOpen && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '12px 14px',
      borderBottom: '1px solid color-mix(in srgb, var(--border) 55%, transparent)',
      display: 'flex',
      flexDirection: 'column',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("input", {
    autoFocus: true,
    style: {
      border: '1px solid var(--border)',
      borderRadius: 7,
      padding: '6px 10px',
      fontSize: 13,
      fontFamily: 'inherit'
    },
    placeholder: "What needs to happen next?",
    value: taskDraft,
    onChange: e => setTaskDraft(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter') addTask();
      if (e.key === 'Escape') setTaskOpen(false);
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-seg",
    style: {
      flex: 1
    }
  }, [['Today', 0], ['In 3 days', 3], ['Next week', 7]].map(([l, d]) => /*#__PURE__*/React.createElement("button", {
    key: d,
    className: cx(taskDue === d && 'is-on'),
    onClick: () => setTaskDue(d)
  }, l))), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: addTask,
    disabled: !taskDraft.trim()
  }, "Add"))), tasks.length === 0 && !taskOpen ? /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '18px 16px',
      fontSize: 13,
      color: 'var(--muted-foreground)'
    }
  }, "No follow-ups scheduled.") : sortedTasks.map(t => {
    const dm = dueMeta(t.dueDays);
    return /*#__PURE__*/React.createElement("div", {
      key: t.id,
      className: "tm-cd__task-row"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-cd__task-check",
      onClick: () => toggleTask(t.id)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: t.done ? 'CheckSquare' : 'Square',
      size: 16,
      color: t.done ? 'var(--success, #059669)' : 'var(--muted-foreground)'
    })), /*#__PURE__*/React.createElement("span", {
      style: {
        flex: 1,
        fontSize: 13,
        minWidth: 0,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        textDecoration: t.done ? 'line-through' : 'none',
        color: t.done ? 'var(--muted-foreground)' : 'var(--foreground)'
      }
    }, t.title), !t.done && /*#__PURE__*/React.createElement("span", {
      className: "tm-pill",
      style: {
        background: dm.bg,
        color: dm.fg,
        fontSize: 10.5
      }
    }, dm.label), /*#__PURE__*/React.createElement("span", {
      className: "tm-pl__av",
      style: {
        width: 20,
        height: 20,
        fontSize: 9
      }
    }, t.assignee));
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Activity", /*#__PURE__*/React.createElement("div", {
    style: {
      marginLeft: 'auto',
      display: 'flex',
      gap: 5
    }
  }, [['note', 'Note', 'StickyNote'], ['call', 'Call', 'Phone'], ['email', 'Email', 'Mail'], ['meeting', 'Meeting', 'Calendar']].map(([t, label, ic]) => /*#__PURE__*/React.createElement("button", {
    key: t,
    className: "tm-add-pipeline-btn",
    onClick: () => logActivity(t),
    title: 'Log ' + label.toLowerCase()
  }, /*#__PURE__*/React.createElement(Icon, {
    name: ic,
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, label))))), activities.map(a => {
    const m = ACC_ACT_META[a.type] || ACC_ACT_META.note;
    return /*#__PURE__*/React.createElement("div", {
      key: a.id,
      className: "tm-cd__act-item"
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-cd__act-ic",
      style: {
        background: m.bg,
        color: m.fg
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: m.icon,
      size: 13
    })), /*#__PURE__*/React.createElement("div", {
      className: "tm-cd__act-body"
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-cd__act-text"
    }, a.body), /*#__PURE__*/React.createElement("div", {
      className: "tm-cd__act-meta"
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 10,
        color: 'var(--muted-foreground)'
      }
    }, formatAge(a.ageDays)), /*#__PURE__*/React.createElement("span", {
      className: "tm-pl__av",
      style: {
        width: 18,
        height: 18,
        fontSize: 8
      }
    }, a.author))));
  }))))));
}
Object.assign(window, {
  AccountsScreen,
  AccountDetail,
  AccountAvatar,
  AccountTypePill,
  StrengthMeter,
  tmBuildAccountTasks,
  tmBuildAccountActivity
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/accounts.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/app.jsx
try { (() => {
/* global React, ReactDOM, Icon, cx, UnifiedSidebar, TopBar, CommandPalette,
   UniverseView, MapView, TableView, Dashboard, PipelineView, PositionView,
   StrategyView, LongListView, StatusReportView, InternalView, ContactsScreen,
   RightPanel, ProjectsScreen, SettingsScreen, LoginScreen, SignupScreen,
   TM_COMPANIES, TM_PROJECTS, TM_PROJECT_TEAM, TM_SUGGESTIONS, TM_PIPELINE,
   AccountsScreen, AccountDetail, MyDayScreen, BizDevScreen, MandatesScreen, MandateDetail,
   ContactDetail, OffLimitsModal, defaultCriteriaFor,
   MandateSidebar, OverviewScreen, AIAgentScreen, InboxScreen, OutreachScreen,
   PositionScreen, StrategyScreen, ReportsScreen,
   Button, Tooltip, SearchWizardPage, SearchWizardModal */
// ── App: unified sidebar flow ────────────────────────────────────────────────
// Phases: onboarding | overview | workspace | universe | crm | settings

// ── New search modal (popup CommandBar for returning users) ──────────────────
function NewSearchModal({
  onSubmit,
  onClose
}) {
  const [mode, setMode] = React.useState('search');
  const [input, setInput] = React.useState('');
  const suggestions = window.TM_SUGGESTIONS || [];
  const submit = () => {
    onSubmit(input.trim() || suggestions[0] || 'New search');
    onClose();
  };
  const modes = [{
    id: 'search',
    icon: 'Search',
    t: 'Search'
  }, {
    id: 'import',
    icon: 'Upload',
    t: 'Import list'
  }, {
    id: 'brief',
    icon: 'FileText',
    t: 'From brief'
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__overlay tm-fadein",
    onClick: onClose
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-newsearch",
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      marginBottom: 16
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-newsearch__ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 16
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 16,
      fontWeight: 700,
      flex: 1
    }
  }, "New search map"), /*#__PURE__*/React.createElement("button", {
    className: "tm-usidebar__ibtn",
    onClick: onClose
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 16
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbarseg",
    role: "tablist",
    style: {
      marginBottom: 12
    }
  }, modes.map(m => /*#__PURE__*/React.createElement("button", {
    key: m.id,
    role: "tab",
    className: cx('tm-cbarseg__b', mode === m.id && 'is-on'),
    onClick: () => setMode(m.id)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: m.icon,
    size: 14
  }), m.t))), mode === 'search' && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("textarea", {
    className: "tm-cbar__ta",
    rows: 3,
    placeholder: 'Describe the universe you want to map \u2014 e.g. \u201cLargest FMCG distributors across the GCC, founder-led\u201d',
    value: input,
    onChange: e => setInput(e.target.value),
    autoFocus: true,
    onKeyDown: e => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) submit();
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__chips",
    style: {
      marginTop: 8
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cbar__chiplbl"
  }, "Try"), suggestions.slice(0, 3).map(s => /*#__PURE__*/React.createElement("button", {
    key: s,
    className: "tm-chip",
    onClick: () => setInput(s)
  }, s)))), mode === 'import' && /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__drop"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__dropic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 18
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__dropt"
  }, "Drop a company list to import"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__dropd"
  }, "CSV or XLSX \u2014 we map your columns to companies & executives, then extend with AI.")), mode === 'brief' && /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__brief"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__briefdrop",
    style: {
      marginBottom: 8
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 22
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__briefdropt"
  }, "Upload job description or company brief"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__briefdropd"
  }, "Drag and drop, or click to browse"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__brieffmt"
  }, "PDF \xB7 DOCX \xB7 TXT"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      justifyContent: 'flex-end',
      marginTop: 16
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: onClose
  }, "Cancel"), mode === 'search' && /*#__PURE__*/React.createElement(Button, {
    onClick: submit
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14
  }), "Build universe"), mode === 'import' && /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: () => {
      onSubmit('Imported list \u2014 FMCG distributors');
      onClose();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 14
  }), "Choose file"), mode === 'brief' && /*#__PURE__*/React.createElement(Button, {
    onClick: () => {
      onSubmit('From brief \u2014 CFO, FMCG, GCC');
      onClose();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14
  }), "Analyse brief"))));
}

// ── Onboarding screen (new users, no maps yet) ──────────────────────────────
function OnboardingScreen({
  onDiscover
}) {
  const [input, setInput] = React.useState('');
  const suggestions = window.TM_SUGGESTIONS || [];
  const submit = () => onDiscover(input.trim() || suggestions[0]);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-onboard"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-onboard__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-onboard__badge"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14
  }), "AI-powered talent mapping"), /*#__PURE__*/React.createElement("h1", {
    className: "tm-onboard__title"
  }, "Build your first search map"), /*#__PURE__*/React.createElement("p", {
    className: "tm-onboard__sub"
  }, "Describe the market you want to map. AI will identify companies, map decision-makers, and enrich data in real time."), /*#__PURE__*/React.createElement("div", {
    className: "tm-onboard__card"
  }, /*#__PURE__*/React.createElement("textarea", {
    className: "tm-cbar__ta",
    rows: 3,
    autoFocus: true,
    placeholder: 'e.g. \u201cTop FMCG distributors across the GCC, founder-led\u201d',
    value: input,
    onChange: e => setInput(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) submit();
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__chips",
    style: {
      marginTop: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cbar__chiplbl"
  }, "Try"), suggestions.map(s => /*#__PURE__*/React.createElement("button", {
    key: s,
    className: "tm-chip",
    onClick: () => setInput(s)
  }, s))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'flex-end',
      marginTop: 14
    }
  }, /*#__PURE__*/React.createElement(Button, {
    onClick: submit
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 15
  }), "Build universe")))));
}
function SectionPlaceholder({
  title,
  icon
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      textAlign: 'center',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 12
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 36,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 15,
      fontWeight: 600,
      color: 'var(--foreground)'
    }
  }, title), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      color: 'var(--muted-foreground)'
    }
  }, "Coming in the next task")));
}
function OffLimitsModal({
  payload,
  onCancel,
  onOverride
}) {
  if (!payload) return null;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__overlay",
    onClick: onCancel
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__confirm",
    style: {
      width: 460,
      maxWidth: '90vw',
      alignItems: 'flex-start',
      gap: 0
    },
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      marginBottom: 12
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 34,
      height: 34,
      borderRadius: 9,
      background: 'rgba(220,38,38,.10)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      flexShrink: 0
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldAlert",
    size: 18,
    color: "#b91c1c"
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 15,
      fontWeight: 700
    }
  }, "Off-limits company")), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      lineHeight: 1.55,
      color: 'var(--foreground)',
      marginBottom: 10
    }
  }, /*#__PURE__*/React.createElement("b", null, payload.name), " works at ", /*#__PURE__*/React.createElement("b", null, payload.company), ", which is marked off-limits:"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12.5,
      color: '#b91c1c',
      background: 'rgba(220,38,38,.07)',
      border: '1px solid rgba(220,38,38,.18)',
      borderRadius: 8,
      padding: '8px 12px',
      marginBottom: 12,
      width: '100%',
      boxSizing: 'border-box'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Ban",
    size: 12,
    style: {
      marginRight: 5,
      verticalAlign: '-1px'
    }
  }), payload.reason), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      lineHeight: 1.5,
      marginBottom: 18
    }
  }, "Approaching executives here may breach a client agreement. Only a partner should override, and the override is recorded on the candidate."), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      width: '100%'
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: onCancel,
    style: {
      flex: 1
    }
  }, "Cancel"), /*#__PURE__*/React.createElement("button", {
    className: "tm-btn-danger",
    onClick: onOverride,
    style: {
      flex: 1
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldAlert",
    size: 14
  }), "Override & add"))));
}

// ── Main app ─────────────────────────────────────────────────────────────────
function App() {
  // ── Core state ─────────────────────────────────────────────────────────────
  const [phase, setPhase] = React.useState('overview'); // onboarding | overview | workspace | crm | settings
  const [activeMap, setActiveMap] = React.useState(null);
  const [query, setQuery] = React.useState('');
  const [project, setProject] = React.useState('');
  const [companies, setCompanies] = React.useState(TM_COMPANIES);
  const [view, setView] = React.useState('overview');
  const [theme, setTheme] = React.useState('light');
  const [selCompany, setSelCompany] = React.useState(null);
  const [selExec, setSelExec] = React.useState(null);
  const [metric, setMetric] = React.useState('revenue');
  const [showSats, setShowSats] = React.useState(false);
  const [cmd, setCmd] = React.useState(false);
  const [authed, setAuthed] = React.useState(false);
  const [authView, setAuthView] = React.useState('login');
  const [demoUserMode, setDemoUserMode] = React.useState('existing');
  const [resumeUniverse, setResumeUniverse] = React.useState(false);
  const [removedIds, setRemovedIds] = React.useState(() => new Set());
  const [newSearchOpen, setNewSearchOpen] = React.useState(false);

  // ── Sourcing state (lifted so the sidebar persists across view switches) ──
  // statusMap stores per-company triage status across the whole mandate.
  // aiSearches stores all AI sourcing runs inside this mandate — each owns
  // its own query, criteria, and the subset of companies it surfaced.
  // activeSubState routes the sourcing screen between buckets and AI runs:
  //   'aiSearch'    → an AI sourcing run (which one is activeSearchId)
  //   'universe'    → in-universe (approved) bucket — no criteria bar
  //   'shortlisted' → shortlisted bucket — no criteria bar
  //   'declined'    → declined bucket — no criteria bar
  const [statusMap, setStatusMap] = React.useState({});
  const [activeSubState, setActiveSubState] = React.useState('aiSearch');
  const [aiSearches, setAiSearches] = React.useState([]);
  const [activeSearchId, setActiveSearchId] = React.useState(null);
  const [addSearchOpen, setAddSearchOpen] = React.useState(false);
  const activeSearch = aiSearches.find(s => s.id === activeSearchId) || aiSearches[0] || null;
  const activeCriteria = activeSearch?.criteria || (typeof defaultCriteriaFor === 'function' ? defaultCriteriaFor('') : {});
  const setActiveCriteria = newCrit => {
    if (!activeSearch) return;
    setAiSearches(prev => prev.map(s => s.id === activeSearch.id ? {
      ...s,
      criteria: newCrit
    } : s));
  };

  // ── Pipeline ───────────────────────────────────────────────────────────────
  const [pipelineNames, setPipelineNames] = React.useState(() => new Set(TM_PIPELINE.map(e => e.contactName)));
  const [extraPipelineEntries, setExtraPipelineEntries] = React.useState([]);
  const [offLimitsPrompt, setOffLimitsPrompt] = React.useState(null);

  // ── CRM ────────────────────────────────────────────────────────────────────
  const [crmContactId, setCrmContactId] = React.useState(null);
  const [crmTab, setCrmTab] = React.useState('myday');
  const [crmAccountId, setCrmAccountId] = React.useState(null);
  const [crmMandateId, setCrmMandateId] = React.useState(null);
  const [projectTeam, setProjectTeam] = React.useState(() => window.TM_PROJECT_TEAM || []);
  const [projectClients, setProjectClients] = React.useState([]);
  const [sidebarCollapsed, setSidebarCollapsed] = React.useState(() => {
    try {
      return localStorage.getItem('tm_sidebar_collapsed') === '1';
    } catch (e) {
      return false;
    }
  });
  const toggleSidebar = () => setSidebarCollapsed(c => {
    const next = !c;
    try {
      localStorage.setItem('tm_sidebar_collapsed', next ? '1' : '0');
    } catch (e) {}
    return next;
  });

  // ── Derived ────────────────────────────────────────────────────────────────
  const projects = TM_PROJECTS.filter(p => !removedIds.has(p.id));
  const isNewUser = projects.length === 0;

  // Sourcing counts (used by sidebar + screens) ─────────────────────────────
  // 'universe' now means strictly approved (added to universe). Shortlisted
  // and declined are their own buckets — they are NOT counted in universe.
  // aiSourced counts everything still un-triaged across all searches.
  const sourcingCounts = React.useMemo(() => {
    const c = {
      universe: 0,
      shortlisted: 0,
      declined: 0,
      aiSourced: 0
    };
    for (const co of companies) {
      const s = statusMap[co.id];
      if (s === 'declined') c.declined++;else if (s === 'shortlisted') c.shortlisted++;else if (s === 'approved' || s === 'in_universe') c.universe++;else c.aiSourced++;
    }
    return c;
  }, [companies, statusMap]);

  // Per-search untriaged counts (so the sidebar can show how many cards
  // are still waiting to be triaged inside each AI sourcing run).
  const aiSearchesWithCounts = React.useMemo(() => {
    return aiSearches.map(s => {
      const ids = new Set(s.companyIds || []);
      let count = 0;
      for (const co of companies) {
        if (ids.has(co.id) && !statusMap[co.id]) count++;
      }
      return {
        ...s,
        count,
        active: s.id === activeSearchId
      };
    });
  }, [aiSearches, companies, statusMap, activeSearchId]);
  const setStatus = (id, value) => setStatusMap(p => {
    const n = {
      ...p
    };
    if (n[id] === value) delete n[id];else n[id] = value;
    return n;
  });
  const resetSourcingFor = q => {
    const id = 'search-' + Date.now();
    const crit = typeof defaultCriteriaFor === 'function' ? defaultCriteriaFor(q) : {};
    const companyIds = TM_COMPANIES.map(c => c.id);
    // Seed plausible mock triage so sidebar counts feel real out of the gate
    setStatusMap({
      1: 'shortlisted',
      3: 'shortlisted',
      10: 'approved',
      6: 'declined'
    });
    setActiveSubState('aiSearch');
    setAiSearches([{
      id,
      label: (q || 'New search').slice(0, 38),
      query: q,
      criteria: crit,
      companyIds,
      when: 'just now'
    }]);
    setActiveSearchId(id);
  };

  // Add a new AI sourcing run inside the CURRENT mandate (does not create a
  // new mandate). Each run owns its own criteria and surfaces its own subset
  // of companies — but they share the mandate's universe/shortlist/declined.
  const addAISearch = q => {
    if (!q || !q.trim()) return;
    const id = 'search-' + Date.now();
    const crit = typeof defaultCriteriaFor === 'function' ? defaultCriteriaFor(q) : {};
    const all = TM_COMPANIES.map(c => c.id);
    // Deterministic but distinct subset per additional run, so each search
    // surfaces a different (overlapping) slice of the company pool.
    const offset = aiSearches.length;
    const subset = offset === 0 ? all : Array.from(new Set(all.filter((_, i) => (i + offset) % 2 !== 0).concat(all.slice(-3))));
    setAiSearches(prev => [...prev, {
      id,
      label: q.slice(0, 38),
      query: q,
      criteria: crit,
      companyIds: subset,
      when: 'just now'
    }]);
    setActiveSearchId(id);
    setActiveSubState('aiSearch');
    setView('sourcing');
    window.showToast && window.showToast('Started new sourcing run');
  };
  React.useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  // ── Keyboard shortcuts ─────────────────────────────────────────────────────
  React.useEffect(() => {
    const h = e => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setCmd(c => !c);
      }
      if (e.key === 'Escape') {
        setCmd(false);
        setNewSearchOpen(false);
      }
      if (phase === 'workspace' && !e.metaKey && !e.ctrlKey && !/INPUT|TEXTAREA/.test(e.target.tagName)) {
        if (e.key === '1') setView('sourcing');
        if (e.key === '2') setView('map');
        if (e.key === '3') setView('candidates');
        if (e.key === '4') setView('reports');
      }
    };
    document.addEventListener('keydown', h);
    return () => document.removeEventListener('keydown', h);
  }, [phase]);

  // ── Handlers ───────────────────────────────────────────────────────────────
  const goToOverview = () => {
    setActiveMap(null);
    setPhase('overview');
    setView('overview');
    setSelCompany(null);
    setSelExec(null);
  };
  const selectMap = m => {
    setActiveMap(m);
    setProject(m.name.length > 28 ? m.name.slice(0, 28) + '\u2026' : m.name);
    setQuery(m.name);
    setResumeUniverse(true);
    setCompanies(TM_COMPANIES);
    setPhase('workspace');
    setView('overview');
    resetSourcingFor(m.name);
  };
  const startDiscovery = q => {
    setResumeUniverse(false);
    setQuery(q);
    setProject(q.length > 28 ? q.slice(0, 28) + '\u2026' : q);
    setCompanies(TM_COMPANIES);
    setActiveMap({
      id: 'new-' + Date.now(),
      name: q,
      clientId: null,
      companies: TM_COMPANIES.length,
      execs: TM_COMPANIES.reduce((s, c) => s + c.execs.length, 0),
      when: 'just now',
      ageDays: 0
    });
    setPhase('workspace');
    setView('sourcing');
    resetSourcingFor(q);
  };
  const confirmUniverse = ids => {
    const set = new Set(ids);
    const chosen = TM_COMPANIES.filter(c => set.has(c.id));
    setCompanies(chosen.length ? chosen : TM_COMPANIES);
    setView('map');
  };

  // Save draft retained as a no-op for legacy callers; drafts concept removed.
  const saveDraft = () => {};
  const openNewSearch = () => {
    if (isNewUser) {
      setPhase('onboarding');
    } else {
      setNewSearchOpen(true);
    }
  };
  const deleteProjects = ids => setRemovedIds(prev => {
    const n = new Set(prev);
    ids.forEach(i => n.add(i));
    return n;
  });
  const selectCompany = id => {
    setSelCompany(id);
    setSelExec(null);
  };
  const selectExec = (cid, eid) => {
    setSelCompany(cid);
    setSelExec(eid);
  };
  const toggleTheme = () => setTheme(t => t === 'dark' ? 'light' : 'dark');
  const onLogin = () => {
    setAuthed(true);
    setPhase(demoUserMode === 'new' ? 'onboarding' : 'overview');
  };
  const onSignupComplete = () => {
    setAuthed(true);
    setPhase('onboarding');
  };
  const signOut = () => {
    setAuthed(false);
    setAuthView('login');
    setPhase('overview');
  };

  // ── CRM nav ────────────────────────────────────────────────────────────────
  const openCrm = () => {
    setPhase('crm');
    setCrmTab('myday');
    setCrmContactId(null);
    setCrmAccountId(null);
    setCrmMandateId(null);
  };
  const openSettings = () => {
    setPhase('settings');
  };
  React.useEffect(() => {
    window.__goToCrm = openCrm;
    return () => {
      delete window.__goToCrm;
    };
  });
  const goToPipeline = () => {
    if (activeMap) {
      setPhase('workspace');
      setView('outreach');
    }
  };
  const openProjectByName = name => {
    const p = projects.find(x => x.name === name);
    if (p) selectMap(p);
  };
  const openContact = contactId => {
    setPhase('crm');
    setCrmTab('contacts');
    setCrmContactId(contactId);
    setCrmAccountId(null);
  };
  const openAccount = accountId => {
    setPhase('crm');
    setCrmTab('companies');
    setCrmAccountId(accountId);
    setCrmContactId(null);
  };
  const openAccountByName = name => {
    const a = window.tmFindAccountByName && window.tmFindAccountByName(name);
    if (a) openAccount(a.id);
  };
  const openMandate = mandateId => {
    setPhase('crm');
    setCrmTab('searches');
    setCrmMandateId(mandateId);
    setCrmAccountId(null);
    setCrmContactId(null);
  };

  // ── Pipeline add with off-limits check ─────────────────────────────────────
  const doAddToPipeline = ({
    name,
    title,
    company
  }, opts = {}) => {
    if (pipelineNames.has(name)) return;
    setPipelineNames(p => {
      const n = new Set(p);
      n.add(name);
      return n;
    });
    setExtraPipelineEntries(p => [...p, {
      id: 'px-' + Date.now() + '-' + Math.random().toString(36).slice(2, 5),
      contactName: name,
      title,
      company,
      stage: 'Sourced',
      assignees: [window.TM_USER && window.TM_USER.initials || 'YM'],
      ageDays: 0,
      availability: 'Unknown',
      offLimitsOverride: opts.override || false
    }]);
    window.showToast && window.showToast(opts.override ? name + ' added with off-limits override \u2014 logged' : name + ' added to pipeline');
  };
  const addToPipeline = payload => {
    if (pipelineNames.has(payload.name)) return;
    const ol = window.tmOffLimitsFor && window.tmOffLimitsFor(payload.company);
    if (ol) {
      setOffLimitsPrompt({
        ...payload,
        reason: ol.reason
      });
      return;
    }
    doAddToPipeline(payload);
  };
  const selectedCompanyObj = companies.find(c => c.id === selCompany) || null;

  // ── Auth gate ──────────────────────────────────────────────────────────────
  if (!authed) {
    return authView === 'signup' ? /*#__PURE__*/React.createElement(SignupScreen, {
      onComplete: onSignupComplete,
      onGoLogin: () => setAuthView('login')
    }) : /*#__PURE__*/React.createElement(LoginScreen, {
      onLogin: onLogin,
      onGoSignup: () => setAuthView('signup')
    });
  }

  // ── Demo toggle bar ────────────────────────────────────────────────────────
  const demoToggle = /*#__PURE__*/React.createElement("div", {
    className: "sw-demo-bar"
  }, /*#__PURE__*/React.createElement("span", {
    className: "sw-demo-bar__label"
  }, "DEMO MODE"), /*#__PURE__*/React.createElement("div", {
    className: "sw-demo-bar__toggle"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx('sw-demo-bar__btn', demoUserMode === 'new' && 'is-on'),
    onClick: () => {
      setDemoUserMode('new');
      setPhase('onboarding');
    }
  }, "New user"), /*#__PURE__*/React.createElement("button", {
    className: cx('sw-demo-bar__btn', demoUserMode === 'existing' && 'is-on'),
    onClick: () => {
      setDemoUserMode('existing');
      setPhase('overview');
    }
  }, "Existing user")));

  // ── Shared sidebar ─────────────────────────────────────────────────────────
  const sidebar = /*#__PURE__*/React.createElement(UnifiedSidebar, {
    activeMap: activeMap,
    view: view,
    onView: v => {
      if (activeMap) {
        setPhase('workspace');
        setView(v);
      }
    },
    collapsed: sidebarCollapsed,
    onToggle: toggleSidebar,
    maps: projects,
    onSelectMap: selectMap,
    onSelectAll: goToOverview,
    onNewSearch: openNewSearch,
    onSearch: () => setCmd(true),
    onCrm: openCrm,
    crmActive: phase === 'crm',
    onSettings: openSettings,
    settingsActive: phase === 'settings',
    theme: theme,
    onTheme: toggleTheme,
    phase: phase
  });

  // Mandate-scoped sidebar (used inside an open mandate AND on the overview
  // screen — with activeMap=null — so the left menu stays consistent).
  const mandateClient = activeMap && activeMap.clientId && window.getClientName ? window.getClientName(activeMap.clientId) : 'GCC mandate';
  // Pick a map then immediately route to the requested view (used by the
  // "all search maps" picker screen).
  const selectMapToView = (m, v) => {
    setActiveMap(m);
    setProject(m.name.length > 28 ? m.name.slice(0, 28) + '\u2026' : m.name);
    setQuery(m.name);
    setResumeUniverse(true);
    setCompanies(TM_COMPANIES);
    setPhase('workspace');
    setView(v || 'overview');
    setSelCompany(null);
    setSelExec(null);
    resetSourcingFor(m.name);
  };
  const mandateSidebar = /*#__PURE__*/React.createElement(MandateSidebar, {
    view: view,
    onView: v => {
      setSelCompany(null);
      setSelExec(null);
      if (activeMap) {
        setPhase('workspace');
        setView(v);
      } else {
        setPhase('overview');
        setView(v);
      }
    },
    collapsed: sidebarCollapsed,
    onToggle: toggleSidebar,
    mandateName: activeMap ? project || activeMap.name : '',
    clientName: activeMap ? mandateClient : '',
    maps: projects,
    activeMap: activeMap,
    onSelectMandate: selectMap,
    onSelectAll: goToOverview,
    onNewSearch: openNewSearch,
    onBack: goToOverview,
    onSearch: () => setCmd(true),
    sourcingCounts: sourcingCounts,
    activeSubState: activeSubState,
    onSubState: s => {
      setActiveSubState(s);
      setView('sourcing');
    },
    aiSearches: aiSearchesWithCounts,
    activeSearchId: activeSearchId,
    onSelectSearch: id => {
      setActiveSearchId(id);
      setActiveSubState('aiSearch');
      setView('sourcing');
    },
    onAddSearch: () => setAddSearchOpen(true),
    theme: theme,
    onTheme: toggleTheme,
    onCrm: openCrm,
    crmActive: phase === 'crm'
  });
  const cmdPalette = /*#__PURE__*/React.createElement(CommandPalette, {
    open: cmd,
    onClose: () => setCmd(false),
    onView: setView,
    onBack: goToOverview
  });

  // ── Onboarding (new user, no maps) ─────────────────────────────────────────
  if (phase === 'onboarding' || demoUserMode === 'new' && phase === 'overview') {
    return /*#__PURE__*/React.createElement("div", {
      className: "tm-app",
      style: {
        flexDirection: 'column'
      }
    }, demoToggle, /*#__PURE__*/React.createElement(SearchWizardPage, {
      onSubmit: q => {
        startDiscovery(q);
        setDemoUserMode('existing');
      }
    }), cmdPalette);
  }

  // ── Overview (all search maps) ─────────────────────────────────────────────
  if (phase === 'overview') {
    // Routing while in the "All search maps" context:
    //   overview  → account-wide aggregate dashboard
    //   aiAgent   → generic AI Agent screen (no mandate)
    //   sourcing / position / strategy → picker so the user chooses a map
    //   map / candidates / outreach / inbox / reports → disabled (sidebar blocks)
    let body;
    if (view === 'aiAgent') {
      body = /*#__PURE__*/React.createElement(AIAgentScreen, {
        mandateName: "",
        clientName: ""
      });
    } else if (['position', 'strategy', 'sourcing'].includes(view)) {
      body = /*#__PURE__*/React.createElement(window.AllSearchesPicker, {
        view: view,
        projects: projects,
        onSelect: m => selectMapToView(m, view),
        onNewSearch: openNewSearch
      });
    } else {
      body = /*#__PURE__*/React.createElement(window.AllSearchesOverview, {
        projects: projects,
        onOpen: selectMap,
        onNewSearch: openNewSearch,
        onView: v => setView(v)
      });
    }
    return /*#__PURE__*/React.createElement("div", {
      className: "tm-app"
    }, demoToggle, mandateSidebar, body, cmdPalette, newSearchOpen && /*#__PURE__*/React.createElement(SearchWizardModal, {
      onSubmit: startDiscovery,
      onClose: () => setNewSearchOpen(false)
    }));
  }

  // Universe phase removed — sourcing is now a view inside workspace.

  // ── CRM ────────────────────────────────────────────────────────────────────
  if (phase === 'crm') {
    const onTab = t => {
      setCrmTab(t);
      setCrmContactId(null);
      setCrmAccountId(null);
      setCrmMandateId(null);
    };
    let crmBody;
    if (crmTab === 'myday') {
      crmBody = /*#__PURE__*/React.createElement(MyDayScreen, {
        onOpenContact: openContact,
        onOpenAccount: openAccount
      });
    } else if (crmTab === 'searches') {
      crmBody = crmMandateId ? /*#__PURE__*/React.createElement(MandateDetail, {
        mandateId: crmMandateId,
        onBack: () => setCrmMandateId(null),
        onOpenAccount: openAccount,
        onOpenBizDev: () => onTab('bizdev'),
        onGoToPipeline: goToPipeline,
        onOpenProject: openProjectByName
      }) : /*#__PURE__*/React.createElement(MandatesScreen, {
        onSelectMandate: openMandate
      });
    } else if (crmTab === 'bizdev') {
      crmBody = /*#__PURE__*/React.createElement(BizDevScreen, {
        onOpenAccount: openAccount,
        onOpenMandate: openMandate
      });
    } else if (crmTab === 'companies') {
      crmBody = crmAccountId ? /*#__PURE__*/React.createElement(AccountDetail, {
        accountId: crmAccountId,
        onBack: () => setCrmAccountId(null),
        onOpenContact: openContact,
        onGoToPipeline: goToPipeline,
        onOpenMandate: openMandate
      }) : /*#__PURE__*/React.createElement(AccountsScreen, {
        onSelectAccount: a => openAccount(a.id)
      });
    } else {
      crmBody = crmContactId ? /*#__PURE__*/React.createElement(ContactDetail, {
        contactId: crmContactId,
        onBack: () => setCrmContactId(null),
        onGoToPipeline: goToPipeline,
        onOpenAccount: openAccountByName
      }) : /*#__PURE__*/React.createElement(ContactsScreen, {
        onSelectContact: c => openContact(c.id),
        onAddToPipeline: addToPipeline
      });
    }
    const TABS = [['myday', 'My Day', 'Sunrise'], ['contacts', 'Contacts', 'Users'], ['companies', 'Companies', 'Building2'], ['searches', 'Searches', 'Target'], ['bizdev', 'Business Dev', 'TrendingUp']];
    return /*#__PURE__*/React.createElement("div", {
      className: "tm-app"
    }, sidebar, /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-crmtabs"
    }, TABS.map(([id, label, icon]) => /*#__PURE__*/React.createElement("button", {
      key: id,
      className: cx('tm-crmtab', crmTab === id && 'is-on'),
      onClick: () => onTab(id)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: icon,
      size: 15
    }), label))), crmBody), cmdPalette, newSearchOpen && /*#__PURE__*/React.createElement(SearchWizardModal, {
      onSubmit: startDiscovery,
      onClose: () => setNewSearchOpen(false)
    }));
  }

  // ── Settings ───────────────────────────────────────────────────────────────
  if (phase === 'settings') {
    return /*#__PURE__*/React.createElement("div", {
      className: "tm-app"
    }, sidebar, /*#__PURE__*/React.createElement(SettingsScreen, {
      theme: theme,
      onTheme: setTheme,
      onSignOut: signOut
    }), cmdPalette);
  }

  // ── Workspace (mandate selected) ──────────────────────────────────────────
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-app"
  }, mandateSidebar, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      display: 'flex',
      flexDirection: 'column',
      minWidth: 0,
      position: 'relative'
    }
  }, view === 'overview' && /*#__PURE__*/React.createElement(OverviewScreen, {
    mandateName: project,
    clientName: mandateClient,
    companies: companies,
    sourcingCounts: sourcingCounts,
    onView: v => {
      setView(v);
      setSelCompany(null);
      setSelExec(null);
    }
  }), view === 'aiAgent' && /*#__PURE__*/React.createElement(AIAgentScreen, {
    mandateName: project,
    clientName: mandateClient
  }), view === 'position' && /*#__PURE__*/React.createElement(PositionScreen, {
    mandateName: project
  }), view === 'strategy' && /*#__PURE__*/React.createElement(StrategyScreen, {
    mandateName: project
  }), view === 'sourcing' && /*#__PURE__*/React.createElement(UniverseView, {
    query: activeSearch && activeSearch.query || query,
    resume: resumeUniverse,
    mandateName: project,
    clientName: mandateClient,
    companies: companies,
    statusMap: statusMap,
    setStatus: setStatus,
    activeSubState: activeSubState,
    onSubState: setActiveSubState,
    criteria: activeCriteria,
    setCriteria: setActiveCriteria,
    activeSearch: activeSearch,
    aiSearches: aiSearchesWithCounts,
    onSelectSearch: id => {
      setActiveSearchId(id);
      setActiveSubState('aiSearch');
    },
    onAddSearch: () => setAddSearchOpen(true),
    onConfirm: confirmUniverse
  }), (view === 'map' || view === 'candidates' || view === 'table') && /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-fadein",
    style: {
      flex: 1,
      display: 'flex',
      flexDirection: 'column',
      minHeight: 0
    }
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: view === 'map' ? 'Map view' : 'Candidates',
    title: project,
    subtitle: view === 'map' ? `${companies.length} companies positioned by ${metric}` : `${companies.reduce((s, c) => s + c.execs.length, 0)} executives across ${companies.length} companies`
  }, view === 'map' && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("select", {
    className: "tm-ws__select",
    value: metric,
    onChange: e => setMetric(e.target.value)
  }, /*#__PURE__*/React.createElement("option", {
    value: "revenue"
  }, "Scale by revenue"), /*#__PURE__*/React.createElement("option", {
    value: "employees"
  }, "Scale by employees")), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn",
    onClick: () => setShowSats(s => !s)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: showSats ? 'EyeOff' : 'Eye',
    size: 13
  }), /*#__PURE__*/React.createElement("span", null, showSats ? 'Hide execs' : 'Show execs'))), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 13
  }), " Export")), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      display: 'flex',
      minHeight: 0,
      position: 'relative'
    }
  }, view === 'map' && /*#__PURE__*/React.createElement(MapView, {
    companies: companies,
    selectedCompany: selCompany,
    selectedExec: selExec,
    scalingMetric: metric,
    showSats: showSats,
    theme: theme,
    onSelectCompany: selectCompany,
    onSelectExec: selectExec
  }), (view === 'candidates' || view === 'table') && /*#__PURE__*/React.createElement(TableView, {
    companies: companies,
    onSelectCompany: selectCompany,
    onSelectExec: selectExec,
    pipelineNames: pipelineNames,
    onAddToPipeline: addToPipeline,
    onGoToPipeline: goToPipeline
  }), view === 'map' && selectedCompanyObj && /*#__PURE__*/React.createElement(RightPanel, {
    company: selectedCompanyObj,
    scalingMetric: metric,
    onMetric: setMetric,
    onClose: () => {
      setSelCompany(null);
      setSelExec(null);
    },
    onSelectExec: selectExec,
    pipelineNames: pipelineNames,
    onAddToPipeline: addToPipeline,
    onGoToPipeline: goToPipeline
  }))), view === 'outreach' && /*#__PURE__*/React.createElement(OutreachScreen, {
    mandateName: project,
    companies: companies,
    sourcingCounts: sourcingCounts
  }), view === 'inbox' && /*#__PURE__*/React.createElement(InboxScreen, {
    mandateName: project
  }), (view === 'reports' || view === 'dashboard') && /*#__PURE__*/React.createElement(ReportsScreen, {
    companies: companies,
    mandateName: project
  }), view === 'settings' && /*#__PURE__*/React.createElement(SettingsScreen, {
    theme: theme,
    onTheme: setTheme,
    onSignOut: signOut
  })), cmdPalette, newSearchOpen && /*#__PURE__*/React.createElement(SearchWizardModal, {
    onSubmit: startDiscovery,
    onClose: () => setNewSearchOpen(false)
  }), addSearchOpen && /*#__PURE__*/React.createElement(SearchWizardModal, {
    onSubmit: q => {
      addAISearch(q);
      setAddSearchOpen(false);
    },
    onClose: () => setAddSearchOpen(false)
  }), /*#__PURE__*/React.createElement(OffLimitsModal, {
    payload: offLimitsPrompt,
    onCancel: () => setOffLimitsPrompt(null),
    onOverride: () => {
      doAddToPipeline(offLimitsPrompt, {
        override: true
      });
      setOffLimitsPrompt(null);
    }
  }));
}
ReactDOM.createRoot(document.getElementById('root')).render(/*#__PURE__*/React.createElement(App, null));
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/app.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/auth.jsx
try { (() => {
/* global React, Icon, Button, cx */
// ── Auth: split-screen login + multi-step organization signup ────────────────
// SSO glyphs are NEUTRAL monograms, not real Google/Microsoft logos (trademarks).
// Swap in official provider marks in production.

function Brandmark({
  onDark
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: onDark ? 'wmk' : 'tm-auth__brandmark'
  }, /*#__PURE__*/React.createElement("span", {
    className: "mk"
  }, "AL"), /*#__PURE__*/React.createElement("span", {
    className: "wm"
  }, "ALAC", /*#__PURE__*/React.createElement("small", null, "Global Talent Map")));
}
function AuthMapVisual() {
  const bubbles = [{
    x: 26,
    y: 36,
    d: 60,
    c: 'hsl(222 55% 32%)',
    o: .5
  }, {
    x: 74,
    y: 30,
    d: 40,
    c: 'hsl(222 55% 32%)',
    o: .42
  }, {
    x: 80,
    y: 58,
    d: 48,
    c: 'hsl(222 55% 32%)',
    o: .4
  }, {
    x: 18,
    y: 62,
    d: 26,
    c: 'hsl(222 55% 32%)',
    o: .35
  }, {
    x: 50,
    y: 72,
    d: 30,
    c: 'hsl(222 55% 32%)',
    o: .35
  }];
  const labels = [{
    t: 'Saudi Arabia',
    x: 40,
    y: 50
  }, {
    t: 'U.A.E.',
    x: 82,
    y: 44
  }, {
    t: 'Gulf',
    x: 58,
    y: 22
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-authmap"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-authmap__grid"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-authmap__glow"
  }), labels.map(l => /*#__PURE__*/React.createElement("div", {
    className: "tm-authmap__rlabel",
    key: l.t,
    style: {
      left: l.x + '%',
      top: l.y + '%'
    }
  }, l.t)), bubbles.map((b, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: "tm-authmap__bubble",
    style: {
      left: b.x + '%',
      top: b.y + '%',
      width: b.d,
      height: b.d,
      background: b.c,
      opacity: b.o
    }
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      left: '48%',
      top: '7%',
      width: 250,
      height: 150
    }
  }, /*#__PURE__*/React.createElement("svg", {
    width: "250",
    height: "150",
    style: {
      position: 'absolute',
      inset: 0,
      overflow: 'visible'
    }
  }, /*#__PURE__*/React.createElement("circle", {
    cx: "30",
    cy: "56",
    r: "22",
    fill: "hsl(35 92% 50%)",
    opacity: "0.9"
  }), /*#__PURE__*/React.createElement("line", {
    x1: "50",
    y1: "52",
    x2: "84",
    y2: "44",
    stroke: "hsl(35 92% 50%)",
    strokeWidth: "1.5",
    strokeDasharray: "3 2",
    opacity: "0.7"
  }), /*#__PURE__*/React.createElement("line", {
    x1: "48",
    y1: "62",
    x2: "96",
    y2: "96",
    stroke: "hsl(35 92% 50%)",
    strokeWidth: "1.5",
    strokeDasharray: "3 2",
    opacity: "0.55"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-authmap__pill",
    style: {
      left: 86,
      top: 44
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "av"
  }, "AH"), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("span", {
    className: "nm"
  }, "Amira Haddad"), /*#__PURE__*/React.createElement("br", null), /*#__PURE__*/React.createElement("span", {
    className: "ti"
  }, "Group CEO"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-authmap__pill is-child",
    style: {
      left: 98,
      top: 96
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "av"
  }, "RK"), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("span", {
    className: "nm"
  }, "Rami Khoury"), /*#__PURE__*/React.createElement("br", null), /*#__PURE__*/React.createElement("span", {
    className: "ti"
  }, "CFO")))));
}
function BrandPanel({
  variant
}) {
  const copy = variant === 'signup' ? {
    h: 'Build your talent universe in minutes.',
    p: 'Set up your workspace, invite your team, and start mapping the executives that move your markets.'
  } : {
    h: 'Map the talent that moves markets.',
    p: 'AI-driven market intelligence and executive search, visualised across the Gulf and beyond.'
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-auth__brand"
  }, /*#__PURE__*/React.createElement(AuthMapVisual, null), /*#__PURE__*/React.createElement(Brandmark, {
    onDark: true
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-auth__copy"
  }, /*#__PURE__*/React.createElement("h2", null, copy.h), /*#__PURE__*/React.createElement("p", null, copy.p)), /*#__PURE__*/React.createElement("div", {
    className: "tm-auth__quote"
  }, /*#__PURE__*/React.createElement("p", null, "\u201CWe compress weeks of desk research into an afternoon. The map is how our consultants think now.\u201D"), /*#__PURE__*/React.createElement("div", {
    className: "who"
  }, /*#__PURE__*/React.createElement("span", {
    className: "av"
  }, "SL"), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "nm"
  }, "Sami Laremi"), /*#__PURE__*/React.createElement("div", {
    className: "ti"
  }, "Managing Partner, ALAC")))));
}
function SsoButtons({
  verb
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-sso"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-sso-btn"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sso-glyph",
    style: {
      background: '#1a2233'
    }
  }, "G"), verb, " with Google"), /*#__PURE__*/React.createElement("button", {
    className: "tm-sso-btn"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sso-glyph",
    style: {
      background: '#1a2233'
    }
  }, "M"), verb, " with Microsoft"));
}
function PasswordField({
  value,
  onChange,
  placeholder = '••••••••',
  label,
  extra
}) {
  const [show, setShow] = React.useState(false);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-afield"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-alabel"
  }, /*#__PURE__*/React.createElement("span", null, label), extra), /*#__PURE__*/React.createElement("div", {
    className: "tm-pw"
  }, /*#__PURE__*/React.createElement("input", {
    className: "tm-ainput",
    type: show ? 'text' : 'password',
    value: value,
    onChange: onChange,
    placeholder: placeholder
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-pw__toggle",
    onClick: () => setShow(s => !s),
    type: "button"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: show ? 'EyeOff' : 'Eye',
    size: 16
  }))));
}

// ── Login ────────────────────────────────────────────────────────────────────
function LoginScreen({
  onLogin,
  onGoSignup
}) {
  const [email, setEmail] = React.useState('');
  const [pw, setPw] = React.useState('');
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-auth"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-auth__form"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-auth__card"
  }, /*#__PURE__*/React.createElement(Brandmark, null), /*#__PURE__*/React.createElement("h1", {
    className: "tm-auth__title"
  }, "Welcome back"), /*#__PURE__*/React.createElement("p", {
    className: "tm-auth__sub"
  }, "Sign in to your ALAC workspace."), /*#__PURE__*/React.createElement(SsoButtons, {
    verb: "Continue"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-or"
  }, /*#__PURE__*/React.createElement("span", null, "or")), /*#__PURE__*/React.createElement("div", {
    className: "tm-afield"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-alabel"
  }, /*#__PURE__*/React.createElement("span", null, "Work email")), /*#__PURE__*/React.createElement("input", {
    className: "tm-ainput",
    type: "email",
    placeholder: "you@company.com",
    value: email,
    onChange: e => setEmail(e.target.value)
  })), /*#__PURE__*/React.createElement(PasswordField, {
    label: "Password",
    value: pw,
    onChange: e => setPw(e.target.value),
    extra: /*#__PURE__*/React.createElement("span", {
      className: "tm-link-inline"
    }, "Forgot password?")
  }), /*#__PURE__*/React.createElement(Button, {
    className: "tm-auth__btn",
    onClick: onLogin
  }, "Sign in"), /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    className: "tm-auth__btn",
    onClick: onLogin
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "KeyRound",
    size: 16
  }), "Sign in with SSO"), /*#__PURE__*/React.createElement("p", {
    className: "tm-auth__foot"
  }, "New to ALAC Partners? ", /*#__PURE__*/React.createElement("span", {
    className: "tm-link-inline",
    onClick: onGoSignup
  }, "Create an organization")), /*#__PURE__*/React.createElement("p", {
    className: "tm-auth__legal"
  }, "By continuing you agree to our ", /*#__PURE__*/React.createElement("a", null, "Terms"), " and ", /*#__PURE__*/React.createElement("a", null, "Privacy Policy"), "."))), /*#__PURE__*/React.createElement(BrandPanel, {
    variant: "login"
  }));
}

// ── Signup (create organization) ─────────────────────────────────────────────
function SignupScreen({
  onComplete,
  onGoLogin
}) {
  const [step, setStep] = React.useState(0);
  const [name, setName] = React.useState('');
  const [email, setEmail] = React.useState('');
  const [pw, setPw] = React.useState('');
  const [org, setOrg] = React.useState('');
  const [slug, setSlug] = React.useState('');
  const [size, setSize] = React.useState('2–10');
  const [region, setRegion] = React.useState('Middle East (GCC)');
  const [invites, setInvites] = React.useState(['', '', '']);
  const [agree, setAgree] = React.useState(false);
  const steps = ['Account', 'Organization', 'Invite team'];
  const slugFrom = s => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-auth"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-auth__form"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-auth__card"
  }, /*#__PURE__*/React.createElement(Brandmark, null), /*#__PURE__*/React.createElement("div", {
    className: "tm-steps"
  }, steps.map((s, i) => /*#__PURE__*/React.createElement(React.Fragment, {
    key: s
  }, /*#__PURE__*/React.createElement("div", {
    className: cx('tm-step', i === step && 'is-on', i < step && 'is-done')
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-step__n"
  }, i < step ? /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 13
  }) : i + 1), /*#__PURE__*/React.createElement("span", {
    className: "tm-step__l"
  }, s)), i < steps.length - 1 && /*#__PURE__*/React.createElement("span", {
    className: "tm-step__bar"
  })))), step === 0 && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("h1", {
    className: "tm-auth__title"
  }, "Create your account"), /*#__PURE__*/React.createElement("p", {
    className: "tm-auth__sub"
  }, "Start your 14-day trial. No card required."), /*#__PURE__*/React.createElement(SsoButtons, {
    verb: "Sign up"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-or"
  }, /*#__PURE__*/React.createElement("span", null, "or")), /*#__PURE__*/React.createElement("div", {
    className: "tm-afield"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-alabel"
  }, /*#__PURE__*/React.createElement("span", null, "Full name")), /*#__PURE__*/React.createElement("input", {
    className: "tm-ainput",
    value: name,
    onChange: e => setName(e.target.value),
    placeholder: "Yara Mansour"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-afield"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-alabel"
  }, /*#__PURE__*/React.createElement("span", null, "Work email")), /*#__PURE__*/React.createElement("input", {
    className: "tm-ainput",
    type: "email",
    value: email,
    onChange: e => setEmail(e.target.value),
    placeholder: "you@company.com"
  })), /*#__PURE__*/React.createElement(PasswordField, {
    label: "Password",
    value: pw,
    onChange: e => setPw(e.target.value),
    placeholder: "At least 8 characters"
  }), /*#__PURE__*/React.createElement(Button, {
    className: "tm-auth__btn",
    disabled: !email.includes('@'),
    onClick: () => {
      if (!slug) setSlug(slugFrom(name || 'workspace'));
      setStep(1);
    }
  }, "Continue"), /*#__PURE__*/React.createElement("p", {
    className: "tm-auth__foot"
  }, "Already have an account? ", /*#__PURE__*/React.createElement("span", {
    className: "tm-link-inline",
    onClick: onGoLogin
  }, "Sign in"))), step === 1 && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("h1", {
    className: "tm-auth__title"
  }, "Set up your organization"), /*#__PURE__*/React.createElement("p", {
    className: "tm-auth__sub"
  }, "This is the shared workspace your team will join."), /*#__PURE__*/React.createElement("div", {
    className: "tm-afield"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-alabel"
  }, /*#__PURE__*/React.createElement("span", null, "Organization name")), /*#__PURE__*/React.createElement("input", {
    className: "tm-ainput",
    value: org,
    onChange: e => {
      setOrg(e.target.value);
      setSlug(slugFrom(e.target.value));
    },
    placeholder: "ALAC Partners"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-afield"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-alabel"
  }, /*#__PURE__*/React.createElement("span", null, "Workspace URL")), /*#__PURE__*/React.createElement("div", {
    className: "tm-prefix"
  }, /*#__PURE__*/React.createElement("input", {
    value: slug,
    onChange: e => setSlug(slugFrom(e.target.value)),
    placeholder: "alac-partners"
  }), /*#__PURE__*/React.createElement("span", {
    className: "sfx"
  }, ".talentmap.app"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-afield"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-alabel"
  }, /*#__PURE__*/React.createElement("span", null, "Team size")), /*#__PURE__*/React.createElement("select", {
    className: "tm-ainput",
    value: size,
    onChange: e => setSize(e.target.value),
    style: {
      cursor: 'pointer'
    }
  }, /*#__PURE__*/React.createElement("option", null, "Just me"), /*#__PURE__*/React.createElement("option", null, "2\u201310"), /*#__PURE__*/React.createElement("option", null, "11\u201350"), /*#__PURE__*/React.createElement("option", null, "51\u2013200"), /*#__PURE__*/React.createElement("option", null, "200+"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-afield"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-alabel"
  }, /*#__PURE__*/React.createElement("span", null, "Primary region")), /*#__PURE__*/React.createElement("select", {
    className: "tm-ainput",
    value: region,
    onChange: e => setRegion(e.target.value),
    style: {
      cursor: 'pointer'
    }
  }, /*#__PURE__*/React.createElement("option", null, "Middle East (GCC)"), /*#__PURE__*/React.createElement("option", null, "North Africa"), /*#__PURE__*/React.createElement("option", null, "Europe"), /*#__PURE__*/React.createElement("option", null, "Global"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 10,
      marginTop: 6
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: () => setStep(0),
    style: {
      flex: '0 0 auto'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowLeft",
    size: 16
  })), /*#__PURE__*/React.createElement(Button, {
    className: "tm-auth__btn",
    style: {
      marginTop: 0,
      flex: 1
    },
    disabled: !org.trim(),
    onClick: () => setStep(2)
  }, "Continue"))), step === 2 && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("h1", {
    className: "tm-auth__title"
  }, "Invite your team"), /*#__PURE__*/React.createElement("p", {
    className: "tm-auth__sub"
  }, "Add colleagues now, or do it later from Settings."), invites.map((v, i) => /*#__PURE__*/React.createElement("div", {
    className: "tm-afield",
    key: i,
    style: {
      marginBottom: 10
    }
  }, /*#__PURE__*/React.createElement("input", {
    className: "tm-ainput",
    type: "email",
    value: v,
    placeholder: "name@company.com",
    onChange: e => setInvites(arr => arr.map((x, j) => j === i ? e.target.value : x))
  }))), /*#__PURE__*/React.createElement("button", {
    className: "tm-link-inline",
    style: {
      fontSize: 13
    },
    onClick: () => setInvites(a => [...a, ''])
  }, "+ Add another"), /*#__PURE__*/React.createElement("label", {
    className: cx('tm-check', agree && 'on'),
    style: {
      margin: '18px 0 16px'
    },
    onClick: () => setAgree(a => !a)
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-check__box"
  }, agree && /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 12
  })), /*#__PURE__*/React.createElement("span", null, "I agree to the Terms of Service and Privacy Policy.")), /*#__PURE__*/React.createElement(Button, {
    className: "tm-auth__btn",
    style: {
      marginTop: 0
    },
    disabled: !agree,
    onClick: onComplete
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 16
  }), "Create workspace"), /*#__PURE__*/React.createElement("p", {
    className: "tm-auth__foot"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-link-inline",
    onClick: onComplete
  }, "Skip for now"))))), /*#__PURE__*/React.createElement(BrandPanel, {
    variant: "signup"
  }));
}
Object.assign(window, {
  LoginScreen,
  SignupScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/auth.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/chrome.jsx
try { (() => {
/* global React, Icon, Button, Tooltip, cx, DraftBadge */
// ── Chrome: unified sidebar, top bar, command palette ────────────────────────
// Single sidebar replaces the old Rail + ProjectSidebar pair.

const TM_SECTIONS = [{
  id: 'dashboard',
  icon: 'LayoutDashboard',
  label: 'Dashboard',
  matchViews: ['dashboard']
}, {
  id: 'position',
  icon: 'Briefcase',
  label: 'Position',
  matchViews: ['position']
}, {
  id: 'strategy',
  icon: 'Target',
  label: 'Strategy',
  matchViews: ['strategy']
}, {
  id: 'map',
  icon: 'Map',
  label: 'Map',
  matchViews: ['map']
}, {
  id: 'table',
  icon: 'Users',
  label: 'Candidates',
  matchViews: ['table']
}, {
  id: 'longlist',
  icon: 'ListChecks',
  label: 'Long list',
  matchViews: ['longlist']
}, {
  id: 'pipeline',
  icon: 'KanbanSquare',
  label: 'Pipeline',
  matchViews: ['pipeline']
}, null, {
  id: 'statusreport',
  icon: 'FileText',
  label: 'Status report',
  matchViews: ['statusreport']
}, {
  id: 'internal',
  icon: 'Lock',
  label: 'Internal',
  matchViews: ['internal']
}];

// ── Map picker dropdown ──────────────────────────────────────────────────────
// Rendered as a portal on document.body so it escapes the sidebar's overflow:hidden
function MapPickerDropdown({
  maps,
  activeMap,
  onSelect,
  onSelectAll,
  onClose,
  onNewSearch,
  anchorRect
}) {
  const gbc = window.groupByClient || function (m) {
    return {
      clientGroups: [],
      unassigned: m
    };
  };
  const {
    clientGroups,
    unassigned
  } = gbc(maps);
  const [expanded, setExpanded] = React.useState(() => {
    const auto = new Set();
    clientGroups.forEach(cg => {
      if (activeMap && cg.maps.some(m => m.id === activeMap.id)) auto.add(cg.clientId);else if (cg.maps.some(m => m.draft || m.active)) auto.add(cg.clientId);
    });
    if (auto.size === 0 && clientGroups.length > 0) auto.add(clientGroups[0].clientId);
    return auto;
  });
  const toggle = id => setExpanded(s => {
    const n = new Set(s);
    n.has(id) ? n.delete(id) : n.add(id);
    return n;
  });
  const MapRow = m => /*#__PURE__*/React.createElement("button", {
    key: m.id,
    className: cx('tm-mappick__map', activeMap && activeMap.id === m.id && 'is-active'),
    onClick: () => {
      onSelect(m);
      onClose();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, m.name), m.draft && /*#__PURE__*/React.createElement(DraftBadge, null));

  // Use fixed positioning derived from the anchor button's bounding rect
  const dropStyle = anchorRect ? {
    position: 'fixed',
    left: anchorRect.left,
    top: anchorRect.bottom + 4,
    zIndex: 101
  } : {
    position: 'fixed',
    left: 8,
    top: 60,
    zIndex: 101
  };
  const content = /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__scrim",
    onClick: onClose
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__drop",
    style: dropStyle
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__head"
  }, "Switch search map"), /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__list"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx('tm-mappick__all', !activeMap && 'is-active'),
    onClick: () => {
      onSelectAll();
      onClose();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Layers",
    size: 14
  }), /*#__PURE__*/React.createElement("span", null, "All Search Maps")), /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__div"
  }), clientGroups.map(cg => {
    const isOpen = expanded.has(cg.clientId);
    return /*#__PURE__*/React.createElement(React.Fragment, {
      key: cg.clientId
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-mappick__client",
      onClick: () => toggle(cg.clientId)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: isOpen ? 'ChevronDown' : 'ChevronRight',
      size: 11
    }), /*#__PURE__*/React.createElement(Icon, {
      name: "Building2",
      size: 12,
      color: "var(--muted-foreground)"
    }), /*#__PURE__*/React.createElement("span", {
      className: "tm-mappick__cname"
    }, cg.name), /*#__PURE__*/React.createElement("span", {
      className: "tm-mappick__cn"
    }, cg.maps.length)), isOpen && cg.maps.map(MapRow));
  }), unassigned.length > 0 && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("button", {
    className: "tm-mappick__client",
    style: {
      opacity: .7
    },
    onClick: () => toggle('__ua')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: expanded.has('__ua') ? 'ChevronDown' : 'ChevronRight',
    size: 11
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "Inbox",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-mappick__cname"
  }, "Unassigned"), /*#__PURE__*/React.createElement("span", {
    className: "tm-mappick__cn"
  }, unassigned.length)), expanded.has('__ua') && unassigned.map(MapRow))), /*#__PURE__*/React.createElement("button", {
    className: "tm-mappick__new",
    onClick: () => {
      onClose();
      onNewSearch();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 13
  }), "New search map")));
  return ReactDOM.createPortal(content, document.body);
}

// ── Unified sidebar ──────────────────────────────────────────────────────────
function UnifiedSidebar({
  activeMap,
  view,
  onView,
  collapsed,
  onToggle,
  maps,
  onSelectMap,
  onSelectAll,
  onNewSearch,
  onSearch,
  onCrm,
  crmActive,
  onSettings,
  settingsActive,
  theme,
  onTheme,
  phase
}) {
  const [pickerOpen, setPickerOpen] = React.useState(false);
  const [pickerRect, setPickerRect] = React.useState(null);
  const mapBtnRef = React.useRef(null);
  const openPicker = () => {
    if (mapBtnRef.current) setPickerRect(mapBtnRef.current.getBoundingClientRect());
    setPickerOpen(o => !o);
  };
  const initials = window.TM_USER && window.TM_USER.initials || 'YM';
  const userName = window.TM_USER && window.TM_USER.name || 'Yousef Iman';
  const gcn = window.getClientName || function () {
    return '';
  };
  const hasMap = !!activeMap;
  const mapName = hasMap ? activeMap.name : 'All Search Maps';
  const clientName = hasMap && activeMap.clientId ? gcn(activeMap.clientId) || '' : '';
  const isWorkspace = phase === 'workspace';
  const isActive = s => isWorkspace && (s.matchViews ? s.matchViews.includes(view) : view === s.id);
  React.useEffect(() => {
    const h = e => {
      if (e.key === 'Escape') setPickerOpen(false);
    };
    document.addEventListener('keydown', h);
    return () => document.removeEventListener('keydown', h);
  }, []);

  // ── Collapsed ──────────────────────────────────────────────────────────────
  if (collapsed) {
    return /*#__PURE__*/React.createElement("div", {
      className: "tm-usidebar is-collapsed"
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-usidebar__cicons"
    }, /*#__PURE__*/React.createElement(Tooltip, {
      label: "Expand",
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-usidebar__ibtn",
      onClick: onToggle
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "PanelLeftOpen",
      size: 16
    }))), /*#__PURE__*/React.createElement(Tooltip, {
      label: "New search",
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-usidebar__ibtn",
      onClick: onNewSearch
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Plus",
      size: 16
    }))), /*#__PURE__*/React.createElement(Tooltip, {
      label: "Search  Ctrl+K",
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-usidebar__ibtn",
      onClick: onSearch
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Search",
      size: 16
    })))), /*#__PURE__*/React.createElement("div", {
      className: "tm-usidebar__div"
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        position: 'relative'
      }
    }, /*#__PURE__*/React.createElement(Tooltip, {
      label: mapName,
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      ref: mapBtnRef,
      className: cx('tm-usidebar__ibtn', pickerOpen && 'is-active'),
      onClick: openPicker
    }, /*#__PURE__*/React.createElement(Icon, {
      name: hasMap ? 'Map' : 'Layers',
      size: 16
    }))), pickerOpen && /*#__PURE__*/React.createElement(MapPickerDropdown, {
      maps: maps,
      activeMap: activeMap,
      onSelect: onSelectMap,
      onSelectAll: onSelectAll,
      onClose: () => setPickerOpen(false),
      onNewSearch: onNewSearch,
      anchorRect: pickerRect
    })), /*#__PURE__*/React.createElement("div", {
      className: "tm-usidebar__div"
    }), hasMap && TM_SECTIONS.map((s, i) => {
      if (!s) return /*#__PURE__*/React.createElement("div", {
        key: 'd' + i,
        className: "tm-usidebar__div"
      });
      return /*#__PURE__*/React.createElement(Tooltip, {
        key: s.id,
        label: s.label,
        side: "right"
      }, /*#__PURE__*/React.createElement("button", {
        className: cx('tm-usidebar__ibtn', isActive(s) && 'is-active'),
        onClick: () => onView(s.id)
      }, /*#__PURE__*/React.createElement(Icon, {
        name: s.icon,
        size: 16
      })));
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1
      }
    }), /*#__PURE__*/React.createElement("div", {
      className: "tm-usidebar__div"
    }), /*#__PURE__*/React.createElement(Tooltip, {
      label: "CRM",
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: cx('tm-usidebar__ibtn', crmActive && 'is-active'),
      onClick: onCrm
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Users",
      size: 16
    }))), /*#__PURE__*/React.createElement("div", {
      className: "tm-usidebar__div"
    }), /*#__PURE__*/React.createElement(Tooltip, {
      label: userName,
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: cx('tm-usidebar__avt', settingsActive && 'is-active'),
      onClick: onSettings
    }, initials)), /*#__PURE__*/React.createElement(Tooltip, {
      label: theme === 'dark' ? 'Light mode' : 'Dark mode',
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-usidebar__ibtn",
      onClick: onTheme
    }, /*#__PURE__*/React.createElement(Icon, {
      name: theme === 'dark' ? 'Sun' : 'Moon',
      size: 15
    }))), /*#__PURE__*/React.createElement("div", {
      style: {
        height: 6
      }
    }));
  }

  // ── Expanded ───────────────────────────────────────────────────────────────
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-usidebar"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-usidebar__head"
  }, /*#__PURE__*/React.createElement(Tooltip, {
    label: "New search map"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-usidebar__ibtn",
    onClick: onNewSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 15
  }))), /*#__PURE__*/React.createElement(Tooltip, {
    label: "Search  Ctrl+K"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-usidebar__ibtn",
    onClick: onSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 15
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement(Tooltip, {
    label: "Collapse sidebar"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-usidebar__ibtn",
    onClick: onToggle
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "PanelLeftClose",
    size: 15
  })))), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '0 8px 4px',
      position: 'relative'
    }
  }, /*#__PURE__*/React.createElement("button", {
    ref: mapBtnRef,
    className: "tm-usidebar__mapbtn",
    onClick: openPicker
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-usidebar__mapinfo"
  }, hasMap && clientName && /*#__PURE__*/React.createElement("span", {
    className: "tm-usidebar__maplbl"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 10
  }), clientName), /*#__PURE__*/React.createElement("span", {
    className: "tm-usidebar__mapname"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: hasMap ? 'Map' : 'Layers',
    size: 12,
    style: {
      flexShrink: 0
    }
  }), /*#__PURE__*/React.createElement("span", null, mapName))), /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronsUpDown",
    size: 13,
    color: "var(--muted-foreground)",
    style: {
      flexShrink: 0
    }
  })), pickerOpen && /*#__PURE__*/React.createElement(MapPickerDropdown, {
    maps: maps,
    activeMap: activeMap,
    onSelect: onSelectMap,
    onSelectAll: onSelectAll,
    onClose: () => setPickerOpen(false),
    onNewSearch: onNewSearch,
    anchorRect: pickerRect
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-usidebar__div"
  }), hasMap && /*#__PURE__*/React.createElement("nav", {
    className: "tm-usidebar__nav"
  }, TM_SECTIONS.map((s, i) => {
    if (!s) return /*#__PURE__*/React.createElement("div", {
      key: 'd' + i,
      className: "tm-usidebar__div"
    });
    return /*#__PURE__*/React.createElement("button", {
      key: s.id,
      className: cx('tm-usidebar__item', isActive(s) && 'is-active'),
      onClick: () => onView(s.id)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: s.icon,
      size: 14
    }), /*#__PURE__*/React.createElement("span", null, s.label));
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-usidebar__div"
  }), /*#__PURE__*/React.createElement("button", {
    className: cx('tm-usidebar__item', crmActive && 'is-active'),
    onClick: onCrm
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 14
  }), /*#__PURE__*/React.createElement("span", null, "CRM")), /*#__PURE__*/React.createElement("div", {
    className: "tm-usidebar__foot"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx('tm-usidebar__avt', settingsActive && 'is-active'),
    onClick: onSettings
  }, initials), /*#__PURE__*/React.createElement("button", {
    className: "tm-usidebar__uname",
    onClick: onSettings
  }, userName), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement(Tooltip, {
    label: theme === 'dark' ? 'Light mode' : 'Dark mode'
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-usidebar__ibtn",
    onClick: onTheme
  }, /*#__PURE__*/React.createElement(Icon, {
    name: theme === 'dark' ? 'Sun' : 'Moon',
    size: 14
  })))));
}

// ── Team avatar stack ────────────────────────────────────────────────────────
function TeamAvatarStack({
  people,
  size
}) {
  const sz = size || 28;
  const shown = (people || []).slice(0, 4);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center'
    }
  }, shown.map((p, i) => /*#__PURE__*/React.createElement(Tooltip, {
    key: p.initials + i,
    label: p.name + ' \u00b7 ' + p.role,
    side: "bottom"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      marginLeft: i === 0 ? 0 : -8,
      zIndex: shown.length - i,
      position: 'relative',
      width: sz,
      height: sz,
      borderRadius: '50%',
      background: 'var(--sidebar-accent)',
      border: '2px solid var(--background)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontSize: 10,
      fontWeight: 700,
      color: 'var(--foreground)',
      cursor: 'default'
    }
  }, p.initials))));
}

// ── Top bar (workspace) ──────────────────────────────────────────────────────
function TopBar({
  project,
  companies,
  execs,
  view,
  onBack,
  onSearch,
  onTheme,
  theme,
  showSats,
  onToggleSats,
  projectTeam,
  projectClients
}) {
  const execCount = companies.reduce((s, c) => s + c.execs.length, 0);
  const team = projectTeam || [];
  const clients = projectClients || [];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-top"
  }, /*#__PURE__*/React.createElement(Tooltip, {
    label: "All search maps"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn",
    style: {
      width: 28,
      height: 28
    },
    onClick: onBack
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowLeft",
    size: 16
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-divider-v"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-top__name"
  }, project, /*#__PURE__*/React.createElement(Icon, {
    name: "Pencil",
    size: 14,
    className: "tm-pencil"
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 12,
      marginLeft: 4
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-stat"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 12
  }), companies.length), /*#__PURE__*/React.createElement("span", {
    className: "tm-stat"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 12
  }), execCount)), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement(TeamAvatarStack, {
    people: team,
    size: 28
  }), /*#__PURE__*/React.createElement(Tooltip, {
    label: "Add team member",
    side: "bottom"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn tm-avt-ghost",
    onClick: () => window.showToast && window.showToast('Team management coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 12
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-divider-v"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6
    }
  }, clients.length === 0 ? /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      whiteSpace: 'nowrap'
    }
  }, "No clients") : /*#__PURE__*/React.createElement(TeamAvatarStack, {
    people: clients,
    size: 28
  }), /*#__PURE__*/React.createElement(Tooltip, {
    label: "Add client contact",
    side: "bottom"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn tm-avt-ghost",
    onClick: () => window.showToast && window.showToast('Client management coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 12
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-divider-v"
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-searchbtn",
    onClick: onSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 12
  }), /*#__PURE__*/React.createElement("span", null, "Search\u2026"), /*#__PURE__*/React.createElement("span", {
    className: "tm-kbd"
  }, "Ctrl K")), /*#__PURE__*/React.createElement("div", {
    className: "tm-divider-v"
  }), /*#__PURE__*/React.createElement(Tooltip, {
    label: "Add company",
    side: "bottom"
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "icon"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 15
  }))), /*#__PURE__*/React.createElement(Tooltip, {
    label: "Enrich all",
    side: "bottom"
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "icon"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Zap",
    size: 15
  }))), /*#__PURE__*/React.createElement(Tooltip, {
    label: "Export to Excel",
    side: "bottom"
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "icon"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 15
  }))), /*#__PURE__*/React.createElement(Tooltip, {
    label: view !== 'map' ? 'Executives (map view)' : showSats ? 'Hide executives' : 'Show executives',
    side: "bottom"
  }, /*#__PURE__*/React.createElement(Button, {
    variant: view === 'map' && showSats ? 'default' : 'ghost',
    size: "icon",
    onClick: onToggleSats,
    disabled: view !== 'map'
  }, /*#__PURE__*/React.createElement(Icon, {
    name: view === 'map' && showSats ? 'EyeOff' : 'Eye',
    size: 15
  }))));
}

// ── Command palette ──────────────────────────────────────────────────────────
function CommandPalette({
  open,
  onClose,
  onView,
  onBack
}) {
  const [q, setQ] = React.useState('');
  React.useEffect(() => {
    if (open) setQ('');
  }, [open]);
  if (!open) return null;
  const groups = [{
    h: 'Navigation',
    items: [{
      ic: 'Map',
      t: 'Map View',
      sc: '1',
      run: () => onView('map')
    }, {
      ic: 'Table2',
      t: 'Table View',
      sc: '2',
      run: () => onView('table')
    }, {
      ic: 'LayoutDashboard',
      t: 'Dashboard',
      sc: '3',
      run: () => onView('dashboard')
    }, {
      ic: 'KanbanSquare',
      t: 'Pipeline',
      sc: '4',
      run: () => onView('pipeline')
    }, {
      ic: 'Users',
      t: 'CRM',
      run: () => {
        if (window.__goToCrm) window.__goToCrm();
      }
    }, {
      ic: 'Layers',
      t: 'All search maps',
      run: onBack
    }]
  }, {
    h: 'Actions',
    items: [{
      ic: 'Zap',
      t: 'Enrich all companies',
      run: onClose
    }, {
      ic: 'Download',
      t: 'Export to Excel',
      run: onClose
    }, {
      ic: 'Upload',
      t: 'Import data',
      run: onClose
    }]
  }];
  const ql = q.toLowerCase();
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-cmd-scrim tm-fadein",
    onClick: onClose
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cmd",
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cmd__input"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 18,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    autoFocus: true,
    placeholder: "Type a command or search\u2026",
    value: q,
    onChange: e => setQ(e.target.value)
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-kbd"
  }, "Esc")), groups.map(g => {
    const items = g.items.filter(i => i.t.toLowerCase().includes(ql));
    if (!items.length) return null;
    return /*#__PURE__*/React.createElement("div", {
      className: "tm-cmd__group",
      key: g.h
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-cmd__gh"
    }, g.h), items.map(i => /*#__PURE__*/React.createElement("div", {
      className: "tm-cmd__item",
      key: i.t,
      onClick: () => {
        i.run();
        onClose();
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: i.ic,
      size: 16,
      color: "var(--muted-foreground)"
    }), i.t, i.sc && /*#__PURE__*/React.createElement("span", {
      className: "tm-kbd sc"
    }, i.sc))));
  })));
}
Object.assign(window, {
  UnifiedSidebar,
  TopBar,
  CommandPalette,
  TeamAvatarStack,
  TM_SECTIONS
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/chrome.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/cookies.jsx
try { (() => {
/* global React, ReactDOM, Icon, Button */
// ── Cookie consent banner (self-mounted to #cookie-root; persists choice) ─────

function CookieSwitch({
  on,
  onChange
}) {
  return /*#__PURE__*/React.createElement("button", {
    className: on ? 'tm-switch on' : 'tm-switch off',
    onClick: () => onChange(!on),
    role: "switch",
    "aria-checked": on
  }, /*#__PURE__*/React.createElement("span", {
    className: "k"
  }));
}
function CookieConsent() {
  const KEY = 'tm-cookie-consent';
  const [open, setOpen] = React.useState(() => {
    try {
      return !localStorage.getItem(KEY);
    } catch (e) {
      return true;
    }
  });
  const [expanded, setExpanded] = React.useState(false);
  const [cats, setCats] = React.useState({
    ads: false,
    personalization: false,
    analytics: false
  });
  if (!open) return null;
  const decide = choice => {
    try {
      localStorage.setItem(KEY, JSON.stringify({
        essential: true,
        ...choice,
        at: Date.now()
      }));
    } catch (e) {}
    setOpen(false);
  };
  const set = (k, v) => setCats(c => ({
    ...c,
    [k]: v
  }));
  const CATS = [{
    k: 'ads',
    nm: 'Targeted Advertising',
    ds: 'Used to show you relevant ads on other sites.'
  }, {
    k: 'personalization',
    nm: 'Personalization',
    ds: 'Remembers your choices to tailor your experience.'
  }, {
    k: 'analytics',
    nm: 'Analytics',
    ds: 'Helps us understand how the product is used.'
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-cookie",
    role: "dialog",
    "aria-label": "Cookie preferences"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cookie__head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Cookie",
    size: 16
  })), /*#__PURE__*/React.createElement("span", {
    className: "t"
  }, "Your privacy"), /*#__PURE__*/React.createElement("button", {
    className: "tm-cookie__x",
    title: "Close \u2014 essential cookies only",
    onClick: () => decide({
      ads: false,
      personalization: false,
      analytics: false
    })
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 15
  }))), /*#__PURE__*/React.createElement("p", {
    className: "tm-cookie__body"
  }, "This website utilizes technologies such as cookies to enable essential site functionality, as well as for analytics, personalization, and targeted advertising. You may change your settings at any time or accept the default settings. You may close this banner to continue with only essential cookies."), /*#__PURE__*/React.createElement("div", {
    className: "tm-cookie__links"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-link"
  }, "Privacy Policy"), /*#__PURE__*/React.createElement("button", {
    className: "tm-link",
    onClick: () => setExpanded(e => !e)
  }, "Storage Preferences ", /*#__PURE__*/React.createElement(Icon, {
    name: expanded ? 'ChevronUp' : 'ChevronDown',
    size: 12,
    style: {
      verticalAlign: 'middle'
    }
  }))), expanded && /*#__PURE__*/React.createElement("div", {
    className: "tm-cookie__cats"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cookie__cat"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "nm"
  }, "Essential"), /*#__PURE__*/React.createElement("div", {
    className: "ds"
  }, "Required for the site to function. Always active.")), /*#__PURE__*/React.createElement("span", {
    className: "always"
  }, "Always on")), CATS.map(c => /*#__PURE__*/React.createElement("div", {
    className: "tm-cookie__cat",
    key: c.k
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "nm"
  }, c.nm), /*#__PURE__*/React.createElement("div", {
    className: "ds"
  }, c.ds)), /*#__PURE__*/React.createElement(CookieSwitch, {
    on: cats[c.k],
    onChange: v => set(c.k, v)
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cookie__actions"
  }, expanded ? /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: () => decide(cats)
  }, "Save") : /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: () => decide({
      ads: false,
      personalization: false,
      analytics: false
    })
  }, "Reject Non-Essential"), /*#__PURE__*/React.createElement(Button, {
    onClick: () => decide({
      ads: true,
      personalization: true,
      analytics: true
    })
  }, "Accept All")));
}
(function mountCookie() {
  const el = document.getElementById('cookie-root');
  if (el && window.ReactDOM) ReactDOM.createRoot(el).render(/*#__PURE__*/React.createElement(CookieConsent, null));
})();
window.CookieConsent = CookieConsent;
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/cookies.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/crm.jsx
try { (() => {
/* global React, Icon, Button, Avatar, cx, initials, TM_PIPELINE, TM_CONTACTS, TM_CONTACT_ENTRIES, TM_CONTACT_ACTIVITIES */
// ── CRM / Contacts screen — global directory across projects ─────────────────

function ContactsScreen({
  onSelectContact,
  onAddToPipeline
}) {
  const CRM_ME = 'LH';
  const [q, setQ] = React.useState('');
  const [filterAvail, setFilterAvail] = React.useState('');
  const [filterCompany, setFilterCompany] = React.useState('');
  const [filterType, setFilterType] = React.useState('');
  const [filterOwner, setFilterOwner] = React.useState('');
  const [segment, setSegment] = React.useState('all');
  const [sort, setSort] = React.useState({
    key: 'name',
    dir: 'asc'
  });
  const [selected, setSelected] = React.useState(() => new Set());
  const contacts = TM_CONTACTS || [];
  const companies = [...new Set(contacts.map(c => c.company))].sort();
  const availabilities = [...new Set(contacts.map(c => c.availability))].filter(a => a && a !== 'Unknown').sort();
  const typeOf = c => c.type || 'Candidate';

  // Owner + next-follow-up come from the same per-contact profile the detail uses.
  const meta = React.useMemo(() => {
    const m = {};
    contacts.forEach(c => {
      const p = window.tmBuildContactProfile ? window.tmBuildContactProfile(c) : {};
      const openTasks = (p.tasks || []).filter(t => !t.done);
      const nextDue = openTasks.length ? Math.min(...openTasks.map(t => t.dueDays)) : null;
      m[c.id] = {
        owner: p.owner || 'LH',
        nextDue,
        openTasks: openTasks.length
      };
    });
    return m;
  }, [contacts]);
  const owners = [...new Set(Object.values(meta).map(m => m.owner))].sort();
  const SEGMENTS = [{
    id: 'all',
    label: 'All',
    test: () => true
  }, {
    id: 'mine',
    label: 'My contacts',
    test: c => meta[c.id].owner === CRM_ME
  }, {
    id: 'due',
    label: 'Due follow-up',
    test: c => meta[c.id].nextDue !== null && meta[c.id].nextDue <= 0
  }, {
    id: 'candidate',
    label: 'Candidates',
    test: c => typeOf(c) === 'Candidate'
  }, {
    id: 'client',
    label: 'Clients',
    test: c => typeOf(c) === 'Client'
  }, {
    id: 'open',
    label: 'Open to move',
    test: c => c.availability === 'Open to move'
  }];
  const activeSeg = SEGMENTS.find(s => s.id === segment) || SEGMENTS[0];
  const filtered = contacts.filter(c => {
    if (!activeSeg.test(c)) return false;
    if (q && !c.name.toLowerCase().includes(q.toLowerCase()) && !c.company.toLowerCase().includes(q.toLowerCase()) && !c.title.toLowerCase().includes(q.toLowerCase())) return false;
    if (filterType && typeOf(c) !== filterType) return false;
    if (filterOwner && meta[c.id].owner !== filterOwner) return false;
    if (filterAvail && c.availability !== filterAvail) return false;
    if (filterCompany && c.company !== filterCompany) return false;
    return true;
  });
  const sorted = [...filtered].sort((a, b) => {
    let av, bv;
    if (sort.key === 'name') {
      av = a.name.toLowerCase();
      bv = b.name.toLowerCase();
    } else if (sort.key === 'type') {
      av = typeOf(a);
      bv = typeOf(b);
    } else if (sort.key === 'company') {
      av = a.company.toLowerCase();
      bv = b.company.toLowerCase();
    } else if (sort.key === 'owner') {
      av = meta[a.id].owner;
      bv = meta[b.id].owner;
    } else if (sort.key === 'availability') {
      av = a.availability || '';
      bv = b.availability || '';
    } else if (sort.key === 'lastActivity') {
      av = a.lastActivityDays;
      bv = b.lastActivityDays;
    } else {
      av = a[sort.key];
      bv = b[sort.key];
    }
    if (av < bv) return sort.dir === 'asc' ? -1 : 1;
    if (av > bv) return sort.dir === 'asc' ? 1 : -1;
    return 0;
  });
  const toggleSort = key => setSort(s => s.key === key ? {
    key,
    dir: s.dir === 'asc' ? 'desc' : 'asc'
  } : {
    key,
    dir: 'asc'
  });
  const SortH = ({
    k,
    children,
    right
  }) => /*#__PURE__*/React.createElement("button", {
    className: cx('tm-sorth', right && 'is-right'),
    onClick: () => toggleSort(k)
  }, children, sort.key === k && /*#__PURE__*/React.createElement(Icon, {
    name: sort.dir === 'asc' ? 'ChevronUp' : 'ChevronDown',
    size: 12
  }));

  // Selection
  const allShownSelected = sorted.length > 0 && sorted.every(c => selected.has(c.id));
  const toggleOne = (id, e) => {
    e.stopPropagation();
    setSelected(prev => {
      const n = new Set(prev);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
  };
  const toggleAll = () => setSelected(prev => {
    if (allShownSelected) {
      const n = new Set(prev);
      sorted.forEach(c => n.delete(c.id));
      return n;
    }
    const n = new Set(prev);
    sorted.forEach(c => n.add(c.id));
    return n;
  });
  const clearSel = () => setSelected(new Set());
  const bulk = label => {
    window.showToast && window.showToast(label + ' · ' + selected.size + ' contact' + (selected.size !== 1 ? 's' : ''));
    clearSel();
  };
  const ownerName = i => ({
    LH: 'Layla Hassan',
    OK: 'Omar Khalil',
    SM: 'Sara Mitchell',
    FO: 'Farah Obeid'
  })[i] || i;
  const rowAction = (e, fn) => {
    e.stopPropagation();
    fn();
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "CRM"), /*#__PURE__*/React.createElement("h1", {
    className: "tm-pscreen__title"
  }, "Contacts")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-search-field"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 15,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    placeholder: "Search contacts\u2026",
    value: q,
    onChange: e => setQ(e.target.value)
  })), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Type",
    options: ['Candidate', 'Client', 'Source'],
    value: filterType,
    onChange: setFilterType
  }), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Owner",
    options: owners,
    value: filterOwner,
    onChange: setFilterOwner
  }), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Availability",
    options: availabilities,
    value: filterAvail,
    onChange: setFilterAvail
  }), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Account",
    options: companies,
    value: filterCompany,
    onChange: setFilterCompany
  }), /*#__PURE__*/React.createElement(Button, {
    onClick: () => window.showToast && window.showToast('New contact — form coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 16
  }), "New contact"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-seg-chips"
  }, SEGMENTS.map(s => {
    const n = contacts.filter(s.test).length;
    return /*#__PURE__*/React.createElement("button", {
      key: s.id,
      className: cx('tm-seg-chip', segment === s.id && 'is-on'),
      onClick: () => setSegment(s.id)
    }, s.id === 'mine' && /*#__PURE__*/React.createElement(Icon, {
      name: "UserCircle",
      size: 12
    }), s.id === 'due' && /*#__PURE__*/React.createElement(Icon, {
      name: "Clock",
      size: 12
    }), s.label, /*#__PURE__*/React.createElement("span", {
      className: "tm-seg-chip__n"
    }, n));
  })), selected.size > 0 ? /*#__PURE__*/React.createElement("div", {
    className: "tm-bulkbar"
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontWeight: 600,
      fontSize: 13
    }
  }, selected.size, " selected"), /*#__PURE__*/React.createElement("div", {
    style: {
      width: 1,
      height: 18,
      background: 'var(--border)',
      margin: '0 4px'
    }
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-bulkbar__btn",
    onClick: () => bulk('Added to search')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 13
  }), "Add to search"), /*#__PURE__*/React.createElement("button", {
    className: "tm-bulkbar__btn",
    onClick: () => bulk('Owner assigned')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserCircle",
    size: 13
  }), "Assign owner"), /*#__PURE__*/React.createElement("button", {
    className: "tm-bulkbar__btn",
    onClick: () => bulk('Tagged')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Tag",
    size: 13
  }), "Tag"), /*#__PURE__*/React.createElement("button", {
    className: "tm-bulkbar__btn",
    onClick: () => bulk('Exported')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 13
  }), "Export"), /*#__PURE__*/React.createElement("button", {
    className: "tm-bulkbar__btn",
    style: {
      marginLeft: 'auto'
    },
    onClick: clearSel
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 13
  }), "Clear")) : /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      marginBottom: 14,
      marginTop: 14
    }
  }, sorted.length, " contact", sorted.length !== 1 ? 's' : '', filterAvail || filterCompany || filterType || filterOwner || q || segment !== 'all' ? ' shown' : ''), /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ctable__head tm-ctable__head--v2"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-ctable__check"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-check",
    onClick: toggleAll
  }, /*#__PURE__*/React.createElement(Icon, {
    name: allShownSelected ? 'CheckSquare' : 'Square',
    size: 15,
    color: allShownSelected ? 'var(--primary)' : 'var(--muted-foreground)'
  }))), /*#__PURE__*/React.createElement(SortH, {
    k: "name"
  }, "Name"), /*#__PURE__*/React.createElement(SortH, {
    k: "type"
  }, "Type"), /*#__PURE__*/React.createElement(SortH, {
    k: "company"
  }, "Company"), /*#__PURE__*/React.createElement(SortH, {
    k: "owner"
  }, "Owner"), /*#__PURE__*/React.createElement(SortH, {
    k: "availability"
  }, "Status"), /*#__PURE__*/React.createElement(SortH, {
    k: "lastActivity"
  }, "Activity"), /*#__PURE__*/React.createElement("span", {
    className: "tm-r"
  }, "Actions")), sorted.map(c => {
    const t = typeOf(c);
    const tm = (window.TM_CONTACT_TYPE_META || {})[t] || {};
    const minfo = meta[c.id];
    const due = minfo.nextDue;
    const isSel = selected.has(c.id);
    return /*#__PURE__*/React.createElement("div", {
      key: c.id,
      className: cx('tm-ctable__row tm-ctable__row--v2', isSel && 'is-sel'),
      onClick: () => onSelectContact && onSelectContact(c)
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-ctable__check"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-check",
      onClick: e => toggleOne(c.id, e)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: isSel ? 'CheckSquare' : 'Square',
      size: 15,
      color: isSel ? 'var(--primary)' : 'var(--muted-foreground)'
    }))), /*#__PURE__*/React.createElement("div", {
      className: "tm-ctable__name"
    }, /*#__PURE__*/React.createElement(Avatar, {
      name: c.name,
      size: 28,
      tone: t === 'Client' ? 'neutral' : 'primary'
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, c.name), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: 'var(--muted-foreground)',
        fontWeight: 400,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, c.title))), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("span", {
      className: "tm-pill",
      style: {
        background: tm.bg,
        color: tm.fg,
        gap: 4,
        fontSize: 10.5
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: tm.icon || 'User',
      size: 10
    }), tm.label || t)), /*#__PURE__*/React.createElement("span", {
      className: "tm-ctable__cell",
      style: {
        color: 'var(--foreground)',
        fontWeight: 500,
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, c.company), window.tmOffLimitsFor && window.tmOffLimitsFor(c.company) && /*#__PURE__*/React.createElement(OffLimitsBadge, {
      compact: true,
      reason: window.tmOffLimitsFor(c.company).reason
    })), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("span", {
      className: "tm-pl__av",
      style: {
        width: 24,
        height: 24,
        fontSize: 10
      },
      title: ownerName(minfo.owner)
    }, minfo.owner)), /*#__PURE__*/React.createElement("span", null, t === 'Candidate' ? /*#__PURE__*/React.createElement(AvailPill, {
      availability: c.availability
    }) : /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        color: 'var(--muted-foreground)'
      }
    }, t === 'Client' ? c.clientRole || '—' : 'Market')), /*#__PURE__*/React.createElement("span", {
      style: {
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: 'var(--muted-foreground)'
      }
    }, formatAge(c.lastActivityDays)), due !== null && /*#__PURE__*/React.createElement("span", {
      className: "tm-pill",
      style: {
        marginTop: 2,
        fontSize: 10,
        ...(due < 0 ? {
          background: 'rgba(220,38,38,.10)',
          color: '#b91c1c'
        } : due === 0 ? {
          background: 'rgba(245,158,11,.12)',
          color: '#b45309'
        } : {
          background: 'var(--muted)',
          color: 'var(--muted-foreground)'
        })
      }
    }, due < 0 ? 'Overdue ' + Math.abs(due) + 'd' : due === 0 ? 'Due today' : 'Due ' + due + 'd')), /*#__PURE__*/React.createElement("span", {
      className: "tm-ctable__actions",
      onClick: e => e.stopPropagation()
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-rowact",
      title: "Log activity",
      onClick: e => rowAction(e, () => window.showToast && window.showToast('Log activity — ' + c.name))
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Plus",
      size: 14
    })), t === 'Candidate' && /*#__PURE__*/React.createElement("button", {
      className: "tm-rowact",
      title: "Add to search",
      onClick: e => rowAction(e, () => onAddToPipeline ? onAddToPipeline({
        name: c.name,
        title: c.title,
        company: c.company
      }) : window.showToast && window.showToast('Add to search — ' + c.name))
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Target",
      size: 14
    })), /*#__PURE__*/React.createElement("button", {
      className: "tm-rowact",
      title: "Email",
      onClick: e => rowAction(e, () => window.showToast && window.showToast('Email — ' + c.name))
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Mail",
      size: 14
    }))));
  }), sorted.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable__empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 20,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, "No contacts match this view.")))));
}

// ── Off-limits badge ─────────────────────────────────────────────────────────
function OffLimitsBadge({
  compact,
  reason
}) {
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    title: reason,
    style: {
      background: 'rgba(220,38,38,.10)',
      color: '#b91c1c',
      gap: 4,
      fontSize: compact ? 10 : 11,
      padding: compact ? '1px 6px' : '2px 8px',
      flexShrink: 0,
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldAlert",
    size: compact ? 10 : 12
  }), "Off-limits");
}

// ── Stage pill (soft-tint per tone) ───────────────────────────────────────────
const PL_PILL_BG = {
  slate: 'var(--muted)',
  blue: 'var(--info-bg, rgba(37,99,235,.08))',
  violet: 'var(--ai-bg, rgba(124,58,237,.08))',
  amber: 'var(--warning-bg, rgba(245,156,11,.08))',
  emerald: 'var(--success-bg, rgba(5,150,105,.08))',
  muted: 'var(--muted)'
};
function StagePill({
  stage
}) {
  const s = PL_STAGES.find(x => x.id === stage) || PL_STAGES[0];
  const t = PL_TONE[s.tone];
  const bg = PL_PILL_BG[s.tone];
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: bg,
      color: t.fg
    }
  }, stage);
}
function AvailPill({
  availability
}) {
  if (!availability || availability === 'Unknown') return /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)'
    }
  }, "\u2014");
  const isOpen = availability === 'Open to move';
  const bg = isOpen ? 'var(--success-bg, rgba(5,150,105,.08))' : 'var(--muted)';
  const fg = isOpen ? 'var(--success-fg, #15803d)' : 'var(--muted-foreground)';
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: bg,
      color: fg
    }
  }, availability);
}
function formatAge(days) {
  if (days === 0) return 'today';
  if (days === 1) return '1d ago';
  if (days < 7) return days + 'd ago';
  if (days < 30) return Math.floor(days / 7) + 'w ago';
  return Math.floor(days / 30) + 'mo ago';
}

// ── Pipeline table sort header ───────────────────────────────────────────────
function PlSortH({
  k,
  sort,
  onSort,
  children,
  right
}) {
  const active = sort.key === k;
  return /*#__PURE__*/React.createElement("button", {
    className: cx('tm-sorth', right && 'is-right'),
    onClick: () => onSort(k)
  }, children, active && /*#__PURE__*/React.createElement(Icon, {
    name: sort.dir === 'asc' ? 'ChevronUp' : 'ChevronDown',
    size: 11
  }));
}

// ── Pipeline table view ──────────────────────────────────────────────────────
function PipelineTable({
  entries,
  onSelect,
  selected
}) {
  const [sort, setSort] = React.useState({
    key: 'contactName',
    dir: 'asc'
  });
  const toggleSort = key => setSort(s => s.key === key ? {
    key,
    dir: s.dir === 'asc' ? 'desc' : 'asc'
  } : {
    key,
    dir: 'asc'
  });
  const stageOrder = {};
  PL_STAGES.forEach((s, i) => {
    stageOrder[s.id] = i;
  });
  const sorted = [...entries].sort((a, b) => {
    let av, bv;
    if (sort.key === 'contactName') {
      av = a.contactName.toLowerCase();
      bv = b.contactName.toLowerCase();
    } else if (sort.key === 'title') {
      av = a.title.toLowerCase();
      bv = b.title.toLowerCase();
    } else if (sort.key === 'company') {
      av = a.company.toLowerCase();
      bv = b.company.toLowerCase();
    } else if (sort.key === 'stage') {
      av = stageOrder[a.stage] ?? 99;
      bv = stageOrder[b.stage] ?? 99;
    } else if (sort.key === 'availability') {
      av = a.availability || '';
      bv = b.availability || '';
    } else if (sort.key === 'ageDays') {
      av = a.ageDays;
      bv = b.ageDays;
    } else {
      av = a[sort.key];
      bv = b[sort.key];
    }
    if (av < bv) return sort.dir === 'asc' ? -1 : 1;
    if (av > bv) return sort.dir === 'asc' ? 1 : -1;
    return 0;
  });
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__table-wrap"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__tbl"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__tbl-head"
  }, /*#__PURE__*/React.createElement(PlSortH, {
    k: "contactName",
    sort: sort,
    onSort: toggleSort
  }, "Name"), /*#__PURE__*/React.createElement(PlSortH, {
    k: "title",
    sort: sort,
    onSort: toggleSort
  }, "Title"), /*#__PURE__*/React.createElement(PlSortH, {
    k: "company",
    sort: sort,
    onSort: toggleSort
  }, "Company"), /*#__PURE__*/React.createElement(PlSortH, {
    k: "stage",
    sort: sort,
    onSort: toggleSort
  }, "Stage"), /*#__PURE__*/React.createElement(PlSortH, {
    k: "availability",
    sort: sort,
    onSort: toggleSort
  }, "Availability"), /*#__PURE__*/React.createElement("span", null, "Assignees"), /*#__PURE__*/React.createElement(PlSortH, {
    k: "ageDays",
    sort: sort,
    onSort: toggleSort,
    right: true
  }, "Last activity")), sorted.map(e => /*#__PURE__*/React.createElement("div", {
    key: e.id,
    className: cx('tm-pl__tbl-row', selected?.id === e.id && 'is-sel'),
    onClick: () => onSelect(e)
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__tbl-name"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: e.contactName,
    size: 24,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("span", null, e.contactName)), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__tbl-cell"
  }, e.title), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__tbl-cell"
  }, e.company), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(StagePill, {
    stage: e.stage
  })), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(AvailPill, {
    availability: e.availability
  })), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(AssigneeStack, {
    assignees: e.assignees
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__tbl-time"
  }, formatAge(e.ageDays)))), sorted.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__tbl-empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 18
  }), /*#__PURE__*/React.createElement("span", null, "No entries match your filters."))));
}

// ── Pipeline constants ───────────────────────────────────────────────────────
const PL_STAGES = [{
  id: 'Sourced',
  label: 'Sourced',
  tone: 'slate'
}, {
  id: 'Contacted',
  label: 'Contacted',
  tone: 'blue'
}, {
  id: 'Screening',
  label: 'Screening',
  tone: 'violet'
}, {
  id: 'Interview',
  label: 'Interview',
  tone: 'amber'
}, {
  id: 'Offer',
  label: 'Offer',
  tone: 'emerald'
}, {
  id: 'Hired',
  label: 'Hired',
  tone: 'emerald'
}, {
  id: 'Closed',
  label: 'Closed',
  tone: 'muted'
}];
const PL_TONE = {
  slate: {
    fg: 'var(--muted-foreground)',
    accent: 'var(--muted-foreground)'
  },
  blue: {
    fg: '#1d4ed8',
    accent: '#2563eb'
  },
  violet: {
    fg: '#6d28d9',
    accent: '#7c3aed'
  },
  amber: {
    fg: '#b45309',
    accent: '#d97706'
  },
  emerald: {
    fg: 'var(--success-fg, #15803d)',
    accent: 'var(--success, #059669)'
  },
  muted: {
    fg: 'var(--muted-foreground)',
    accent: 'var(--muted-foreground)'
  }
};
const PL_OWNERS = ['LH', 'OK', 'SM', 'FO'];

// ── Filter dropdown ──────────────────────────────────────────────────────────
function PlFilter({
  label,
  options,
  value,
  onChange
}) {
  const [open, setOpen] = React.useState(false);
  const ref = React.useRef(null);
  React.useEffect(() => {
    if (!open) return;
    const h = e => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [open]);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__filter",
    ref: ref
  }, /*#__PURE__*/React.createElement("button", {
    className: cx('tm-pl__filter-btn', value && 'is-active'),
    onClick: () => setOpen(o => !o)
  }, value || label, /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronDown",
    size: 12
  })), open && /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__filter-drop"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-pl__filter-opt",
    onClick: () => {
      onChange('');
      setOpen(false);
    }
  }, "All"), options.map(o => /*#__PURE__*/React.createElement("button", {
    key: o,
    className: cx('tm-pl__filter-opt', value === o && 'is-on'),
    onClick: () => {
      onChange(o);
      setOpen(false);
    }
  }, o))));
}

// ── Assignee avatar stack ────────────────────────────────────────────────────
function AssigneeStack({
  assignees,
  max = 3
}) {
  const show = assignees.slice(0, max);
  const overflow = assignees.length - max;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__avatars"
  }, show.map((a, i) => /*#__PURE__*/React.createElement("span", {
    key: i,
    className: "tm-pl__av",
    style: {
      zIndex: max - i
    }
  }, a)), overflow > 0 && /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__av tm-pl__av--over"
  }, "+", overflow));
}

// ── Candidate card ───────────────────────────────────────────────────────────
function PipelineCard({
  entry,
  onDragStart,
  onClick
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card",
    draggable: true,
    onClick: onClick,
    onDragStart: e => {
      e.dataTransfer.setData('text/plain', entry.id);
      e.dataTransfer.effectAllowed = 'move';
      if (onDragStart) onDragStart(entry.id);
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-top"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: entry.contactName,
    size: 28,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-name"
  }, entry.contactName), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-role"
  }, entry.title, " \xB7 ", entry.company))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-bottom"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__card-age"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 11
  }), entry.ageDays, "d"), window.tmOffLimitsFor && window.tmOffLimitsFor(entry.company) && /*#__PURE__*/React.createElement(OffLimitsBadge, {
    compact: true,
    reason: window.tmOffLimitsFor(entry.company).reason
  }), entry.availability && entry.availability !== 'Unknown' && /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__card-avail"
  }, entry.availability), /*#__PURE__*/React.createElement(AssigneeStack, {
    assignees: entry.assignees
  })));
}

// ── Kanban column ────────────────────────────────────────────────────────────
function PlColumn({
  stage,
  entries,
  onDrop,
  onCardClick
}) {
  const [over, setOver] = React.useState(false);
  const colors = PL_TONE[stage.tone];
  const isClosed = stage.id === 'Closed';
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-pl__col', over && 'is-over', isClosed && 'is-closed'),
    onDragOver: e => {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      setOver(true);
    },
    onDragLeave: () => setOver(false),
    onDrop: e => {
      e.preventDefault();
      setOver(false);
      onDrop(e.dataTransfer.getData('text/plain'), stage.id);
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__col-head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__col-dot",
    style: {
      background: colors.accent
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__col-label",
    style: {
      color: colors.fg
    }
  }, stage.label), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__col-count"
  }, entries.length)), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__col-cards"
  }, entries.map(e => /*#__PURE__*/React.createElement(PipelineCard, {
    key: e.id,
    entry: e,
    onClick: () => onCardClick && onCardClick(e)
  })), entries.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__col-empty"
  }, over ? 'Drop here' : 'No candidates')));
}

// ── Entry side panel (stub) ──────────────────────────────────────────────────
function EntryPanel({
  entry,
  onClose
}) {
  if (!entry) return null;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__panel"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__panel-head"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10
    }
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: entry.contactName,
    size: 34,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 14,
      fontWeight: 700
    }
  }, entry.contactName), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)',
      marginTop: 1
    }
  }, entry.title, " \xB7 ", entry.company))), /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn",
    style: {
      width: 28,
      height: 28
    },
    onClick: onClose
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 15
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__panel-body"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__panel-section"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 9,
      marginBottom: 8
    }
  }, "Details"), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__panel-row"
  }, /*#__PURE__*/React.createElement("span", null, "Stage"), /*#__PURE__*/React.createElement("span", {
    style: {
      fontWeight: 600
    }
  }, entry.stage)), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__panel-row"
  }, /*#__PURE__*/React.createElement("span", null, "Availability"), /*#__PURE__*/React.createElement("span", null, entry.availability)), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__panel-row"
  }, /*#__PURE__*/React.createElement("span", null, "Days in stage"), /*#__PURE__*/React.createElement("span", null, entry.ageDays, "d")), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__panel-row"
  }, /*#__PURE__*/React.createElement("span", null, "Assignees"), /*#__PURE__*/React.createElement(AssigneeStack, {
    assignees: entry.assignees
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__panel-section"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 9,
      marginBottom: 8
    }
  }, "Activity"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      lineHeight: 1.5
    }
  }, "Full activity timeline coming soon. Interactions, notes, and stage transitions will appear here."))));
}

// ── PipelineView ─────────────────────────────────────────────────────────────
function PipelineView({
  project,
  extraEntries = [],
  onOpenContact
}) {
  const [plView, setPlView] = React.useState('board');
  const [entries, setEntries] = React.useState(() => [...TM_PIPELINE]);

  // Merge in entries added from other views (add to pipeline button)
  React.useEffect(() => {
    if (!extraEntries || extraEntries.length === 0) return;
    setEntries(prev => {
      const existingIds = new Set(prev.map(e => e.id));
      const newOnes = extraEntries.filter(e => !existingIds.has(e.id));
      return newOnes.length > 0 ? [...prev, ...newOnes] : prev;
    });
  }, [extraEntries.length]);
  const [search, setSearch] = React.useState('');
  const [filterOwner, setFilterOwner] = React.useState('');
  const [filterAvail, setFilterAvail] = React.useState('');
  const [selected, setSelected] = React.useState(null);
  const handleDrop = (entryId, newStage) => {
    setEntries(prev => prev.map(e => e.id === entryId ? {
      ...e,
      stage: newStage,
      ageDays: 0
    } : e));
  };
  const filtered = entries.filter(e => {
    if (search && !e.contactName.toLowerCase().includes(search.toLowerCase()) && !e.company.toLowerCase().includes(search.toLowerCase())) return false;
    if (filterOwner && !e.assignees.includes(filterOwner)) return false;
    if (filterAvail && e.availability !== filterAvail) return false;
    return true;
  });
  const availabilities = [...new Set(entries.map(e => e.availability))].filter(a => a && a !== 'Unknown').sort();
  const handleEntryClick = e => {
    const c = (window.TM_CONTACTS || []).find(co => co.name === e.contactName);
    if (onOpenContact && c) {
      onOpenContact(c.id);
    } else {
      setSelected(e);
    }
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header-left"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "Pipeline"), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 10,
      color: 'var(--muted-foreground)'
    }
  }, "\xB7"), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      fontWeight: 600,
      color: 'var(--foreground)'
    }
  }, project || 'Project'))), /*#__PURE__*/React.createElement("div", {
    className: "tm-seg"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx(plView === 'board' && 'is-on'),
    onClick: () => setPlView('board')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Columns3",
    size: 13,
    style: {
      marginRight: 4
    }
  }), "Board"), /*#__PURE__*/React.createElement("button", {
    className: cx(plView === 'table' && 'is-on'),
    onClick: () => setPlView('table')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Table2",
    size: 13,
    style: {
      marginRight: 4
    }
  }), "Table"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header-right"
  }, /*#__PURE__*/React.createElement(PlFilter, {
    label: "Assignee",
    options: PL_OWNERS,
    value: filterOwner,
    onChange: setFilterOwner
  }), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Availability",
    options: availabilities,
    value: filterAvail,
    onChange: setFilterAvail
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-search-field",
    style: {
      width: 170
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 14,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    placeholder: "Search\u2026",
    value: search,
    onChange: e => setSearch(e.target.value)
  })), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    onClick: () => {}
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 15
  }), "Export"))), plView === 'board' ? /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__board-wrap"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__board"
  }, PL_STAGES.map(stage => /*#__PURE__*/React.createElement(PlColumn, {
    key: stage.id,
    stage: stage,
    entries: filtered.filter(e => e.stage === stage.id),
    onDrop: handleDrop,
    onCardClick: handleEntryClick
  }))), /*#__PURE__*/React.createElement(EntryPanel, {
    entry: selected,
    onClose: () => setSelected(null)
  })) : /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__board-wrap"
  }, /*#__PURE__*/React.createElement(PipelineTable, {
    entries: filtered,
    onSelect: handleEntryClick,
    selected: selected
  }), /*#__PURE__*/React.createElement(EntryPanel, {
    entry: selected,
    onClose: () => setSelected(null)
  })));
}

// ── Contact detail helpers ───────────────────────────────────────────────────
const ACT_META = {
  note: {
    icon: 'StickyNote',
    bg: 'var(--muted)',
    fg: 'var(--muted-foreground)'
  },
  call: {
    icon: 'Phone',
    bg: 'rgba(37,99,235,.10)',
    fg: '#1d4ed8'
  },
  email: {
    icon: 'Mail',
    bg: 'rgba(124,58,237,.10)',
    fg: '#6d28d9'
  },
  meeting: {
    icon: 'Calendar',
    bg: 'rgba(245,158,11,.10)',
    fg: '#b45309'
  },
  stage_change: {
    icon: 'ArrowRightLeft',
    bg: 'color-mix(in srgb, var(--primary) 12%, transparent)',
    fg: 'var(--primary)'
  },
  added: {
    icon: 'UserPlus',
    bg: 'rgba(5,150,105,.10)',
    fg: 'var(--success-fg, #15803d)'
  }
};
function CdField({
  icon,
  label,
  value,
  multiline,
  onSave
}) {
  const [editing, setEditing] = React.useState(false);
  const [draft, setDraft] = React.useState(value || '');
  const commit = v => {
    setEditing(false);
    if (v !== (value || '')) onSave(v);
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    onClick: () => !editing && setEditing(true)
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 13
  }), label), editing ? multiline ? /*#__PURE__*/React.createElement("textarea", {
    autoFocus: true,
    rows: 3,
    className: "tm-cd__field-edit tm-cd__field-ta",
    value: draft,
    onChange: e => setDraft(e.target.value),
    onBlur: () => commit(draft)
  }) : /*#__PURE__*/React.createElement("input", {
    autoFocus: true,
    className: "tm-cd__field-edit",
    value: draft,
    onChange: e => setDraft(e.target.value),
    onBlur: () => commit(draft),
    onKeyDown: e => {
      if (e.key === 'Enter') commit(draft);
      if (e.key === 'Escape') {
        setDraft(value || '');
        setEditing(false);
      }
    }
  }) : /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val"
  }, value || /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontStyle: 'italic'
    }
  }, "\u2014")), /*#__PURE__*/React.createElement(Icon, {
    name: "Pencil",
    size: 12,
    className: "tm-cd__field-pencil"
  })));
}
function CdStageSelector({
  stage,
  onChangeStage
}) {
  const idx = PL_STAGES.findIndex(s => s.id === stage);
  const cycle = e => {
    e.stopPropagation();
    onChangeStage(PL_STAGES[(idx + 1) % PL_STAGES.length].id);
  };
  return /*#__PURE__*/React.createElement("span", {
    onClick: cycle,
    title: "Click to advance stage",
    style: {
      cursor: 'pointer',
      flexShrink: 0
    }
  }, /*#__PURE__*/React.createElement(StagePill, {
    stage: stage
  }));
}
function AddActivityModal({
  projects,
  initialType,
  onSave,
  onClose
}) {
  const TYPES = [{
    id: 'note',
    label: 'Note',
    icon: 'StickyNote'
  }, {
    id: 'call',
    label: 'Call',
    icon: 'Phone'
  }, {
    id: 'email',
    label: 'Email',
    icon: 'Mail'
  }, {
    id: 'meeting',
    label: 'Meeting',
    icon: 'Calendar'
  }];
  const [type, setType] = React.useState(initialType || 'note');
  const [body, setBody] = React.useState('');
  const [project, setProject] = React.useState(projects[0] || '');
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__overlay",
    onClick: onClose
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__confirm",
    style: {
      width: 440,
      maxWidth: '90vw',
      alignItems: 'flex-start'
    },
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 14,
      fontWeight: 700,
      marginBottom: 14,
      alignSelf: 'center'
    }
  }, "Log activity"), /*#__PURE__*/React.createElement("div", {
    className: "tm-seg",
    style: {
      marginBottom: 14
    }
  }, TYPES.map(t => /*#__PURE__*/React.createElement("button", {
    key: t.id,
    className: cx(type === t.id && 'is-on'),
    onClick: () => setType(t.id)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: t.icon,
    size: 13,
    style: {
      marginRight: 4
    }
  }), t.label))), projects.length > 1 && /*#__PURE__*/React.createElement("div", {
    style: {
      width: '100%',
      marginBottom: 10
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      marginBottom: 4,
      textTransform: 'uppercase',
      letterSpacing: '.04em'
    }
  }, "Project"), /*#__PURE__*/React.createElement("select", {
    className: "tm-set-input",
    value: project,
    onChange: e => setProject(e.target.value)
  }, projects.map(p => /*#__PURE__*/React.createElement("option", {
    key: p,
    value: p
  }, p)))), /*#__PURE__*/React.createElement("textarea", {
    rows: 4,
    className: "tm-set-input",
    style: {
      resize: 'vertical',
      fontFamily: 'var(--font-sans)',
      fontSize: 13,
      width: '100%'
    },
    placeholder: "Add notes\u2026",
    value: body,
    onChange: e => setBody(e.target.value)
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 10,
      marginTop: 14,
      width: '100%'
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: onClose,
    style: {
      flex: 1
    }
  }, "Cancel"), /*#__PURE__*/React.createElement(Button, {
    disabled: !body.trim(),
    onClick: () => onSave({
      type,
      body,
      project
    }),
    style: {
      flex: 1
    }
  }, "Save"))));
}

// ── Contact status pill (lifecycle / compliance state) ───────────────────────
const CONTACT_STATUS_META = {
  'Active': {
    fg: '#1d4ed8',
    bg: 'rgba(37,99,235,.10)'
  },
  'Placed': {
    fg: 'var(--success-fg, #15803d)',
    bg: 'var(--success-bg, rgba(5,150,105,.10))'
  },
  'On hold': {
    fg: '#b45309',
    bg: 'rgba(245,158,11,.10)'
  },
  'Do not contact': {
    fg: '#b91c1c',
    bg: 'rgba(220,38,38,.10)'
  }
};
function StatusPill({
  status
}) {
  const m = CONTACT_STATUS_META[status] || CONTACT_STATUS_META['Active'];
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: m.bg,
      color: m.fg,
      gap: 5
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 6,
      height: 6,
      borderRadius: '50%',
      background: m.fg
    }
  }), status);
}
function dueMeta(dueDays) {
  if (dueDays < 0) return {
    label: `Overdue ${Math.abs(dueDays)}d`,
    fg: '#b91c1c',
    bg: 'rgba(220,38,38,.10)'
  };
  if (dueDays === 0) return {
    label: 'Due today',
    fg: '#b45309',
    bg: 'rgba(245,158,11,.12)'
  };
  if (dueDays <= 7) return {
    label: `Due in ${dueDays}d`,
    fg: 'var(--muted-foreground)',
    bg: 'var(--muted)'
  };
  return {
    label: `Due in ${Math.round(dueDays / 7)}w`,
    fg: 'var(--muted-foreground)',
    bg: 'var(--muted)'
  };
}

// ── Career history block ─────────────────────────────────────────────────────
function CdCareer({
  career,
  education,
  boards
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Briefcase",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Career history"), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '12px 16px 4px'
    }
  }, career.map((r, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: "tm-cd__career-row"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__career-rail"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__career-dot",
    style: {
      background: r.current ? 'var(--primary)' : 'var(--border)'
    }
  }), i < career.length - 1 && /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__career-line"
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      paddingBottom: 14,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 600,
      display: 'flex',
      alignItems: 'center',
      gap: 7
    }
  }, r.role, r.current && /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: 'color-mix(in srgb, var(--primary) 12%, transparent)',
      color: 'var(--primary)',
      fontSize: 9,
      padding: '0 6px'
    }
  }, "Current")), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      marginTop: 1
    }
  }, r.company), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)',
      marginTop: 2,
      fontVariantNumeric: 'tabular-nums'
    }
  }, r.period))))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 0,
      borderTop: '1px solid color-mix(in srgb, var(--border) 55%, transparent)'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      padding: '10px 16px'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__mini-label"
  }, "Education"), education.map((ed, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    style: {
      fontSize: 12,
      marginTop: 3
    }
  }, ed.degree, " \xB7 ", /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)'
    }
  }, ed.school, " \u2019", ed.year.slice(2))))), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      padding: '10px 16px',
      borderLeft: '1px solid color-mix(in srgb, var(--border) 55%, transparent)'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__mini-label"
  }, "Board seats"), boards.length === 0 ? /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      marginTop: 3
    }
  }, "\u2014") : boards.map((b, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    style: {
      fontSize: 12,
      marginTop: 3
    }
  }, b)))));
}
const DOC_META = {
  CV: {
    icon: 'FileText',
    fg: 'var(--primary)'
  },
  Reference: {
    icon: 'FileCheck',
    fg: '#6d28d9'
  },
  default: {
    icon: 'File',
    fg: 'var(--muted-foreground)'
  }
};

// ── ContactDetail — global person screen ─────────────────────────────────────
function ContactDetail({
  contactId,
  onBack,
  onGoToPipeline,
  onOpenAccount
}) {
  const contact = (window.TM_CONTACTS || []).find(c => c.id === contactId);
  if (!contact) return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      padding: 48,
      color: 'var(--muted-foreground)',
      fontSize: 14
    }
  }, "Contact not found."));
  const base = React.useMemo(() => window.tmBuildContactProfile(contact), [contactId]);
  const [profile, setProfile] = React.useState(() => ({
    email: base.email,
    phone: base.phone,
    linkedin: base.linkedin,
    location: base.location,
    remuneration: base.remuneration
  }));
  const save = (field, val) => setProfile(p => ({
    ...p,
    [field]: val
  }));
  const [entries, setEntries] = React.useState(() => {
    const seeded = (window.TM_CONTACT_ENTRIES || {})[contactId];
    if (seeded) return seeded;
    return (contact.pipelines || []).map((proj, i) => ({
      id: 'fb-' + i,
      project: proj,
      stage: 'Sourced',
      assignees: [base.owner],
      ageDays: contact.lastActivityDays
    }));
  });
  const [activities, setActivities] = React.useState(() => (window.TM_CONTACT_ACTIVITIES || {})[contactId] || []);
  const [tasks, setTasks] = React.useState(() => base.tasks.map(t => ({
    ...t
  })));
  const [docs] = React.useState(() => base.documents.map(d => ({
    ...d
  })));
  const [addModal, setAddModal] = React.useState(null); // null | type string
  const [addPlOpen, setAddPlOpen] = React.useState(false);
  const [taskOpen, setTaskOpen] = React.useState(false);
  const [taskDraft, setTaskDraft] = React.useState('');
  const [taskDue, setTaskDue] = React.useState(0);
  const [enriched, setEnriched] = React.useState(false);
  const changeStage = (entryId, newStage) => {
    const entry = entries.find(e => e.id === entryId);
    setEntries(prev => prev.map(e => e.id === entryId ? {
      ...e,
      stage: newStage
    } : e));
    setActivities(prev => [{
      id: 'ac-' + Date.now(),
      type: 'stage_change',
      fromStage: entry?.stage,
      toStage: newStage,
      project: entry?.project || '',
      author: base.owner,
      ageDays: 0
    }, ...prev]);
    window.showToast && window.showToast('Stage updated to ' + newStage);
  };
  const addToProject = proj => {
    setEntries(prev => [...prev, {
      id: 'ce-' + Date.now(),
      project: proj,
      stage: 'Sourced',
      assignees: [base.owner],
      ageDays: 0
    }]);
    setActivities(prev => [{
      id: 'ac-' + Date.now(),
      type: 'added',
      body: 'Added to search "' + proj + '"',
      project: proj,
      author: base.owner,
      ageDays: 0
    }, ...prev]);
    setAddPlOpen(false);
    window.showToast && window.showToast('Added to ' + proj);
  };
  const removeFromProject = entryId => {
    setEntries(prev => prev.filter(e => e.id !== entryId));
    window.showToast && window.showToast('Removed from search');
  };
  const addActivity = ({
    type,
    body,
    project
  }) => {
    setActivities(prev => [{
      id: 'ac-' + Date.now(),
      type,
      body,
      project,
      author: base.owner,
      ageDays: 0
    }, ...prev]);
    setAddModal(null);
    window.showToast && window.showToast('Activity logged');
  };
  const toggleTask = id => setTasks(ts => ts.map(t => t.id === id ? {
    ...t,
    done: !t.done
  } : t));
  const addTask = () => {
    const v = taskDraft.trim();
    if (!v) return;
    setTasks(ts => [...ts, {
      id: 'tk-' + Date.now(),
      title: v,
      dueDays: taskDue,
      assignee: base.owner,
      done: false
    }]);
    setTaskDraft('');
    setTaskDue(0);
    setTaskOpen(false);
    window.showToast && window.showToast('Follow-up added');
  };
  const enrich = () => {
    setEnriched(true);
    window.showToast && window.showToast('Profile enriched from talent map');
  };
  const projectList = entries.map(e => e.project);
  const allProjects = (window.TM_PROJECTS || []).map(p => p.name);
  const availableProjects = allProjects.filter(p => !projectList.includes(p));
  const sortedTasks = [...tasks].sort((a, b) => a.done - b.done || a.dueDays - b.dueDays);
  const openTasks = tasks.filter(t => !t.done).length;
  const accountOffLimits = window.tmOffLimitsFor && window.tmOffLimitsFor(contact.company) || null;
  const offLimits = base.offLimits || accountOffLimits;
  const flagged = base.status === 'Do not contact' || offLimits;
  const hasAccount = !!(window.tmFindAccountByName && window.tmFindAccountByName(contact.company));
  const ownerName = {
    LH: 'Layla Hassan',
    OK: 'Omar Khalil',
    SM: 'Sara Mitchell',
    FO: 'Farah Obeid',
    YM: 'You'
  }[base.owner] || base.owner;
  const ctype = base.type || 'Candidate';
  const isCandidate = ctype === 'Candidate';
  const typeMeta = (window.TM_CONTACT_TYPE_META || {})[ctype] || {};
  const hiringFor = base.hiringFor || [];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__head",
    style: {
      paddingBottom: flagged ? 16 : 22,
      marginBottom: flagged ? 14 : 28,
      borderBottom: flagged ? 'none' : '1px solid var(--border)'
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn",
    style: {
      width: 30,
      height: 30,
      flexShrink: 0
    },
    onClick: onBack,
    title: "Back to contacts"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowLeft",
    size: 16
  })), /*#__PURE__*/React.createElement(Avatar, {
    name: contact.name,
    size: 42,
    tone: isCandidate ? 'primary' : 'neutral'
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 20,
      fontWeight: 700,
      lineHeight: 1.2,
      display: 'flex',
      alignItems: 'center',
      gap: 9
    }
  }, contact.name, /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: typeMeta.bg,
      color: typeMeta.fg,
      gap: 5
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: typeMeta.icon || 'User',
    size: 11
  }), typeMeta.label || ctype), isCandidate && /*#__PURE__*/React.createElement(StatusPill, {
    status: base.status
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      color: 'var(--muted-foreground)',
      marginTop: 3,
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      flexWrap: 'wrap'
    }
  }, /*#__PURE__*/React.createElement("span", null, contact.title, " \xB7 ", hasAccount ? /*#__PURE__*/React.createElement("button", {
    className: "tm-cd__company-link",
    onClick: () => onOpenAccount && onOpenAccount(contact.company)
  }, contact.company, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUpRight",
    size: 12
  })) : /*#__PURE__*/React.createElement("span", {
    style: {
      fontWeight: 500,
      color: 'var(--foreground)'
    }
  }, contact.company)), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 3
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 12
  }), profile.location))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginLeft: 'auto',
      display: 'flex',
      gap: 8,
      alignItems: 'center'
    }
  }, isCandidate ? /*#__PURE__*/React.createElement(AvailPill, {
    availability: contact.availability
  }) : ctype === 'Client' && /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: 'var(--muted)',
      color: 'var(--muted-foreground)'
    }
  }, contact.clientRole || 'Client'), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Send",
    size: 14
  }), "Email"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Phone",
    size: 14
  }), "Call"), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: () => setAddModal('note')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 14
  }), "Log activity"))), flagged && /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      padding: '9px 14px',
      borderRadius: 10,
      marginBottom: 22,
      background: 'rgba(220,38,38,.07)',
      border: '1px solid rgba(220,38,38,.18)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldAlert",
    size: 15,
    color: "#b91c1c"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12.5,
      color: '#b91c1c',
      fontWeight: 500
    }
  }, offLimits ? /*#__PURE__*/React.createElement(React.Fragment, null, "Off-limits \u2014 ", offLimits.reason, ". Do not approach for new mandates.") : /*#__PURE__*/React.createElement(React.Fragment, null, "Marked \u201CDo not contact\u201D. Consent withdrawn \u2014 exclude from all outreach."))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__grid"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 18
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Profile", /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    style: {
      marginLeft: 'auto'
    },
    onClick: enrich,
    disabled: enriched
  }, /*#__PURE__*/React.createElement(Icon, {
    name: enriched ? 'Check' : 'Sparkles',
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, enriched ? 'Enriched' : 'Enrich'))), /*#__PURE__*/React.createElement(CdField, {
    icon: "Mail",
    label: "Email",
    value: profile.email,
    onSave: v => save('email', v)
  }), /*#__PURE__*/React.createElement(CdField, {
    icon: "Phone",
    label: "Phone",
    value: profile.phone,
    onSave: v => save('phone', v)
  }), /*#__PURE__*/React.createElement(CdField, {
    icon: "Link",
    label: "LinkedIn",
    value: profile.linkedin,
    onSave: v => save('linkedin', v)
  }), /*#__PURE__*/React.createElement(CdField, {
    icon: "MapPin",
    label: "Location",
    value: profile.location,
    onSave: v => save('location', v)
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "BadgeCheck",
    size: 13
  }), "Level"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val"
  }, base.level)), isCandidate && /*#__PURE__*/React.createElement(CdField, {
    icon: "DollarSign",
    label: "Remuneration",
    value: profile.remuneration,
    onSave: v => save('remuneration', v)
  })), isCandidate && window.tmGetAiIntel && (() => {
    const ai = window.tmGetAiIntel(contact.name);
    if (!ai) return null;
    const scoreColor = ai.aiScore >= 80 ? '#059669' : ai.aiScore >= 60 ? '#b45309' : '#dc2626';
    const scoreBg = ai.aiScore >= 80 ? 'rgba(5,150,105,.09)' : ai.aiScore >= 60 ? 'rgba(245,158,11,.09)' : 'rgba(220,38,38,.09)';
    return /*#__PURE__*/React.createElement("div", {
      className: "tm-cd__card"
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-cd__card-h",
      style: {
        color: '#7c3aed'
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Sparkles",
      size: 13,
      style: {
        marginRight: 5
      }
    }), "AI Assessment", /*#__PURE__*/React.createElement("span", {
      style: {
        marginLeft: 'auto',
        fontSize: 10,
        fontWeight: 400,
        color: 'var(--muted-foreground)',
        textTransform: 'none',
        letterSpacing: 0
      }
    }, "last run ", formatAge(contact.lastActivityDays))), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: '14px 16px'
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        alignItems: 'flex-start',
        gap: 12,
        marginBottom: 13
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        width: 56,
        height: 56,
        borderRadius: 12,
        background: scoreBg,
        border: '1.5px solid ' + scoreColor + '50',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 19,
        fontWeight: 800,
        color: scoreColor,
        lineHeight: 1,
        fontVariantNumeric: 'tabular-nums'
      }
    }, ai.aiScore, "%"), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 8.5,
        fontWeight: 700,
        color: scoreColor,
        textTransform: 'uppercase',
        letterSpacing: '.05em',
        marginTop: 2
      }
    }, "match")), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 0,
        paddingTop: 2
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        height: 5,
        borderRadius: 3,
        background: 'var(--muted)',
        overflow: 'hidden',
        marginBottom: 9
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        height: '100%',
        width: ai.aiScore + '%',
        background: scoreColor,
        borderRadius: 3
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        lineHeight: 1.7,
        color: 'var(--foreground)',
        fontStyle: 'italic'
      }
    }, ai.aiRationale))), ai.aiCriteria && ai.aiCriteria.length > 0 && /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 10,
        fontWeight: 700,
        textTransform: 'uppercase',
        letterSpacing: '.06em',
        color: 'var(--muted-foreground)',
        marginBottom: 7
      }
    }, "Matched criteria"), /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        flexWrap: 'wrap',
        gap: 5
      }
    }, ai.aiCriteria.map((c, i) => /*#__PURE__*/React.createElement("span", {
      key: i,
      className: "tm-pill",
      style: {
        fontSize: 10.5,
        background: 'rgba(124,58,237,.09)',
        color: '#7c3aed',
        gap: 4
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Check",
      size: 10
    }), c))))));
  })(), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Relationship"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserCircle",
    size: 13
  }), "Owner"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val",
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 7
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__av",
    style: {
      width: 20,
      height: 20,
      fontSize: 9
    }
  }, base.owner), ownerName)), ctype === 'Client' && /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserCheck",
    size: 13
  }), "Role"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val"
  }, contact.clientRole || 'Client contact')), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "GitBranch",
    size: 13
  }), "Source"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val"
  }, base.source)), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "CalendarPlus",
    size: 13
  }), "Added"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val",
    style: {
      color: 'var(--muted-foreground)'
    }
  }, formatAge(base.addedDays)))), isCandidate && /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldCheck",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Compliance"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Activity",
    size: 13
  }), "Status"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val"
  }, /*#__PURE__*/React.createElement(StatusPill, {
    status: base.status
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileBadge",
    size: 13
  }), "Consent"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val",
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 7
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      color: base.consent.state === 'Withdrawn' ? '#b91c1c' : base.consent.state === 'Pending' ? '#b45309' : 'var(--success-fg, #15803d)',
      fontWeight: 600
    }
  }, base.consent.state), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, "\xB7 ", formatAge(base.consent.ageDays)))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Ban",
    size: 13
  }), "Off-limits"), /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-val",
    style: {
      color: offLimits ? '#b91c1c' : 'var(--muted-foreground)'
    }
  }, offLimits ? offLimits.reason : 'None'))), isCandidate && /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Documents", /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    style: {
      marginLeft: 'auto'
    },
    onClick: () => window.showToast && window.showToast('Upload coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "Upload"))), docs.map((d, i) => {
    const m = DOC_META[d.kind] || DOC_META.default;
    return /*#__PURE__*/React.createElement("div", {
      key: i,
      className: "tm-cd__doc-row"
    }, /*#__PURE__*/React.createElement(Icon, {
      name: m.icon,
      size: 15,
      color: m.fg
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        fontWeight: 500,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, d.name), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 10.5,
        color: 'var(--muted-foreground)'
      }
    }, d.kind, " \xB7 ", d.size, " \xB7 ", formatAge(d.ageDays))), /*#__PURE__*/React.createElement("button", {
      className: "tm-rail__btn",
      style: {
        width: 24,
        height: 24,
        flexShrink: 0
      },
      title: "Download"
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Download",
      size: 13
    })));
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 18
    }
  }, isCandidate && /*#__PURE__*/React.createElement(CdCareer, {
    career: base.career,
    education: base.education,
    boards: base.boards
  }), isCandidate ? /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "In pipelines", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 8,
      fontSize: 12,
      fontWeight: 700,
      color: 'var(--foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, entries.length), /*#__PURE__*/React.createElement("div", {
    style: {
      marginLeft: 'auto',
      position: 'relative'
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    onClick: () => setAddPlOpen(o => !o),
    disabled: availableProjects.length === 0
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "Add to search")), addPlOpen && availableProjects.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__filter-drop",
    style: {
      right: 0,
      left: 'auto',
      minWidth: 220,
      maxHeight: 220,
      overflowY: 'auto'
    }
  }, availableProjects.map(p => /*#__PURE__*/React.createElement("button", {
    key: p,
    className: "tm-pl__filter-opt",
    onClick: () => addToProject(p)
  }, p))))), entries.length === 0 ? /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '20px 16px',
      fontSize: 13,
      color: 'var(--muted-foreground)'
    }
  }, "Not in any search yet.") : entries.map(e => /*#__PURE__*/React.createElement("div", {
    key: e.id,
    className: "tm-cd__pl-row tm-cd__pl-row--hov"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__pl-proj"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12.5,
      fontWeight: 600,
      marginBottom: 3,
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, e.project), /*#__PURE__*/React.createElement(AssigneeStack, {
    assignees: e.assignees
  })), /*#__PURE__*/React.createElement(CdStageSelector, {
    stage: e.stage,
    onChangeStage: s => changeStage(e.id, s)
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn",
    style: {
      width: 26,
      height: 26,
      flexShrink: 0
    },
    onClick: onGoToPipeline,
    title: "Open pipeline"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn tm-cd__pl-remove",
    style: {
      width: 26,
      height: 26,
      flexShrink: 0
    },
    onClick: () => removeFromProject(e.id),
    title: "Remove from search"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 13
  }))))) : /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Briefcase",
    size: 13,
    style: {
      marginRight: 5
    }
  }), ctype === 'Client' ? 'Hiring for' : 'Connected searches', /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 8,
      fontSize: 12,
      fontWeight: 700,
      color: 'var(--foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, hiringFor.length)), hiringFor.length === 0 ? /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '20px 16px',
      fontSize: 13,
      color: 'var(--muted-foreground)'
    }
  }, ctype === 'Client' ? 'No open mandates with this contact.' : 'No searches linked yet.') : hiringFor.map((proj, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: "tm-cd__pl-row tm-cd__pl-row--hov",
    style: {
      cursor: 'pointer'
    },
    onClick: onGoToPipeline
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__pl-proj"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12.5,
      fontWeight: 600,
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, proj)), /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn",
    style: {
      width: 26,
      height: 26,
      flexShrink: 0
    },
    onClick: onGoToPipeline,
    title: "Open search"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14
  }))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "CheckSquare",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Follow-ups", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 8,
      fontSize: 12,
      fontWeight: 700,
      color: openTasks ? 'var(--foreground)' : 'var(--muted-foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, openTasks), /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    style: {
      marginLeft: 'auto'
    },
    onClick: () => setTaskOpen(o => !o)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "Add"))), taskOpen && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '12px 14px',
      borderBottom: '1px solid color-mix(in srgb, var(--border) 55%, transparent)',
      display: 'flex',
      flexDirection: 'column',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("input", {
    autoFocus: true,
    className: "tm-cd__field-edit",
    style: {
      border: '1px solid var(--border)',
      borderRadius: 7,
      padding: '6px 10px',
      fontSize: 13
    },
    placeholder: "What needs to happen next?",
    value: taskDraft,
    onChange: e => setTaskDraft(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter') addTask();
      if (e.key === 'Escape') setTaskOpen(false);
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-seg",
    style: {
      flex: 1
    }
  }, [['Today', 0], ['In 3 days', 3], ['Next week', 7]].map(([l, d]) => /*#__PURE__*/React.createElement("button", {
    key: d,
    className: cx(taskDue === d && 'is-on'),
    onClick: () => setTaskDue(d)
  }, l))), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: addTask,
    disabled: !taskDraft.trim()
  }, "Add"))), tasks.length === 0 && !taskOpen ? /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '20px 16px',
      fontSize: 13,
      color: 'var(--muted-foreground)'
    }
  }, "No follow-ups scheduled.") : sortedTasks.map(t => {
    const dm = dueMeta(t.dueDays);
    return /*#__PURE__*/React.createElement("div", {
      key: t.id,
      className: "tm-cd__task-row"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-cd__task-check",
      onClick: () => toggleTask(t.id),
      title: t.done ? 'Mark not done' : 'Mark done'
    }, /*#__PURE__*/React.createElement(Icon, {
      name: t.done ? 'CheckSquare' : 'Square',
      size: 16,
      color: t.done ? 'var(--success, #059669)' : 'var(--muted-foreground)'
    })), /*#__PURE__*/React.createElement("span", {
      style: {
        flex: 1,
        fontSize: 13,
        minWidth: 0,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        textDecoration: t.done ? 'line-through' : 'none',
        color: t.done ? 'var(--muted-foreground)' : 'var(--foreground)'
      }
    }, t.title), !t.done && /*#__PURE__*/React.createElement("span", {
      className: "tm-pill",
      style: {
        background: dm.bg,
        color: dm.fg,
        fontSize: 10.5
      }
    }, dm.label), /*#__PURE__*/React.createElement("span", {
      className: "tm-pl__av",
      style: {
        width: 20,
        height: 20,
        fontSize: 9
      }
    }, t.assignee));
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Activity", /*#__PURE__*/React.createElement("div", {
    style: {
      marginLeft: 'auto',
      display: 'flex',
      gap: 5
    }
  }, [['note', 'Note', 'StickyNote'], ['call', 'Call', 'Phone'], ['email', 'Email', 'Mail'], ['meeting', 'Meeting', 'Calendar']].map(([t, label, ic]) => /*#__PURE__*/React.createElement("button", {
    key: t,
    className: "tm-add-pipeline-btn",
    onClick: () => setAddModal(t),
    title: 'Log ' + label.toLowerCase()
  }, /*#__PURE__*/React.createElement(Icon, {
    name: ic,
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, label))))), activities.length === 0 ? /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '20px 16px',
      fontSize: 13,
      color: 'var(--muted-foreground)'
    }
  }, "No activity yet.") : activities.map(a => {
    const m = ACT_META[a.type] || ACT_META.note;
    return /*#__PURE__*/React.createElement("div", {
      key: a.id,
      className: "tm-cd__act-item"
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-cd__act-ic",
      style: {
        background: m.bg,
        color: m.fg
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: m.icon,
      size: 13
    })), /*#__PURE__*/React.createElement("div", {
      className: "tm-cd__act-body"
    }, a.type === 'stage_change' ? /*#__PURE__*/React.createElement("span", {
      className: "tm-cd__act-text"
    }, "Stage changed ", /*#__PURE__*/React.createElement("b", null, a.fromStage), " \u2192 ", /*#__PURE__*/React.createElement("b", null, a.toStage)) : /*#__PURE__*/React.createElement("span", {
      className: "tm-cd__act-text"
    }, a.body), /*#__PURE__*/React.createElement("div", {
      className: "tm-cd__act-meta"
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-cd__act-proj-tag"
    }, a.project), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 10,
        color: 'var(--muted-foreground)'
      }
    }, formatAge(a.ageDays)), /*#__PURE__*/React.createElement("span", {
      className: "tm-pl__av",
      style: {
        width: 18,
        height: 18,
        fontSize: 8
      }
    }, a.author))));
  }))))), addModal && /*#__PURE__*/React.createElement(AddActivityModal, {
    projects: projectList.length ? projectList : ['General'],
    initialType: addModal,
    onSave: addActivity,
    onClose: () => setAddModal(null)
  }));
}

// ── PositionView ─────────────────────────────────────────────────────────────
function PositionView({
  project
}) {
  const seed = window.TM_POSITION || {};
  const [jdText, setJdText] = React.useState(seed.jdText || '');
  const [requirements, setReq] = React.useState(seed.requirements || '');
  const [reqExpanded, setReqExp] = React.useState(false);
  const [criteria, setCriteria] = React.useState(seed.criteria ? [...seed.criteria] : []);
  const [addingChip, setAddingChip] = React.useState(false);
  const [chipDraft, setChipDraft] = React.useState('');
  const [salary, setSalary] = React.useState(seed.salary || '');
  const [bonus, setBonus] = React.useState(seed.bonus || '');
  const [equity, setEquity] = React.useState(seed.equity || '');
  const [compNotes, setCompNotes] = React.useState(seed.compensationNotes || '');
  const [location, setLocation] = React.useState(seed.location || '');
  const REQ_LINES = 5;
  const lineCount = requirements.split('\n').length;
  const needsToggle = lineCount > REQ_LINES;
  const collapsedH = REQ_LINES * 22;
  const removeChip = i => setCriteria(c => c.filter((_, idx) => idx !== i));
  const commitChip = () => {
    const v = chipDraft.trim();
    if (v) setCriteria(c => [...c, v]);
    setChipDraft('');
    setAddingChip(false);
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "Position"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      color: 'var(--muted-foreground)',
      marginTop: 2
    }
  }, project)), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    onClick: () => window.showToast && window.showToast('AI generation coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14
  }), "Generate with AI")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'grid',
      gridTemplateColumns: '1fr 1fr',
      gap: 18,
      marginBottom: 18
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 18
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Job description"), seed.jdFile && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      padding: '8px 16px',
      fontSize: 13
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 13,
    color: "var(--primary)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--primary)',
      fontWeight: 500,
      flex: 1
    }
  }, seed.jdFile), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)',
      background: 'var(--muted)',
      borderRadius: 4,
      padding: '1px 6px'
    }
  }, "PDF")), /*#__PURE__*/React.createElement("div", {
    style: {
      height: 1,
      background: 'var(--border)',
      margin: '0 16px 4px'
    }
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '4px 12px 12px'
    }
  }, /*#__PURE__*/React.createElement("textarea", {
    className: "tm-set-input",
    style: {
      width: '100%',
      minHeight: 120,
      resize: 'vertical',
      fontSize: 13,
      fontFamily: 'var(--font-sans)',
      borderRadius: 8
    },
    placeholder: "Describe the role and key responsibilities\u2026",
    value: jdText,
    onChange: e => setJdText(e.target.value)
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Job requirements"), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '4px 12px 12px'
    }
  }, /*#__PURE__*/React.createElement("textarea", {
    className: "tm-set-input",
    style: {
      width: '100%',
      fontSize: 13,
      fontFamily: 'var(--font-sans)',
      borderRadius: 8,
      minHeight: needsToggle && !reqExpanded ? collapsedH : 80,
      maxHeight: needsToggle && !reqExpanded ? collapsedH : 'none',
      resize: needsToggle && !reqExpanded ? 'none' : 'vertical',
      overflow: needsToggle && !reqExpanded ? 'hidden' : 'auto'
    },
    placeholder: "Key competencies and interview questions to ask\u2026",
    value: requirements,
    onChange: e => setReq(e.target.value)
  }), needsToggle && /*#__PURE__*/React.createElement("button", {
    onClick: () => setReqExp(e => !e),
    style: {
      marginTop: 6,
      fontSize: 12,
      color: 'var(--primary)',
      background: 'none',
      border: 'none',
      cursor: 'pointer',
      padding: 0,
      fontFamily: 'inherit',
      display: 'flex',
      alignItems: 'center',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: reqExpanded ? 'ChevronUp' : 'ChevronDown',
    size: 13
  }), reqExpanded ? 'Show less \u2191' : 'Show more \u2193')))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 18
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Research criteria", /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    style: {
      marginLeft: 'auto',
      fontSize: 11,
      height: 22,
      padding: '0 8px'
    },
    onClick: () => window.showToast && window.showToast('Copy from another project coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Copy",
    size: 12
  }), "Copy from\u2026")), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '8px 14px 4px',
      display: 'flex',
      flexWrap: 'wrap',
      gap: 6,
      minHeight: 32
    }
  }, criteria.length === 0 && !addingChip && /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      color: 'var(--muted-foreground)',
      fontStyle: 'italic'
    }
  }, "No criteria added yet"), criteria.map((c, i) => /*#__PURE__*/React.createElement("span", {
    key: i,
    className: "tm-pill",
    style: {
      background: 'var(--muted)',
      color: 'var(--foreground)',
      gap: 5
    }
  }, c, /*#__PURE__*/React.createElement("button", {
    onClick: () => removeChip(i),
    style: {
      background: 'none',
      border: 'none',
      cursor: 'pointer',
      padding: 0,
      color: 'var(--muted-foreground)',
      display: 'flex',
      lineHeight: 1
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 10
  })))), addingChip && /*#__PURE__*/React.createElement("input", {
    autoFocus: true,
    className: "tm-cd__field-edit",
    style: {
      fontSize: 12,
      padding: '2px 10px',
      borderRadius: 9999,
      minWidth: 130,
      height: 26
    },
    value: chipDraft,
    onChange: e => setChipDraft(e.target.value),
    onBlur: commitChip,
    onKeyDown: e => {
      if (e.key === 'Enter') commitChip();
      if (e.key === 'Escape') {
        setChipDraft('');
        setAddingChip(false);
      }
    },
    placeholder: "Type and press Enter\u2026"
  })), /*#__PURE__*/React.createElement("button", {
    onClick: () => {
      setAddingChip(true);
      setChipDraft('');
    },
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 5,
      margin: '4px 14px 12px',
      fontSize: 12,
      color: 'var(--primary)',
      background: 'none',
      border: 'none',
      cursor: 'pointer',
      padding: 0,
      fontFamily: 'inherit',
      fontWeight: 500
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 13
  }), "Add criteria")), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Compensation"), /*#__PURE__*/React.createElement(CdField, {
    icon: "DollarSign",
    label: "Salary",
    value: salary,
    onSave: setSalary
  }), /*#__PURE__*/React.createElement(CdField, {
    icon: "TrendingUp",
    label: "Bonus",
    value: bonus,
    onSave: setBonus
  }), /*#__PURE__*/React.createElement(CdField, {
    icon: "Percent",
    label: "Equity",
    value: equity,
    onSave: setEquity
  }), /*#__PURE__*/React.createElement(CdField, {
    icon: "FileText",
    label: "Notes",
    value: compNotes,
    onSave: setCompNotes,
    multiline: true
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 13,
    style: {
      marginRight: 4
    }
  }), "Location"), /*#__PURE__*/React.createElement(CdField, {
    icon: "MapPin",
    label: "Target geography",
    value: location,
    onSave: setLocation
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      margin: '8px 14px 14px',
      border: '1px solid var(--border)',
      borderRadius: 10,
      height: 80,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      color: 'var(--muted-foreground)',
      fontSize: 12,
      fontStyle: 'italic'
    }
  }, "Map preview \u2014 geography from search"))));
}

// ── EditableCell — inline-editable table cell ────────────────────────────────
function EditableCell({
  value,
  onSave,
  style,
  placeholder
}) {
  const [editing, setEditing] = React.useState(false);
  const [draft, setDraft] = React.useState(value);
  React.useEffect(() => {
    if (!editing) setDraft(value);
  }, [value, editing]);
  const commit = () => {
    setEditing(false);
    if (draft !== value) onSave(draft);
  };
  if (editing) {
    return /*#__PURE__*/React.createElement("input", {
      autoFocus: true,
      className: "tm-cd__field-edit",
      style: {
        fontSize: 13,
        padding: '2px 6px',
        borderRadius: 4,
        width: '100%'
      },
      value: draft,
      onChange: e => setDraft(e.target.value),
      onBlur: commit,
      onKeyDown: e => {
        if (e.key === 'Enter') commit();
        if (e.key === 'Escape') {
          setDraft(value);
          setEditing(false);
        }
      }
    });
  }
  return /*#__PURE__*/React.createElement("div", {
    onClick: () => {
      setDraft(value);
      setEditing(true);
    },
    title: "Click to edit",
    style: {
      cursor: 'text',
      minHeight: 20,
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap',
      ...style
    }
  }, value || /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontStyle: 'italic',
      fontSize: 12
    }
  }, placeholder));
}

// ── StrategyView — Layer 01: Strategic mandate definition ──────────────────
function StrategyView({
  project
}) {
  const seed = window.TM_STRATEGY || {};

  // ── Mandate lock ──────────────────────────────────────────────────────────
  const [genLoading, setGenLoading] = React.useState(false);

  // ── Weighted criteria — the AI scoring basis ──────────────────────────────
  const [criteria, setCriteria] = React.useState([{
    id: 'w1',
    label: 'FMCG or consumer goods background',
    weight: 5,
    required: true
  }, {
    id: 'w2',
    label: 'GCC market experience',
    weight: 5,
    required: true
  }, {
    id: 'w3',
    label: 'C-suite technology leadership',
    weight: 4,
    required: true
  }, {
    id: 'w4',
    label: 'Digital transformation track record',
    weight: 4,
    required: false
  }, {
    id: 'w5',
    label: 'P&L ownership >$500M',
    weight: 4,
    required: false
  }, {
    id: 'w6',
    label: 'Listed company exposure',
    weight: 3,
    required: false
  }, {
    id: 'w7',
    label: 'Board-level presence',
    weight: 3,
    required: false
  }, {
    id: 'w8',
    label: 'Multi-country operations',
    weight: 3,
    required: false
  }, {
    id: 'w9',
    label: 'Arabic language preferred',
    weight: 2,
    required: false
  }]);
  const [addingCrit, setAddingCrit] = React.useState(false);
  const [critDraft, setCritDraft] = React.useState('');

  // ── Company context fingerprint ───────────────────────────────────────────
  const [ctx, setCtx] = React.useState({
    culture: 'Family-owned, professionalising governance ahead of 2027 IPO. Conservative, relationship-driven culture. Arabic-speaking leadership preferred at board level.',
    leadership: 'Reports to Chairman (Sultan Al Rabie) — personal sponsor. Board involvement in shortlist review. CFO already placed by firm (established trust).',
    constraints: 'Current CIO departing Q2 — 90-day urgency. No direct poaching from Almarai (informal courtesy). Must be UAE/KSA-based or willing to relocate.'
  });

  // ── Sector scope ─────────────────────────────────────────────────────────
  const [sectors, setSectors] = React.useState({
    direct: ['FMCG', 'Food & Beverage', 'Consumer Goods', 'Food Manufacturing'],
    adjacent: ['Retail', 'Agri-Business', 'Food Service', 'Packaged Goods', 'Logistics'],
    excluded: ['Banking & Financial Services', 'Telecom', 'Public Sector', 'Consulting']
  });
  const [addingSector, setAddingSector] = React.useState(null);
  const [sectorDraft, setSectorDraft] = React.useState('');
  const SECTOR_META = {
    direct: {
      label: 'Direct',
      color: '#059669',
      bg: 'rgba(5,150,105,.09)'
    },
    adjacent: {
      label: 'Adjacent',
      color: '#1d4ed8',
      bg: 'rgba(37,99,235,.09)'
    },
    excluded: {
      label: 'Excluded',
      color: 'var(--muted-foreground)',
      bg: 'var(--muted)'
    }
  };

  // ── Search profile ────────────────────────────────────────────────────────
  const [profile, setProfile] = React.useState({
    industry: seed.industry || 'FMCG & Consumer Goods',
    specialty: seed.specialty || 'Technology & Digital',
    seniority: seed.seniority || 'C-Suite',
    locationFocus: seed.locationFocus || 'UAE, Saudi Arabia',
    clientCompany: seed.clientCompany || 'Al Rabie Saudi Foods Co.',
    salary: 'AED 850,000 – 1,100,000 base',
    compensation: '30–40% bonus · LTIP eligible (Year 2+)'
  });

  // ── AI search brief ───────────────────────────────────────────────────────
  const [searchParams, setSearchParams] = React.useState(seed.searchParams || 'Targeting senior technology executives with a proven FMCG transformation track record in GCC markets. Priority on candidates with P&L ownership, enterprise ERP rollouts, and experience scaling digital operations across multi-country environments. The role demands executive presence at board level, strong Arabic communication, and demonstrable experience leading technology teams through M&A integrations in the region.');

  // ── Target companies ──────────────────────────────────────────────────────
  const [companies, setCompanies] = React.useState(seed.targetCompanies ? [...seed.targetCompanies] : []);
  const STATUS_OPTS = ['Target', 'Approached', 'Off-limits'];
  const STATUS_STYLE = {
    'Target': {
      color: '#1d4ed8',
      background: 'rgba(37,99,235,.08)'
    },
    'Approached': {
      color: 'hsl(35,92%,40%)',
      background: 'rgba(245,158,11,.08)'
    },
    'Off-limits': {
      color: 'var(--muted-foreground)',
      background: 'var(--muted)'
    }
  };
  const addCompany = () => setCompanies(c => [...c, {
    id: 'tc-' + Date.now(),
    name: '',
    status: 'Target',
    notes: ''
  }]);
  const removeCompany = id => setCompanies(c => c.filter(co => co.id !== id));
  const updateCompany = (id, field, val) => setCompanies(c => c.map(co => co.id === id ? {
    ...co,
    [field]: val
  } : co));
  const cycleStatus = id => {
    setCompanies(c => c.map(co => {
      if (co.id !== id) return co;
      return {
        ...co,
        status: STATUS_OPTS[(STATUS_OPTS.indexOf(co.status) + 1) % STATUS_OPTS.length]
      };
    }));
  };

  // ── Universe stats ────────────────────────────────────────────────────────
  const pipelineCount = (window.TM_PIPELINE || []).length;
  const universeCount = (window.TM_COMPANIES || []).length;
  const llCount = (window.TM_LONG_LIST || []).length;

  // ── Mandate completeness ──────────────────────────────────────────────────
  const completeness = React.useMemo(() => {
    let s = 0;
    if (criteria.length >= 3) s += 30;
    if (ctx.culture.trim() && ctx.leadership.trim()) s += 20;
    if (searchParams.trim().length > 50) s += 20;
    if (companies.length >= 3) s += 20;
    if (sectors.direct.length >= 2) s += 10;
    return s;
  }, [criteria, ctx, searchParams, companies, sectors]);

  // ── Strategy versioning & change detection ────────────────────────────────
  const [version, setVersion] = React.useState('1.0');
  const [svHistory, setSvHistory] = React.useState([{
    v: '1.0',
    label: 'Initial mandate from AI analysis',
    ts: Date.now()
  }]);
  const [lastApplied, setLastApplied] = React.useState(() => ({
    criteriaCount: criteria.length,
    weights: criteria.map(c => c.weight).join(','),
    requiredCount: criteria.filter(c => c.required).length,
    directCount: sectors.direct.length,
    adjacentCount: sectors.adjacent.length
  }));
  const [showImpact, setShowImpact] = React.useState(false);
  const [showFork, setShowFork] = React.useState(false);
  const changes = React.useMemo(() => {
    const la = lastApplied;
    const ac = criteria.length - la.criteriaCount;
    const ad = sectors.direct.length - la.directCount;
    const aa = sectors.adjacent.length - la.adjacentCount;
    const wc = criteria.map(c => c.weight).join(',') !== la.weights;
    const rc = criteria.filter(c => c.required).length !== la.requiredCount;
    const isAdd = (ac > 0 || ad > 0 || aa > 0) && !wc && !rc;
    const isRecal = wc || rc;
    const isMajor = Math.abs(ac) >= 3 || wc && rc && Math.abs(ac) >= 1;
    const total = Math.abs(ac) + Math.abs(ad) + Math.abs(aa) + (wc ? 1 : 0) + (rc ? 1 : 0);
    const desc = [];
    if (ac !== 0) desc.push(Math.abs(ac) + ' criteria ' + (ac > 0 ? 'added' : 'removed'));
    if (ad !== 0 || aa !== 0) desc.push(Math.abs(ad) + Math.abs(aa) + ' sector' + (Math.abs(ad) + Math.abs(aa) !== 1 ? 's' : '') + ' changed');
    if (wc) desc.push('weights adjusted');
    if (rc) desc.push('required/preferred changed');
    return {
      total,
      isAdd,
      isRecal,
      isMajor,
      desc
    };
  }, [criteria, sectors, lastApplied]);
  const hasChanges = changes.total > 0;
  const nextVer = React.useMemo(() => {
    const [maj, min] = version.split('.').map(Number);
    return changes.isRecal || changes.isMajor ? maj + 1 + '.0' : maj + '.' + (min + 1);
  }, [version, changes]);
  const commitChanges = () => {
    const nv = nextVer;
    setVersion(nv);
    setSvHistory(h => [...h, {
      v: nv,
      label: changes.desc.join(' · '),
      ts: Date.now()
    }]);
    setLastApplied({
      criteriaCount: criteria.length,
      weights: criteria.map(c => c.weight).join(','),
      requiredCount: criteria.filter(c => c.required).length,
      directCount: sectors.direct.length,
      adjacentCount: sectors.adjacent.length
    });
    window.__HAK_SV = nv;
    window.__HAK_SV_TS = Date.now();
    setShowImpact(false);
    setShowFork(false);
    const typeLabel = changes.isMajor ? 'Major update' : changes.isRecal ? 'Recalibration' : 'Additive update';
    window.showToast && window.showToast('Strategy v' + nv + ' applied — ' + typeLabel.toLowerCase());
  };
  const applyChanges = () => {
    if (changes.isMajor) {
      setShowFork(true);
      return;
    }
    if (changes.isRecal) {
      setShowImpact(true);
      return;
    }
    commitChanges();
  };
  React.useEffect(() => {
    window.__HAK_SV = version;
    window.__HAK_SV_TS = Date.now();
  }, []);
  const gaps = [];
  if (criteria.length < 3) gaps.push('Add weighted criteria');
  if (!ctx.culture.trim()) gaps.push('Describe client culture');
  if (searchParams.trim().length < 50) gaps.push('Write AI search brief');
  if (companies.length < 3) gaps.push('Add target companies');
  if (sectors.direct.length < 2) gaps.push('Define sector scope');
  const scoreColor = completeness >= 80 ? '#059669' : completeness >= 50 ? '#b45309' : '#dc2626';

  // ── Criteria helpers ──────────────────────────────────────────────────────
  const updateCrit = (id, field, val) => setCriteria(cs => cs.map(c => c.id === id ? {
    ...c,
    [field]: val
  } : c));
  const removeCrit = id => setCriteria(cs => cs.filter(c => c.id !== id));
  const commitCrit = () => {
    const v = critDraft.trim();
    if (v) setCriteria(cs => [...cs, {
      id: 'w-' + Date.now(),
      label: v,
      weight: 3,
      required: false
    }]);
    setCritDraft('');
    setAddingCrit(false);
  };

  // ── Sector helpers ────────────────────────────────────────────────────────
  const removeSector = (cat, name) => setSectors(s => ({
    ...s,
    [cat]: s[cat].filter(x => x !== name)
  }));
  const commitSector = () => {
    const v = sectorDraft.trim();
    if (v && addingSector) setSectors(s => ({
      ...s,
      [addingSector]: [...s[addingSector], v]
    }));
    setSectorDraft('');
    setAddingSector(null);
  };

  // ── Lock / generate ───────────────────────────────────────────────────────

  const generateBrief = () => {
    setGenLoading(true);
    setTimeout(() => {
      setSearchParams('Targeting senior technology executives with a proven FMCG transformation track record in GCC markets. Priority on candidates with P&L ownership, enterprise ERP rollouts, and experience scaling digital operations across multi-country environments. The role demands executive presence at board level, strong Arabic communication, and demonstrable experience leading technology teams through M&A integrations in the region. Adjacent sector candidates from consumer retail and logistics will be considered where GCC digital leadership credentials are exceptional.');
      setGenLoading(false);
      window.showToast && window.showToast('AI search brief generated from Position brief + Strategy profile');
    }, 1400);
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header-left"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      marginBottom: 6
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10,
      letterSpacing: '.08em'
    }
  }, "Layer 01 \xB7 Strategy"), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 10,
      color: 'var(--muted-foreground)'
    }
  }, "\xB7"), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      fontWeight: 600
    }
  }, project), /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      fontSize: 10,
      gap: 4,
      background: completeness >= 80 ? 'rgba(5,150,105,.10)' : 'rgba(245,158,11,.10)',
      color: completeness >= 80 ? '#059669' : '#b45309'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: completeness >= 80 ? 'CheckCircle2' : 'Clock',
    size: 10
  }), completeness >= 80 ? 'Ready' : 'In progress')), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: 200,
      height: 4,
      borderRadius: 2,
      background: 'var(--muted)',
      overflow: 'hidden'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      height: '100%',
      width: completeness + '%',
      borderRadius: 2,
      background: scoreColor,
      transition: 'width .4s'
    }
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontWeight: 600,
      color: scoreColor
    }
  }, completeness, "%"), " complete", gaps.length > 0 && /*#__PURE__*/React.createElement("span", {
    style: {
      color: '#b45309'
    }
  }, " \xB7 ", gaps[0]))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header-right"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 5
    }
  }, [['Building2', universeCount + ' companies'], ['ListChecks', llCount + ' long list'], ['Kanban', pipelineCount + ' pipeline']].map(([ic, label]) => /*#__PURE__*/React.createElement("span", {
    key: label,
    className: "tm-pill",
    style: {
      background: 'var(--muted)',
      color: 'var(--muted-foreground)',
      fontSize: 11
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: ic,
    size: 11
  }), label))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__board-wrap",
    style: {
      overflowY: 'auto',
      overflowX: 'hidden'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '20px 20px 64px',
      display: 'flex',
      flexDirection: 'column',
      gap: 16
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'grid',
      gridTemplateColumns: '1fr 360px',
      gap: 16
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13,
    style: {
      marginRight: 5,
      color: '#7c3aed'
    }
  }), "Weighted search criteria", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6,
      fontSize: 10.5,
      fontWeight: 400,
      color: 'var(--muted-foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, "\u2014 each criterion's weight drives AI match scores"), /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    style: {
      marginLeft: 'auto'
    },
    onClick: () => setAddingCrit(true)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "Add"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'grid',
      gridTemplateColumns: '1fr 120px 84px 24px',
      gap: 10,
      padding: '5px 16px',
      borderBottom: '1px solid var(--border)',
      fontSize: 10,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      textTransform: 'uppercase',
      letterSpacing: '.05em'
    }
  }, /*#__PURE__*/React.createElement("span", null, "Criterion"), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 4
    }
  }, "Weight ", /*#__PURE__*/React.createElement("span", {
    style: {
      fontWeight: 400,
      textTransform: 'none'
    }
  }, "(1\u20135)")), /*#__PURE__*/React.createElement("span", null, "Type"), /*#__PURE__*/React.createElement("span", null)), criteria.map(item => /*#__PURE__*/React.createElement("div", {
    key: item.id,
    style: {
      display: 'grid',
      gridTemplateColumns: '1fr 120px 84px 24px',
      gap: 10,
      padding: '8px 16px',
      borderBottom: '1px solid color-mix(in srgb, var(--border) 50%, transparent)',
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 7,
      fontSize: 13,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 6,
      height: 6,
      borderRadius: '50%',
      flexShrink: 0,
      background: item.required ? '#b91c1c' : 'var(--border)'
    },
    title: item.required ? 'Required' : 'Preferred'
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, item.label)), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 5,
      alignItems: 'center'
    }
  }, [1, 2, 3, 4, 5].map(n => /*#__PURE__*/React.createElement("button", {
    key: n,
    onClick: () => updateCrit(item.id, 'weight', item.weight === n && n > 1 ? n - 1 : n),
    title: 'Weight ' + n + '/5',
    style: {
      width: 14,
      height: 14,
      borderRadius: '50%',
      border: 'none',
      padding: 0,
      cursor: 'pointer',
      transition: 'background .12s, transform .1s',
      background: n <= item.weight ? 'var(--primary)' : 'var(--muted)'
    }
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 10,
      color: 'var(--muted-foreground)',
      marginLeft: 2,
      fontVariantNumeric: 'tabular-nums'
    }
  }, item.weight, "/5")), /*#__PURE__*/React.createElement("button", {
    onClick: () => updateCrit(item.id, 'required', !item.required),
    className: "tm-pill",
    style: {
      border: 'none',
      cursor: 'pointer',
      fontSize: 10,
      justifyContent: 'center',
      background: item.required ? 'rgba(185,28,28,.10)' : 'rgba(37,99,235,.09)',
      color: item.required ? '#b91c1c' : '#1d4ed8'
    }
  }, item.required ? 'Required' : 'Preferred'), /*#__PURE__*/React.createElement("button", {
    onClick: () => removeCrit(item.id),
    style: {
      background: 'none',
      border: 'none',
      cursor: 'pointer',
      padding: 0,
      color: 'var(--muted-foreground)',
      display: 'flex',
      opacity: .35,
      transition: 'opacity .12s'
    },
    onMouseOver: e => e.currentTarget.style.opacity = 1,
    onMouseOut: e => e.currentTarget.style.opacity = .35
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 13
  })))), addingCrit && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '10px 16px',
      borderBottom: '1px solid var(--border)'
    }
  }, /*#__PURE__*/React.createElement("input", {
    autoFocus: true,
    className: "tm-cd__field-edit",
    style: {
      width: '100%',
      fontSize: 13,
      padding: '5px 10px',
      borderRadius: 6,
      border: '1px solid var(--border)'
    },
    placeholder: "New criterion \u2014 e.g. M&A integration experience",
    value: critDraft,
    onChange: e => setCritDraft(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter') commitCrit();
      if (e.key === 'Escape') {
        setCritDraft('');
        setAddingCrit(false);
      }
    },
    onBlur: commitCrit
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '10px 16px',
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      background: 'rgba(124,58,237,.03)',
      borderTop: '1px solid var(--border)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 12,
    color: "#7c3aed"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11.5,
      color: 'var(--muted-foreground)'
    }
  }, /*#__PURE__*/React.createElement("b", {
    style: {
      color: 'var(--foreground)'
    }
  }, criteria.filter(c => c.required).length, " required"), ' · ', /*#__PURE__*/React.createElement("b", {
    style: {
      color: 'var(--foreground)'
    }
  }, criteria.filter(c => !c.required).length, " preferred"), ' · avg weight ', /*#__PURE__*/React.createElement("b", {
    style: {
      color: 'var(--foreground)'
    }
  }, (criteria.reduce((s, c) => s + c.weight, 0) / Math.max(1, criteria.length)).toFixed(1), "/5")), /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 'auto',
      fontSize: 11,
      color: 'var(--muted-foreground)',
      fontStyle: 'italic'
    }
  }, "These weights drive every AI match score"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 14
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Client context"), [{
    key: 'culture',
    icon: 'Users',
    label: 'Culture & style'
  }, {
    key: 'leadership',
    icon: 'UserCircle',
    label: 'Leadership & sponsor'
  }, {
    key: 'constraints',
    icon: 'AlertCircle',
    label: 'Key constraints'
  }].map(({
    key,
    icon,
    label
  }) => /*#__PURE__*/React.createElement("div", {
    key: key,
    style: {
      padding: '8px 14px',
      borderBottom: '1px solid color-mix(in srgb, var(--border) 50%, transparent)',
      display: 'flex',
      gap: 10,
      alignItems: 'flex-start'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 5,
      fontSize: 11,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      flexShrink: 0,
      paddingTop: 2,
      width: 116
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 12
  }), label), /*#__PURE__*/React.createElement("textarea", {
    rows: 2,
    className: "tm-set-input",
    style: {
      flex: 1,
      resize: 'vertical',
      fontSize: 12,
      fontFamily: 'var(--font-sans)',
      borderRadius: 6,
      lineHeight: 1.65,
      minHeight: 44
    },
    value: ctx[key],
    onChange: e => setCtx(c => ({
      ...c,
      [key]: e.target.value
    }))
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Target",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Search profile"), /*#__PURE__*/React.createElement(CdField, {
    icon: "BarChart2",
    label: "Industry",
    value: profile.industry,
    onSave: v => setProfile(p => ({
      ...p,
      industry: v
    }))
  }), /*#__PURE__*/React.createElement(CdField, {
    icon: "Layers",
    label: "Specialty",
    value: profile.specialty,
    onSave: v => setProfile(p => ({
      ...p,
      specialty: v
    }))
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__field",
    style: {
      cursor: 'default',
      gap: 8,
      flexWrap: 'wrap'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cd__field-label"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Award",
    size: 13
  }), "Seniority"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 4
    }
  }, ['C-Suite', 'VP', 'Director'].map(opt => /*#__PURE__*/React.createElement("button", {
    key: opt,
    onClick: () => setProfile(p => ({
      ...p,
      seniority: opt
    })),
    className: "tm-pill",
    style: {
      cursor: 'pointer',
      border: 'none',
      fontSize: 10,
      transition: 'background .12s',
      background: profile.seniority === opt ? 'var(--primary)' : 'var(--muted)',
      color: profile.seniority === opt ? '#fff' : 'var(--muted-foreground)'
    }
  }, opt)))), /*#__PURE__*/React.createElement(CdField, {
    icon: "MapPin",
    label: "Geography",
    value: profile.locationFocus,
    onSave: v => setProfile(p => ({
      ...p,
      locationFocus: v
    }))
  }), /*#__PURE__*/React.createElement(CdField, {
    icon: "DollarSign",
    label: "Salary",
    value: profile.salary,
    onSave: v => setProfile(p => ({
      ...p,
      salary: v
    }))
  }), /*#__PURE__*/React.createElement(CdField, {
    icon: "TrendingUp",
    label: "Package",
    value: profile.compensation,
    onSave: v => setProfile(p => ({
      ...p,
      compensation: v
    }))
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Globe",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Sector scope", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6,
      fontSize: 10.5,
      fontWeight: 400,
      color: 'var(--muted-foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, "\u2014 AI sources candidates from Direct and Adjacent; skips Excluded")), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '14px 16px',
      display: 'flex',
      flexDirection: 'column',
      gap: 13
    }
  }, Object.entries(SECTOR_META).map(([cat, meta]) => /*#__PURE__*/React.createElement("div", {
    key: cat,
    style: {
      display: 'flex',
      alignItems: 'flex-start',
      gap: 14
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6,
      width: 84,
      flexShrink: 0,
      paddingTop: 4
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 8,
      height: 8,
      borderRadius: '50%',
      background: meta.color,
      flexShrink: 0
    }
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      fontWeight: 700,
      color: meta.color,
      textTransform: 'uppercase',
      letterSpacing: '.04em'
    }
  }, meta.label)), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6,
      flexWrap: 'wrap',
      flex: 1,
      alignItems: 'center'
    }
  }, sectors[cat].map(name => /*#__PURE__*/React.createElement("span", {
    key: name,
    className: "tm-pill",
    style: {
      background: meta.bg,
      color: meta.color,
      fontSize: 11,
      gap: 5,
      textDecoration: cat === 'excluded' ? 'line-through' : 'none'
    }
  }, name, /*#__PURE__*/React.createElement("button", {
    onClick: () => removeSector(cat, name),
    style: {
      background: 'none',
      border: 'none',
      cursor: 'pointer',
      padding: 0,
      color: meta.color,
      display: 'flex',
      opacity: .6,
      lineHeight: 1
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 9
  })))), addingSector === cat ? /*#__PURE__*/React.createElement("input", {
    autoFocus: true,
    className: "tm-cd__field-edit",
    style: {
      fontSize: 11,
      padding: '2px 9px',
      borderRadius: 9999,
      minWidth: 120,
      border: '1px solid var(--border)'
    },
    value: sectorDraft,
    onChange: e => setSectorDraft(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter') commitSector();
      if (e.key === 'Escape') {
        setSectorDraft('');
        setAddingSector(null);
      }
    },
    onBlur: commitSector,
    placeholder: "Add sector..."
  }) : /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    onClick: () => {
      setAddingSector(cat);
      setSectorDraft('');
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 10
  }), /*#__PURE__*/React.createElement("span", null, "Add"))))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13,
    style: {
      marginRight: 5,
      color: '#7c3aed'
    }
  }), "AI search brief", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6,
      fontSize: 10.5,
      fontWeight: 400,
      color: 'var(--muted-foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, "\u2014 the narrative the AI uses to reason about candidate fit"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    style: {
      marginLeft: 'auto',
      height: 24,
      padding: '0 10px',
      fontSize: 12,
      color: '#7c3aed'
    },
    onClick: generateBrief,
    disabled: genLoading
  }, /*#__PURE__*/React.createElement(Icon, {
    name: genLoading ? 'Loader' : 'Sparkles',
    size: 13,
    style: {
      color: '#7c3aed'
    }
  }), genLoading ? 'Generating\u2026' : 'Generate with AI')), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '8px 14px 14px'
    }
  }, /*#__PURE__*/React.createElement("textarea", {
    className: "tm-set-input",
    style: {
      width: '100%',
      minHeight: 88,
      resize: 'vertical',
      fontSize: 13,
      fontFamily: 'var(--font-sans)',
      borderRadius: 8,
      lineHeight: 1.75
    },
    placeholder: "Describe the ideal candidate profile and search rationale \u2014 or click Generate with AI to draft from your Position brief.",
    value: searchParams,
    onChange: e => setSearchParams(e.target.value)
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Target companies", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6,
      fontSize: 11,
      fontWeight: 700,
      color: 'var(--muted-foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, companies.filter(c => c.status !== 'Off-limits').length), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    style: {
      marginLeft: 'auto',
      height: 24,
      padding: '0 10px',
      fontSize: 12
    },
    onClick: addCompany
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 13
  }), "Add")), companies.length === 0 ? /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '28px 16px',
      textAlign: 'center',
      color: 'var(--muted-foreground)',
      fontSize: 13,
      fontStyle: 'italic'
    }
  }, "No target companies yet \u2014 add firms to define the source universe.") : /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'grid',
      gridTemplateColumns: '20px 1fr 108px 1fr 28px',
      gap: 8,
      padding: '5px 14px',
      borderBottom: '1px solid var(--border)',
      fontSize: 10,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      textTransform: 'uppercase',
      letterSpacing: '.05em'
    }
  }, /*#__PURE__*/React.createElement("span", null), /*#__PURE__*/React.createElement("span", null, "Company"), /*#__PURE__*/React.createElement("span", null, "Status"), /*#__PURE__*/React.createElement("span", null, "Notes"), /*#__PURE__*/React.createElement("span", null)), companies.map(co => {
    const isOff = co.status === 'Off-limits';
    const st = STATUS_STYLE[co.status] || STATUS_STYLE['Target'];
    return /*#__PURE__*/React.createElement("div", {
      key: co.id,
      style: {
        display: 'grid',
        gridTemplateColumns: '20px 1fr 108px 1fr 28px',
        gap: 8,
        padding: '7px 14px',
        borderBottom: '1px solid var(--border)',
        alignItems: 'center',
        opacity: isOff ? .45 : 1,
        transition: 'opacity .15s'
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)',
        cursor: 'grab',
        display: 'flex'
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "GripVertical",
      size: 13
    })), /*#__PURE__*/React.createElement(EditableCell, {
      value: co.name,
      onSave: v => updateCompany(co.id, 'name', v),
      style: {
        fontWeight: 500,
        fontSize: 13,
        textDecoration: isOff ? 'line-through' : 'none'
      },
      placeholder: "Company name\u2026"
    }), /*#__PURE__*/React.createElement("button", {
      onClick: () => cycleStatus(co.id),
      className: "tm-pill",
      style: {
        background: st.background,
        color: st.color,
        border: 'none',
        cursor: 'pointer',
        fontSize: 11
      },
      title: "Click to cycle status"
    }, co.status), /*#__PURE__*/React.createElement(EditableCell, {
      value: co.notes,
      onSave: v => updateCompany(co.id, 'notes', v),
      style: {
        fontSize: 12,
        color: 'var(--muted-foreground)'
      },
      placeholder: "Add notes\u2026"
    }), /*#__PURE__*/React.createElement("button", {
      onClick: () => removeCompany(co.id),
      style: {
        background: 'none',
        border: 'none',
        cursor: 'pointer',
        padding: 0,
        color: 'var(--muted-foreground)',
        display: 'flex',
        opacity: .35,
        transition: 'opacity .12s'
      },
      onMouseOver: e => e.currentTarget.style.opacity = 1,
      onMouseOut: e => e.currentTarget.style.opacity = .35
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "X",
      size: 14
    })));
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      padding: '4px 0',
      flexWrap: 'wrap'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "GitBranch",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 10,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      textTransform: 'uppercase',
      letterSpacing: '.05em'
    }
  }, "History"), svHistory.map((h, i) => /*#__PURE__*/React.createElement("span", {
    key: i,
    className: "tm-pill",
    title: h.label,
    style: {
      fontSize: 10,
      cursor: 'default',
      background: i === svHistory.length - 1 ? 'rgba(124,58,237,.09)' : 'var(--muted)',
      color: i === svHistory.length - 1 ? '#7c3aed' : 'var(--muted-foreground)'
    }
  }, "v", h.v, i === svHistory.length - 1 && ' (current)')), hasChanges && /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      fontSize: 10,
      background: 'rgba(245,158,11,.12)',
      color: '#b45309',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 9
  }), "v", nextVer, " pending"))), hasChanges && /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'sticky',
      bottom: 0,
      padding: '12px 20px',
      background: changes.isMajor ? 'rgba(220,38,38,.06)' : changes.isRecal ? 'rgba(245,158,11,.06)' : 'rgba(5,150,105,.05)',
      borderTop: '1px solid ' + (changes.isMajor ? 'rgba(220,38,38,.2)' : changes.isRecal ? 'rgba(245,158,11,.22)' : 'rgba(5,150,105,.2)'),
      display: 'flex',
      alignItems: 'center',
      gap: 12,
      zIndex: 5
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: changes.isMajor ? 'AlertTriangle' : changes.isRecal ? 'RefreshCw' : 'Plus',
    size: 15,
    color: changes.isMajor ? '#dc2626' : changes.isRecal ? '#b45309' : '#059669'
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 600,
      color: 'var(--foreground)'
    }
  }, changes.isMajor ? 'Major strategy change' : changes.isRecal ? 'Recalibration required' : 'Additive changes ready'), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11.5,
      color: 'var(--muted-foreground)',
      marginTop: 1
    }
  }, changes.desc.join(' · '), " \u2014 scores will ", changes.isRecal ? 'shift for ' + llCount + ' candidates' : 'extend to new candidates')), changes.isRecal && /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm",
    onClick: () => setShowImpact(true)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Eye",
    size: 13
  }), "Preview impact"), /*#__PURE__*/React.createElement("button", {
    className: "tm-btn tm-btn--default tm-btn--sm",
    onClick: applyChanges,
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: changes.isMajor ? 'AlertTriangle' : 'Check',
    size: 13
  }), "Apply v", nextVer))), showImpact && /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__overlay",
    onClick: () => setShowImpact(false)
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__confirm",
    style: {
      width: 520,
      maxWidth: '90vw',
      alignItems: 'flex-start',
      gap: 0
    },
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      marginBottom: 16
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 36,
      height: 36,
      borderRadius: 10,
      background: 'rgba(245,158,11,.10)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "RefreshCw",
    size: 18,
    color: "#b45309"
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 15,
      fontWeight: 700
    }
  }, "Impact preview \u2014 v", version, " \u2192 v", nextVer), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      marginTop: 2
    }
  }, changes.desc.join(' · ')))), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      fontWeight: 700,
      textTransform: 'uppercase',
      letterSpacing: '.05em',
      color: 'var(--muted-foreground)',
      marginBottom: 8
    }
  }, "Affected candidates"), /*#__PURE__*/React.createElement("div", {
    style: {
      width: '100%',
      border: '1px solid var(--border)',
      borderRadius: 8,
      overflow: 'hidden',
      marginBottom: 16
    }
  }, [{
    name: 'Amira Haddad',
    before: 94,
    after: 91,
    flag: false
  }, {
    name: 'Hamad Al Ketbi',
    before: 88,
    after: 85,
    flag: false
  }, {
    name: 'Khalid Mansour',
    before: 85,
    after: 82,
    flag: false
  }, {
    name: 'Nadia Fawzy',
    before: 71,
    after: 64,
    flag: true
  }, {
    name: 'Marcos Tavares',
    before: 38,
    after: 31,
    flag: true
  }].map((c, i) => {
    const delta = c.after - c.before;
    return /*#__PURE__*/React.createElement("div", {
      key: i,
      style: {
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '8px 14px',
        borderBottom: i < 4 ? '1px solid var(--border)' : 'none',
        background: c.flag ? 'rgba(220,38,38,.04)' : 'transparent'
      }
    }, /*#__PURE__*/React.createElement(Avatar, {
      name: c.name,
      size: 24,
      tone: "primary"
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 13,
        fontWeight: 500,
        flex: 1
      }
    }, c.name), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        color: 'var(--muted-foreground)',
        fontVariantNumeric: 'tabular-nums'
      }
    }, c.before, "%"), /*#__PURE__*/React.createElement(Icon, {
      name: "ArrowRight",
      size: 11,
      color: "var(--muted-foreground)"
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        fontWeight: 600,
        fontVariantNumeric: 'tabular-nums',
        color: delta >= 0 ? '#059669' : '#dc2626'
      }
    }, c.after, "%"), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 10,
        color: delta >= 0 ? '#059669' : '#dc2626',
        fontWeight: 600
      }
    }, delta >= 0 ? '+' : '', delta), c.flag && /*#__PURE__*/React.createElement("span", {
      className: "tm-pill",
      style: {
        fontSize: 9,
        background: 'rgba(220,38,38,.10)',
        color: '#dc2626',
        padding: '1px 6px'
      }
    }, "Flag"));
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      marginBottom: 18,
      padding: '10px 12px',
      borderRadius: 8,
      background: 'var(--muted)',
      width: '100%'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldAlert",
    size: 14,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      lineHeight: 1.5
    }
  }, "Pipeline candidates are ", /*#__PURE__*/React.createElement("b", {
    style: {
      color: 'var(--foreground)'
    }
  }, "never auto-removed"), ". Flagged candidates stay in pipeline with a review notice.")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      width: '100%'
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: () => setShowImpact(false),
    style: {
      flex: 1
    }
  }, "Cancel"), /*#__PURE__*/React.createElement("button", {
    className: "tm-btn tm-btn--default",
    onClick: commitChanges,
    style: {
      flex: 1,
      justifyContent: 'center'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 14
  }), "Apply v", nextVer, " & recalculate")))), showFork && /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__overlay",
    onClick: () => setShowFork(false)
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__confirm",
    style: {
      width: 480,
      maxWidth: '90vw',
      alignItems: 'flex-start',
      gap: 0
    },
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      marginBottom: 16
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 36,
      height: 36,
      borderRadius: 10,
      background: 'rgba(220,38,38,.10)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "AlertTriangle",
    size: 18,
    color: "#dc2626"
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 15,
      fontWeight: 700
    }
  }, "Major strategy change detected"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      marginTop: 2
    }
  }, changes.desc.join(' · ')))), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      color: 'var(--foreground)',
      lineHeight: 1.7,
      marginBottom: 18
    }
  }, "This change will significantly affect ", /*#__PURE__*/React.createElement("b", null, llCount, " assessed candidates"), " and ", /*#__PURE__*/React.createElement("b", null, pipelineCount, " active pipeline relationships"), ". You can amend this search or fork to a new one."), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 10,
      width: '100%',
      marginBottom: 18
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-btn tm-btn--default",
    onClick: commitChanges,
    style: {
      width: '100%',
      justifyContent: 'center',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "RefreshCw",
    size: 14
  }), "Amend this search", /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      opacity: .7
    }
  }, "\u2014 recalculate all scores")), /*#__PURE__*/React.createElement("button", {
    className: "tm-btn tm-btn--outline",
    onClick: () => {
      setShowFork(false);
      window.showToast && window.showToast('New search created from fork — original preserved');
    },
    style: {
      width: '100%',
      justifyContent: 'center',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "GitFork",
    size: 14
  }), "Fork to new search", /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      opacity: .7
    }
  }, "\u2014 preserve current, start fresh")), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    onClick: () => setShowFork(false),
    style: {
      width: '100%',
      justifyContent: 'center'
    }
  }, "Cancel")))));
}

// ── LongListView ─────────────────────────────────────────────────────────────
// ── Sync-to-ATS menu (the talent map is the exportable asset) ────────────────
function SyncToAtsButton({
  count,
  label = 'Sync to ATS'
}) {
  const [open, setOpen] = React.useState(false);
  const DESTS = [{
    name: 'Clockwork',
    icon: 'Target',
    note: 'Connected'
  }, {
    name: 'Bullhorn',
    icon: 'Zap',
    note: 'Connected'
  }, {
    name: 'Greenhouse',
    icon: 'Sprout',
    note: 'Connect…'
  }, {
    name: 'Export CSV',
    icon: 'Download',
    note: ''
  }];
  const n = count || 0;
  React.useEffect(() => {
    if (!open) return;
    const close = () => setOpen(false);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, [open]);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'relative'
    },
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    onClick: () => setOpen(o => !o)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Share2",
    size: 15
  }), label, /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronDown",
    size: 13
  })), open && /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__filter-drop",
    style: {
      right: 0,
      left: 'auto',
      top: 'calc(100% + 6px)',
      minWidth: 236,
      padding: 6,
      zIndex: 20
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 10.5,
      fontWeight: 700,
      textTransform: 'uppercase',
      letterSpacing: '.05em',
      color: 'var(--muted-foreground)',
      padding: '6px 10px 4px'
    }
  }, n > 0 ? 'Send ' + n + ' mapped ' + (n === 1 ? 'person' : 'people') + ' to' : 'Send mapped people to'), DESTS.map(d => /*#__PURE__*/React.createElement("button", {
    key: d.name,
    className: "tm-ats-opt",
    onClick: () => {
      window.showToast && window.showToast(d.name === 'Export CSV' ? 'Exported ' + (n || 'all') + ' mapped people' : 'Synced ' + (n || 'all') + ' to ' + d.name);
      setOpen(false);
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: d.icon,
    size: 14
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1,
      textAlign: 'left'
    }
  }, d.name), d.note && /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 10.5,
      color: d.note === 'Connected' ? 'var(--success-fg, #15803d)' : 'var(--muted-foreground)'
    }
  }, d.note)))));
}
function LongListView({
  project,
  pipelineNames,
  onAddToPipeline,
  onOpenContact
}) {
  const [entries, setEntries] = React.useState(() => (window.TM_LONG_LIST || []).map(e => ({
    ...e
  })));
  const [filterScore, setFilterScore] = React.useState('');
  const [filterAvail, setFilterAvail] = React.useState('');
  const [search, setSearch] = React.useState('');
  const [dragId, setDragId] = React.useState(null);
  const [editId, setEditId] = React.useState(null);
  const isInPipeline = e => e.inPipeline || pipelineNames && pipelineNames.has(e.contactName);
  const setScore = (id, score) => setEntries(es => es.map(e => e.id === id ? {
    ...e,
    score: e.score === score ? '' : score
  } : e));
  const saveComment = (id, comment) => {
    setEntries(es => es.map(e => e.id === id ? {
      ...e,
      comment
    } : e));
    setEditId(null);
  };
  const handleAdd = entry => {
    if (isInPipeline(entry)) return;
    setEntries(es => es.map(e => e.id === entry.id ? {
      ...e,
      inPipeline: true
    } : e));
    if (onAddToPipeline) onAddToPipeline({
      name: entry.contactName,
      title: entry.title,
      company: entry.company
    });else window.showToast && window.showToast(entry.contactName + ' added to pipeline');
  };
  const handleNameClick = name => {
    const c = (window.TM_CONTACTS || []).find(co => co.name === name);
    if (c && onOpenContact) onOpenContact(c.id);
  };
  const handleDragOver = (e, targetId) => {
    e.preventDefault();
    if (!dragId || dragId === targetId) return;
    setEntries(prev => {
      const arr = [...prev];
      const fi = arr.findIndex(x => x.id === dragId);
      const ti = arr.findIndex(x => x.id === targetId);
      if (fi < 0 || ti < 0) return prev;
      const [moved] = arr.splice(fi, 1);
      arr.splice(ti, 0, moved);
      return arr.map((x, i) => ({
        ...x,
        rank: i + 1
      }));
    });
  };
  const SCORE_BTN = [{
    key: 'up',
    icon: 'ThumbsUp',
    activeStyle: {
      background: 'rgba(5,150,105,.12)',
      color: '#059669'
    }
  }, {
    key: 'neutral',
    icon: 'Minus',
    activeStyle: {
      background: 'var(--muted)',
      color: 'var(--foreground)'
    }
  }, {
    key: 'down',
    icon: 'ThumbsDown',
    activeStyle: {
      background: 'rgba(220,38,38,.10)',
      color: '#dc2626'
    }
  }];
  const inactiveStyle = {
    background: 'transparent',
    color: 'var(--muted-foreground)'
  };
  const availabilities = [...new Set(entries.map(e => e.availability))].filter(Boolean).sort();
  const upC = entries.filter(e => e.score === 'up').length;
  const neutC = entries.filter(e => e.score === 'neutral').length;
  const downC = entries.filter(e => e.score === 'down').length;
  const scoredC = upC + neutC + downC;
  const aiScores = entries.map(e => window.tmGetAiIntel ? window.tmGetAiIntel(e.contactName) : null).filter(Boolean).map(a => a.aiScore);
  const avgAiScore = aiScores.length ? Math.round(aiScores.reduce((s, v) => s + v, 0) / aiScores.length) : null;
  const filtered = entries.filter(e => {
    if (filterScore && e.score !== filterScore) return false;
    if (filterAvail && e.availability !== filterAvail) return false;
    if (search && !e.contactName.toLowerCase().includes(search.toLowerCase()) && !e.company.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });
  const COL = '20px 26px 1fr 1fr 1fr 106px 118px 1fr 52px 114px';
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header-left"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "Long list"), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 10,
      color: 'var(--muted-foreground)'
    }
  }, "\xB7"), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      fontWeight: 600
    }
  }, project), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)'
    }
  }, entries.length)), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)',
      marginTop: 3,
      display: 'flex',
      alignItems: 'center',
      gap: 6,
      flexWrap: 'wrap'
    }
  }, avgAiScore != null && /*#__PURE__*/React.createElement("span", {
    style: {
      fontWeight: 700,
      color: '#7c3aed',
      display: 'inline-flex',
      alignItems: 'center',
      gap: 3
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 10
  }), avgAiScore, "% avg AI match"), avgAiScore != null && /*#__PURE__*/React.createElement("span", {
    style: {
      opacity: .35
    }
  }, "\xB7"), /*#__PURE__*/React.createElement("span", null, scoredC, " assessed", scoredC > 0 && /*#__PURE__*/React.createElement(React.Fragment, null, " \u2014 ", /*#__PURE__*/React.createElement("span", {
    style: {
      color: '#059669'
    }
  }, upC, "\u2191"), " \xB7 ", /*#__PURE__*/React.createElement("span", null, neutC, "\u2014"), " \xB7 ", /*#__PURE__*/React.createElement("span", {
    style: {
      color: '#dc2626'
    }
  }, downC, "\u2193")))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header-right"
  }, /*#__PURE__*/React.createElement(PlFilter, {
    label: "Score",
    options: ['up', 'neutral', 'down'],
    value: filterScore,
    onChange: setFilterScore
  }), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Availability",
    options: availabilities,
    value: filterAvail,
    onChange: setFilterAvail
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-search-field",
    style: {
      width: 158
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 14,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    placeholder: "Search\u2026",
    value: search,
    onChange: e => setSearch(e.target.value)
  })), /*#__PURE__*/React.createElement(SyncToAtsButton, {
    count: entries.length,
    label: "Sync to ATS"
  }))), window.__HAK_SV && window.__HAK_SV !== '1.0' && /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      padding: '9px 16px',
      margin: '0 0 2px',
      background: 'rgba(245,158,11,.06)',
      borderBottom: '1px solid rgba(245,158,11,.18)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "RefreshCw",
    size: 13,
    color: "#b45309"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      color: '#b45309',
      fontWeight: 500,
      flex: 1
    }
  }, "Strategy updated to v", window.__HAK_SV, " \u2014 AI scores reflect the latest criteria and weights."), /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      fontSize: 10,
      background: 'rgba(124,58,237,.09)',
      color: '#7c3aed'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 9
  }), "Scores current")), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__board-wrap",
    style: {
      overflowX: 'auto'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      minWidth: 960
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'grid',
      gridTemplateColumns: COL,
      gap: 8,
      padding: '5px 16px',
      borderBottom: '1px solid var(--border)',
      fontSize: 10,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      textTransform: 'uppercase',
      letterSpacing: '.05em',
      background: 'var(--background)',
      position: 'sticky',
      top: 0,
      zIndex: 2
    }
  }, /*#__PURE__*/React.createElement("span", null), /*#__PURE__*/React.createElement("span", null, "#"), /*#__PURE__*/React.createElement("span", null, "Name \xB7 AI rationale"), /*#__PURE__*/React.createElement("span", null, "Title"), /*#__PURE__*/React.createElement("span", null, "Company"), /*#__PURE__*/React.createElement("span", null, "Availability"), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 10,
    color: "#7c3aed"
  }), "AI Score \xB7 Assess."), /*#__PURE__*/React.createElement("span", null, "Comment"), /*#__PURE__*/React.createElement("span", null, "Added"), /*#__PURE__*/React.createElement("span", null, "Action")), filtered.map(entry => {
    const pip = isInPipeline(entry);
    return /*#__PURE__*/React.createElement("div", {
      key: entry.id,
      draggable: true,
      onDragStart: () => setDragId(entry.id),
      onDragEnd: () => setDragId(null),
      onDragOver: e => handleDragOver(e, entry.id),
      style: {
        display: 'grid',
        gridTemplateColumns: COL,
        gap: 8,
        padding: '7px 16px',
        borderBottom: '1px solid var(--border)',
        alignItems: 'center',
        background: dragId === entry.id ? 'var(--muted)' : 'transparent',
        transition: 'background .1s'
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)',
        cursor: 'grab',
        display: 'flex'
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "GripVertical",
      size: 13
    })), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        color: 'var(--muted-foreground)',
        fontVariantNumeric: 'tabular-nums'
      }
    }, entry.rank), /*#__PURE__*/React.createElement("div", {
      style: {
        overflow: 'hidden',
        cursor: 'pointer'
      },
      onClick: () => handleNameClick(entry.contactName)
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        alignItems: 'center',
        gap: 7
      }
    }, /*#__PURE__*/React.createElement(Avatar, {
      name: entry.contactName,
      size: 24,
      tone: "primary"
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 13,
        fontWeight: 500,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        color: 'var(--primary)'
      }
    }, entry.contactName)), window.tmGetAiIntel && (() => {
      const ai = window.tmGetAiIntel(entry.contactName);
      return ai ? /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11,
          color: 'var(--muted-foreground)',
          fontStyle: 'italic',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          marginTop: 2,
          paddingLeft: 31
        }
      }, ai.aiRationale) : null;
    })()), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, entry.title), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        fontWeight: 500,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, entry.company), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(AvailPill, {
      availability: entry.availability
    })), window.tmGetAiIntel ? (() => {
      const ai = window.tmGetAiIntel(entry.contactName);
      const scoreColor = ai.aiScore >= 80 ? '#059669' : ai.aiScore >= 60 ? '#b45309' : '#dc2626';
      return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
        style: {
          display: 'flex',
          alignItems: 'center',
          gap: 5,
          marginBottom: 3
        }
      }, /*#__PURE__*/React.createElement("span", {
        style: {
          fontSize: 16,
          fontWeight: 800,
          fontVariantNumeric: 'tabular-nums',
          color: scoreColor,
          lineHeight: 1
        }
      }, ai.aiScore, "%"), /*#__PURE__*/React.createElement(Icon, {
        name: "Sparkles",
        size: 10,
        color: "#7c3aed"
      })), /*#__PURE__*/React.createElement("div", {
        style: {
          height: 3,
          borderRadius: 2,
          background: 'var(--muted)',
          overflow: 'hidden',
          marginBottom: 5
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          height: '100%',
          width: ai.aiScore + '%',
          background: scoreColor,
          borderRadius: 2
        }
      })), /*#__PURE__*/React.createElement("div", {
        style: {
          display: 'flex',
          gap: 2
        },
        title: "Your assessment"
      }, SCORE_BTN.map(({
        key,
        icon,
        activeStyle
      }) => /*#__PURE__*/React.createElement("button", {
        key: key,
        onClick: () => setScore(entry.id, key),
        style: {
          width: 22,
          height: 19,
          borderRadius: 4,
          border: 'none',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'background .12s, color .12s',
          ...(entry.score === key ? activeStyle : inactiveStyle)
        }
      }, /*#__PURE__*/React.createElement(Icon, {
        name: icon,
        size: 10
      })))));
    })() : /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        gap: 2
      }
    }, SCORE_BTN.map(({
      key,
      icon,
      activeStyle
    }) => /*#__PURE__*/React.createElement("button", {
      key: key,
      onClick: () => setScore(entry.id, key),
      style: {
        width: 28,
        height: 28,
        borderRadius: 6,
        border: 'none',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'background .12s, color .12s',
        ...(entry.score === key ? activeStyle : inactiveStyle)
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: icon,
      size: 13
    })))), editId === entry.id ? /*#__PURE__*/React.createElement("input", {
      autoFocus: true,
      className: "tm-cd__field-edit",
      style: {
        fontSize: 12,
        padding: '2px 6px',
        borderRadius: 4,
        width: '100%'
      },
      defaultValue: entry.comment,
      onBlur: e => saveComment(entry.id, e.target.value),
      onKeyDown: e => {
        if (e.key === 'Enter') e.target.blur();
        if (e.key === 'Escape') setEditId(null);
      }
    }) : /*#__PURE__*/React.createElement("div", {
      onClick: () => setEditId(entry.id),
      title: entry.comment,
      style: {
        fontSize: 12,
        cursor: 'text',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        minHeight: 20,
        color: entry.comment ? 'var(--foreground)' : 'var(--muted-foreground)',
        fontStyle: entry.comment ? 'normal' : 'italic'
      }
    }, entry.comment || 'Add comment\u2026'), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 11,
        color: 'var(--muted-foreground)'
      }
    }, formatAge(entry.ageDays)), pip ? /*#__PURE__*/React.createElement("span", {
      className: "tm-pill",
      style: {
        background: 'rgba(5,150,105,.10)',
        color: '#059669',
        fontSize: 11,
        gap: 4
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Check",
      size: 11
    }), "In pipeline") : /*#__PURE__*/React.createElement("button", {
      className: "tm-add-pipeline-btn",
      onClick: () => handleAdd(entry),
      style: {
        fontSize: 11,
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        whiteSpace: 'nowrap'
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "UserPlus",
      size: 12
    }), "Add to pipeline"));
  }), filtered.length === 0 && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '40px 16px',
      textAlign: 'center',
      color: 'var(--muted-foreground)',
      fontSize: 13
    }
  }, "No entries match your filters."))));
}

// ── StatusReportView ──────────────────────────────────────────────────────────
function StatusReportView({
  project
}) {
  const seed = window.TM_STATUS_REPORT || {};
  const [keyCandidates, setKeyCandidates] = React.useState(seed.keyCandidates || '');
  const [marketObs, setMarketObs] = React.useState(seed.marketObservations || '');
  const [nextSteps, setNextSteps] = React.useState(seed.nextSteps || '');
  const [clientMode, setClientMode] = React.useState(false);
  const [shareModal, setShareModal] = React.useState(false);
  const [copied, setCopied] = React.useState(false);
  const [open, setOpen] = React.useState({
    progress: true,
    candidates: true,
    market: true,
    steps: true
  });
  const toggle = k => setOpen(o => ({
    ...o,
    [k]: !o[k]
  }));
  const pipeline = window.TM_PIPELINE || [];
  const stageCounts = {};
  pipeline.forEach(e => {
    stageCounts[e.stage] = (stageCounts[e.stage] || 0) + 1;
  });
  const STAGES_BAR = [{
    id: 'Sourced',
    color: '#94a3b8'
  }, {
    id: 'Contacted',
    color: '#1d4ed8'
  }, {
    id: 'Screening',
    color: '#6d28d9'
  }, {
    id: 'Interview',
    color: '#b45309'
  }, {
    id: 'Offer',
    color: '#059669'
  }, {
    id: 'Hired',
    color: '#15803d'
  }];
  const AccHead = ({
    label,
    sKey,
    icon
  }) => /*#__PURE__*/React.createElement("div", {
    onClick: () => toggle(sKey),
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      padding: '10px 16px',
      cursor: 'pointer',
      userSelect: 'none',
      borderBottom: open[sKey] ? '1px solid var(--border)' : 'none'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 14,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      fontWeight: 600,
      flex: 1
    }
  }, label), /*#__PURE__*/React.createElement(Icon, {
    name: open[sKey] ? 'ChevronUp' : 'ChevronDown',
    size: 14,
    color: "var(--muted-foreground)"
  }));
  const reportBody = /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 14
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement(AccHead, {
    label: "Search progress",
    sKey: "progress",
    icon: "BarChart2"
  }), open.progress && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '12px 16px'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexWrap: 'wrap',
      gap: 14,
      marginBottom: 14
    }
  }, STAGES_BAR.map(s => {
    const n = stageCounts[s.id] || 0;
    return /*#__PURE__*/React.createElement("div", {
      key: s.id,
      style: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 4
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 20,
        fontWeight: 700,
        color: n > 0 ? s.color : 'var(--muted-foreground)',
        fontVariantNumeric: 'tabular-nums'
      }
    }, n), /*#__PURE__*/React.createElement("span", {
      className: "tm-pill",
      style: {
        fontSize: 10,
        background: n > 0 ? s.color + '1a' : 'var(--muted)',
        color: n > 0 ? s.color : 'var(--muted-foreground)'
      }
    }, s.id));
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      height: 6,
      background: 'var(--muted)',
      borderRadius: 3,
      overflow: 'hidden',
      display: 'flex'
    }
  }, STAGES_BAR.map(s => {
    const pct = pipeline.length ? (stageCounts[s.id] || 0) / pipeline.length * 100 : 0;
    return pct > 0 ? /*#__PURE__*/React.createElement("div", {
      key: s.id,
      style: {
        width: pct + '%',
        background: s.color
      }
    }) : null;
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 8,
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, pipeline.length, " candidates total"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement(AccHead, {
    label: "Key candidates",
    sKey: "candidates",
    icon: "Users"
  }), open.candidates && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '8px 12px 12px'
    }
  }, clientMode ? /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      lineHeight: 1.75,
      whiteSpace: 'pre-wrap'
    }
  }, keyCandidates || /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontStyle: 'italic'
    }
  }, "No update provided.")) : /*#__PURE__*/React.createElement("textarea", {
    className: "tm-set-input",
    style: {
      width: '100%',
      minHeight: 100,
      resize: 'vertical',
      fontSize: 13,
      fontFamily: 'var(--font-sans)',
      borderRadius: 8
    },
    placeholder: "Summarise candidates to highlight for the client this week\\u2026",
    value: keyCandidates,
    onChange: e => setKeyCandidates(e.target.value)
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement(AccHead, {
    label: "Market observations",
    sKey: "market",
    icon: "TrendingUp"
  }), open.market && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '8px 12px 12px'
    }
  }, clientMode ? /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      lineHeight: 1.75,
      whiteSpace: 'pre-wrap'
    }
  }, marketObs || /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontStyle: 'italic'
    }
  }, "No update provided.")) : /*#__PURE__*/React.createElement("textarea", {
    className: "tm-set-input",
    style: {
      width: '100%',
      minHeight: 100,
      resize: 'vertical',
      fontSize: 13,
      fontFamily: 'var(--font-sans)',
      borderRadius: 8
    },
    placeholder: "Notes on market conditions, candidate availability, compensation benchmarks\\u2026",
    value: marketObs,
    onChange: e => setMarketObs(e.target.value)
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement(AccHead, {
    label: "Next steps",
    sKey: "steps",
    icon: "ArrowRight"
  }), open.steps && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '8px 12px 12px'
    }
  }, clientMode ? /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      lineHeight: 1.9,
      whiteSpace: 'pre-wrap'
    }
  }, nextSteps || /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontStyle: 'italic'
    }
  }, "No next steps defined.")) : /*#__PURE__*/React.createElement("textarea", {
    className: "tm-set-input",
    style: {
      width: '100%',
      minHeight: 100,
      resize: 'vertical',
      fontSize: 13,
      fontFamily: 'var(--font-sans)',
      borderRadius: 8
    },
    placeholder: '• Action item 1\n• Action item 2',
    value: nextSteps,
    onChange: e => setNextSteps(e.target.value)
  }))));

  // Share modal
  const shareDlg = shareModal && /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__overlay",
    onClick: () => setShareModal(false)
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__confirm",
    style: {
      width: 380,
      alignItems: 'flex-start'
    },
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 14,
      fontWeight: 700,
      marginBottom: 16
    }
  }, "Share status report"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      textTransform: 'uppercase',
      letterSpacing: '.04em',
      marginBottom: 6
    }
  }, "Client link"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      width: '100%',
      marginBottom: 14
    }
  }, /*#__PURE__*/React.createElement("input", {
    readOnly: true,
    className: "tm-cd__field-edit",
    value: "https://app.alacpartners.com/portal/report/abc123",
    style: {
      flex: 1,
      fontSize: 12,
      padding: '6px 10px',
      overflow: 'hidden',
      textOverflow: 'ellipsis'
    }
  }), /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm",
    onClick: () => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: copied ? 'Check' : 'Copy',
    size: 13
  }), copied ? 'Copied' : 'Copy')), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      marginBottom: 16,
      display: 'flex',
      alignItems: 'center',
      gap: 5
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 12
  }), "Last shared: never"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      width: '100%'
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: () => setShareModal(false),
    style: {
      flex: 1
    }
  }, "Cancel"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    style: {
      flex: 1
    },
    onClick: () => {
      window.showToast && window.showToast('Email sharing coming soon');
      setShareModal(false);
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Mail",
    size: 14
  }), "Send email"))));

  // Client preview — full-screen overlay
  if (clientMode) {
    return /*#__PURE__*/React.createElement("div", {
      style: {
        position: 'fixed',
        inset: 0,
        background: 'var(--background)',
        zIndex: 50,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden'
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        height: 48,
        borderBottom: '1px solid var(--border)',
        display: 'flex',
        alignItems: 'center',
        padding: '0 24px',
        gap: 10,
        flexShrink: 0
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 13,
        fontWeight: 700
      }
    }, "ALAC Partners"), /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)'
      }
    }, "\xB7"), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 13,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        maxWidth: 260
      }
    }, project), /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)'
      }
    }, "\u2014"), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 13,
        color: 'var(--muted-foreground)'
      }
    }, "Search update"), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1
      }
    }), /*#__PURE__*/React.createElement(Button, {
      variant: "ghost",
      size: "sm",
      onClick: () => setClientMode(false)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "X",
      size: 14
    }), "Close preview")), /*#__PURE__*/React.createElement("div", {
      style: {
        background: 'rgba(37,99,235,.06)',
        borderBottom: '1px solid rgba(37,99,235,.15)',
        padding: '6px 24px',
        display: 'flex',
        alignItems: 'center',
        gap: 7,
        flexShrink: 0
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Eye",
      size: 13,
      color: "#1d4ed8"
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12,
        color: '#1d4ed8'
      }
    }, "Previewing as client \u2014 this is what your client sees")), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        overflowY: 'auto',
        padding: '28px 0'
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        maxWidth: 720,
        margin: '0 auto',
        padding: '0 28px'
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 24
      }
    }, /*#__PURE__*/React.createElement("h1", {
      style: {
        fontSize: 22,
        fontWeight: 700,
        margin: '0 0 4px'
      }
    }, project), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 13,
        color: 'var(--muted-foreground)'
      }
    }, "Search update \u2014 ", new Date().toLocaleDateString('en-GB', {
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }))), reportBody)));
  }
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "Status report"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      color: 'var(--muted-foreground)',
      marginTop: 2
    }
  }, project)), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    onClick: () => setClientMode(true)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Eye",
    size: 14
  }), "Preview as client"), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: () => setShareModal(true)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Share2",
    size: 14
  }), "Share"))), reportBody), shareDlg);
}

// ── InternalView — private team workspace (scratchpad + activity feed) ────────
function InternalView({
  project
}) {
  const seed = window.TM_INTERNAL || {};
  const [notes, setNotes] = React.useState(seed.scratchpad || '');
  const [comments, setComments] = React.useState(() => (seed.comments || []).map(c => ({
    ...c
  })));
  const [draft, setDraft] = React.useState('');
  const me = (window.TM_PROJECT_TEAM || [])[0] || {
    initials: 'LH',
    name: 'Layla Hassan'
  };
  const postComment = () => {
    const v = draft.trim();
    if (!v) return;
    setComments(cs => [{
      id: 'c-' + Date.now(),
      author: me.name,
      initials: me.initials,
      text: v,
      ageDays: 0
    }, ...cs]);
    setDraft('');
  };
  const activity = window.TM_INTERNAL_ACTIVITY || [];
  const ACT_ICON = {
    stage: {
      icon: 'ArrowRight',
      color: '#1d4ed8'
    },
    note: {
      icon: 'MessageSquare',
      color: '#6d28d9'
    },
    score: {
      icon: 'Star',
      color: 'hsl(35,92%,45%)'
    },
    add: {
      icon: 'UserPlus',
      color: '#059669'
    },
    status: {
      icon: 'Flag',
      color: '#b45309'
    }
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "Internal"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      color: 'var(--muted-foreground)',
      marginTop: 2
    }
  }, project)), /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: 'var(--muted)',
      color: 'var(--muted-foreground)',
      fontSize: 11,
      gap: 5
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Lock",
    size: 11
  }), "Team only \u2014 never shared with client")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'grid',
      gridTemplateColumns: '1fr 360px',
      gap: 18,
      alignItems: 'start'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 18
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "StickyNote",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Team scratchpad"), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '4px 12px 12px'
    }
  }, /*#__PURE__*/React.createElement("textarea", {
    className: "tm-set-input",
    style: {
      width: '100%',
      minHeight: 130,
      resize: 'vertical',
      fontSize: 13,
      fontFamily: 'var(--font-sans)',
      borderRadius: 8
    },
    placeholder: "Private working notes \u2014 candidate intel, off-the-record context, reminders\u2026",
    value: notes,
    onChange: e => setNotes(e.target.value)
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Discussion", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6,
      fontSize: 11,
      fontWeight: 700,
      color: 'var(--muted-foreground)'
    }
  }, comments.length)), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 10,
      padding: '12px 14px',
      borderBottom: '1px solid var(--border)'
    }
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: me.name,
    size: 28,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }, /*#__PURE__*/React.createElement("textarea", {
    className: "tm-set-input",
    style: {
      width: '100%',
      minHeight: 54,
      resize: 'vertical',
      fontSize: 13,
      fontFamily: 'var(--font-sans)',
      borderRadius: 8
    },
    placeholder: "Add a comment for the team\u2026",
    value: draft,
    onChange: e => setDraft(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) postComment();
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'flex-end',
      marginTop: 6
    }
  }, /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: postComment,
    disabled: !draft.trim()
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Send",
    size: 13
  }), "Post")))), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '4px 14px 8px'
    }
  }, comments.length === 0 && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '24px 0',
      textAlign: 'center',
      color: 'var(--muted-foreground)',
      fontSize: 13,
      fontStyle: 'italic'
    }
  }, "No comments yet."), comments.map(c => /*#__PURE__*/React.createElement("div", {
    key: c.id,
    style: {
      display: 'flex',
      gap: 10,
      padding: '12px 0',
      borderBottom: '1px solid var(--border)'
    }
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: c.author,
    size: 28,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'baseline',
      gap: 7,
      marginBottom: 3
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      fontWeight: 600
    }
  }, c.author), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, formatAge(c.ageDays))), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      lineHeight: 1.6,
      whiteSpace: 'pre-wrap'
    }
  }, c.text))))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Activity",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Activity"), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '6px 0'
    }
  }, activity.map((a, i) => {
    const meta = ACT_ICON[a.type] || ACT_ICON.note;
    return /*#__PURE__*/React.createElement("div", {
      key: i,
      style: {
        display: 'flex',
        gap: 10,
        padding: '8px 14px'
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        width: 26,
        height: 26,
        borderRadius: '50%',
        flexShrink: 0,
        marginTop: 1,
        background: meta.color + '18',
        color: meta.color,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center'
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: meta.icon,
      size: 13
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12.5,
        lineHeight: 1.45
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontWeight: 600
      }
    }, a.actor), " ", a.text), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 11,
        color: 'var(--muted-foreground)',
        marginTop: 2
      }
    }, formatAge(a.ageDays))));
  }))))));
}

// ── BriefView — Layer 01 entry: mandate intake + AI processing ────────────────
function BriefView({
  project,
  initialQuery,
  onAnalysisComplete
}) {
  const [role, setRole] = React.useState(initialQuery || 'Group Chief Information Officer');
  const [client, setClient] = React.useState('Al Rabie Saudi Foods Co.');
  const [context, setContext] = React.useState('Confidential search. Chairman-sponsored. 90-day target close. No direct approach from Almarai.');
  const [jdFile, setJdFile] = React.useState('Group_CIO_Brief_2024.pdf');
  const [phase, setPhase] = React.useState(initialQuery ? 'processing' : 'form');

  // Auto-launch analysis when arriving from a search query
  React.useEffect(() => {
    if (initialQuery) launch();
  }, []); // form | processing | done

  const STEPS = [{
    id: 's1',
    label: 'Reading position description',
    detail: 'Extracting requirements, seniority, scope and compensation signals'
  }, {
    id: 's2',
    label: 'Fingerprinting ' + (client || 'client'),
    detail: 'Sector, ownership structure, culture markers, recent news'
  }, {
    id: 's3',
    label: 'Inferring sector scope',
    detail: 'Direct, adjacent and excluded sectors mapped'
  }, {
    id: 's4',
    label: 'Weighting search criteria',
    detail: '9 criteria extracted and weighted by brief analysis'
  }, {
    id: 's5',
    label: 'Mapping company universe',
    detail: '47 companies found across 3 sectors'
  }];
  const [doneSteps, setDoneSteps] = React.useState([]);
  const [activeStep, setActiveStep] = React.useState(-1);
  const launch = () => {
    if (!role.trim() || !client.trim()) {
      window.showToast && window.showToast('Fill in role title and client company first');
      return;
    }
    setPhase('processing');
    setDoneSteps([]);
    setActiveStep(0);
    STEPS.forEach((_, i) => {
      const delay = i === 0 ? 600 : 600 + i * 1500 + Math.random() * 300;
      setTimeout(() => {
        setActiveStep(i + 1);
        setDoneSteps(prev => [...prev, i]);
        if (i === STEPS.length - 1) {
          setTimeout(() => setPhase('done'), 900);
        }
      }, delay);
    });
  };
  const progress = phase === 'done' ? 100 : phase === 'processing' ? Math.round(doneSteps.length / STEPS.length * 100) : 0;

  // ── PROCESSING / DONE STATE ────────────────────────────────────────────────
  if (phase === 'processing' || phase === 'done') {
    return /*#__PURE__*/React.createElement("div", {
      className: "tm-pscreen tm-fadein"
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        maxWidth: 540,
        margin: '0 auto',
        padding: '72px 24px 48px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center'
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        width: 72,
        height: 72,
        borderRadius: 20,
        background: 'rgba(124,58,237,.10)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: 32,
        boxShadow: '0 0 0 14px rgba(124,58,237,.06), 0 0 0 28px rgba(124,58,237,.025)'
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: phase === 'done' ? 'CheckCircle2' : 'Sparkles',
      size: 30,
      color: phase === 'done' ? '#059669' : '#7c3aed'
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 22,
        fontWeight: 700,
        textAlign: 'center',
        marginBottom: 6
      }
    }, phase === 'done' ? 'Analysis complete' : 'Building your search…'), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 13,
        color: 'var(--muted-foreground)',
        textAlign: 'center',
        marginBottom: 40,
        lineHeight: 1.6
      }
    }, phase === 'done' ? 'Strategy, weighted criteria and company universe are ready. Review and adjust before execution begins.' : 'AI is reading the brief and mapping the talent landscape for ' + (client || 'the client') + '.'), /*#__PURE__*/React.createElement("div", {
      style: {
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
        marginBottom: 36
      }
    }, STEPS.map((step, i) => {
      const done = doneSteps.includes(i);
      const active = activeStep === i && phase === 'processing';
      return /*#__PURE__*/React.createElement("div", {
        key: step.id,
        style: {
          display: 'flex',
          alignItems: 'flex-start',
          gap: 14,
          opacity: done || active ? 1 : .28,
          transition: 'opacity .4s'
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          width: 24,
          height: 24,
          borderRadius: '50%',
          flexShrink: 0,
          marginTop: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'background .3s',
          background: done ? 'rgba(5,150,105,.12)' : active ? 'rgba(124,58,237,.12)' : 'var(--muted)'
        }
      }, done ? /*#__PURE__*/React.createElement(Icon, {
        name: "Check",
        size: 12,
        color: "#059669"
      }) : active ? /*#__PURE__*/React.createElement("div", {
        style: {
          width: 9,
          height: 9,
          borderRadius: '50%',
          background: '#7c3aed'
        }
      }) : /*#__PURE__*/React.createElement("div", {
        style: {
          width: 6,
          height: 6,
          borderRadius: '50%',
          background: 'var(--muted-foreground)'
        }
      })), /*#__PURE__*/React.createElement("div", {
        style: {
          minWidth: 0
        }
      }, /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 13.5,
          fontWeight: done ? 400 : 600,
          lineHeight: 1.3,
          color: done ? 'var(--muted-foreground)' : 'var(--foreground)'
        }
      }, step.label), (done || active) && /*#__PURE__*/React.createElement("div", {
        style: {
          fontSize: 11.5,
          color: 'var(--muted-foreground)',
          marginTop: 3
        }
      }, step.detail)));
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        width: '100%',
        marginBottom: 28
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        height: 5,
        borderRadius: 3,
        background: 'var(--muted)',
        overflow: 'hidden',
        marginBottom: 8
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        height: '100%',
        width: progress + '%',
        borderRadius: 3,
        background: phase === 'done' ? '#059669' : '#7c3aed',
        transition: 'width .6s ease'
      }
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        justifyContent: 'space-between',
        fontSize: 11.5,
        color: 'var(--muted-foreground)'
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        fontWeight: 600
      }
    }, progress, "%"), phase !== 'done' && /*#__PURE__*/React.createElement("span", null, Math.max(1, Math.round((STEPS.length - doneSteps.length) * 1.5)), "s remaining"), phase === 'done' && /*#__PURE__*/React.createElement("span", {
      style: {
        color: '#059669',
        fontWeight: 600
      }
    }, "Complete"))), phase === 'done' && /*#__PURE__*/React.createElement("button", {
      className: "tm-btn tm-btn--default",
      onClick: () => onAnalysisComplete && onAnalysisComplete({
        role,
        client
      }),
      style: {
        width: '100%',
        justifyContent: 'center',
        fontSize: 14,
        minHeight: 44,
        gap: 8
      }
    }, "Review AI strategy ", /*#__PURE__*/React.createElement(Icon, {
      name: "ArrowRight",
      size: 15
    }))));
  }

  // ── FORM STATE ─────────────────────────────────────────────────────────────
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner",
    style: {
      maxWidth: 640
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "Layer 01 \xB7 Brief"), /*#__PURE__*/React.createElement("h1", {
    style: {
      fontSize: 21,
      fontWeight: 700,
      margin: '4px 0 0',
      lineHeight: 1.2
    }
  }, "Define the search mandate"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 14
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, "Role & client"), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '14px 16px',
      display: 'flex',
      flexDirection: 'column',
      gap: 14
    }
  }, [{
    label: 'Role title',
    placeholder: 'e.g. Group Chief Financial Officer',
    val: role,
    set: setRole
  }, {
    label: 'Client company',
    placeholder: 'e.g. Al Rabie Saudi Foods Co.',
    val: client,
    set: setClient
  }].map(({
    label,
    placeholder,
    val,
    set
  }) => /*#__PURE__*/React.createElement("div", {
    key: label
  }, /*#__PURE__*/React.createElement("label", {
    style: {
      display: 'block',
      fontSize: 11,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      marginBottom: 7,
      textTransform: 'uppercase',
      letterSpacing: '.05em'
    }
  }, label, " ", /*#__PURE__*/React.createElement("span", {
    style: {
      color: '#b91c1c'
    }
  }, "*")), /*#__PURE__*/React.createElement("input", {
    className: "tm-cd__field-edit",
    style: {
      width: '100%',
      fontSize: 15,
      padding: '10px 13px',
      borderRadius: 8,
      border: '1.5px solid var(--border)',
      fontFamily: 'var(--font-sans)',
      fontWeight: 500,
      transition: 'border-color .15s'
    },
    placeholder: placeholder,
    value: val,
    onChange: e => set(e.target.value),
    onFocus: e => e.target.style.borderColor = '#7c3aed',
    onBlur: e => e.target.style.borderColor = ''
  }))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Position description", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6,
      fontSize: 10.5,
      fontWeight: 400,
      color: 'var(--muted-foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, "optional \u2014 but improves AI accuracy significantly")), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '12px 16px 14px'
    }
  }, jdFile ? /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      padding: '10px 14px',
      borderRadius: 8,
      background: 'rgba(5,150,105,.06)',
      border: '1px solid rgba(5,150,105,.22)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 16,
    color: "#059669"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      fontWeight: 500,
      flex: 1,
      color: '#059669'
    }
  }, jdFile), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      color: '#059669',
      background: 'rgba(5,150,105,.12)',
      padding: '1px 8px',
      borderRadius: 4
    }
  }, "Ready"), /*#__PURE__*/React.createElement("button", {
    onClick: () => setJdFile(''),
    style: {
      background: 'none',
      border: 'none',
      cursor: 'pointer',
      color: 'var(--muted-foreground)',
      display: 'flex',
      padding: 0,
      marginLeft: 4
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 13
  }))) : /*#__PURE__*/React.createElement("div", {
    onClick: () => setJdFile('Position_Brief.pdf'),
    style: {
      border: '1.5px dashed var(--border)',
      borderRadius: 10,
      padding: '22px 20px',
      textAlign: 'center',
      cursor: 'pointer',
      transition: 'border-color .15s, background .15s'
    },
    onMouseOver: e => {
      e.currentTarget.style.borderColor = '#7c3aed';
      e.currentTarget.style.background = 'rgba(124,58,237,.03)';
    },
    onMouseOut: e => {
      e.currentTarget.style.borderColor = '';
      e.currentTarget.style.background = '';
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 20,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 500,
      marginTop: 8,
      marginBottom: 3
    }
  }, "Drop JD or brief here"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)'
    }
  }, "PDF \xB7 DOCX \xB7 TXT \u2014 or click to browse")))), /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MessageSquare",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Additional context", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6,
      fontSize: 10.5,
      fontWeight: 400,
      color: 'var(--muted-foreground)',
      textTransform: 'none',
      letterSpacing: 0
    }
  }, "optional")), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '8px 14px 14px'
    }
  }, /*#__PURE__*/React.createElement("textarea", {
    className: "tm-set-input",
    style: {
      width: '100%',
      minHeight: 72,
      resize: 'vertical',
      fontSize: 13,
      fontFamily: 'var(--font-sans)',
      borderRadius: 8,
      lineHeight: 1.75
    },
    placeholder: "Urgency, sensitivities, constraints, or anything AI should know before analysing \u2014 e.g. 'No approach from Almarai, 90-day close target, chairman-sponsored'",
    value: context,
    onChange: e => setContext(e.target.value)
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-btn tm-btn--default",
    onClick: launch,
    style: {
      width: '100%',
      justifyContent: 'center',
      fontSize: 14,
      minHeight: 46,
      gap: 8,
      opacity: !role.trim() || !client.trim() ? .45 : 1
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 16
  }), "Analyse with AI"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 9,
      padding: '11px 14px',
      borderRadius: 8,
      background: 'rgba(124,58,237,.04)',
      border: '1px solid rgba(124,58,237,.12)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13,
    color: "#7c3aed",
    style: {
      flexShrink: 0,
      marginTop: 1
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      lineHeight: 1.65
    }
  }, /*#__PURE__*/React.createElement("b", {
    style: {
      color: 'var(--foreground)'
    }
  }, "AI will generate:"), " weighted search criteria \xB7 client fingerprint \xB7 sector scope \xB7 25\u201350 target companies \xB7 ranked executive long list. You review and adjust everything before outreach begins."))))));
}
Object.assign(window, {
  ContactsScreen,
  ContactDetail,
  PipelineView,
  PositionView,
  StrategyView,
  LongListView,
  StatusReportView,
  InternalView,
  SyncToAtsButton,
  BriefView,
  AvailPill,
  StagePill,
  StatusPill,
  PlFilter,
  OffLimitsBadge,
  formatAge,
  dueMeta
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/crm.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/data.jsx
try { (() => {
// ── ALAC Talent Map UI kit · mock dataset (GCC / MENA executive search) ──────
// Map positions are stylized x/y percentages over an abstract map canvas,
// not real geo — the kit demonstrates the interaction model, not live data.

const TM_SUGGESTIONS = ['Top FMCG distributors in UAE', 'Leading PE firms in Saudi Arabia', 'Industrial equipment manufacturers in Egypt', 'Retail chains across GCC'];
const TM_RATIONALE = 'Targeting large-cap FMCG and food manufacturers headquartered in the GCC, then expanding into adjacent retail and agri-business sectors that share executive talent dynamics.';
const TM_COMPANIES = [{
  id: 1,
  name: 'Almarai',
  city: 'Riyadh',
  country: 'Saudi Arabia',
  sector: 'FMCG',
  relevance: 'Direct',
  revenue: '$1B–5B',
  employees: '10K–50K',
  confidence: 91,
  x: 52,
  y: 50,
  color: 'var(--node-navy)',
  summary: 'The largest vertically integrated dairy and food company in the MiddEast, operating across dairy, bakery, poultry and infant nutrition.',
  execs: [{
    id: 11,
    name: 'Amira Haddad',
    title: 'Group Chief Executive',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }, {
    id: 12,
    name: 'Rami Khoury',
    title: 'Chief Financial Officer',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }, {
    id: 13,
    name: 'Lina Nasr',
    title: 'Chief Operating Officer',
    level: 'C-Suite',
    enriched: false,
    verified: true
  }, {
    id: 14,
    name: 'Omar Saleh',
    title: 'VP, Supply Chain',
    level: 'N-1',
    enriched: false,
    verified: false
  }]
}, {
  id: 2,
  name: 'Savola Group',
  city: 'Jeddah',
  country: 'Saudi Arabia',
  sector: 'Food & Retail',
  relevance: 'Adjacent',
  revenue: '>$5B',
  employees: '10K–50K',
  confidence: 74,
  x: 40,
  y: 64,
  color: 'var(--node-navy)',
  summary: 'Diversified holding with leading positions in edible oils, sugar and retail across the Middle East, North Africa and Turkey.',
  execs: [{
    id: 21,
    name: 'Yousef Iman',
    title: 'Chief Executive Officer',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }, {
    id: 22,
    name: 'Dana Aziz',
    title: 'Group HR Director',
    level: 'N-1',
    enriched: false,
    verified: false
  }, {
    id: 23,
    name: 'Tariq Bahar',
    title: 'CFO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 3,
  name: 'Agthia Group',
  city: 'Abu Dhabi',
  country: 'UAE',
  sector: 'FMCG',
  relevance: 'Direct',
  revenue: '$500M–1B',
  employees: '5K–10K',
  confidence: 86,
  x: 69,
  y: 60,
  color: 'var(--node-navy)',
  summary: 'UAE-based food & beverage group spanning water, snacks, protein and agri-business, expanding aggressively across the region by acquisition.',
  execs: [{
    id: 31,
    name: 'Khalid Mansour',
    title: 'Group CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }, {
    id: 32,
    name: 'Sara Fadel',
    title: 'Chief Strategy Officer',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 4,
  name: 'IFFCO',
  city: 'Sharjah',
  country: 'UAE',
  sector: 'FMCG',
  relevance: 'Direct',
  revenue: '$1B–5B',
  employees: '10K–50K',
  confidence: 83,
  x: 78,
  y: 44,
  color: 'var(--node-navy)',
  summary: 'Privately held manufacturer of food products, oils and derivatives with operations across 30+ countries.',
  execs: [{
    id: 41,
    name: 'Hassan Noor',
    title: 'Chief Executive Officer',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }, {
    id: 42,
    name: 'Mona Rashed',
    title: 'VP Marketing',
    level: 'N-1',
    enriched: false,
    verified: false
  }]
}, {
  id: 5,
  name: 'Juhayna Food',
  city: 'Cairo',
  country: 'Egypt',
  sector: 'Dairy & Juice',
  relevance: 'Adjacent',
  revenue: '$100M–500M',
  employees: '5K–10K',
  confidence: 69,
  x: 24,
  y: 56,
  color: 'var(--node-navy)',
  summary: 'Egyptian leader in dairy, juice and cooking products with an integrated farming and distribution network.',
  execs: [{
    id: 51,
    name: 'Nadia Fawzy',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }, {
    id: 52,
    name: 'Ahmed Sami',
    title: 'Commercial Director',
    level: 'N-1',
    enriched: false,
    verified: false
  }]
}, {
  id: 6,
  name: 'Americana',
  city: 'Kuwait City',
  country: 'Kuwait',
  sector: 'Food Service',
  relevance: 'AI Inferred',
  revenue: '$1B–5B',
  employees: '>50K',
  confidence: 58,
  x: 57,
  y: 32,
  color: 'var(--node-navy)',
  summary: 'Operator and franchisor of quick-service restaurants and a major food manufacturer across MENA.',
  execs: [{
    id: 61,
    name: 'Faisal Otaibi',
    title: 'Group CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 7,
  name: 'NADEC',
  city: 'Riyadh',
  country: 'Saudi Arabia',
  sector: 'Agri & Dairy',
  relevance: 'Direct',
  revenue: '$500M–1B',
  employees: '5K–10K',
  confidence: 80,
  x: 45,
  y: 38,
  color: 'var(--node-navy)',
  summary: 'National Agricultural Development Company — integrated dairy, juice and arable farming operations.',
  execs: [{
    id: 71,
    name: 'Sami Ha](',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }, {
    id: 72,
    name: 'Reem Adel',
    title: 'CHRO',
    level: 'N-1',
    enriched: false,
    verified: false
  }]
}, {
  id: 8,
  name: 'Al Ain Farms',
  city: 'Al Ain',
  country: 'UAE',
  sector: 'Dairy',
  relevance: 'Adjacent',
  revenue: '$100M–500M',
  employees: '1K–5K',
  confidence: 64,
  x: 84,
  y: 66,
  color: 'var(--node-navy)',
  summary: 'The first national dairy producer in the UAE, covering fresh dairy, juice and poultry.',
  execs: [{
    id: 81,
    name: 'Bilal Aziz',
    title: 'Managing Director',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}];
// fix a stray typo id 71 name
TM_COMPANIES.find(c => c.id === 7).execs[0].name = 'Sami Halabi';

// ── Extended universe (25 total for pagination / filter demo) ────────────────
TM_COMPANIES.push({
  id: 9,
  name: 'BRF Middle East',
  city: 'Dubai',
  country: 'UAE',
  sector: 'FMCG',
  relevance: 'Adjacent',
  revenue: '$1B–5B',
  employees: '5K–10K',
  confidence: 71,
  x: 72,
  y: 52,
  color: 'var(--node-navy)',
  summary: 'Brazilian food giant\u2019s Middle East subsidiary handling distribution of processed foods and protein products.',
  execs: [{
    id: 91,
    name: 'Marcos Tavares',
    title: 'Regional CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 10,
  name: 'Al Islami Foods',
  city: 'Dubai',
  country: 'UAE',
  sector: 'FMCG',
  relevance: 'Direct',
  revenue: '$100M–500M',
  employees: '1K–5K',
  confidence: 88,
  x: 75,
  y: 48,
  color: 'var(--node-navy)',
  summary: 'Leading halal frozen food manufacturer in the UAE with distribution across 25 countries.',
  execs: [{
    id: 101,
    name: 'Saleh Al Abdooli',
    title: 'CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }, {
    id: 102,
    name: 'Fatima Al Hammadi',
    title: 'CFO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 11,
  name: 'Mezzan Holding',
  city: 'Kuwait City',
  country: 'Kuwait',
  sector: 'Food & Retail',
  relevance: 'Adjacent',
  revenue: '$500M–1B',
  employees: '5K–10K',
  confidence: 67,
  x: 60,
  y: 28,
  color: 'var(--node-navy)',
  summary: 'Kuwaiti food manufacturing and distribution conglomerate covering FMCG, catering and retail.',
  execs: [{
    id: 111,
    name: 'Jamal Shaker',
    title: 'Group CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 12,
  name: 'Oman Food Industries',
  city: 'Muscat',
  country: 'Oman',
  sector: 'FMCG',
  relevance: 'AI Inferred',
  revenue: '$100M–500M',
  employees: '1K–5K',
  confidence: 52,
  x: 85,
  y: 42,
  color: 'var(--node-navy)',
  summary: 'Omani manufacturer of ambient and chilled food products serving local and export markets.',
  execs: [{
    id: 121,
    name: 'Rashid Al Said',
    title: 'Managing Director',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 13,
  name: 'Halwani Brothers',
  city: 'Jeddah',
  country: 'Saudi Arabia',
  sector: 'FMCG',
  relevance: 'Direct',
  revenue: '$100M–500M',
  employees: '1K–5K',
  confidence: 84,
  x: 38,
  y: 58,
  color: 'var(--node-navy)',
  summary: 'Saudi food manufacturer specialising in processed meats, halva and tahini for regional markets.',
  execs: [{
    id: 131,
    name: 'Nabil Halwani',
    title: 'CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }, {
    id: 132,
    name: 'Layla Kassab',
    title: 'VP Operations',
    level: 'N-1',
    enriched: false,
    verified: false
  }]
}, {
  id: 14,
  name: 'Masafi',
  city: 'Ras Al Khaimah',
  country: 'UAE',
  sector: 'FMCG',
  relevance: 'Direct',
  revenue: '$100M–500M',
  employees: '1K–5K',
  confidence: 79,
  x: 80,
  y: 40,
  color: 'var(--node-navy)',
  summary: 'UAE water and tissue products company with market leadership in bottled water across the Gulf.',
  execs: [{
    id: 141,
    name: 'Karim Rahal',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 15,
  name: 'NFPC Group',
  city: 'Abu Dhabi',
  country: 'UAE',
  sector: 'FMCG',
  relevance: 'Direct',
  revenue: '$500M–1B',
  employees: '5K–10K',
  confidence: 82,
  x: 72,
  y: 62,
  color: 'var(--node-navy)',
  summary: 'National Food Products Company — diversified food and beverage group with dairy, water and juice.',
  execs: [{
    id: 151,
    name: 'Hamad Al Ketbi',
    title: 'Group CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }, {
    id: 152,
    name: 'Mariam Al Zaabi',
    title: 'CHRO',
    level: 'N-1',
    enriched: false,
    verified: false
  }]
}, {
  id: 16,
  name: 'SADAFCO',
  city: 'Jeddah',
  country: 'Saudi Arabia',
  sector: 'Dairy',
  relevance: 'Direct',
  revenue: '$500M–1B',
  employees: '1K–5K',
  confidence: 85,
  x: 36,
  y: 52,
  color: 'var(--node-navy)',
  summary: 'Saudi Dairy & Foodstuff Company — a leading producer of long-life dairy, ice cream and tomato paste.',
  execs: [{
    id: 161,
    name: 'Wout Matthijs',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 17,
  name: 'Al Rawabi Dairy',
  city: 'Dubai',
  country: 'UAE',
  sector: 'Dairy',
  relevance: 'Adjacent',
  revenue: '$100M–500M',
  employees: '1K–5K',
  confidence: 72,
  x: 76,
  y: 56,
  color: 'var(--node-navy)',
  summary: 'UAE fresh dairy producer with vertically integrated farms, processing and distribution.',
  execs: [{
    id: 171,
    name: 'Ahmad Abdallah',
    title: 'General Manager',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 18,
  name: 'Edita Food Industries',
  city: 'Cairo',
  country: 'Egypt',
  sector: 'FMCG',
  relevance: 'Adjacent',
  revenue: '$100M–500M',
  employees: '5K–10K',
  confidence: 65,
  x: 20,
  y: 52,
  color: 'var(--node-navy)',
  summary: 'Egyptian leader in packaged snacks and baked goods with brands across North Africa.',
  execs: [{
    id: 181,
    name: 'Hani Berzi',
    title: 'Chairman & CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }, {
    id: 182,
    name: 'Menna El Baz',
    title: 'CFO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 19,
  name: 'Olam Agri',
  city: 'Dubai',
  country: 'UAE',
  sector: 'Agri & Commodities',
  relevance: 'AI Inferred',
  revenue: '>$5B',
  employees: '10K–50K',
  confidence: 48,
  x: 74,
  y: 64,
  color: 'var(--node-navy)',
  summary: 'Global agri-business with Middle East headquarters, trading and processing grains, edible oils and cotton.',
  execs: [{
    id: 191,
    name: 'Sunny Verghese',
    title: 'Group CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 20,
  name: 'Al Munajem Foods',
  city: 'Riyadh',
  country: 'Saudi Arabia',
  sector: 'Food & Retail',
  relevance: 'Direct',
  revenue: '$500M–1B',
  employees: '1K–5K',
  confidence: 81,
  x: 50,
  y: 44,
  color: 'var(--node-navy)',
  summary: 'Saudi food distribution and cold-chain logistics company covering frozen, chilled and ambient segments.',
  execs: [{
    id: 201,
    name: 'Abdulaziz Al Munajem',
    title: 'CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }]
}, {
  id: 21,
  name: 'Gulf Food Industries',
  city: 'Ajman',
  country: 'UAE',
  sector: 'FMCG',
  relevance: 'AI Inferred',
  revenue: '<$100M',
  employees: '500–1K',
  confidence: 55,
  x: 78,
  y: 50,
  color: 'var(--node-navy)',
  summary: 'Ajman-based manufacturer of confectionery, biscuits and snacks for GCC and export markets.',
  execs: [{
    id: 211,
    name: 'Tariq Jassim',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 22,
  name: 'Arabian Food Supplies',
  city: 'Doha',
  country: 'Qatar',
  sector: 'Food Service',
  relevance: 'Adjacent',
  revenue: '$100M–500M',
  employees: '1K–5K',
  confidence: 63,
  x: 65,
  y: 34,
  color: 'var(--node-navy)',
  summary: 'Qatari food service distributor supplying hotels, restaurants and institutional catering.',
  execs: [{
    id: 221,
    name: 'Hassan Al Thani',
    title: 'Managing Director',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 23,
  name: 'Basamh Trading',
  city: 'Jeddah',
  country: 'Saudi Arabia',
  sector: 'Food & Retail',
  relevance: 'AI Inferred',
  revenue: '$100M–500M',
  employees: '1K–5K',
  confidence: 47,
  x: 34,
  y: 62,
  color: 'var(--node-navy)',
  summary: 'Saudi import and distribution company for branded food products and FMCG.',
  execs: [{
    id: 231,
    name: 'Omar Basamh',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 24,
  name: 'KDD',
  city: 'Kuwait City',
  country: 'Kuwait',
  sector: 'Dairy',
  relevance: 'Adjacent',
  revenue: '<$100M',
  employees: '500–1K',
  confidence: 59,
  x: 58,
  y: 30,
  color: 'var(--node-navy)',
  summary: 'Kuwait Danish Dairy — producer of fresh dairy, juice and ice cream for the Kuwaiti market.',
  execs: [{
    id: 241,
    name: 'Bader Al Kharafi',
    title: 'Chairman',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  id: 25,
  name: 'Obour Land',
  city: 'Cairo',
  country: 'Egypt',
  sector: 'Dairy & Juice',
  relevance: 'AI Inferred',
  revenue: '<$100M',
  employees: '1K–5K',
  confidence: 44,
  x: 18,
  y: 60,
  color: 'var(--node-navy)',
  summary: 'Egyptian dairy and juice brand competing in the mass-market fresh dairy segment.',
  execs: [{
    id: 251,
    name: 'Khaled Mansour',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
});

// ── Master filter lists (scope-based: all possible values) ───────────────────
const TM_MASTER_COUNTRIES = ['UAE', 'Saudi Arabia', 'Kuwait', 'Qatar', 'Bahrain', 'Oman', 'Egypt', 'Jordan', 'Lebanon'];
const TM_MASTER_SECTORS = ['FMCG', 'Food & Retail', 'Dairy', 'Dairy & Juice', 'Food Service', 'Agri & Dairy', 'Agri & Commodities', 'Logistics & Supply Chain', 'Healthcare', 'Pharmaceuticals', 'Hospitality & Tourism', 'Construction & Real Estate', 'Technology', 'Energy & Utilities', 'Financial Services'];
const TM_MASTER_REVENUE = ['<$100M', '$100M\u2013500M', '$500M\u20131B', '$1B\u20135B', '>$5B'];
const TM_MASTER_RELEVANCE = ['Direct', 'Adjacent', 'AI Inferred'];

// ── Fetchable company pool (AI adds these when user expands scope) ───────────
let _nextId = 100;
const TM_FETCHABLE_POOL = [
// Bahrain
{
  name: 'Bahrain Flour Mills',
  city: 'Manama',
  country: 'Bahrain',
  sector: 'FMCG',
  relevance: 'AI Inferred',
  revenue: '<$100M',
  employees: '500\u20131K',
  confidence: 51,
  summary: 'Bahraini manufacturer of flour, animal feed and bakery products serving the local market.',
  execs: [{
    name: 'Ali Al Khalifa',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  name: 'Delmon Poultry',
  city: 'Manama',
  country: 'Bahrain',
  sector: 'Food & Retail',
  relevance: 'AI Inferred',
  revenue: '<$100M',
  employees: '500\u20131K',
  confidence: 46,
  summary: 'Leading poultry producer in Bahrain with integrated farming and processing operations.',
  execs: [{
    name: 'Yusuf Kanoo',
    title: 'General Manager',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
},
// Jordan
{
  name: 'Nuqul Group',
  city: 'Amman',
  country: 'Jordan',
  sector: 'FMCG',
  relevance: 'Adjacent',
  revenue: '$100M\u2013500M',
  employees: '5K\u201310K',
  confidence: 68,
  summary: 'Jordanian conglomerate manufacturing tissue, hygiene and packaging products across MENA.',
  execs: [{
    name: 'Ghassan Nuqul',
    title: 'Vice Chairman',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }]
}, {
  name: 'Jordan Dairy',
  city: 'Amman',
  country: 'Jordan',
  sector: 'Dairy',
  relevance: 'AI Inferred',
  revenue: '<$100M',
  employees: '1K\u20135K',
  confidence: 53,
  summary: 'Jordanian dairy company producing fresh milk, yoghurt and cheese for local and regional distribution.',
  execs: [{
    name: 'Ayman Hajjar',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  name: 'Hikma Pharmaceuticals',
  city: 'Amman',
  country: 'Jordan',
  sector: 'Pharmaceuticals',
  relevance: 'Adjacent',
  revenue: '$1B\u20135B',
  employees: '5K\u201310K',
  confidence: 72,
  summary: 'Global generic pharmaceuticals company headquartered in Jordan with US and European operations.',
  execs: [{
    name: 'Said Darwazah',
    title: 'CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }]
},
// Lebanon
{
  name: 'Liban Lait',
  city: 'Beirut',
  country: 'Lebanon',
  sector: 'Dairy',
  relevance: 'AI Inferred',
  revenue: '<$100M',
  employees: '500\u20131K',
  confidence: 42,
  summary: 'Lebanese dairy producer known for its labneh and fresh cheese brands across the Levant.',
  execs: [{
    name: 'Georges Haddad',
    title: 'Managing Director',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  name: 'Gandour',
  city: 'Beirut',
  country: 'Lebanon',
  sector: 'FMCG',
  relevance: 'Adjacent',
  revenue: '$100M\u2013500M',
  employees: '1K\u20135K',
  confidence: 61,
  summary: 'Lebanese confectionery and biscuits manufacturer with distribution across the Middle East and Africa.',
  execs: [{
    name: 'Rami Gandour',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
},
// New sectors for existing countries
{
  name: 'Aramex',
  city: 'Dubai',
  country: 'UAE',
  sector: 'Logistics & Supply Chain',
  relevance: 'Adjacent',
  revenue: '$1B\u20135B',
  employees: '10K\u201350K',
  confidence: 66,
  summary: 'Global logistics and courier company headquartered in UAE serving e-commerce and freight sectors.',
  execs: [{
    name: 'Othman Aljeda',
    title: 'CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }]
}, {
  name: 'Agility Logistics',
  city: 'Kuwait City',
  country: 'Kuwait',
  sector: 'Logistics & Supply Chain',
  relevance: 'AI Inferred',
  revenue: '>$5B',
  employees: '10K\u201350K',
  confidence: 49,
  summary: 'Kuwaiti logistics company operating warehousing, freight forwarding and supply chain solutions globally.',
  execs: [{
    name: 'Tarek Sultan',
    title: 'Vice Chairman & CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }]
}, {
  name: 'NMC Healthcare',
  city: 'Abu Dhabi',
  country: 'UAE',
  sector: 'Healthcare',
  relevance: 'AI Inferred',
  revenue: '$1B\u20135B',
  employees: '10K\u201350K',
  confidence: 44,
  summary: 'UAE-based private healthcare company operating hospitals and clinics across the Gulf.',
  execs: [{
    name: 'Michael Davis',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  name: 'Rotana Hotels',
  city: 'Abu Dhabi',
  country: 'UAE',
  sector: 'Hospitality & Tourism',
  relevance: 'AI Inferred',
  revenue: '$500M\u20131B',
  employees: '5K\u201310K',
  confidence: 41,
  summary: 'Leading hotel operator in the Middle East with properties across UAE, Saudi and beyond.',
  execs: [{
    name: 'Guy Hutchinson',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  name: 'Saudi German Hospitals',
  city: 'Jeddah',
  country: 'Saudi Arabia',
  sector: 'Healthcare',
  relevance: 'AI Inferred',
  revenue: '$500M\u20131B',
  employees: '5K\u201310K',
  confidence: 47,
  summary: 'Private hospital group operating multi-specialty hospitals in Saudi Arabia, UAE and Egypt.',
  execs: [{
    name: 'Sobhi Batterjee',
    title: 'Chairman',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  name: 'ACWA Power',
  city: 'Riyadh',
  country: 'Saudi Arabia',
  sector: 'Energy & Utilities',
  relevance: 'AI Inferred',
  revenue: '>$5B',
  employees: '5K\u201310K',
  confidence: 39,
  summary: 'Saudi developer and operator of power and desalinated water plants across the region.',
  execs: [{
    name: 'Paddy Padmanathan',
    title: 'CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }]
}, {
  name: 'Emaar Properties',
  city: 'Dubai',
  country: 'UAE',
  sector: 'Construction & Real Estate',
  relevance: 'AI Inferred',
  revenue: '>$5B',
  employees: '10K\u201350K',
  confidence: 38,
  summary: 'Dubai-based real estate development company behind Burj Khalifa and Dubai Mall.',
  execs: [{
    name: 'Mohamed Alabbar',
    title: 'Managing Director',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }]
}, {
  name: 'Gulf Finance House',
  city: 'Manama',
  country: 'Bahrain',
  sector: 'Financial Services',
  relevance: 'AI Inferred',
  revenue: '$100M\u2013500M',
  employees: '500\u20131K',
  confidence: 43,
  summary: 'Bahrain-based Islamic investment bank managing projects across GCC and North Africa.',
  execs: [{
    name: 'Hisham Al Rayes',
    title: 'CEO',
    level: 'C-Suite',
    enriched: false,
    verified: false
  }]
}, {
  name: 'Careem',
  city: 'Dubai',
  country: 'UAE',
  sector: 'Technology',
  relevance: 'AI Inferred',
  revenue: '$500M\u20131B',
  employees: '1K\u20135K',
  confidence: 45,
  summary: 'Regional ride-hailing and super-app platform operating across MENA.',
  execs: [{
    name: 'Mudassir Sheikha',
    title: 'CEO',
    level: 'C-Suite',
    enriched: true,
    verified: true
  }]
}];

// Assign IDs to the fetchable pool
TM_FETCHABLE_POOL.forEach((c, i) => {
  c.id = _nextId + i;
  c.x = 20 + Math.random() * 60;
  c.y = 25 + Math.random() * 50;
  c.color = 'var(--node-navy)';
  c.execs.forEach((e, j) => {
    e.id = c.id * 10 + j;
  });
});
const TM_REGION_LABELS = [{
  label: 'Saudi Arabia',
  x: 46,
  y: 46
}, {
  label: 'U.A.E.',
  x: 80,
  y: 54
}, {
  label: 'Egypt',
  x: 22,
  y: 50
}, {
  label: 'Gulf',
  x: 62,
  y: 26
}];
Object.assign(window, {
  TM_SUGGESTIONS,
  TM_RATIONALE,
  TM_COMPANIES,
  TM_REGION_LABELS,
  TM_MASTER_COUNTRIES,
  TM_MASTER_SECTORS,
  TM_MASTER_REVENUE,
  TM_MASTER_RELEVANCE,
  TM_FETCHABLE_POOL
});

// ── Pipeline mock data (project-scoped candidate entries) ────────────────────
const TM_PIPELINE = [{
  id: 'p1',
  contactName: 'Amira Haddad',
  title: 'Group Chief Executive',
  company: 'Almarai',
  stage: 'Interview',
  assignees: ['LH', 'OK'],
  ageDays: 2,
  availability: 'Open to move'
}, {
  id: 'p2',
  contactName: 'Yousef Iman',
  title: 'Chief Executive Officer',
  company: 'Savola Group',
  stage: 'Screening',
  assignees: ['OK'],
  ageDays: 5,
  availability: 'Passive'
}, {
  id: 'p3',
  contactName: 'Khalid Mansour',
  title: 'Group CEO',
  company: 'Agthia Group',
  stage: 'Contacted',
  assignees: ['LH'],
  ageDays: 3,
  availability: 'Open to move'
}, {
  id: 'p4',
  contactName: 'Sara Fadel',
  title: 'Chief Strategy Officer',
  company: 'Agthia Group',
  stage: 'Sourced',
  assignees: ['SM', 'FO'],
  ageDays: 1,
  availability: 'Passive'
}, {
  id: 'p5',
  contactName: 'Nadia Fawzy',
  title: 'CEO',
  company: 'Juhayna Food',
  stage: 'Offer',
  assignees: ['LH', 'OK', 'SM'],
  ageDays: 8,
  availability: 'Open to move'
}, {
  id: 'p6',
  contactName: 'Faisal Otaibi',
  title: 'Group CEO',
  company: 'Americana',
  stage: 'Sourced',
  assignees: ['FO'],
  ageDays: 4,
  availability: 'Unknown'
}, {
  id: 'p7',
  contactName: 'Saleh Al Abdooli',
  title: 'CEO',
  company: 'Al Islami Foods',
  stage: 'Contacted',
  assignees: ['OK', 'SM'],
  ageDays: 6,
  availability: 'Passive'
}, {
  id: 'p8',
  contactName: 'Hani Berzi',
  title: 'Chairman & CEO',
  company: 'Edita Food Industries',
  stage: 'Screening',
  assignees: ['SM'],
  ageDays: 3,
  availability: 'Open to move'
}, {
  id: 'p9',
  contactName: 'Nabil Halwani',
  title: 'CEO',
  company: 'Halwani Brothers',
  stage: 'Hired',
  assignees: ['LH'],
  ageDays: 14,
  availability: 'Relocated'
}, {
  id: 'p10',
  contactName: 'Rami Khoury',
  title: 'Chief Financial Officer',
  company: 'Almarai',
  stage: 'Closed',
  assignees: ['OK'],
  ageDays: 12,
  availability: 'Not interested'
}, {
  id: 'p11',
  contactName: 'Hamad Al Ketbi',
  title: 'Group CEO',
  company: 'NFPC Group',
  stage: 'Interview',
  assignees: ['SM', 'LH'],
  ageDays: 4,
  availability: 'Open to move'
}, {
  id: 'p12',
  contactName: 'Wout Matthijs',
  title: 'CEO',
  company: 'SADAFCO',
  stage: 'Sourced',
  assignees: ['FO', 'OK'],
  ageDays: 1,
  availability: 'Passive'
}];

// ── Global contacts directory (CRM — people across all projects) ─────────────
const TM_CONTACTS = [{
  id: 'c1',
  name: 'Amira Haddad',
  title: 'Group Chief Executive',
  company: 'Almarai',
  availability: 'Open to move',
  pipelines: ['Top FMCG distributors in UAE', 'CFO search — Qatar banking'],
  lastActivityDays: 2
}, {
  id: 'c2',
  name: 'Yousef Iman',
  title: 'Chief Executive Officer',
  company: 'Savola Group',
  availability: 'Passive',
  pipelines: ['Top FMCG distributors in UAE'],
  lastActivityDays: 5
}, {
  id: 'c3',
  name: 'Khalid Mansour',
  title: 'Group CEO',
  company: 'Agthia Group',
  availability: 'Open to move',
  pipelines: ['Top FMCG distributors in UAE'],
  lastActivityDays: 3
}, {
  id: 'c4',
  name: 'Sara Fadel',
  title: 'Chief Strategy Officer',
  company: 'Agthia Group',
  availability: 'Passive',
  pipelines: ['Top FMCG distributors in UAE'],
  lastActivityDays: 1
}, {
  id: 'c5',
  name: 'Nadia Fawzy',
  title: 'CEO',
  company: 'Juhayna Food',
  availability: 'Open to move',
  pipelines: ['Top FMCG distributors in UAE', 'Industrial equipment — Egypt'],
  lastActivityDays: 8
}, {
  id: 'c6',
  name: 'Faisal Otaibi',
  title: 'Group CEO',
  company: 'Americana',
  availability: 'Unknown',
  pipelines: ['Top FMCG distributors in UAE'],
  lastActivityDays: 4
}, {
  id: 'c7',
  name: 'Saleh Al Abdooli',
  title: 'CEO',
  company: 'Al Islami Foods',
  availability: 'Passive',
  pipelines: ['Top FMCG distributors in UAE'],
  lastActivityDays: 6
}, {
  id: 'c8',
  name: 'Hani Berzi',
  title: 'Chairman & CEO',
  company: 'Edita Food Industries',
  availability: 'Open to move',
  pipelines: ['Top FMCG distributors in UAE', 'Industrial equipment — Egypt'],
  lastActivityDays: 3
}, {
  id: 'c9',
  name: 'Nabil Halwani',
  title: 'CEO',
  company: 'Halwani Brothers',
  availability: 'Relocated',
  pipelines: ['Top FMCG distributors in UAE'],
  lastActivityDays: 14
}, {
  id: 'c10',
  name: 'Rami Khoury',
  title: 'Chief Financial Officer',
  company: 'Almarai',
  availability: 'Not interested',
  pipelines: ['Top FMCG distributors in UAE'],
  lastActivityDays: 12
}, {
  id: 'c11',
  name: 'Hamad Al Ketbi',
  title: 'Group CEO',
  company: 'NFPC Group',
  availability: 'Open to move',
  pipelines: ['Top FMCG distributors in UAE', 'Retail chains across GCC'],
  lastActivityDays: 4
}, {
  id: 'c12',
  name: 'Wout Matthijs',
  title: 'CEO',
  company: 'SADAFCO',
  availability: 'Passive',
  pipelines: ['Top FMCG distributors in UAE'],
  lastActivityDays: 1
}, {
  id: 'c13',
  name: 'Lina Nasr',
  title: 'Chief Operating Officer',
  company: 'Almarai',
  availability: 'Passive',
  pipelines: ['Leading PE firms in Saudi Arabia'],
  lastActivityDays: 18
}, {
  id: 'c14',
  name: 'Dana Aziz',
  title: 'Group HR Director',
  company: 'Savola Group',
  availability: 'Open to move',
  pipelines: ['Leading PE firms in Saudi Arabia'],
  lastActivityDays: 22
}, {
  id: 'c15',
  name: 'Omar Saleh',
  title: 'VP, Supply Chain',
  company: 'Almarai',
  availability: 'Unknown',
  pipelines: [],
  lastActivityDays: 30
}, {
  id: 'c16',
  name: 'Fatima Al Hammadi',
  title: 'CFO',
  company: 'Al Islami Foods',
  availability: 'Passive',
  pipelines: ['Retail chains across GCC'],
  lastActivityDays: 9
}, {
  id: 'c17',
  name: 'Marcos Tavares',
  title: 'Regional CEO',
  company: 'BRF Middle East',
  availability: 'Unknown',
  pipelines: [],
  lastActivityDays: 45
}, {
  id: 'c18',
  name: 'Mariam Al Zaabi',
  title: 'CHRO',
  company: 'NFPC Group',
  availability: 'Open to move',
  pipelines: ['Top FMCG distributors in UAE'],
  lastActivityDays: 7
}];

// ── Contact detail: per-project pipeline entries ─────────────────────────────
const TM_CONTACT_ENTRIES = {
  'c1': [
  // Amira Haddad — 2 projects
  {
    id: 'ce1a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Interview',
    assignees: ['LH', 'OK'],
    ageDays: 2
  }, {
    id: 'ce1b',
    project: 'CFO search — Qatar banking',
    stage: 'Sourced',
    assignees: ['SM'],
    ageDays: 8
  }],
  'c2': [{
    id: 'ce2a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Screening',
    assignees: ['OK'],
    ageDays: 5
  }],
  'c3': [{
    id: 'ce3a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Contacted',
    assignees: ['LH'],
    ageDays: 3
  }],
  'c4': [{
    id: 'ce4a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Sourced',
    assignees: ['SM', 'FO'],
    ageDays: 1
  }],
  'c5': [
  // Nadia Fawzy — 2 projects
  {
    id: 'ce5a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Offer',
    assignees: ['LH', 'OK', 'SM'],
    ageDays: 8
  }, {
    id: 'ce5b',
    project: 'Industrial equipment — Egypt',
    stage: 'Contacted',
    assignees: ['FO'],
    ageDays: 5
  }],
  'c6': [{
    id: 'ce6a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Sourced',
    assignees: ['FO'],
    ageDays: 4
  }],
  'c7': [{
    id: 'ce7a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Contacted',
    assignees: ['OK', 'SM'],
    ageDays: 6
  }],
  'c8': [
  // Hani Berzi — 2 projects
  {
    id: 'ce8a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Screening',
    assignees: ['SM'],
    ageDays: 3
  }, {
    id: 'ce8b',
    project: 'Industrial equipment — Egypt',
    stage: 'Sourced',
    assignees: ['LH'],
    ageDays: 10
  }],
  'c9': [{
    id: 'ce9a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Hired',
    assignees: ['LH'],
    ageDays: 14
  }],
  'c10': [{
    id: 'ce10a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Closed',
    assignees: ['OK'],
    ageDays: 12
  }],
  'c11': [
  // Hamad Al Ketbi — 2 projects
  {
    id: 'ce11a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Interview',
    assignees: ['SM', 'LH'],
    ageDays: 4
  }, {
    id: 'ce11b',
    project: 'Retail chains across GCC',
    stage: 'Sourced',
    assignees: ['FO'],
    ageDays: 6
  }],
  'c12': [{
    id: 'ce12a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Sourced',
    assignees: ['FO', 'OK'],
    ageDays: 1
  }],
  'c13': [{
    id: 'ce13a',
    project: 'Leading PE firms in Saudi Arabia',
    stage: 'Contacted',
    assignees: ['LH'],
    ageDays: 18
  }],
  'c14': [{
    id: 'ce14a',
    project: 'Leading PE firms in Saudi Arabia',
    stage: 'Sourced',
    assignees: ['OK'],
    ageDays: 22
  }],
  'c16': [{
    id: 'ce16a',
    project: 'Retail chains across GCC',
    stage: 'Contacted',
    assignees: ['LH'],
    ageDays: 9
  }],
  'c18': [{
    id: 'ce18a',
    project: 'Top FMCG distributors in UAE',
    stage: 'Screening',
    assignees: ['SM'],
    ageDays: 7
  }]
};

// ── Contact detail: activity timelines ───────────────────────────────────────
const TM_CONTACT_ACTIVITIES = {
  'c1': [{
    id: 'ca1a',
    type: 'stage_change',
    fromStage: 'Screening',
    toStage: 'Interview',
    project: 'Top FMCG distributors in UAE',
    author: 'LH',
    ageDays: 2
  }, {
    id: 'ca1b',
    type: 'call',
    body: 'Introductory call. Open to hearing more, cautious on relocation.',
    project: 'Top FMCG distributors in UAE',
    author: 'LH',
    ageDays: 3
  }, {
    id: 'ca1c',
    type: 'email',
    body: 'Sent role brief and NDA. Awaiting response.',
    project: 'Top FMCG distributors in UAE',
    author: 'OK',
    ageDays: 5
  }, {
    id: 'ca1d',
    type: 'note',
    body: 'Strong FMCG track record. Currently weighing an internal promotion.',
    project: 'Top FMCG distributors in UAE',
    author: 'LH',
    ageDays: 6
  }, {
    id: 'ca1e',
    type: 'added',
    body: 'Added from search "Top FMCG distributors in UAE"',
    project: 'Top FMCG distributors in UAE',
    author: 'LH',
    ageDays: 7
  }, {
    id: 'ca1f',
    type: 'added',
    body: 'Added from search "CFO search — Qatar banking"',
    project: 'CFO search — Qatar banking',
    author: 'SM',
    ageDays: 8
  }],
  'c5': [{
    id: 'ca5a',
    type: 'stage_change',
    fromStage: 'Interview',
    toStage: 'Offer',
    project: 'Top FMCG distributors in UAE',
    author: 'LH',
    ageDays: 4
  }, {
    id: 'ca5b',
    type: 'meeting',
    body: 'Panel interview with client. Very positive feedback received.',
    project: 'Top FMCG distributors in UAE',
    author: 'OK',
    ageDays: 6
  }, {
    id: 'ca5c',
    type: 'call',
    body: 'Salary expectations aligned. Offer to be issued by end of week.',
    project: 'Top FMCG distributors in UAE',
    author: 'SM',
    ageDays: 7
  }, {
    id: 'ca5d',
    type: 'added',
    body: 'Added from search "Industrial equipment — Egypt"',
    project: 'Industrial equipment — Egypt',
    author: 'FO',
    ageDays: 5
  }, {
    id: 'ca5e',
    type: 'added',
    body: 'Added from search "Top FMCG distributors in UAE"',
    project: 'Top FMCG distributors in UAE',
    author: 'LH',
    ageDays: 8
  }],
  'c8': [{
    id: 'ca8a',
    type: 'stage_change',
    fromStage: 'Contacted',
    toStage: 'Screening',
    project: 'Top FMCG distributors in UAE',
    author: 'SM',
    ageDays: 2
  }, {
    id: 'ca8b',
    type: 'email',
    body: 'Sent detailed brief. Good initial interest expressed.',
    project: 'Top FMCG distributors in UAE',
    author: 'SM',
    ageDays: 3
  }, {
    id: 'ca8c',
    type: 'added',
    body: 'Added from search "Industrial equipment — Egypt"',
    project: 'Industrial equipment — Egypt',
    author: 'LH',
    ageDays: 10
  }, {
    id: 'ca8d',
    type: 'added',
    body: 'Added from search "Top FMCG distributors in UAE"',
    project: 'Top FMCG distributors in UAE',
    author: 'SM',
    ageDays: 3
  }],
  'c11': [{
    id: 'ca11a',
    type: 'stage_change',
    fromStage: 'Contacted',
    toStage: 'Interview',
    project: 'Top FMCG distributors in UAE',
    author: 'SM',
    ageDays: 2
  }, {
    id: 'ca11b',
    type: 'call',
    body: 'Second call. Needs board sign-off before moving forward.',
    project: 'Top FMCG distributors in UAE',
    author: 'LH',
    ageDays: 3
  }, {
    id: 'ca11c',
    type: 'note',
    body: 'Flagged for Retail chains mandate — strong GCC consumer background.',
    project: 'Retail chains across GCC',
    author: 'FO',
    ageDays: 5
  }, {
    id: 'ca11d',
    type: 'added',
    body: 'Added from search "Top FMCG distributors in UAE"',
    project: 'Top FMCG distributors in UAE',
    author: 'SM',
    ageDays: 4
  }, {
    id: 'ca11e',
    type: 'added',
    body: 'Added from search "Retail chains across GCC"',
    project: 'Retail chains across GCC',
    author: 'FO',
    ageDays: 6
  }]
};

// ── Project team members (seed — users working the active mandate) ───────────
const TM_PROJECT_TEAM = [{
  initials: 'LH',
  name: 'Layla Hassan',
  role: 'Lead partner'
}, {
  initials: 'OK',
  name: 'Omar Khalil',
  role: 'Researcher'
}, {
  initials: 'SM',
  name: 'Sara Mitchell',
  role: 'Coordinator'
}];

// ── Position brief (seed data for the active project mandate) ────────────────
const TM_POSITION = {
  jdFile: 'Group_CIO_Brief_2024.pdf',
  jdText: '',
  requirements: '• 15+ years in senior technology leadership roles\n• P&L responsibility across multi-country operations\n• Track record of digital transformation in regulated industries\n• Fluent Arabic and English preferred\n• Experience leading M&A integrations in emerging markets\n• Strong stakeholder management at Board level',
  criteria: ['FMCG or consumer goods background', 'GCC market experience', 'Listed company exposure', 'Board-level presence'],
  salary: 'AED 850,000 \u2013 1,100,000 base',
  bonus: '30\u201340% of base',
  equity: 'LTIP eligible (Year 2+)',
  compensationNotes: '',
  location: 'UAE (Dubai or Abu Dhabi) \u2014 relocation supported'
};

// ── Strategy brief (seed data — project profile + target companies) ──────────
const TM_STRATEGY = {
  industry: 'FMCG \u0026 Consumer Goods',
  specialty: 'Technology \u0026 Digital',
  department: 'Information Technology',
  seniority: 'C-Suite',
  locationFocus: 'UAE, Saudi Arabia',
  investor: '',
  clientCompany: 'Al Rabie Saudi Foods Co.',
  searchParams: 'Targeting senior technology executives with a proven FMCG transformation track record in GCC markets. Priority on candidates with P\u0026L ownership, enterprise ERP rollouts, and experience scaling digital operations across multi-country environments.',
  targetCompanies: [{
    id: 'tc1',
    name: 'Almarai',
    status: 'Approached',
    notes: 'Spoke to CHRO \u2014 open to exploratory calls'
  }, {
    id: 'tc2',
    name: 'Savola Group',
    status: 'Target',
    notes: 'Warm intro via Khalid'
  }, {
    id: 'tc3',
    name: 'Agthia Group',
    status: 'Target',
    notes: ''
  }, {
    id: 'tc4',
    name: 'IFFCO',
    status: 'Off-limits',
    notes: 'Conflict \u2014 active client relationship'
  }, {
    id: 'tc5',
    name: 'Juhayna Food',
    status: 'Target',
    notes: 'Egypt expansion angle'
  }, {
    id: 'tc6',
    name: 'Americana',
    status: 'Approached',
    notes: 'No response to outreach yet'
  }, {
    id: 'tc7',
    name: 'NFPC Group',
    status: 'Target',
    notes: ''
  }, {
    id: 'tc8',
    name: 'Halwani Brothers',
    status: 'Off-limits',
    notes: 'Former client \u2014 12-month exclusion period'
  }]
};

// ── Long list (scored candidate shortlist for the active project) ─────────────
const TM_LONG_LIST = [{
  id: 'll1',
  rank: 1,
  contactName: 'Amira Haddad',
  title: 'Group Chief Executive',
  company: 'Almarai',
  availability: 'Open to move',
  score: 'up',
  comment: 'Exceptional FMCG pedigree — top priority',
  ageDays: 2,
  inPipeline: true
}, {
  id: 'll2',
  rank: 2,
  contactName: 'Hamad Al Ketbi',
  title: 'Group CEO',
  company: 'NFPC Group',
  availability: 'Open to move',
  score: 'up',
  comment: 'Strong GCC consumer goods background',
  ageDays: 4,
  inPipeline: true
}, {
  id: 'll3',
  rank: 3,
  contactName: 'Khalid Mansour',
  title: 'Group CEO',
  company: 'Agthia Group',
  availability: 'Open to move',
  score: 'up',
  comment: 'Digital transformation record stands out',
  ageDays: 3,
  inPipeline: true
}, {
  id: 'll4',
  rank: 4,
  contactName: 'Hani Berzi',
  title: 'Chairman & CEO',
  company: 'Edita Food Industries',
  availability: 'Open to move',
  score: 'up',
  comment: '',
  ageDays: 3,
  inPipeline: true
}, {
  id: 'll5',
  rank: 5,
  contactName: 'Nadia Fawzy',
  title: 'CEO',
  company: 'Juhayna Food',
  availability: 'Open to move',
  score: 'neutral',
  comment: 'Strong background \u2014 salary expectations to manage',
  ageDays: 8,
  inPipeline: true
}, {
  id: 'll6',
  rank: 6,
  contactName: 'Yousef Iman',
  title: 'Chief Executive Officer',
  company: 'Savola Group',
  availability: 'Passive',
  score: 'neutral',
  comment: '',
  ageDays: 5,
  inPipeline: true
}, {
  id: 'll7',
  rank: 7,
  contactName: 'Saleh Al Abdooli',
  title: 'CEO',
  company: 'Al Islami Foods',
  availability: 'Passive',
  score: 'neutral',
  comment: 'Monitor \u2014 may become available in Q2',
  ageDays: 6,
  inPipeline: true
}, {
  id: 'll8',
  rank: 8,
  contactName: 'Wout Matthijs',
  title: 'CEO',
  company: 'SADAFCO',
  availability: 'Passive',
  score: '',
  comment: '',
  ageDays: 1,
  inPipeline: true
}, {
  id: 'll9',
  rank: 9,
  contactName: 'Faisal Otaibi',
  title: 'Group CEO',
  company: 'Americana',
  availability: 'Unknown',
  score: '',
  comment: '',
  ageDays: 4,
  inPipeline: true
}, {
  id: 'll10',
  rank: 10,
  contactName: 'Mariam Al Zaabi',
  title: 'CHRO',
  company: 'NFPC Group',
  availability: 'Open to move',
  score: '',
  comment: 'Referred by Layla \u2014 first call pending',
  ageDays: 2,
  inPipeline: false
}, {
  id: 'll11',
  rank: 11,
  contactName: 'Marcos Tavares',
  title: 'Regional CEO',
  company: 'BRF Middle East',
  availability: 'Unknown',
  score: 'down',
  comment: 'Does not meet GCC experience threshold',
  ageDays: 7,
  inPipeline: false
}, {
  id: 'll12',
  rank: 12,
  contactName: 'Rami Khoury',
  title: 'Chief Financial Officer',
  company: 'Almarai',
  availability: 'Not interested',
  score: 'down',
  comment: 'Not interested \u2014 confirmed via email',
  ageDays: 12,
  inPipeline: false
}];

// ── Status report (seed — partial draft for the active project) ──────────────
const TM_STATUS_REPORT = {
  keyCandidates: 'Amira Haddad (Almarai) has progressed to Interview stage and remains our highest-priority candidate. Hamad Al Ketbi (NFPC Group) has confirmed interest and is available for a first call next week.\n\nNadia Fawzy (Juhayna Food) is at Offer stage — compensation alignment in progress.',
  marketObservations: 'Senior technology talent in the GCC remains in short supply, particularly candidates combining FMCG sector depth with listed-company board exposure. Compensation expectations have risen approximately 15% year-on-year for C-Suite technology mandates in the UAE and KSA.',
  nextSteps: '\u2022 Confirm interview panel availability for Amira Haddad\n\u2022 Schedule introductory call with Hamad Al Ketbi\n\u2022 Finalise offer package discussion with Nadia Fawzy\n\u2022 Expand outreach to Savola Group and Agthia Group technology leadership'
};

// ── Internal workspace (seed — private team notes + activity feed) ───────────
const TM_INTERNAL = {
  scratchpad: 'Client (Al Rabie) strongly prefers an internal-to-sector hire — flagged sensitivity around poaching from Almarai directly. Keep Amira approach discreet.\n\nBudget has flex up to AED 1.2M base for the right profile — do not disclose ceiling to candidates.',
  comments: [{
    id: 'ci1',
    author: 'Omar Khalil',
    initials: 'OK',
    text: 'Amira confirmed for interview panel next Tuesday. Sending brief to the client this afternoon.',
    ageDays: 0
  }, {
    id: 'ci2',
    author: 'Sara Mitchell',
    initials: 'SM',
    text: 'Heads up — Rami Khoury declined via email, removed from active outreach.',
    ageDays: 1
  }, {
    id: 'ci3',
    author: 'Layla Hassan',
    initials: 'LH',
    text: 'Let\u2019s prioritise the NFPC and Agthia leads this week. Savola can wait until we hear back on the first round.',
    ageDays: 2
  }]
};
const TM_INTERNAL_ACTIVITY = [{
  type: 'stage',
  actor: 'Omar Khalil',
  text: 'moved Amira Haddad to Interview',
  ageDays: 0
}, {
  type: 'note',
  actor: 'Sara Mitchell',
  text: 'added a comment on the discussion thread',
  ageDays: 1
}, {
  type: 'add',
  actor: 'Layla Hassan',
  text: 'added Mariam Al Zaabi to the long list',
  ageDays: 1
}, {
  type: 'score',
  actor: 'Layla Hassan',
  text: 'scored Hamad Al Ketbi as recommended',
  ageDays: 2
}, {
  type: 'status',
  actor: 'Omar Khalil',
  text: 'marked IFFCO as off-limits',
  ageDays: 3
}, {
  type: 'stage',
  actor: 'Sara Mitchell',
  text: 'moved Nadia Fawzy to Offer',
  ageDays: 4
}, {
  type: 'add',
  actor: 'Omar Khalil',
  text: 'created the search strategy',
  ageDays: 6
}];

// ── Contact type metadata ─────────────────────────────────────────────────────
const TM_CONTACT_TYPE_META = {
  'Candidate': {
    label: 'Candidate',
    icon: 'User',
    fg: '#1d4ed8',
    bg: 'rgba(37,99,235,.10)'
  },
  'Client': {
    label: 'Client',
    icon: 'Briefcase',
    fg: 'var(--success-fg, #15803d)',
    bg: 'var(--success-bg, rgba(5,150,105,.10))'
  },
  'Source': {
    label: 'Source',
    icon: 'BookOpen',
    fg: '#7c3aed',
    bg: 'rgba(124,58,237,.10)'
  }
};

// ── Account type metadata ─────────────────────────────────────────────────────
const TM_ACCOUNT_TYPE_META = {
  'Client': {
    label: 'Client',
    icon: 'Briefcase',
    fg: 'var(--success-fg, #15803d)',
    bg: 'var(--success-bg, rgba(5,150,105,.10))'
  },
  'Prospect': {
    label: 'Prospect',
    icon: 'Target',
    fg: '#1d4ed8',
    bg: 'rgba(37,99,235,.10)'
  },
  'Source': {
    label: 'Source',
    icon: 'BookOpen',
    fg: '#7c3aed',
    bg: 'rgba(124,58,237,.10)'
  },
  'Off-limits': {
    label: 'Off-limits',
    icon: 'ShieldAlert',
    fg: '#b91c1c',
    bg: 'rgba(220,38,38,.10)'
  }
};

// ── Relationship strength metadata ───────────────────────────────────────────
const TM_STRENGTH_META = {
  'Strong': {
    v: 3,
    fg: 'var(--success-fg, #15803d)'
  },
  'Active': {
    v: 2,
    fg: '#1d4ed8'
  },
  'Dormant': {
    v: 1,
    fg: 'var(--muted-foreground)'
  }
};

// ── Mandate status metadata ──────────────────────────────────────────────────
const TM_MANDATE_STATUS_META = {
  'Active': {
    fg: '#1d4ed8',
    bg: 'rgba(37,99,235,.10)'
  },
  'Placed': {
    fg: 'var(--success-fg, #15803d)',
    bg: 'var(--success-bg, rgba(5,150,105,.10))'
  },
  'Pitching': {
    fg: '#b45309',
    bg: 'rgba(245,158,11,.12)'
  },
  'On hold': {
    fg: 'var(--muted-foreground)',
    bg: 'var(--muted)'
  },
  'Lost': {
    fg: '#b91c1c',
    bg: 'rgba(220,38,38,.10)'
  }
};

// ── Accounts (CRM company records) ──────────────────────────────────────────
const TM_ACCOUNTS = [{
  id: 'acc1',
  name: 'Al Rabie Saudi Foods Co.',
  type: 'Client',
  owner: 'LH',
  strength: 'Strong',
  website: 'alrabie.com',
  ownership: 'Public (Tadawul)',
  source: 'Direct referral',
  sinceDays: 840,
  lastActivityDays: 2,
  offLimits: null,
  keyFacts: 'Major Saudi F&B manufacturer. Expanding into GCC-wide distribution. Board recently approved a 5-year digital transformation programme. Strong existing relationship through 2 placed mandates.',
  appearances: [{
    project: 'FMCG & Food — GCC',
    role: 'Client',
    mapped: 4
  }],
  mandates: [{
    id: 'am1',
    role: 'Group Chief Information Officer',
    status: 'Active',
    stageNote: '3 screening, 1 interview',
    placed: null,
    fee: '$120K'
  }, {
    id: 'am2',
    role: 'VP Supply Chain',
    status: 'Placed',
    stageNote: null,
    placed: 'Khalid Rahman',
    fee: '$85K'
  }]
}, {
  id: 'acc2',
  name: 'Almarai',
  type: 'Source',
  owner: 'OK',
  strength: 'Active',
  website: 'almarai.com',
  ownership: 'Public (Tadawul)',
  source: 'Talent map',
  sinceDays: 365,
  lastActivityDays: 5,
  offLimits: null,
  keyFacts: 'Largest dairy company in the Middle East. Excellent pipeline of senior FMCG executives. Multiple candidates sourced for other mandates.',
  appearances: [{
    project: 'FMCG & Food — GCC',
    role: 'Source',
    mapped: 12
  }],
  mandates: []
}, {
  id: 'acc3',
  name: 'IFFCO',
  type: 'Off-limits',
  owner: 'LH',
  strength: 'Dormant',
  website: 'iffco.com',
  ownership: 'Private (cooperative)',
  source: 'Industry mapping',
  sinceDays: 280,
  lastActivityDays: 30,
  offLimits: {
    reason: 'Active client placement — 12-month exclusion (VP Manufacturing placed Aug 2024)',
    untilDays: 120
  },
  keyFacts: 'Major oils & fats manufacturer. Cooperative structure. Previously placed VP Manufacturing; off-limits restriction applies until Q1 2025.',
  appearances: [{
    project: 'FMCG & Food — GCC',
    role: 'Source',
    mapped: 6
  }],
  mandates: [{
    id: 'am3',
    role: 'VP Manufacturing',
    status: 'Placed',
    stageNote: null,
    placed: 'Rashid Khoury',
    fee: '$90K'
  }]
}, {
  id: 'acc4',
  name: 'Savola Group',
  type: 'Source',
  owner: 'SM',
  strength: 'Active',
  website: 'savola.com',
  ownership: 'Public (Tadawul)',
  source: 'Talent map',
  sinceDays: 210,
  lastActivityDays: 8,
  offLimits: null,
  keyFacts: 'Diversified food group — edible oils (Afia), sugar, retail (Panda). Broad executive bench across operations and commercial roles.',
  appearances: [{
    project: 'FMCG & Food — GCC',
    role: 'Source',
    mapped: 8
  }],
  mandates: []
}, {
  id: 'acc5',
  name: 'NFPC Group',
  type: 'Prospect',
  owner: 'LH',
  strength: 'Active',
  website: 'nfpcgroup.com',
  ownership: 'Private',
  source: 'Conference (Gulf Food 2024)',
  sinceDays: 180,
  lastActivityDays: 12,
  offLimits: null,
  keyFacts: 'Water & beverages leader in UAE. Growth mode — actively expanding into new categories. CHRO expressed interest in a CCO search.',
  appearances: [{
    project: 'FMCG & Food — GCC',
    role: 'Source',
    mapped: 5
  }],
  mandates: [{
    id: 'am4',
    role: 'Chief Commercial Officer',
    status: 'Pitching',
    stageNote: 'Proposal sent',
    placed: null,
    fee: '$110K'
  }]
}, {
  id: 'acc6',
  name: 'Agthia Group',
  type: 'Client',
  owner: 'OK',
  strength: 'Strong',
  website: 'agthia.com',
  ownership: 'Public (ADX)',
  source: 'Partner referral',
  sinceDays: 520,
  lastActivityDays: 3,
  offLimits: null,
  keyFacts: 'Abu Dhabi-listed F&B group — Al Ain water, Grand Mills flour. Active mandate for digital transformation. Strong board-level access.',
  appearances: [{
    project: 'FMCG & Food — GCC',
    role: 'Client',
    mapped: 3
  }],
  mandates: [{
    id: 'am5',
    role: 'Head of Digital Transformation',
    status: 'Active',
    stageNote: '2 contacted, 1 screening',
    placed: null,
    fee: '$95K'
  }]
}, {
  id: 'acc7',
  name: 'Americana Restaurants',
  type: 'Prospect',
  owner: 'SM',
  strength: 'Dormant',
  website: 'americana-food.com',
  ownership: 'Public (ADX)',
  source: 'Cold outreach',
  sinceDays: 90,
  lastActivityDays: 45,
  offLimits: null,
  keyFacts: 'Largest restaurant operator in MENA — KFC, Pizza Hut, Hardee\'s. Recently relisted. Potential for VP Operations and technology mandates.',
  appearances: [],
  mandates: []
}, {
  id: 'acc8',
  name: 'Al Ain Farms',
  type: 'Source',
  owner: 'OK',
  strength: 'Active',
  website: 'alainfarms.com',
  ownership: 'Government-linked',
  source: 'Talent map',
  sinceDays: 150,
  lastActivityDays: 6,
  offLimits: null,
  keyFacts: 'Premium dairy and poultry in Abu Dhabi. Government-linked ownership. Good source for operations and supply chain executives.',
  appearances: [{
    project: 'FMCG & Food — GCC',
    role: 'Source',
    mapped: 4
  }],
  mandates: []
}];

// ── Mandates (search records) ────────────────────────────────────────────────
const TM_MANDATES = [{
  id: 'mn1',
  role: 'Group Chief Information Officer',
  status: 'Active',
  owner: 'LH',
  accountId: 'acc1',
  bdDealId: 'bd1',
  fee: '$120K',
  value: 120,
  openedDays: 28,
  candidates: 8,
  stageNote: '3 in screening, 1 interview',
  placed: null,
  pipeline: [{
    name: 'Amira Haddad',
    title: 'Group Chief Executive',
    company: 'Almarai',
    stage: 'Interview',
    sourcedFrom: 'FMCG talent map'
  }, {
    name: 'Tariq Al Blooshi',
    title: 'CTO',
    company: 'NFPC Group',
    stage: 'Screening',
    sourcedFrom: 'FMCG talent map'
  }, {
    name: 'Nadia Fawzy',
    title: 'Director of Technology',
    company: 'Americana Restaurants',
    stage: 'Screening',
    sourcedFrom: null
  }, {
    name: 'Saeed Al Hamli',
    title: 'VP Digital',
    company: 'Agthia Group',
    stage: 'Contacted',
    sourcedFrom: 'FMCG talent map'
  }, {
    name: 'Hamad Al Ketbi',
    title: 'Group CEO',
    company: 'NFPC Group',
    stage: 'Sourced',
    sourcedFrom: 'FMCG talent map'
  }]
}, {
  id: 'mn2',
  role: 'VP Supply Chain',
  status: 'Placed',
  owner: 'LH',
  accountId: 'acc1',
  bdDealId: null,
  fee: '$85K',
  value: 85,
  openedDays: 120,
  candidates: 12,
  stageNote: null,
  placed: 'Khalid Rahman',
  pipeline: []
}, {
  id: 'mn3',
  role: 'Head of Digital Transformation',
  status: 'Active',
  owner: 'OK',
  accountId: 'acc6',
  bdDealId: null,
  fee: '$95K',
  value: 95,
  openedDays: 14,
  candidates: 5,
  stageNote: '2 contacted, 1 screening',
  placed: null,
  pipeline: [{
    name: 'Fatima Al Mulla',
    title: 'Digital Strategy Director',
    company: 'Savola Group',
    stage: 'Screening',
    sourcedFrom: null
  }, {
    name: 'Omar Farooqi',
    title: 'Head of IT',
    company: 'Al Ain Farms',
    stage: 'Contacted',
    sourcedFrom: 'GCC talent map'
  }]
}, {
  id: 'mn4',
  role: 'Chief Commercial Officer',
  status: 'Pitching',
  owner: 'LH',
  accountId: 'acc5',
  bdDealId: 'bd3',
  fee: '$110K',
  value: 110,
  openedDays: 7,
  candidates: 0,
  stageNote: 'Proposal sent',
  placed: null,
  pipeline: []
}, {
  id: 'mn5',
  role: 'VP Manufacturing',
  status: 'Placed',
  owner: 'SM',
  accountId: 'acc3',
  bdDealId: null,
  fee: '$90K',
  value: 90,
  openedDays: 200,
  candidates: 10,
  stageNote: null,
  placed: 'Rashid Khoury',
  pipeline: []
}];

// ── BD pipeline stages ───────────────────────────────────────────────────────
const TM_BD_STAGES = [{
  id: 'Lead',
  label: 'Lead',
  tone: 'slate'
}, {
  id: 'Qualified',
  label: 'Qualified',
  tone: 'blue'
}, {
  id: 'Proposal',
  label: 'Proposal',
  tone: 'violet'
}, {
  id: 'Negotiation',
  label: 'Negotiation',
  tone: 'amber'
}, {
  id: 'Won',
  label: 'Won',
  tone: 'emerald'
}, {
  id: 'Lost',
  label: 'Lost',
  tone: 'slate'
}];

// ── BD deals ─────────────────────────────────────────────────────────────────
const TM_BD_DEALS = [{
  id: 'bd1',
  company: 'Al Rabie Saudi Foods Co.',
  role: 'Group CIO',
  stage: 'Won',
  probability: 100,
  fee: '$120K',
  value: 120,
  nextStep: 'Mandate signed — search underway',
  owner: 'LH',
  ageDays: 28,
  account: 'acc1'
}, {
  id: 'bd2',
  company: 'Agthia Group',
  role: 'Head of Digital',
  stage: 'Won',
  probability: 100,
  fee: '$95K',
  value: 95,
  nextStep: 'Mandate in execution',
  owner: 'OK',
  ageDays: 14,
  account: 'acc6'
}, {
  id: 'bd3',
  company: 'NFPC Group',
  role: 'Chief Commercial Officer',
  stage: 'Proposal',
  probability: 60,
  fee: '$110K',
  value: 110,
  nextStep: 'Follow up on proposal by Thu',
  owner: 'LH',
  ageDays: 7,
  account: 'acc5'
}, {
  id: 'bd4',
  company: 'Americana Restaurants',
  role: 'VP Operations — MENA',
  stage: 'Qualified',
  probability: 35,
  fee: '$100K',
  value: 100,
  nextStep: 'Intro call with CHRO scheduled',
  owner: 'SM',
  ageDays: 3,
  account: 'acc7'
}, {
  id: 'bd5',
  company: 'Saudi Dairy & Foodstuff Co.',
  role: 'CFO',
  stage: 'Lead',
  probability: 15,
  fee: '$130K',
  value: 130,
  nextStep: 'Research company & warm intro via LH',
  owner: 'FO',
  ageDays: 1,
  account: null
}, {
  id: 'bd6',
  company: 'Binghatti Holdings',
  role: 'Group CHRO',
  stage: 'Negotiation',
  probability: 75,
  fee: '$105K',
  value: 105,
  nextStep: 'Final terms review — counter expected',
  owner: 'LH',
  ageDays: 10,
  account: null
}];

// ── Helper: firmographics lookup ─────────────────────────────────────────────
const _SYNTH_FG = {
  'Al Rabie Saudi Foods Co.': {
    sector: 'FMCG',
    city: 'Riyadh',
    country: 'Saudi Arabia',
    revenue: '$500M–1B',
    employees: '5K–10K'
  },
  'Agthia Group': {
    sector: 'Food & Beverage',
    city: 'Abu Dhabi',
    country: 'UAE',
    revenue: '$1B–5B',
    employees: '5K–10K'
  },
  'Americana Restaurants': {
    sector: 'Food Service',
    city: 'Dubai',
    country: 'UAE',
    revenue: '$1B–5B',
    employees: '50K+'
  },
  'Binghatti Holdings': {
    sector: 'Real Estate',
    city: 'Dubai',
    country: 'UAE',
    revenue: '$500M–1B',
    employees: '1K–5K'
  },
  'Saudi Dairy & Foodstuff Co.': {
    sector: 'Dairy',
    city: 'Riyadh',
    country: 'Saudi Arabia',
    revenue: '$100M–500M',
    employees: '1K–5K'
  }
};
function tmAccountFirmographics(name) {
  const c = TM_COMPANIES.find(x => x.name === name);
  if (c) return {
    sector: c.sector,
    city: c.city,
    country: c.country,
    revenue: c.revenue,
    employees: c.employees
  };
  return _SYNTH_FG[name] || {
    sector: '—',
    city: '—',
    country: '—',
    revenue: '—',
    employees: '—'
  };
}

// ── Helper: people at an account ─────────────────────────────────────────────
function tmAccountPeople(account) {
  // Link contacts who work at this company
  const contacts = window.TM_CONTACTS || TM_CONTACTS || [];
  const linked = contacts.filter(c => c.company === account.name).map(c => ({
    name: c.name,
    title: c.title,
    contactId: c.id,
    isClientSide: c.type === 'Client',
    relation: c.type === 'Client' ? c.clientRole || 'Sponsor' : 'Candidate'
  }));
  // Add synthetic client-side contacts for client accounts
  if (account.type === 'Client' && linked.filter(p => p.isClientSide).length === 0) {
    linked.push({
      name: 'CHRO Office',
      title: 'Client contact',
      contactId: null,
      isClientSide: true,
      relation: 'Sponsor'
    });
  }
  return linked;
}

// ── Helper: off-limits check ─────────────────────────────────────────────────
function tmOffLimitsFor(company) {
  const acc = TM_ACCOUNTS.find(a => a.name === company);
  return acc && acc.offLimits ? acc.offLimits : null;
}

// ── Helper: find account by name ─────────────────────────────────────────────
function tmFindAccountByName(name) {
  return TM_ACCOUNTS.find(a => a.name === name) || null;
}

// ── Helper: mandate by ID ────────────────────────────────────────────────────
function tmMandateById(id) {
  return TM_MANDATES.find(m => m.id === id) || null;
}

// ── Helper: mandates for account ─────────────────────────────────────────────
function tmMandatesForAccount(accountId) {
  return TM_MANDATES.filter(m => m.accountId === accountId);
}

// ── Helper: mandate linked to a BD deal ──────────────────────────────────────
function tmMandateForDeal(dealId) {
  return TM_MANDATES.find(m => m.bdDealId === dealId) || null;
}

// ── Helper: AI intel for a contact name ──────────────────────────────────────
const _AI_INTEL = {
  'Amira Haddad': {
    aiScore: 92,
    summary: 'Exceptional fit — deep FMCG + listed-company CEO with GCC board exposure.'
  },
  'Hamad Al Ketbi': {
    aiScore: 85,
    summary: 'Strong GCC commercial leader. Broad F&B experience across UAE.'
  },
  'Yousef Iman': {
    aiScore: 78,
    summary: 'Solid CEO track record. More traditional management style.'
  },
  'Nadia Fawzy': {
    aiScore: 81,
    summary: 'Technology specialist with growing P&L accountability.'
  },
  'Tariq Al Blooshi': {
    aiScore: 74,
    summary: 'CTO with good digital transformation credentials.'
  },
  'Fatima Al Mulla': {
    aiScore: 87,
    summary: 'Digital strategy leader with consumer-goods depth.'
  },
  'Mariam Al Zaabi': {
    aiScore: 70,
    summary: 'Operations background. Needs stretch for CIO-level role.'
  },
  'Saeed Al Hamli': {
    aiScore: 68,
    summary: 'VP Digital with solid ERP/SAP implementation track.'
  }
};
function tmGetAiIntel(name) {
  return _AI_INTEL[name] || null;
}

// ── Helper: build full contact profile ───────────────────────────────────────
const _PROFILE_SEED = {
  'c1': {
    email: 'a.haddad@almarai.com',
    phone: '+966 50 812 3456',
    linkedin: 'linkedin.com/in/amirahaddad',
    location: 'Riyadh, Saudi Arabia',
    remuneration: 'AED 2.8M base + 40% bonus + LTI',
    owner: 'LH',
    type: 'Candidate',
    status: 'Active',
    offLimits: null,
    hiringFor: [],
    career: [{
      role: 'Group Chief Executive',
      company: 'Almarai',
      period: '2021 – present',
      current: true
    }, {
      role: 'Chief Operating Officer',
      company: 'Almarai',
      period: '2018 – 2021',
      current: false
    }, {
      role: 'VP Operations — MENA',
      company: 'Nestlé',
      period: '2013 – 2018',
      current: false
    }],
    education: [{
      degree: 'MBA',
      school: 'INSEAD',
      year: '2012'
    }, {
      degree: 'BSc Mechanical Eng.',
      school: 'AUB',
      year: '2005'
    }],
    boards: ['Saudi Food & Drug Authority (advisory)', 'GCC Dairy Council'],
    documents: [{
      id: 'd1',
      name: 'CV_Amira_Haddad_2024.pdf',
      type: 'CV',
      addedDays: 14
    }],
    tasks: [{
      id: 'tk1',
      title: 'Prepare interview brief for Al Rabie CIO mandate',
      dueDays: 0,
      assignee: 'LH',
      done: false
    }, {
      id: 'tk2',
      title: 'Confirm compensation expectations',
      dueDays: 3,
      assignee: 'OK',
      done: false
    }]
  },
  'c2': {
    email: 'y.iman@savola.com',
    phone: '+966 55 901 2345',
    linkedin: 'linkedin.com/in/yousefiman',
    location: 'Jeddah, Saudi Arabia',
    remuneration: 'SAR 3.2M package',
    owner: 'OK',
    type: 'Candidate',
    status: 'Active',
    offLimits: null,
    hiringFor: [],
    career: [{
      role: 'Chief Executive Officer',
      company: 'Savola Group',
      period: '2020 – present',
      current: true
    }, {
      role: 'Group CFO',
      company: 'Savola Group',
      period: '2016 – 2020',
      current: false
    }],
    education: [{
      degree: 'MBA Finance',
      school: 'London Business School',
      year: '2009'
    }],
    boards: [],
    documents: [],
    tasks: [{
      id: 'tk3',
      title: 'Follow up after initial outreach',
      dueDays: -2,
      assignee: 'OK',
      done: false
    }]
  },
  'c3': {
    email: 'h.alketbi@nfpc.ae',
    phone: '+971 50 445 6789',
    linkedin: 'linkedin.com/in/hamadalketbi',
    location: 'Dubai, UAE',
    remuneration: 'AED 2.1M + 30% bonus',
    owner: 'LH',
    type: 'Candidate',
    status: 'Active',
    offLimits: null,
    hiringFor: [],
    career: [{
      role: 'Group CEO',
      company: 'NFPC Group',
      period: '2019 – present',
      current: true
    }, {
      role: 'COO',
      company: 'Masafi',
      period: '2015 – 2019',
      current: false
    }],
    education: [{
      degree: 'BSc Business Admin',
      school: 'American University of Sharjah',
      year: '2006'
    }],
    boards: ['UAE F&B Association'],
    documents: [{
      id: 'd2',
      name: 'CV_Hamad_AlKetbi.pdf',
      type: 'CV',
      addedDays: 30
    }],
    tasks: [{
      id: 'tk4',
      title: 'Schedule intro call re: CIO opportunity',
      dueDays: 1,
      assignee: 'LH',
      done: false
    }]
  },
  'c4': {
    email: 'n.fawzy@americana.ae',
    phone: '+971 56 234 5678',
    linkedin: 'linkedin.com/in/nadiafawzy',
    location: 'Dubai, UAE',
    remuneration: 'AED 1.4M + equity',
    owner: 'SM',
    type: 'Candidate',
    status: 'Active',
    offLimits: null,
    hiringFor: [],
    career: [{
      role: 'Director of Technology',
      company: 'Americana Restaurants',
      period: '2021 – present',
      current: true
    }, {
      role: 'IT Director — MENA',
      company: 'Yum! Brands',
      period: '2017 – 2021',
      current: false
    }],
    education: [{
      degree: 'MSc Computer Science',
      school: 'Cairo University',
      year: '2010'
    }],
    boards: [],
    documents: [],
    tasks: []
  },
  'c5': {
    email: 't.alblooshi@nfpc.ae',
    phone: '+971 50 678 1234',
    linkedin: 'linkedin.com/in/tariqalblooshi',
    location: 'Dubai, UAE',
    remuneration: 'AED 1.6M package',
    owner: 'OK',
    type: 'Candidate',
    status: 'Active',
    offLimits: null,
    hiringFor: [],
    career: [{
      role: 'Chief Technology Officer',
      company: 'NFPC Group',
      period: '2020 – present',
      current: true
    }],
    education: [{
      degree: 'BEng Software',
      school: 'Khalifa University',
      year: '2011'
    }],
    boards: [],
    documents: [],
    tasks: [{
      id: 'tk5',
      title: 'Send screening questionnaire',
      dueDays: 2,
      assignee: 'OK',
      done: false
    }]
  },
  'c6': {
    email: 'f.almulla@savola.com',
    phone: '+966 54 789 0123',
    linkedin: 'linkedin.com/in/fatimaalmulla',
    location: 'Jeddah, Saudi Arabia',
    remuneration: 'SAR 1.5M + 25% bonus',
    owner: 'SM',
    type: 'Candidate',
    status: 'Active',
    offLimits: null,
    hiringFor: [],
    career: [{
      role: 'Digital Strategy Director',
      company: 'Savola Group',
      period: '2022 – present',
      current: true
    }, {
      role: 'Head of e-Commerce',
      company: 'Extra Stores',
      period: '2018 – 2022',
      current: false
    }],
    education: [{
      degree: 'MBA',
      school: 'KAUST',
      year: '2017'
    }],
    boards: [],
    documents: [],
    tasks: []
  },
  'c7': {
    email: 'mariam.alzaabi@alainfarms.ae',
    phone: '+971 50 345 6789',
    linkedin: 'linkedin.com/in/mariamzaabi',
    location: 'Abu Dhabi, UAE',
    remuneration: 'AED 900K',
    owner: 'OK',
    type: 'Candidate',
    status: 'Active',
    offLimits: null,
    hiringFor: [],
    career: [{
      role: 'Operations Director',
      company: 'Al Ain Farms',
      period: '2021 – present',
      current: true
    }],
    education: [{
      degree: 'BSc Food Science',
      school: 'UAE University',
      year: '2013'
    }],
    boards: [],
    documents: [],
    tasks: [{
      id: 'tk6',
      title: 'Request reference check from previous employer',
      dueDays: 5,
      assignee: 'OK',
      done: false
    }]
  },
  'c8': {
    email: 's.alhamli@agthia.com',
    phone: '+971 50 456 7890',
    linkedin: 'linkedin.com/in/saeedalhamli',
    location: 'Abu Dhabi, UAE',
    remuneration: 'AED 1.1M',
    owner: 'LH',
    type: 'Candidate',
    status: 'Active',
    offLimits: null,
    hiringFor: [],
    career: [{
      role: 'VP Digital',
      company: 'Agthia Group',
      period: '2022 – present',
      current: true
    }, {
      role: 'SAP Programme Lead',
      company: 'ADNOC',
      period: '2017 – 2022',
      current: false
    }],
    education: [{
      degree: 'MSc IT Management',
      school: 'Zayed University',
      year: '2015'
    }],
    boards: [],
    documents: [],
    tasks: []
  }
};
// Contacts added beyond the seeded 8
const _PROFILE_DEFAULTS = {
  email: '',
  phone: '',
  linkedin: '',
  location: 'UAE',
  remuneration: '—',
  owner: 'LH',
  type: 'Candidate',
  status: 'Active',
  offLimits: null,
  hiringFor: [],
  career: [],
  education: [],
  boards: [],
  documents: [],
  tasks: []
};
function tmBuildContactProfile(contact) {
  const seed = _PROFILE_SEED[contact.id];
  if (seed) return {
    ...seed
  };
  // Generate a minimal profile for contacts without an explicit seed
  const t = contact.type || 'Candidate';
  const owners = ['LH', 'OK', 'SM', 'FO'];
  const hash = contact.id.split('').reduce((s, c) => s + c.charCodeAt(0), 0);
  return {
    ..._PROFILE_DEFAULTS,
    owner: owners[hash % owners.length],
    type: t,
    tasks: hash % 3 === 0 ? [{
      id: 'autotk-' + contact.id,
      title: 'Follow up with ' + contact.name,
      dueDays: hash % 14 - 3,
      assignee: owners[hash % owners.length],
      done: false
    }] : []
  };
}
Object.assign(window, {
  TM_PIPELINE,
  TM_CONTACTS,
  TM_CONTACT_ENTRIES,
  TM_CONTACT_ACTIVITIES,
  TM_PROJECT_TEAM,
  TM_POSITION,
  TM_STRATEGY,
  TM_LONG_LIST,
  TM_STATUS_REPORT,
  TM_INTERNAL,
  TM_INTERNAL_ACTIVITY,
  TM_CONTACT_TYPE_META,
  TM_ACCOUNT_TYPE_META,
  TM_STRENGTH_META,
  TM_MANDATE_STATUS_META,
  TM_ACCOUNTS,
  TM_MANDATES,
  TM_BD_STAGES,
  TM_BD_DEALS,
  tmAccountFirmographics,
  tmAccountPeople,
  tmOffLimitsFor,
  tmFindAccountByName,
  tmMandateById,
  tmMandatesForAccount,
  tmMandateForDeal,
  tmGetAiIntel,
  tmBuildContactProfile
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/data.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/home.jsx
try { (() => {
/* global React, ReactDOM, Icon, Button, cx, TM_SUGGESTIONS, TM_COMPANIES */
// ── Home: enterprise command center (post-login) ─────────────────────────────
// AI-first home shell: greeting → AI command bar → signal strip →
// continue working + activity feed. Replaces the old marketing-hero landing.

function greeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
}

// ── Mode hover info panels ────────────────────────────────────────────────────
const MODE_INFO = {
  search: {
    icon: 'Search',
    title: 'AI Universe Builder',
    desc: 'Describe your target market in plain language. AI identifies companies, maps decision-makers, and enriches data in real time.',
    features: [{
      icon: 'Sparkles',
      text: 'Natural language query'
    }, {
      icon: 'Building2',
      text: 'Company identification & enrichment'
    }, {
      icon: 'Users',
      text: 'Executive mapping across sectors'
    }, {
      icon: 'Zap',
      text: 'Live AI intelligence layer'
    }],
    example: '"FMCG distributors across GCC, founder-led"'
  },
  import: {
    icon: 'Upload',
    title: 'Import Your List',
    desc: 'Upload an existing company list — we auto-map columns, resolve entities, then extend with AI enrichment.',
    features: [{
      icon: 'FileSpreadsheet',
      text: 'CSV and XLSX supported'
    }, {
      icon: 'Wand2',
      text: 'Automatic column mapping'
    }, {
      icon: 'BadgeCheck',
      text: 'Entity resolution & deduplication'
    }, {
      icon: 'Sparkles',
      text: 'AI-extended enrichment'
    }],
    example: 'Columns: Company · Region · Sector'
  },
  brief: {
    icon: 'FileText',
    title: 'From Position Brief',
    desc: 'Upload a position description — AI reads the brief, infers target sectors & seniority, and assembles a starting universe.',
    features: [{
      icon: 'ScanText',
      text: 'Brief parsing & analysis'
    }, {
      icon: 'Target',
      text: 'Sector & seniority inference'
    }, {
      icon: 'Globe',
      text: 'Geographic scope detection'
    }, {
      icon: 'ListTree',
      text: 'Auto-generated universe structure'
    }],
    example: 'Accepts PDF · DOCX · TXT'
  }
};
function ModeTip({
  btnRef,
  visible,
  modeId
}) {
  const info = MODE_INFO[modeId];
  const [style, setStyle] = React.useState({
    top: 0,
    left: 0
  });
  React.useLayoutEffect(() => {
    if (visible && btnRef.current) {
      const r = btnRef.current.getBoundingClientRect();
      // keep within viewport horizontally
      const tipW = 268;
      let left = r.left;
      if (left + tipW > window.innerWidth - 12) left = window.innerWidth - tipW - 12;
      setStyle({
        top: r.bottom + 8,
        left
      });
    }
  }, [visible]);
  return ReactDOM.createPortal(/*#__PURE__*/React.createElement("div", {
    className: cx('tm-modetip', visible && 'is-vis'),
    style: style
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-modetip__head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-modetip__ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: info.icon,
    size: 13
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-modetip__title"
  }, info.title)), /*#__PURE__*/React.createElement("p", {
    className: "tm-modetip__desc"
  }, info.desc), /*#__PURE__*/React.createElement("ul", {
    className: "tm-modetip__feats"
  }, info.features.map(f => /*#__PURE__*/React.createElement("li", {
    key: f.text
  }, /*#__PURE__*/React.createElement(Icon, {
    name: f.icon,
    size: 12
  }), f.text))), /*#__PURE__*/React.createElement("div", {
    className: "tm-modetip__eg"
  }, info.example)), document.body);
}

// ── AI command bar ───────────────────────────────────────────────────────────
function CommandBar({
  onDiscover
}) {
  const [mode, setMode] = React.useState('search'); // search | import | brief
  const [input, setInput] = React.useState('');
  const [briefText, setBriefText] = React.useState('');
  const [hoveredMode, setHoveredMode] = React.useState(null);
  const taRef = React.useRef(null);
  const btnRefs = {
    search: React.useRef(null),
    import: React.useRef(null),
    brief: React.useRef(null)
  };
  const modes = [{
    id: 'search',
    icon: 'Search',
    t: 'Search'
  }, {
    id: 'import',
    icon: 'Upload',
    t: 'Import list'
  }, {
    id: 'brief',
    icon: 'FileText',
    t: 'From brief'
  }];
  const submit = () => onDiscover(input.trim() || TM_SUGGESTIONS[0]);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__top"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cbarseg",
    role: "tablist"
  }, modes.map(m => /*#__PURE__*/React.createElement(React.Fragment, {
    key: m.id
  }, /*#__PURE__*/React.createElement("button", {
    ref: btnRefs[m.id],
    role: "tab",
    className: cx('tm-cbarseg__b', mode === m.id && 'is-on'),
    onClick: () => setMode(m.id),
    onMouseEnter: () => setHoveredMode(m.id),
    onMouseLeave: () => setHoveredMode(null)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: m.icon,
    size: 14
  }), m.t), /*#__PURE__*/React.createElement(ModeTip, {
    btnRef: btnRefs[m.id],
    visible: hoveredMode === m.id,
    modeId: m.id
  })))), /*#__PURE__*/React.createElement("span", {
    className: "tm-cbar__ai"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13
  }), "AI Intelligence")), mode === 'search' && /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__body"
  }, /*#__PURE__*/React.createElement("textarea", {
    ref: taRef,
    className: "tm-cbar__ta",
    rows: 3,
    placeholder: "Describe the universe you want to map \u2014 e.g. \u201CLargest FMCG distributors across the GCC, founder-led\u201D",
    value: input,
    onChange: e => setInput(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) submit();
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__chips"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-cbar__chiplbl"
  }, "Try"), TM_SUGGESTIONS.map(s => /*#__PURE__*/React.createElement("button", {
    key: s,
    className: "tm-chip",
    onClick: () => setInput(s)
  }, s)))), mode === 'import' && /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__drop"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__dropic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 18
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__dropt"
  }, "Drop a company list to import"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__dropd"
  }, "CSV or XLSX \u2014 we map your columns to companies & executives, then extend with AI.")), mode === 'brief' && /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__brief"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__briefbanner"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14
  }), /*#__PURE__*/React.createElement("span", null, "AI will read your documents and automatically suggest the most relevant sectors and a starting company universe. You'll review and approve everything before any execution begins.")), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__briefdrop"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 22
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__briefdropt"
  }, "Upload job description or company brief"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__briefdropd"
  }, "Drag and drop, or click to browse"), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__brieffmt"
  }, "PDF \xB7 DOCX \xB7 TXT")), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__briefdiv"
  }, /*#__PURE__*/React.createElement("span", null, "or paste below")), /*#__PURE__*/React.createElement("textarea", {
    className: "tm-cbar__briefta",
    rows: 3,
    placeholder: "Paste a role description, company overview, or any context that describes what you're hiring for\u2026",
    value: briefText,
    onChange: e => setBriefText(e.target.value)
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cbar__foot"
  }, mode === 'brief' ? /*#__PURE__*/React.createElement("span", {
    className: "tm-cbar__hint"
  }, "Sectors and companies will be inferred automatically") : /*#__PURE__*/React.createElement("span", {
    className: "tm-cbar__hint"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-kbd"
  }, "\u2318"), /*#__PURE__*/React.createElement("span", {
    className: "tm-kbd"
  }, "\u21B5"), " to run"), mode === 'search' && /*#__PURE__*/React.createElement(Button, {
    onClick: submit
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 15
  }), "Build universe"), mode === 'import' && /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: () => onDiscover('Imported list — FMCG distributors')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 15
  }), "Choose file"), mode === 'brief' && /*#__PURE__*/React.createElement(Button, {
    onClick: () => onDiscover(briefText.trim() || 'From brief — CFO, FMCG, GCC')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 15
  }), "Analyse brief")));
}

// ── Signal strip ─────────────────────────────────────────────────────────────
function StatTile({
  icon,
  n,
  label,
  foot,
  tone
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-stat__top"
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-stat__ic', tone && 'is-' + tone)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 15
  })), foot && /*#__PURE__*/React.createElement("span", {
    className: cx('tm-stat__foot', tone && 'is-' + tone)
  }, foot)), /*#__PURE__*/React.createElement("div", {
    className: "tm-stat__n"
  }, n), /*#__PURE__*/React.createElement("div", {
    className: "tm-stat__l"
  }, label));
}

// ── Continue working ─────────────────────────────────────────────────────────
function ContinueRow({
  p,
  onOpen
}) {
  return /*#__PURE__*/React.createElement("button", {
    className: cx('tm-crow', p.draft && 'is-draft'),
    onClick: () => onOpen(p)
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-crow__ic', p.draft ? 'is-draft' : 'is-active')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: p.draft ? 'PencilLine' : 'FolderOpen',
    size: 15
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-crow__main"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-crow__name"
  }, p.name), /*#__PURE__*/React.createElement("span", {
    className: "tm-crow__meta"
  }, /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 11
  }), p.companies), p.draft ? /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(Icon, {
    name: "CheckSquare",
    size: 11
  }), p.selected, " selected") : /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 11
  }), p.execs, " execs"), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 11
  }), p.when))), /*#__PURE__*/React.createElement("span", {
    className: "tm-crow__cta"
  }, p.draft ? 'Resume' : 'Open', /*#__PURE__*/React.createElement(Icon, {
    name: p.draft ? 'ArrowUpRight' : 'ArrowRight',
    size: 14
  })));
}

// ── Activity feed ────────────────────────────────────────────────────────────
function ActivityFeed({
  items
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-feed"
  }, items.map((it, i) => /*#__PURE__*/React.createElement("button", {
    key: i,
    className: cx('tm-feed__item', it.onClick && 'is-link'),
    onClick: it.onClick || undefined,
    disabled: !it.onClick
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-feed__ic', 'is-' + it.tone)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: it.icon,
    size: 14
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-feed__body"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-feed__txt"
  }, it.text), /*#__PURE__*/React.createElement("span", {
    className: "tm-feed__sub"
  }, it.sub, " \xB7 ", it.when)), it.onClick && /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronRight",
    size: 14,
    color: "var(--muted-foreground)"
  }))));
}

// ── Home screen ──────────────────────────────────────────────────────────────
function HomeScreen({
  onDiscover,
  projects = [],
  userMode = 'returning',
  user = {
    name: 'Yousef Iman',
    initials: 'YI'
  },
  onOpenProject,
  onSeeAll
}) {
  const returning = projects.length > 0;
  const drafts = projects.filter(p => p.draft);
  const active = projects.filter(p => !p.draft);
  const cont = [...drafts, ...active].slice(0, 3);
  const firstName = user.name.split(' ')[0];
  const companiesInPipeline = projects.reduce((s, p) => s + (p.companies || 0), 0);
  const execsMapped = projects.reduce((s, p) => s + (p.execs || p.selected || 0), 0);
  const feed = [drafts[0] && {
    icon: 'CircleDot',
    tone: 'warn',
    text: 'Universe ready to confirm',
    sub: `${drafts[0].name} · ${drafts[0].selected}/${drafts[0].companies} selected`,
    when: drafts[0].when,
    onClick: () => onOpenProject(drafts[0])
  }, {
    icon: 'BadgeCheck',
    tone: 'ok',
    text: '12 executives enriched & verified',
    sub: 'Top FMCG distributors in UAE',
    when: '2h ago',
    onClick: () => active[0] && onOpenProject(active[0])
  }, {
    icon: 'Sparkles',
    tone: 'ai',
    text: '4 AI-inferred companies added',
    sub: 'Industrial equipment — Egypt',
    when: 'Yesterday'
  }, {
    icon: 'ShieldAlert',
    tone: 'risk',
    text: '2 contacts flagged off-limits',
    sub: 'Retail chains across GCC',
    when: '2 days ago'
  }].filter(Boolean);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-home"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-home__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-home__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-home__eyebrow"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-home__dot"
  }, "AL"), "FMCG & Food practice \xB7 ALAC Partners"), /*#__PURE__*/React.createElement("h1", {
    className: "tm-home__title"
  }, greeting(), ", ", firstName))), /*#__PURE__*/React.createElement(CommandBar, {
    onDiscover: onDiscover
  }), returning ? /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-stats"
  }, /*#__PURE__*/React.createElement(StatTile, {
    icon: "Map",
    n: active.length,
    label: "Active search maps"
  }), /*#__PURE__*/React.createElement(StatTile, {
    icon: "Building2",
    n: companiesInPipeline,
    label: "Companies in pipeline"
  }), /*#__PURE__*/React.createElement(StatTile, {
    icon: "Users",
    n: execsMapped,
    label: "Executives mapped",
    foot: "\u25B2 12",
    tone: "ok"
  }), /*#__PURE__*/React.createElement(StatTile, {
    icon: "Zap",
    n: drafts.length || 38,
    label: drafts.length ? 'Draft maps to confirm' : 'Awaiting enrichment',
    tone: "warn"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-home__cols"
  }, /*#__PURE__*/React.createElement("section", {
    className: "tm-home__sec"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-home__sech"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow"
  }, "Continue working"), /*#__PURE__*/React.createElement("button", {
    className: "tm-link",
    onClick: onSeeAll
  }, "All search maps", /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 12
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-home__list"
  }, cont.map(p => /*#__PURE__*/React.createElement(ContinueRow, {
    key: p.id,
    p: p,
    onOpen: onOpenProject
  })))), /*#__PURE__*/React.createElement("section", {
    className: "tm-home__sec"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-home__sech"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow"
  }, "Activity"), /*#__PURE__*/React.createElement("span", {
    className: "tm-home__live"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-home__livedot"
  }), "Live")), /*#__PURE__*/React.createElement(ActivityFeed, {
    items: feed
  })))) : /*#__PURE__*/React.createElement("div", {
    className: "tm-home__empty"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-home__sech",
    style: {
      marginBottom: 12
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow"
  }, "Start from an example")), /*#__PURE__*/React.createElement("div", {
    className: "tm-home__exgrid"
  }, TM_SUGGESTIONS.map(s => /*#__PURE__*/React.createElement("button", {
    key: s,
    className: "tm-ex",
    onClick: () => onDiscover(s)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 14,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, s), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUpRight",
    size: 14,
    color: "var(--muted-foreground)"
  })))))));
}
Object.assign(window, {
  HomeScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/home.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/landing.jsx
try { (() => {
/* global React, Icon, Button, cx, TM_SUGGESTIONS */
// ── Landing: hero + mode selector + search panel ─────────────────────────────

function Landing({
  onDiscover,
  projects = [],
  userMode = 'returning',
  onOpenProject,
  onSeeAll
}) {
  const [mode, setMode] = React.useState('search');
  const [input, setInput] = React.useState('');
  const taRef = React.useRef(null);
  const returning = projects.length > 0; // show recent projects whenever any exist (incl. a just-saved draft)

  const modes = [{
    id: 'search',
    icon: 'Search',
    t: 'Search',
    d: "Describe what you're looking for. AI builds the company list."
  }, {
    id: 'import',
    icon: 'Upload',
    t: 'Import a list',
    d: 'Upload an existing company list. Extend it from there.'
  }, {
    id: 'brief',
    icon: 'FileText',
    t: 'From brief',
    d: 'Upload a JD or brief. AI infers the sectors and companies.'
  }];
  const submit = () => {
    const q = input.trim() || TM_SUGGESTIONS[0];
    onDiscover(q);
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-landing"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-landing__wash"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-landing__inner"
  }, returning ? /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-greet"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-greet__dot"
  }, "AL"), /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow",
    style: {
      fontSize: 11
    }
  }, "ALAC \xB7 Global Talent Map")), /*#__PURE__*/React.createElement("h1", {
    className: "tm-hero"
  }, "Welcome back"), /*#__PURE__*/React.createElement("p", {
    className: "tm-sub"
  }, "Resume a search map, or start a new one."), /*#__PURE__*/React.createElement(RecentProjects, {
    projects: projects,
    onOpen: onOpenProject,
    onSeeAll: onSeeAll
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-divider-or"
  }, /*#__PURE__*/React.createElement("span", null, "or start a new search"))) : /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-greet"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-greet__dot"
  }, "AL"), /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow",
    style: {
      fontSize: 11
    }
  }, "ALAC \xB7 Global Talent Map")), /*#__PURE__*/React.createElement("h1", {
    className: "tm-hero"
  }, "Build your company universe"), /*#__PURE__*/React.createElement("p", {
    className: "tm-sub"
  }, "Select how you want to define the scope of this search.")), /*#__PURE__*/React.createElement("div", {
    className: "tm-modes"
  }, modes.map(m => /*#__PURE__*/React.createElement("button", {
    key: m.id,
    className: cx('tm-mode', mode === m.id && 'is-sel'),
    onClick: () => setMode(m.id)
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-mode__ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: m.icon,
    size: 18
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-mode__t"
  }, m.t), /*#__PURE__*/React.createElement("div", {
    className: "tm-mode__d"
  }, m.d)))), mode === 'search' && /*#__PURE__*/React.createElement("div", {
    className: "tm-searchpanel tm-fade",
    style: {
      animationDelay: '.18s'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sp__head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sp__chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13
  }), "AI Intelligence"), /*#__PURE__*/React.createElement("button", {
    className: "tm-suggest",
    style: {
      display: 'inline-flex',
      gap: 6,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 13
  }), "Upload PD")), /*#__PURE__*/React.createElement("div", {
    className: "tm-sp__chips"
  }, TM_SUGGESTIONS.map(s => /*#__PURE__*/React.createElement("button", {
    key: s,
    className: "tm-suggest",
    onClick: () => setInput(s)
  }, s))), /*#__PURE__*/React.createElement("textarea", {
    ref: taRef,
    className: "tm-sp__ta",
    placeholder: "Describe what you're looking for\u2026",
    value: input,
    onChange: e => setInput(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) submit();
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-sp__foot"
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-kbd",
    style: {
      marginRight: 4
    }
  }, "\u2318 Enter"), "to search"), /*#__PURE__*/React.createElement(Button, {
    onClick: submit
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 16
  }), "Discover Companies"))), mode === 'import' && /*#__PURE__*/React.createElement("div", {
    className: "tm-searchpanel tm-fade",
    style: {
      padding: 28,
      textAlign: 'center',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 12
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-mode__ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 18
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 14,
      fontWeight: 600
    }
  }, "Drop a company list to import"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      maxWidth: 360
    }
  }, "CSV or XLSX. We'll map your columns to companies and executives, then let you extend the list with AI."), /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: () => onDiscover('Imported list — FMCG distributors')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 15
  }), "Choose file")), mode === 'brief' && /*#__PURE__*/React.createElement("div", {
    className: "tm-searchpanel tm-fade",
    style: {
      padding: 28,
      textAlign: 'center',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 12
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-mode__ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 18
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 14,
      fontWeight: 600
    }
  }, "Upload a position description"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      maxWidth: 360
    }
  }, "AI reads the brief, infers the target sectors and seniority, and assembles a starting company universe."), /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: () => onDiscover('From brief — CFO, FMCG, GCC')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 15
  }), "Upload brief"))));
}
Object.assign(window, {
  Landing
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/landing.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/mandates.jsx
try { (() => {
/* global React, Icon, Button, Avatar, cx, formatAge, AccountAvatar,
   TM_MANDATES, TM_MANDATE_STATUS_META, TM_ACCOUNTS, TM_BD_DEALS, TM_BD_STAGES,
   tmMandateById, tmMandatesForAccount, tmAccountFirmographics, tmAccountPeople */
// ── Mandates (the unified Search object): deal → account → pipeline ──────────

const MN_OWNER_NAMES = {
  LH: 'Layla Hassan',
  OK: 'Omar Khalil',
  SM: 'Sara Mitchell',
  FO: 'Farah Obeid'
};
const MN_PIPE_STAGES = ['Sourced', 'Contacted', 'Screening', 'Interview', 'Offer', 'Placed'];
const MN_STAGE_TONE = {
  Sourced: 'var(--muted-foreground)',
  Contacted: '#1d4ed8',
  Screening: '#7c3aed',
  Interview: '#b45309',
  Offer: '#0e7490',
  Placed: 'var(--success-fg, #15803d)'
};
function MandateStatusPill({
  status,
  size
}) {
  const m = (window.TM_MANDATE_STATUS_META || {})[status] || {};
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: m.bg,
      color: m.fg,
      fontSize: size === 'lg' ? 11.5 : 10.5,
      padding: size === 'lg' ? '3px 10px' : '2px 8px'
    }
  }, status);
}
function mandateAccount(m) {
  return (window.TM_ACCOUNTS || []).find(a => a.id === m.accountId) || null;
}

// ─────────────────────────────────────────────────────────────────────────────
// Mandates registry (Searches list)
// ─────────────────────────────────────────────────────────────────────────────
function MandatesScreen({
  onSelectMandate
}) {
  const [q, setQ] = React.useState('');
  const [filterStatus, setFilterStatus] = React.useState('');
  const [filterOwner, setFilterOwner] = React.useState('');
  const mandates = window.TM_MANDATES || [];
  const owners = [...new Set(mandates.map(m => m.owner))].sort();
  const rows = mandates.map(m => {
    const acc = mandateAccount(m);
    const active = m.pipeline.filter(p => p.stage !== 'Placed').length;
    return {
      ...m,
      accountName: acc ? acc.name : '—',
      candidates: m.pipeline.length,
      active
    };
  });
  const filtered = rows.filter(m => {
    if (q && !m.role.toLowerCase().includes(q.toLowerCase()) && !m.accountName.toLowerCase().includes(q.toLowerCase())) return false;
    if (filterStatus && m.status !== filterStatus) return false;
    if (filterOwner && m.owner !== filterOwner) return false;
    return true;
  });
  const stat = (label, val, tone) => /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__v",
    style: tone ? {
      color: tone
    } : null
  }, val), /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__l"
  }, label));
  const activeN = mandates.filter(m => m.status === 'Active').length;
  const placedN = mandates.filter(m => m.status === 'Placed').length;
  const pitchN = mandates.filter(m => m.status === 'Pitching').length;
  const feeValue = mandates.filter(m => m.status !== 'Pitching').reduce((s, m) => s + m.value, 0);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "CRM"), /*#__PURE__*/React.createElement("h1", {
    className: "tm-pscreen__title"
  }, "Searches")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-search-field"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 15,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    placeholder: "Search mandates\u2026",
    value: q,
    onChange: e => setQ(e.target.value)
  })), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Status",
    options: ['Active', 'Pitching', 'Placed', 'On hold'],
    value: filterStatus,
    onChange: setFilterStatus
  }), /*#__PURE__*/React.createElement(PlFilter, {
    label: "Owner",
    options: owners,
    value: filterOwner,
    onChange: setFilterOwner
  }), /*#__PURE__*/React.createElement(Button, {
    onClick: () => window.showToast && window.showToast('New search — coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 16
  }), "New search"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stats"
  }, stat('Searches', mandates.length), stat('Active', activeN, '#1d4ed8'), stat('Pitching', pitchN, '#b45309'), stat('Placed', placedN, 'var(--success-fg, #15803d)'), stat('Booked fees', '$' + feeValue + 'K')), /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-mntable__head"
  }, /*#__PURE__*/React.createElement("span", null, "Search"), /*#__PURE__*/React.createElement("span", null, "Client"), /*#__PURE__*/React.createElement("span", null, "Status"), /*#__PURE__*/React.createElement("span", null, "Stage"), /*#__PURE__*/React.createElement("span", {
    className: "tm-r"
  }, "Candidates"), /*#__PURE__*/React.createElement("span", {
    className: "tm-r"
  }, "Fee"), /*#__PURE__*/React.createElement("span", null, "Owner")), filtered.map(m => /*#__PURE__*/React.createElement("div", {
    key: m.id,
    className: "tm-mntable__row",
    onClick: () => onSelectMandate && onSelectMandate(m.id)
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontWeight: 600,
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, m.role), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, m.bdDealId ? 'From won deal' : 'Direct mandate', " \xB7 opened ", formatAge(m.openedDays))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement(AccountAvatar, {
    name: m.accountName,
    size: 24
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap',
      fontSize: 12.5
    }
  }, m.accountName)), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(MandateStatusPill, {
    status: m.status
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, m.placed ? /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserCheck",
    size: 12,
    color: "var(--success, #059669)"
  }), m.placed) : m.stageNote), /*#__PURE__*/React.createElement("span", {
    className: "tm-r",
    style: {
      fontVariantNumeric: 'tabular-nums',
      fontWeight: 600
    }
  }, m.candidates || /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontWeight: 400
    }
  }, "\u2014")), /*#__PURE__*/React.createElement("span", {
    className: "tm-r",
    style: {
      fontVariantNumeric: 'tabular-nums',
      fontSize: 12
    }
  }, m.fee), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__av",
    style: {
      width: 24,
      height: 24,
      fontSize: 10
    },
    title: MN_OWNER_NAMES[m.owner]
  }, m.owner)))), filtered.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable__empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 20,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, "No searches match your filters.")))));
}

// ─────────────────────────────────────────────────────────────────────────────
// Mandate detail — the unifying screen
// ─────────────────────────────────────────────────────────────────────────────
function LinkChip({
  icon,
  label,
  sub,
  onClick,
  tone
}) {
  return /*#__PURE__*/React.createElement("button", {
    className: "tm-mn-link",
    onClick: onClick
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-mn-link__ic",
    style: {
      background: tone ? tone.bg : 'var(--muted)',
      color: tone ? tone.fg : 'var(--muted-foreground)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 15
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      minWidth: 0,
      textAlign: 'left'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-mn-link__label"
  }, label), /*#__PURE__*/React.createElement("span", {
    className: "tm-mn-link__sub"
  }, sub)), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUpRight",
    size: 14,
    color: "var(--muted-foreground)"
  }));
}
function MandateDetail({
  mandateId,
  onBack,
  onOpenAccount,
  onOpenBizDev,
  onGoToPipeline,
  onOpenProject
}) {
  const m = (window.TM_MANDATES || []).find(x => x.id === mandateId);
  if (!m) return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      padding: 48,
      color: 'var(--muted-foreground)'
    }
  }, "Search not found."));
  const acc = mandateAccount(m);
  const deal = m.bdDealId ? (window.TM_BD_DEALS || []).find(d => d.id === m.bdDealId) : null;
  const ownerName = MN_OWNER_NAMES[m.owner] || m.owner;
  const clientPeople = acc ? (window.tmAccountPeople(acc) || []).filter(p => p.isClientSide) : [];
  const fg = acc ? window.tmAccountFirmographics(acc.name) || {} : {};
  const sponsor = clientPeople.find(p => /sponsor/i.test(p.relation)) || clientPeople[0];
  const [tab, setTab] = React.useState('pipeline'); // pipeline | status
  const [pipeline, setPipeline] = React.useState(() => m.pipeline.map((p, i) => ({
    id: m.id + '-c' + i,
    ...p
  })));
  React.useEffect(() => {
    setPipeline(m.pipeline.map((p, i) => ({
      id: m.id + '-c' + i,
      ...p
    })));
  }, [mandateId]);
  const sourceGroups = React.useMemo(() => {
    const g = {};
    pipeline.forEach(p => {
      const k = p.sourcedFrom || 'Direct approach';
      (g[k] = g[k] || []).push(p);
    });
    return Object.entries(g).map(([project, people]) => ({
      project,
      count: people.length
    })).sort((a, b) => b.count - a.count);
  }, [pipeline]);
  const counts = {};
  MN_PIPE_STAGES.forEach(s => {
    counts[s] = pipeline.filter(p => p.stage === s).length;
  });
  const moveCard = (cardId, toStage) => setPipeline(prev => prev.map(c => c.id === cardId ? {
    ...c,
    stage: toStage,
    ageDays: 0
  } : c));

  // Role spec (synthesised — kept compact, this is a lightweight CRM)
  const seniority = /chief|group|ceo|cfo|cio|coo|cso|chairman/i.test(m.role) ? 'C-suite · reports to Board' : 'Senior leadership';
  const compLine = m.fee.replace(/\s*\(retained\)/, '') + ' fee · base + bonus + LTIP';
  const location = fg.city ? fg.city + (fg.country ? ', ' + fg.country : '') : '—';
  const stat = (label, val, tone) => /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__v",
    style: tone ? {
      color: tone
    } : null
  }, val), /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__l"
  }, label));
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__inner",
    style: {
      maxWidth: 1160
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__head",
    style: {
      marginBottom: 18,
      paddingBottom: 18,
      borderBottom: '1px solid var(--border)'
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-rail__btn",
    style: {
      width: 30,
      height: 30,
      flexShrink: 0
    },
    onClick: onBack,
    title: "Back to searches"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowLeft",
    size: 16
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      width: 42,
      height: 42,
      borderRadius: 10,
      background: 'color-mix(in srgb, var(--primary) 12%, transparent)',
      color: 'var(--primary)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      flexShrink: 0
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Target",
    size: 20
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 20,
      fontWeight: 700,
      lineHeight: 1.2,
      display: 'flex',
      alignItems: 'center',
      gap: 9
    }
  }, m.role, /*#__PURE__*/React.createElement(MandateStatusPill, {
    status: m.status,
    size: "lg"
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      color: 'var(--muted-foreground)',
      marginTop: 3,
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      flexWrap: 'wrap'
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-cd__company-link",
    onClick: () => acc && onOpenAccount && onOpenAccount(acc.id)
  }, acc ? acc.name : '—', /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUpRight",
    size: 12
  })), /*#__PURE__*/React.createElement("span", null, "\xB7 opened ", formatAge(m.openedDays)), m.status === 'Active' && m.targetDays > 0 && /*#__PURE__*/React.createElement("span", null, "\xB7 target close in ", Math.round(m.targetDays / 7), "w"))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginLeft: 'auto',
      display: 'flex',
      gap: 8,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    onClick: () => window.showToast && window.showToast('Exported ' + pipeline.length + ' candidates as CSV')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 14
  }), "Export shortlist"), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: () => setTab('status')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 14
  }), "Status report"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__row"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__item"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 13
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__l"
  }, "Location"), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__v"
  }, location))), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__item"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "BadgeCheck",
    size: 13
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__l"
  }, "Seniority"), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__v"
  }, seniority))), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__item"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "DollarSign",
    size: 13
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__l"
  }, "Comp & fee"), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__v"
  }, compLine))), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__item"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserCircle",
    size: 13
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__l"
  }, "Lead consultant"), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__v",
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__av",
    style: {
      width: 18,
      height: 18,
      fontSize: 8
    }
  }, m.owner), ownerName))), sponsor && /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__item"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Handshake",
    size: 13
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__l"
  }, "Client sponsor"), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-spec__v"
  }, sponsor.name))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-links",
    style: {
      marginTop: 14
    }
  }, /*#__PURE__*/React.createElement(LinkChip, {
    icon: "TrendingUp",
    label: deal ? 'Won from BD deal' : 'Direct mandate',
    sub: deal ? deal.company + ' · ' + deal.fee : 'No originating deal',
    tone: {
      bg: 'rgba(245,158,11,.12)',
      fg: '#b45309'
    },
    onClick: () => deal ? onOpenBizDev && onOpenBizDev() : window.showToast && window.showToast('No originating deal')
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 16,
    color: "var(--border)"
  }), /*#__PURE__*/React.createElement(LinkChip, {
    icon: "Building2",
    label: acc ? acc.name : '—',
    sub: acc ? acc.type + ' · ' + (acc.owner === m.owner ? 'same owner' : MN_OWNER_NAMES[acc.owner]) : '',
    tone: {
      bg: 'var(--success-bg, rgba(5,150,105,.10))',
      fg: 'var(--success-fg, #15803d)'
    },
    onClick: () => acc && onOpenAccount && onOpenAccount(acc.id)
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 16,
    color: "var(--border)"
  }), /*#__PURE__*/React.createElement(LinkChip, {
    icon: "Kanban",
    label: pipeline.length + ' candidate' + (pipeline.length === 1 ? '' : 's'),
    sub: sourceGroups.length > 1 ? 'Drawn from ' + sourceGroups.length + ' talent maps' : 'In the shortlist pipeline',
    tone: {
      bg: 'rgba(37,99,235,.10)',
      fg: '#1d4ed8'
    },
    onClick: () => setTab('pipeline')
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-tabs"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx('tm-mn-tab', tab === 'pipeline' && 'is-on'),
    onClick: () => setTab('pipeline')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Kanban",
    size: 14
  }), "Pipeline"), /*#__PURE__*/React.createElement("button", {
    className: cx('tm-mn-tab', tab === 'status' && 'is-on'),
    onClick: () => setTab('status')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 14
  }), "Status report")), tab === 'pipeline' ? /*#__PURE__*/React.createElement("div", {
    className: "tm-fadein"
  }, sourceGroups.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-mn-sourcestrip"
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11.5,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      display: 'inline-flex',
      alignItems: 'center',
      gap: 5
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 13
  }), "Sourced from"), sourceGroups.map(g => /*#__PURE__*/React.createElement("button", {
    key: g.project,
    className: "tm-mn-source",
    onClick: () => g.project !== 'Direct approach' && onOpenProject && onOpenProject(g.project),
    disabled: g.project === 'Direct approach'
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 12
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, g.project), /*#__PURE__*/React.createElement("span", {
    className: "tm-mn-source__n"
  }, g.count))), /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    style: {
      marginLeft: 'auto'
    },
    onClick: () => window.showToast && window.showToast('Add candidates from a talent map')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "Add from map"))), pipeline.length === 0 ? /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable__empty",
    style: {
      border: '1px solid var(--border)',
      borderRadius: 12,
      marginTop: 14
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Inbox",
    size: 22,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, "No candidates yet \u2014 this search is still at proposal stage.")) : /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__board",
    style: {
      marginTop: 14
    }
  }, MN_PIPE_STAGES.map(stage => /*#__PURE__*/React.createElement(MnPipeColumn, {
    key: stage,
    stage: stage,
    cards: pipeline.filter(p => p.stage === stage),
    onMove: moveCard
  })))) : /*#__PURE__*/React.createElement(MandateStatusReport, {
    m: m,
    counts: counts,
    pipeline: pipeline,
    acc: acc,
    sponsor: sponsor,
    stat: stat
  })));
}
function MnPipeColumn({
  stage,
  cards,
  onMove
}) {
  const [over, setOver] = React.useState(false);
  const tone = MN_STAGE_TONE[stage];
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-pl__col', over && 'is-over'),
    onDragOver: e => {
      e.preventDefault();
      setOver(true);
    },
    onDragLeave: () => setOver(false),
    onDrop: e => {
      e.preventDefault();
      setOver(false);
      onMove(e.dataTransfer.getData('text/plain'), stage);
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__col-head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__col-dot",
    style: {
      background: tone
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__col-label",
    style: {
      color: tone
    }
  }, stage), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__col-count"
  }, cards.length)), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__col-cards"
  }, cards.map(c => /*#__PURE__*/React.createElement("div", {
    key: c.id,
    className: "tm-pl__card",
    draggable: true,
    onDragStart: e => {
      e.dataTransfer.setData('text/plain', c.id);
      e.dataTransfer.effectAllowed = 'move';
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-top"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: c.name,
    size: 28,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-name"
  }, c.name), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-role"
  }, c.title))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-bottom"
  }, c.sourcedFrom && /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__card-age",
    title: 'Sourced from ' + c.sourcedFrom
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 11
  }), c.sourcedFrom.length > 16 ? c.sourcedFrom.slice(0, 15) + '…' : c.sourcedFrom), window.tmGetAiIntel && (() => {
    const ai = window.tmGetAiIntel(c.name);
    if (!ai) return null;
    const col = ai.aiScore >= 80 ? '#059669' : ai.aiScore >= 60 ? '#b45309' : '#dc2626';
    return /*#__PURE__*/React.createElement("span", {
      title: ai.aiRationale,
      style: {
        display: 'inline-flex',
        alignItems: 'center',
        gap: 3,
        fontSize: 10,
        fontWeight: 700,
        padding: '1px 6px',
        borderRadius: 4,
        background: 'rgba(124,58,237,.09)',
        color: '#7c3aed',
        cursor: 'default',
        letterSpacing: '.02em'
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Sparkles",
      size: 9
    }), ai.aiScore, "%");
  })(), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__card-age",
    style: {
      marginLeft: 'auto'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 11
  }), c.ageDays, "d in stage")))), cards.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__col-empty"
  }, over ? 'Drop here' : '—')));
}
function MandateStatusReport({
  m,
  counts,
  pipeline,
  acc,
  sponsor,
  stat
}) {
  const weekNo = Math.max(1, Math.round(m.openedDays / 7));
  const active = pipeline.filter(p => p.stage !== 'Placed' && p.stage !== 'Sourced').length;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-fadein",
    style: {
      marginTop: 16,
      maxWidth: 760
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card-h",
    style: {
      display: 'flex',
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 13,
    style: {
      marginRight: 5
    }
  }), "Client status report", /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 'auto',
      fontSize: 11,
      color: 'var(--muted-foreground)',
      textTransform: 'none',
      letterSpacing: 0,
      fontWeight: 400
    }
  }, "Week ", weekNo, " \xB7 for ", acc ? acc.name : 'client')), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '16px 18px'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stats",
    style: {
      marginBottom: 16
    }
  }, stat('Approached', pipeline.length), stat('In process', active, active ? '#b45309' : null), stat('Final panel', counts.Interview), stat(m.status === 'Placed' ? 'Placed' : 'Offers', m.status === 'Placed' ? counts.Placed : counts.Offer, m.status === 'Placed' ? 'var(--success-fg, #15803d)' : null)), /*#__PURE__*/React.createElement("p", {
    style: {
      fontSize: 13.5,
      lineHeight: 1.6,
      margin: '0 0 14px',
      color: 'var(--foreground)'
    }
  }, "We have approached ", /*#__PURE__*/React.createElement("b", null, pipeline.length), " executives for the ", /*#__PURE__*/React.createElement("b", null, m.role), " mandate, of whom ", /*#__PURE__*/React.createElement("b", null, active), " are in active process and ", /*#__PURE__*/React.createElement("b", null, counts.Interview), " have reached the client panel. ", m.placed ? /*#__PURE__*/React.createElement(React.Fragment, null, "The search concluded with the successful placement of ", /*#__PURE__*/React.createElement("b", null, m.placed), ".") : /*#__PURE__*/React.createElement(React.Fragment, null, "Current focus is converting panel candidates to offer; we expect to close within ", Math.max(2, Math.round(m.targetDays / 7)), " weeks.")), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11.5,
      fontWeight: 700,
      textTransform: 'uppercase',
      letterSpacing: '.05em',
      color: 'var(--muted-foreground)',
      marginBottom: 8
    }
  }, "Next milestones"), [m.placed ? 'Onboarding support & 100-day check-in' : 'Client panel interviews — final two candidates', m.placed ? 'Off-limits agreement now active on ' + (acc ? acc.name : 'client') : 'Reference & background checks on shortlist', m.placed ? 'Invoice final-stage fee' : 'Offer negotiation and close'].map((t, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 9,
      padding: '7px 0',
      fontSize: 13
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 6,
      height: 6,
      borderRadius: '50%',
      background: 'var(--primary)',
      flexShrink: 0
    }
  }), t)), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      marginTop: 16,
      paddingTop: 14,
      borderTop: '1px solid color-mix(in srgb, var(--border) 55%, transparent)'
    }
  }, /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: () => window.showToast && window.showToast('Status report sent to ' + (sponsor ? sponsor.name : 'client'))
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Send",
    size: 14
  }), "Send to client"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    onClick: () => window.showToast && window.showToast('Exported as PDF')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 14
  }), "Export PDF")))));
}
Object.assign(window, {
  MandatesScreen,
  MandateDetail,
  MandateStatusPill
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/mandates.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/mapview.jsx
try { (() => {
/* global React, Icon, cx, TM_REGION_LABELS, initials */
// ── Map view: abstract map canvas + company bubbles + executive satellites ────
// Stylized: positions are x/y %, not real geo. Demonstrates scaling + the
// drag-to-reparent satellite interaction that defines the product.

const REVENUE_RADIUS = {
  '<$10M': 16,
  '$10M–100M': 20,
  '$100M–500M': 24,
  '$500M–1B': 30,
  '$1B–5B': 38,
  '>$5B': 46
};
const EMP_RADIUS = {
  '<250': 16,
  '250–1K': 20,
  '1K–5K': 24,
  '5K–10K': 30,
  '10K–50K': 38,
  '>50K': 46
};
function radiusFor(c, metric) {
  return (metric === 'employees' ? EMP_RADIUS[c.employees] : REVENUE_RADIUS[c.revenue]) || 22;
}
const ROW_H = 34,
  START_GAP = 18,
  INDENT = 46,
  SNAP = 26;
function SatelliteCluster({
  company,
  radius,
  selectedExec,
  onSelectExec,
  theme
}) {
  const execs = company.execs.slice(0, 8);
  const [hier, setHier] = React.useState({}); // childId -> parentId
  const [drag, setDrag] = React.useState(null); // {id, dx, dy}
  const [snap, setSnap] = React.useState(null);
  const dragRef = React.useRef(null);

  // ordered roots then children (DFS)
  const order = React.useMemo(() => {
    const out = [];
    const byParent = {};
    execs.forEach(e => {
      const p = hier[e.id];
      (byParent[p || '_root'] ||= []).push(e);
    });
    const walk = (pid, depth) => (byParent[pid] || []).forEach(e => {
      out.push({
        e,
        depth
      });
      walk(e.id, depth + 1);
    });
    walk('_root', 0);
    return out;
  }, [execs, hier]);
  const basePos = React.useMemo(() => {
    const pos = {};
    order.forEach(({
      e,
      depth
    }, i) => {
      pos[e.id] = {
        x: 20 + depth * INDENT,
        y: radius + START_GAP + i * ROW_H
      };
    });
    return pos;
  }, [order, radius]);
  const descendants = id => {
    const out = new Set();
    const stack = [id];
    while (stack.length) {
      const cur = stack.pop();
      Object.entries(hier).forEach(([c, p]) => {
        if (p === cur) {
          out.add(c);
          stack.push(c);
        }
      });
    }
    return out;
  };
  const startDrag = (e, id) => {
    e.preventDefault();
    e.stopPropagation();
    const sx = e.clientX,
      sy = e.clientY;
    dragRef.current = {
      id,
      moved: false
    };
    const desc = descendants(id);
    const move = ev => {
      const dx = ev.clientX - sx,
        dy = ev.clientY - sy;
      if (!dragRef.current.moved && Math.hypot(dx, dy) < 4) return;
      dragRef.current.moved = true;
      setDrag({
        id,
        dx,
        dy
      });
      // find snap target
      const me = basePos[id];
      const px = me.x + dx,
        py = me.y + dy;
      let best = null;
      order.forEach(({
        e: o
      }) => {
        if (o.id === id || desc.has(o.id)) return;
        const op = basePos[o.id];
        const d = Math.hypot(px - op.x, py - op.y);
        if (d < SNAP && (!best || d < best.d)) best = {
          id: o.id,
          d
        };
      });
      setSnap(best ? best.id : null);
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
      const moved = dragRef.current.moved;
      const target = snapRef.current;
      if (moved) {
        if (target) setHier(h => ({
          ...h,
          [id]: target
        }));else if (hier[id]) setHier(h => {
          const n = {
            ...h
          };
          delete n[id];
          return n;
        }); // detach
      } else {
        onSelectExec(company.id, id);
      }
      setDrag(null);
      setSnap(null);
      dragRef.current = null;
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  };
  const snapRef = React.useRef(null);
  snapRef.current = snap;
  const pos = id => {
    const b = basePos[id];
    if (drag && drag.id === id) return {
      x: b.x + drag.dx,
      y: b.y + drag.dy
    };
    return b;
  };
  const maxY = order.length ? radius + START_GAP + (order.length - 1) * ROW_H : radius;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      left: 0,
      top: 0,
      pointerEvents: 'none'
    }
  }, /*#__PURE__*/React.createElement("svg", {
    style: {
      position: 'absolute',
      left: 0,
      top: 0,
      overflow: 'visible',
      width: 1,
      height: 1
    }
  }, /*#__PURE__*/React.createElement("line", {
    x1: 0,
    y1: radius,
    x2: 0,
    y2: maxY,
    stroke: "currentColor",
    strokeWidth: 1.5,
    strokeOpacity: 0.18,
    style: {
      color: theme === 'dark' ? '#fff' : '#15213a'
    }
  }), order.map(({
    e
  }) => {
    const p = pos(e.id);
    const parent = hier[e.id];
    if (parent) {
      const pp = pos(parent);
      return /*#__PURE__*/React.createElement("line", {
        key: 'h' + e.id,
        x1: pp.x + 16,
        y1: pp.y,
        x2: p.x - 10,
        y2: p.y,
        stroke: "hsl(35 92% 50%)",
        strokeWidth: 1.5,
        strokeOpacity: 0.55,
        strokeDasharray: "3 2"
      });
    }
    return /*#__PURE__*/React.createElement("line", {
      key: 'b' + e.id,
      x1: 0,
      y1: p.y,
      x2: p.x - 10,
      y2: p.y,
      stroke: "currentColor",
      strokeWidth: 1,
      strokeOpacity: 0.22,
      strokeDasharray: "4 3",
      style: {
        color: theme === 'dark' ? '#fff' : '#15213a'
      }
    });
  }), drag && snap && (() => {
    const a = pos(drag.id),
      b = pos(snap);
    return /*#__PURE__*/React.createElement("line", {
      x1: b.x,
      y1: b.y,
      x2: a.x,
      y2: a.y,
      stroke: "hsl(35 92% 50%)",
      strokeWidth: 2,
      strokeDasharray: "4 3",
      strokeOpacity: 0.85
    });
  })()), order.map(({
    e
  }) => {
    const p = pos(e.id);
    const isChild = !!hier[e.id];
    const isSel = selectedExec === e.id;
    const isSnap = snap === e.id;
    return /*#__PURE__*/React.createElement("div", {
      key: e.id,
      className: cx('tm-sat', isChild && 'is-child', isSel && 'is-sel'),
      style: {
        left: p.x,
        top: p.y,
        pointerEvents: 'auto',
        zIndex: drag && drag.id === e.id ? 30 : 10,
        boxShadow: isSnap ? '0 0 0 3px hsl(35 92% 50% / .3), var(--shadow-node)' : undefined,
        borderColor: isSnap ? 'hsl(35 92% 50%)' : undefined,
        cursor: drag && drag.id === e.id ? 'grabbing' : 'grab'
      },
      onMouseDown: ev => startDrag(ev, e.id)
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        width: 20,
        height: 20,
        borderRadius: '50%',
        background: 'rgba(37,99,235,.15)',
        color: 'var(--primary)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 9,
        fontWeight: 700
      }
    }, initials(e.name)), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("span", {
      className: "tm-sat__nm"
    }, e.name), /*#__PURE__*/React.createElement("br", null), /*#__PURE__*/React.createElement("span", {
      className: "tm-sat__ti"
    }, e.title)));
  }));
}
function CompanyNode({
  company,
  radius,
  selected,
  metric,
  theme,
  onSelect,
  children
}) {
  const fill = selected ? 'hsl(35 92% 50%)' : company.color;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-node",
    style: {
      left: company.x + '%',
      top: company.y + '%'
    }
  }, /*#__PURE__*/React.createElement("div", {
    onClick: () => onSelect(company.id),
    style: {
      width: radius * 2,
      height: radius * 2,
      borderRadius: '50%',
      background: fill,
      opacity: selected ? 0.92 : 0.55,
      cursor: 'pointer',
      transition: 'all .3s cubic-bezier(.4,0,.2,1)',
      boxShadow: selected ? '0 0 0 4px hsl(35 92% 50% / .25)' : 'none'
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-node__label",
    style: {
      top: -radius - 6
    }
  }, company.name), children);
}
function MapView({
  companies,
  selectedCompany,
  selectedExec,
  scalingMetric,
  showSats,
  theme,
  onSelectCompany,
  onSelectExec
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-mapwrap', theme !== 'dark' && 'is-light')
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-graticule",
    style: {
      backgroundImage: `repeating-linear-gradient(to right, ${theme === 'dark' ? 'rgba(255,255,255,.05)' : 'rgba(20,33,58,.06)'} 0 1px, transparent 1px 64px),
          repeating-linear-gradient(to bottom, ${theme === 'dark' ? 'rgba(255,255,255,.05)' : 'rgba(20,33,58,.06)'} 0 1px, transparent 1px 64px)`
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      left: '58%',
      top: '46%',
      width: 380,
      height: 300,
      transform: 'translate(-50%,-50%)',
      background: theme === 'dark' ? 'radial-gradient(circle, rgba(37,99,235,.18), transparent 70%)' : 'radial-gradient(circle, rgba(37,99,235,.10), transparent 70%)',
      pointerEvents: 'none'
    }
  }), TM_REGION_LABELS.map(r => /*#__PURE__*/React.createElement("div", {
    key: r.label,
    className: "tm-region-label",
    style: {
      left: r.x + '%',
      top: r.y + '%'
    }
  }, r.label)), companies.map(c => {
    const radius = radiusFor(c, scalingMetric);
    const isSel = selectedCompany === c.id;
    const showCluster = showSats || isSel;
    return /*#__PURE__*/React.createElement(CompanyNode, {
      key: c.id,
      company: c,
      radius: radius,
      selected: isSel,
      metric: scalingMetric,
      theme: theme,
      onSelect: onSelectCompany
    }, showCluster && c.execs.length > 0 && /*#__PURE__*/React.createElement(SatelliteCluster, {
      company: c,
      radius: radius,
      selectedExec: selectedExec,
      onSelectExec: onSelectExec,
      theme: theme
    }));
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-map-hint"
  }, "Hover or select a company to orbit its executives \xB7 drag a pill onto another to build the org hierarchy"));
}
Object.assign(window, {
  MapView,
  radiusFor
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/mapview.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/panel.jsx
try { (() => {
/* global React, Icon, Button, Badge, Pill, Avatar, cx */
// ── Right panel: company detail ──────────────────────────────────────────────

function RightPanel({
  company,
  scalingMetric,
  onMetric,
  onClose,
  onSelectExec,
  pipelineNames,
  onAddToPipeline,
  onGoToPipeline
}) {
  if (!company) return null;
  const confLabel = company.confidence >= 80 ? ['High', 'var(--success)'] : company.confidence >= 60 ? ['Medium', '#b45309'] : ['Low', '#b91c1c'];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-rp tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-rp__head"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'flex-start'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-rp__title"
  }, company.name), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "icon",
    onClick: onClose
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 16
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 4,
      fontSize: 13,
      color: 'var(--muted-foreground)',
      margin: '6px 0 10px'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 13
  }), company.city, ", ", company.country), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6,
      flexWrap: 'wrap'
    }
  }, /*#__PURE__*/React.createElement(Badge, {
    variant: "outline"
  }, company.sector), /*#__PURE__*/React.createElement(Badge, {
    variant: "secondary"
  }, company.relevance))), /*#__PURE__*/React.createElement("div", {
    className: "tm-rp__body"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-rp__sec-h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "TrendingUp",
    size: 15
  }), "Scale Snapshot"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'grid',
      gridTemplateColumns: '1fr 1fr',
      gap: 12
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: cx('tm-metric', scalingMetric === 'revenue' && 'is-on'),
    onClick: () => onMetric('revenue')
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-metric__h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "DollarSign",
    size: 13
  }), "Revenue"), /*#__PURE__*/React.createElement("div", {
    className: "tm-metric__v tm-mono"
  }, company.revenue), scalingMetric === 'revenue' && /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 10,
      color: 'var(--primary)',
      marginTop: 4
    }
  }, "Map Scaling Active")), /*#__PURE__*/React.createElement("div", {
    className: cx('tm-metric', scalingMetric === 'employees' && 'is-on'),
    onClick: () => onMetric('employees')
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-metric__h"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 13
  }), "Employees"), /*#__PURE__*/React.createElement("div", {
    className: "tm-metric__v tm-mono"
  }, company.employees), scalingMetric === 'employees' && /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 10,
      color: 'var(--primary)',
      marginTop: 4
    }
  }, "Map Scaling Active")))), /*#__PURE__*/React.createElement("div", {
    className: "tm-sep"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-rp__sec-h",
    style: {
      marginBottom: 0
    }
  }, "Company Summary"), /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13
  }), "Enrich")), /*#__PURE__*/React.createElement("p", {
    style: {
      fontSize: 14,
      lineHeight: 1.6,
      marginTop: 10,
      color: 'var(--foreground)'
    }
  }, company.summary)), /*#__PURE__*/React.createElement("div", {
    className: "tm-sep"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginBottom: 10
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-rp__sec-h",
    style: {
      marginBottom: 0
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 15
  }), "Key Executives"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    style: {
      color: 'var(--primary)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 13
  }), "Add")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 8
    }
  }, company.execs.map(e => /*#__PURE__*/React.createElement("div", {
    key: e.id,
    className: "tm-exec-row",
    onClick: () => onSelectExec(company.id, e.id)
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: e.name,
    tone: e.enriched ? 'enriched' : 'primary',
    size: 40
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 14,
      fontWeight: 500
    }
  }, e.name), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 10,
      fontWeight: 500,
      color: e.verified ? 'var(--success)' : 'var(--muted-foreground)'
    }
  }, e.verified ? '10' : '5', "/10")), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)'
    }
  }, e.title), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginTop: 6
    }
  }, pipelineNames && pipelineNames.has(e.name) ? /*#__PURE__*/React.createElement(InPipelineBadge, {
    onClick: () => onGoToPipeline && onGoToPipeline()
  }) : /*#__PURE__*/React.createElement(AddToPipelineBtn, {
    onClick: () => onAddToPipeline && onAddToPipeline({
      name: e.name,
      title: e.title,
      company: company.name
    })
  }), /*#__PURE__*/React.createElement(Pill, {
    tone: e.verified ? 'verified' : 'neutral',
    style: {
      fontSize: 9
    }
  }, e.verified ? 'Verified' : 'Unverified')))))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-rp__foot"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      fontSize: 12,
      color: 'var(--muted-foreground)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldCheck",
    size: 15
  }), "Data Confidence"), /*#__PURE__*/React.createElement(Pill, {
    tone: "neutral",
    style: {
      color: confLabel[1]
    }
  }, confLabel[0], " (", Math.round(company.confidence / 10), "/10)"))));
}
Object.assign(window, {
  RightPanel
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/panel.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/primitives.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/* global React */
// ── ALAC Talent Map UI kit · primitives ──────────────────────────────────────
// Icon (Lucide UMD bridge), Button, Badge, Pill, Avatar, helpers.

const cx = (...a) => a.filter(Boolean).join(' ');
function Icon({
  name,
  size = 16,
  strokeWidth = 2,
  className,
  style,
  color
}) {
  const node = window.lucide?.icons?.[name];
  if (!node) return null;
  const children = (node[2] || []).map(([tag, attrs], i) => React.createElement(tag, {
    key: i,
    ...attrs
  }));
  return React.createElement('svg', {
    xmlns: 'http://www.w3.org/2000/svg',
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: color || 'currentColor',
    strokeWidth,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    className,
    style: {
      flexShrink: 0,
      ...style
    }
  }, children);
}
function Button({
  variant = 'default',
  size = 'default',
  children,
  className,
  ...props
}) {
  return /*#__PURE__*/React.createElement("button", _extends({}, props, {
    className: cx('tm-btn', `tm-btn--${variant}`, size !== 'default' && `tm-btn--${size}`, className)
  }), children);
}
function Badge({
  variant = 'default',
  children,
  className,
  style
}) {
  return /*#__PURE__*/React.createElement("span", {
    className: cx('tm-badge', `tm-badge--${variant}`, className),
    style: style
  }, children);
}

// Soft-tint status pill (relevance / status)
const PILL_TONES = {
  direct: {
    bg: 'var(--success-bg)',
    fg: 'var(--success-fg)'
  },
  adjacent: {
    bg: 'var(--info-bg)',
    fg: '#1d4ed8'
  },
  inferred: {
    bg: 'var(--warning-bg)',
    fg: '#b45309'
  },
  ai: {
    bg: 'var(--ai-bg)',
    fg: '#6d28d9'
  },
  danger: {
    bg: 'var(--destructive-bg)',
    fg: '#b91c1c'
  },
  verified: {
    bg: '#dcfce7',
    fg: '#15803d'
  },
  neutral: {
    bg: 'var(--muted)',
    fg: 'var(--muted-foreground)'
  }
};
function Pill({
  tone = 'neutral',
  children,
  className,
  style
}) {
  const t = PILL_TONES[tone] || PILL_TONES.neutral;
  return /*#__PURE__*/React.createElement("span", {
    className: cx('tm-pill', className),
    style: {
      background: t.bg,
      color: t.fg,
      ...style
    }
  }, children);
}
function initials(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]?.toUpperCase() || '').join('');
}
function Avatar({
  name,
  shape = 'circle',
  tone = 'primary',
  size = 28
}) {
  const tones = {
    primary: {
      bg: 'rgba(37,99,235,.12)',
      fg: 'var(--primary)'
    },
    enriched: {
      bg: '#d1fae5',
      fg: '#047857'
    },
    neutral: {
      bg: 'var(--muted)',
      fg: 'var(--muted-foreground)'
    }
  };
  const t = tones[tone] || tones.primary;
  return /*#__PURE__*/React.createElement("span", {
    style: {
      width: size,
      height: size,
      flexShrink: 0,
      borderRadius: shape === 'square' ? 7 : '50%',
      background: t.bg,
      color: t.fg,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontSize: size <= 22 ? 9 : 11,
      fontWeight: 700,
      letterSpacing: '.02em',
      border: tone === 'neutral' ? '1px solid var(--border)' : 'none'
    }
  }, initials(name));
}
function Tooltip({
  label,
  side = 'right',
  children
}) {
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-tip-wrap"
  }, children, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-tip', `tm-tip--${side}`)
  }, label));
}

// ── Pipeline badge & button (shared across map, table, universe, panel) ──────
function InPipelineBadge({
  onClick
}) {
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-pipeline-badge",
    onClick: e => {
      e.stopPropagation();
      onClick && onClick();
    },
    title: "In pipeline \u2014 click to view"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 10
  }), "In pipeline");
}
function AddToPipelineBtn({
  onClick,
  label = true
}) {
  return /*#__PURE__*/React.createElement("button", {
    className: "tm-add-pipeline-btn",
    onClick: e => {
      e.stopPropagation();
      onClick && onClick();
    },
    title: "Add to pipeline"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserPlus",
    size: 12
  }), label && /*#__PURE__*/React.createElement("span", null, "Add to pipeline"));
}

// Imperative toast — works from any component without prop-drilling
function showToast(msg) {
  let wrap = document.getElementById('tm-toast-root');
  if (!wrap) {
    wrap = document.createElement('div');
    wrap.id = 'tm-toast-root';
    document.body.appendChild(wrap);
  }
  const checkSvg = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0"><path d="M20 6 9 17l-5-5"/></svg>';
  wrap.className = 'tm-toast-wrap';
  wrap.innerHTML = '<div class="tm-toast">' + checkSvg + '<span>' + msg.replace(/</g, '&lt;') + '</span></div>';
  clearTimeout(wrap._t);
  wrap._t = setTimeout(() => {
    wrap.innerHTML = '';
  }, 2600);
}
Object.assign(window, {
  cx,
  Icon,
  Button,
  Badge,
  Pill,
  Avatar,
  Tooltip,
  initials,
  InPipelineBadge,
  AddToPipelineBtn,
  showToast
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/primitives.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/projects.jsx
try { (() => {
/* global React, Icon, Button, cx */
// ── Search Maps: data, popover, and full screen ──────────────────────────────
// Two-tier model: Clients (CRM Accounts) → Search Maps. Each client can have
// multiple search maps. The popover shows expandable client groups; the full
// screen has a table grouped by client with collapsible sections.

const TM_PROJECTS = [{
  id: 105,
  name: 'CFO search — Qatar banking',
  clientId: 'acc1',
  companies: 22,
  execs: 51,
  when: '3 days ago',
  ageDays: 3
}, {
  id: 101,
  name: 'Top FMCG distributors in UAE',
  clientId: 'acc1',
  companies: 24,
  execs: 68,
  when: '2 days ago',
  ageDays: 2,
  active: true
}, {
  id: 107,
  name: 'Logistics & 3PL — KSA',
  clientId: 'acc1',
  companies: 27,
  execs: 44,
  when: '2 months ago',
  ageDays: 60
}, {
  id: 102,
  name: 'Leading PE firms in Saudi Arabia',
  clientId: 'acc6',
  companies: 18,
  execs: 41,
  when: '5 days ago',
  ageDays: 5
}, {
  id: 103,
  name: 'Industrial equipment — Egypt',
  clientId: 'acc5',
  companies: 31,
  execs: 52,
  when: '2 weeks ago',
  ageDays: 14
}, {
  id: 104,
  name: 'Retail chains across GCC',
  clientId: 'acc4',
  companies: 12,
  execs: 22,
  when: '1 month ago',
  ageDays: 30
}, {
  id: 106,
  name: 'CMOs in MENA telecom',
  clientId: 'acc7',
  companies: 19,
  execs: 37,
  when: '6 weeks ago',
  ageDays: 42
}];

// ── Helpers ──────────────────────────────────────────────────────────────────
function getClientName(clientId) {
  const accounts = window.TM_ACCOUNTS || [];
  const acc = accounts.find(a => a.id === clientId);
  return acc ? acc.name : null;
}
function groupByClient(maps) {
  const groups = {};
  const unassigned = [];
  maps.forEach(p => {
    if (p.clientId) {
      if (!groups[p.clientId]) groups[p.clientId] = {
        clientId: p.clientId,
        name: getClientName(p.clientId) || 'Unknown',
        maps: []
      };
      groups[p.clientId].maps.push(p);
    } else {
      unassigned.push(p);
    }
  });
  // Sort client groups by most-recently-active map
  const sorted = Object.values(groups).sort((a, b) => {
    const aMin = Math.min(...a.maps.map(m => m.ageDays));
    const bMin = Math.min(...b.maps.map(m => m.ageDays));
    return aMin - bMin;
  });
  return {
    clientGroups: sorted,
    unassigned
  };
}

// ── Badges ───────────────────────────────────────────────────────────────────
function DraftBadge() {
  return null;
} // draft concept removed — no-op for legacy callers
function ActiveBadge() {
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-statuschip is-active"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 10
  }), "Active");
}
function ProjectMeta({
  p
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 12,
      color: 'var(--muted-foreground)',
      fontSize: 11
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 12
  }), p.companies), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 12
  }), p.execs), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 12
  }), p.when));
}

// ── Landing grid (returning user) ────────────────────────────────────────────
function RecentProjects({
  projects,
  onOpen,
  onSeeAll
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-recent"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-recent__head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow",
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 12
  }), "Recent search maps"), /*#__PURE__*/React.createElement("button", {
    className: "tm-link",
    onClick: onSeeAll
  }, "See all ", projects.length, " ", /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 12
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-recent__grid"
  }, projects.slice(0, 3).map(p => /*#__PURE__*/React.createElement("button", {
    key: p.id,
    className: "tm-proj-card",
    onClick: () => onOpen(p)
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-proj-card__name"
  }, p.name), /*#__PURE__*/React.createElement("span", {
    className: "tm-proj-card__go"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14
  }))), /*#__PURE__*/React.createElement(ProjectMeta, {
    p: p
  })))));
}

// ── Lean rail popover — clients → maps ───────────────────────────────────────
function ProjectsPanel({
  open,
  projects,
  top = 12,
  onClose,
  onOpen,
  onSeeAll
}) {
  if (!open) return null;
  const [expanded, setExpanded] = React.useState(() => new Set());
  const empty = !projects || projects.length === 0;
  const {
    clientGroups,
    unassigned
  } = groupByClient(projects);

  // Auto-expand clients with active or draft maps on first open
  const autoRef = React.useRef(false);
  if (!autoRef.current && clientGroups.length > 0) {
    autoRef.current = true;
    const auto = new Set();
    clientGroups.forEach(cg => {
      if (cg.maps.some(m => m.active)) auto.add(cg.clientId);
    });
    if (auto.size === 0 && clientGroups.length > 0) auto.add(clientGroups[0].clientId);
    if (auto.size > 0) {
      // Set initial state synchronously during first render
      expanded.size === 0 && auto.forEach(id => expanded.add(id));
    }
  }
  const toggle = id => setExpanded(s => {
    const n = new Set(s);
    n.has(id) ? n.delete(id) : n.add(id);
    return n;
  });
  const MapRow = p => /*#__PURE__*/React.createElement("button", {
    key: p.id,
    className: cx('tm-proj-row', p.active && 'is-active'),
    onClick: () => onOpen(p),
    style: {
      width: '100%',
      paddingLeft: 28
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-proj-row__main",
    style: {
      pointerEvents: 'none'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 11,
    color: "var(--muted-foreground)",
    style: {
      flexShrink: 0
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-proj-row__name",
    style: p.active ? {
      color: 'var(--primary)'
    } : null
  }, p.name)), /*#__PURE__*/React.createElement(ProjectMeta, {
    p: p
  })));
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-proj-scrim",
    onClick: onClose
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-proj-panel",
    style: {
      top
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-proj-panel__head"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 15,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      fontWeight: 600,
      flex: 1
    }
  }, "Search Maps")), empty ? /*#__PURE__*/React.createElement("div", {
    className: "tm-proj-empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 28,
    color: "var(--muted-foreground)",
    style: {
      opacity: .4
    }
  }), /*#__PURE__*/React.createElement("p", {
    style: {
      fontSize: 12,
      margin: '8px 0 2px'
    }
  }, "No search maps yet"), /*#__PURE__*/React.createElement("p", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)',
      margin: 0
    }
  }, "Run a search to create your first one.")) : /*#__PURE__*/React.createElement("div", {
    className: "tm-proj-list"
  }, clientGroups.map(cg => {
    const isOpen = expanded.has(cg.clientId);
    return /*#__PURE__*/React.createElement(React.Fragment, {
      key: cg.clientId
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-proj-client",
      onClick: () => toggle(cg.clientId)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: isOpen ? 'ChevronDown' : 'ChevronRight',
      size: 12,
      color: "var(--muted-foreground)"
    }), /*#__PURE__*/React.createElement(Icon, {
      name: "Building2",
      size: 12,
      color: "var(--muted-foreground)"
    }), /*#__PURE__*/React.createElement("span", {
      className: "tm-proj-client__name"
    }, cg.name), /*#__PURE__*/React.createElement("span", {
      className: "tm-proj-client__n"
    }, cg.maps.length)), isOpen && cg.maps.map(MapRow));
  }), unassigned.length > 0 && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("button", {
    className: "tm-proj-client",
    style: {
      opacity: 0.7
    },
    onClick: () => toggle('__unassigned')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: expanded.has('__unassigned') ? 'ChevronDown' : 'ChevronRight',
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "Inbox",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-proj-client__name"
  }, "Unassigned"), /*#__PURE__*/React.createElement("span", {
    className: "tm-proj-client__n"
  }, unassigned.length)), expanded.has('__unassigned') && unassigned.map(p => /*#__PURE__*/React.createElement("button", {
    key: p.id,
    className: cx('tm-proj-row', p.active && 'is-active'),
    onClick: () => onOpen(p),
    style: {
      width: '100%',
      paddingLeft: 28
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-proj-row__main",
    style: {
      pointerEvents: 'none'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 11,
    color: "var(--muted-foreground)",
    style: {
      flexShrink: 0
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-proj-row__name"
  }, p.name)), /*#__PURE__*/React.createElement(ProjectMeta, {
    p: p
  })))))), !empty && /*#__PURE__*/React.createElement("button", {
    className: "tm-proj-seeall",
    onClick: onSeeAll
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "LayoutGrid",
    size: 14
  }), "See all search maps (", projects.length, ")", /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14,
    style: {
      marginLeft: 'auto'
    }
  }))));
}

// ── Full Search Maps screen — grouped by client ──────────────────────────────
function ProjectsScreen({
  projects,
  onOpen,
  onNewSearch,
  onDelete
}) {
  const [q, setQ] = React.useState('');
  const [sort, setSort] = React.useState({
    key: 'updated',
    dir: 'asc'
  });
  const [sel, setSel] = React.useState(() => new Set());
  const [collapsedClients, setCollapsedClients] = React.useState(() => new Set());
  const filtered = projects.filter(p => {
    if (!q) return true;
    const ql = q.toLowerCase();
    const clientName = (getClientName(p.clientId) || '').toLowerCase();
    return p.name.toLowerCase().includes(ql) || clientName.includes(ql);
  });
  const sorted = [...filtered].sort((a, b) => {
    let av, bv;
    if (sort.key === 'name') {
      av = a.name.toLowerCase();
      bv = b.name.toLowerCase();
    } else if (sort.key === 'companies') {
      av = a.companies;
      bv = b.companies;
    } else if (sort.key === 'execs') {
      av = a.execs || a.selected || 0;
      bv = b.execs || b.selected || 0;
    } else {
      av = a.ageDays;
      bv = b.ageDays;
    }
    if (av < bv) return sort.dir === 'asc' ? -1 : 1;
    if (av > bv) return sort.dir === 'asc' ? 1 : -1;
    return 0;
  });
  const {
    clientGroups,
    unassigned
  } = groupByClient(sorted);
  const toggleSort = key => setSort(s => s.key === key ? {
    key,
    dir: s.dir === 'asc' ? 'desc' : 'asc'
  } : {
    key,
    dir: 'asc'
  });
  const SortH = ({
    k,
    children,
    right
  }) => /*#__PURE__*/React.createElement("button", {
    className: cx('tm-sorth', right && 'is-right'),
    onClick: () => toggleSort(k)
  }, children, sort.key === k && /*#__PURE__*/React.createElement(Icon, {
    name: sort.dir === 'asc' ? 'ChevronUp' : 'ChevronDown',
    size: 12
  }));
  const allSel = sorted.length > 0 && sorted.every(p => sel.has(p.id));
  const toggleAll = () => setSel(allSel ? new Set() : new Set(sorted.map(p => p.id)));
  const toggleOne = id => setSel(s => {
    const n = new Set(s);
    n.has(id) ? n.delete(id) : n.add(id);
    return n;
  });
  const toggleClient = id => setCollapsedClients(s => {
    const n = new Set(s);
    n.has(id) ? n.delete(id) : n.add(id);
    return n;
  });
  const bulkDelete = () => {
    onDelete([...sel]);
    setSel(new Set());
  };
  const counts = {
    all: projects.length
  };
  const renderRow = p => /*#__PURE__*/React.createElement("div", {
    key: p.id,
    className: cx('tm-ptable__row', sel.has(p.id) && 'is-sel')
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-checkbox",
    onClick: () => toggleOne(p.id)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: sel.has(p.id) ? 'CheckSquare' : 'Square',
    size: 15,
    color: sel.has(p.id) ? 'var(--primary)' : 'var(--muted-foreground)'
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-ptable__name",
    onClick: () => onOpen(p)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Map",
    size: 13,
    color: "var(--muted-foreground)"
  }), p.name, p.active && /*#__PURE__*/React.createElement("span", {
    className: "tm-dotcur",
    title: "Currently open"
  })), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(ActiveBadge, null)), /*#__PURE__*/React.createElement("span", {
    className: "tm-mono tm-r"
  }, p.companies), /*#__PURE__*/React.createElement("span", {
    className: "tm-mono tm-r"
  }, p.execs), /*#__PURE__*/React.createElement("span", {
    className: "tm-r",
    style: {
      color: 'var(--muted-foreground)',
      fontSize: 12
    }
  }, p.when), /*#__PURE__*/React.createElement("button", {
    className: "tm-proj-del",
    onClick: () => onDelete([p.id])
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Trash2",
    size: 14
  })));
  const renderClientGroup = cg => {
    const isCollapsed = collapsedClients.has(cg.clientId);
    const groupMaps = cg.maps;
    const groupSelCount = groupMaps.filter(p => sel.has(p.id)).length;
    const allGroupSel = groupMaps.length > 0 && groupMaps.every(p => sel.has(p.id));
    const toggleGroupSel = () => {
      setSel(s => {
        const n = new Set(s);
        if (allGroupSel) groupMaps.forEach(p => n.delete(p.id));else groupMaps.forEach(p => n.add(p.id));
        return n;
      });
    };
    return /*#__PURE__*/React.createElement(React.Fragment, {
      key: cg.clientId
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-ptable__cgroup"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-checkbox",
      onClick: toggleGroupSel
    }, /*#__PURE__*/React.createElement(Icon, {
      name: allGroupSel ? 'CheckSquare' : groupSelCount > 0 ? 'MinusSquare' : 'Square',
      size: 15,
      color: allGroupSel || groupSelCount > 0 ? 'var(--primary)' : 'var(--muted-foreground)'
    })), /*#__PURE__*/React.createElement("button", {
      className: "tm-ptable__cname",
      onClick: () => toggleClient(cg.clientId)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: isCollapsed ? 'ChevronRight' : 'ChevronDown',
      size: 13
    }), /*#__PURE__*/React.createElement(Icon, {
      name: "Building2",
      size: 13,
      color: "var(--muted-foreground)"
    }), /*#__PURE__*/React.createElement("span", null, cg.name), /*#__PURE__*/React.createElement("span", {
      className: "tm-ptable__cn"
    }, groupMaps.length, " ", groupMaps.length === 1 ? 'map' : 'maps'))), !isCollapsed && groupMaps.map(renderRow));
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "Workspace"), /*#__PURE__*/React.createElement("h1", {
    className: "tm-pscreen__title"
  }, "Search Maps")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 10,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-search-field"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 15,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    placeholder: "Search maps\u2026",
    value: q,
    onChange: e => setQ(e.target.value)
  })), /*#__PURE__*/React.createElement(Button, {
    onClick: onNewSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 16
  }), "New search"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-tabs"
  }, [['all', 'All']].map(([id, label]) => /*#__PURE__*/React.createElement("button", {
    key: id,
    className: cx('tm-tab', 'is-on')
  }, label, /*#__PURE__*/React.createElement("span", {
    className: "tm-tab__n"
  }, counts[id]))), sel.size > 0 && /*#__PURE__*/React.createElement("button", {
    className: "tm-bulkdel",
    onClick: bulkDelete
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Trash2",
    size: 13
  }), "Delete ", sel.size)), /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable__head"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-checkbox",
    onClick: toggleAll
  }, /*#__PURE__*/React.createElement(Icon, {
    name: allSel ? 'CheckSquare' : 'Square',
    size: 15,
    color: allSel ? 'var(--primary)' : 'var(--muted-foreground)'
  })), /*#__PURE__*/React.createElement(SortH, {
    k: "name"
  }, "Search map"), /*#__PURE__*/React.createElement("span", null, "Status"), /*#__PURE__*/React.createElement(SortH, {
    k: "companies",
    right: true
  }, "Companies"), /*#__PURE__*/React.createElement(SortH, {
    k: "execs",
    right: true
  }, "Executives"), /*#__PURE__*/React.createElement(SortH, {
    k: "updated",
    right: true
  }, "Updated"), /*#__PURE__*/React.createElement("span", null)), clientGroups.map(renderClientGroup), unassigned.length > 0 && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable__cgroup"
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 28
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable__cname",
    style: {
      cursor: 'default'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Inbox",
    size: 13,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)'
    }
  }, "Unassigned"), /*#__PURE__*/React.createElement("span", {
    className: "tm-ptable__cn"
  }, unassigned.length))), unassigned.map(renderRow)), sorted.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable__empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 20,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, "No search maps match \"", q || tab, "\".")))));
}

// ── All-search-maps picker screen ────────────────────────────────────────────
// Shown when the user is in the "All search maps" context and clicks a
// section in the workspace nav (Position, Strategy, Sourcing…) that only
// makes sense scoped to a single search map. Lists every map grouped by
// client; clicking one opens that map directly on the requested section.
const PICKER_VIEW_COPY = {
  position: {
    label: 'Position',
    verb: 'review the position brief'
  },
  strategy: {
    label: 'Strategy',
    verb: 'review the search strategy'
  },
  sourcing: {
    label: 'Sourcing',
    verb: 'source candidates'
  },
  map: {
    label: 'Map',
    verb: 'open the talent map'
  },
  candidates: {
    label: 'Candidates',
    verb: 'review candidates'
  },
  outreach: {
    label: 'Outreach',
    verb: 'send outreach'
  },
  inbox: {
    label: 'Inbox',
    verb: 'review replies'
  },
  reports: {
    label: 'Reports',
    verb: 'open reports'
  },
  aiAgent: {
    label: 'AI Agent',
    verb: 'work with the AI agent'
  }
};
function AllSearchesPicker({
  view,
  projects,
  onSelect,
  onNewSearch
}) {
  const [q, setQ] = React.useState('');
  const copy = PICKER_VIEW_COPY[view] || {
    label: 'This section',
    verb: 'continue'
  };
  const filtered = (projects || []).filter(p => {
    if (!q) return true;
    const ql = q.toLowerCase();
    const cn = (getClientName(p.clientId) || '').toLowerCase();
    return p.name.toLowerCase().includes(ql) || cn.includes(ql);
  });
  const {
    clientGroups,
    unassigned
  } = groupByClient(filtered);
  const renderRow = p => /*#__PURE__*/React.createElement("button", {
    key: p.id,
    className: "tm-pickrow",
    onClick: () => onSelect(p)
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pickrow__ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Target",
    size: 13
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickrow__main"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pickrow__name"
  }, p.name), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickrow__meta"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 11
  }), getClientName(p.clientId) || 'Unassigned', /*#__PURE__*/React.createElement("span", {
    className: "tm-pickrow__dot"
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 11
  }), p.execs || p.selected || 0, " executives", /*#__PURE__*/React.createElement("span", {
    className: "tm-pickrow__dot"
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 11
  }), p.when)), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickrow__cta"
  }, /*#__PURE__*/React.createElement("span", null, "Open ", copy.label.toLowerCase()), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14
  })));
  const renderClientGroup = cg => /*#__PURE__*/React.createElement("div", {
    key: cg.clientId,
    className: "tm-pickgrp"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pickgrp__head"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickgrp__name"
  }, cg.name), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickgrp__count"
  }, cg.maps.length, " ", cg.maps.length === 1 ? 'map' : 'maps')), /*#__PURE__*/React.createElement("div", {
    className: "tm-pickgrp__rows"
  }, cg.maps.map(renderRow)));
  const total = (projects || []).length;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-pickscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pickhero"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pickhero__text"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, copy.label), /*#__PURE__*/React.createElement("h1", {
    className: "tm-pickhero__title"
  }, copy.label, " happens inside a search map"), /*#__PURE__*/React.createElement("p", {
    className: "tm-pickhero__sub"
  }, "Pick a search map below to ", copy.verb, ", or start a new one."), /*#__PURE__*/React.createElement("div", {
    className: "tm-pickhero__actions"
  }, /*#__PURE__*/React.createElement(Button, {
    onClick: onNewSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 14
  }), "New search map"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pickhero__art",
    "aria-hidden": "true"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pickhero__art-card"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pickhero__art-pill"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickhero__art-row"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickhero__art-row is-short"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickhero__art-row"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickhero__art-row is-short"
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-pickhero__art-tag"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Target",
    size: 12
  }), copy.label))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pickbar"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-pickbar__title"
  }, "Your search maps"), /*#__PURE__*/React.createElement("div", {
    className: "tm-pickbar__sub"
  }, total, " ", total === 1 ? 'map' : 'maps', " \xB7 grouped by client")), /*#__PURE__*/React.createElement("div", {
    className: "tm-search-field"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 15,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    placeholder: "Search maps or clients\u2026",
    value: q,
    onChange: e => setQ(e.target.value)
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-picklist"
  }, clientGroups.map(renderClientGroup), unassigned.length > 0 && renderClientGroup({
    clientId: '__ua',
    name: 'Unassigned',
    maps: unassigned
  }), filtered.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-pickempty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 20,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontWeight: 600
    }
  }, "No matching search maps"), /*#__PURE__*/React.createElement("div", {
    style: {
      color: 'var(--muted-foreground)',
      fontSize: 12,
      marginTop: 2
    }
  }, "Try a different search, or start a new search map."))))));
}
Object.assign(window, {
  TM_PROJECTS,
  RecentProjects,
  ProjectsPanel,
  ProjectsScreen,
  ProjectMeta,
  DraftBadge,
  groupByClient,
  getClientName,
  AllSearchesPicker
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/projects.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/search-wizard.jsx
try { (() => {
/* global React, Icon, Button, cx, TM_ACCOUNTS, TM_MASTER_SECTORS, TM_MASTER_COUNTRIES, TM_SUGGESTIONS */
// ── New Search Map Wizard — integrated into ALAC Talent Map UI kit ───────────
// Two entry points:
//   SearchWizardPage  — full-page for new users (no maps yet)
//   SearchWizardModal — modal overlay for returning users

// ── Data ─────────────────────────────────────────────────────────────────────
const SW_CLIENTS = (typeof TM_ACCOUNTS !== 'undefined' ? TM_ACCOUNTS : []).map(a => ({
  name: a.name,
  domain: a.website || '',
  type: a.type
}));
const SW_POSITIONS = ['CEO', 'CFO', 'CTO', 'COO', 'CHRO', 'CMO', 'CIO', 'VP Operations', 'VP Supply Chain', 'VP Marketing', 'VP Sales', 'Head of Digital', 'Head of Strategy', 'Managing Director', 'General Manager'];
const SW_SENIORITY = ['C-Suite', 'C-1 (SVP / EVP)', 'Director', 'VP'];
const SW_EXPERIENCE = ['5–10 years', '10–15 years', '15+ years', '20+ years'];
const SW_INDUSTRIES = typeof TM_MASTER_SECTORS !== 'undefined' ? TM_MASTER_SECTORS : ['FMCG', 'Food & Retail', 'Dairy', 'Technology'];
const SW_REVENUE = ['< $50M', '$50M–$250M', '$250M–$1B', '$1B–$5B', '$5B+'];
const SW_EMPLOYEES = ['< 100', '100–500', '500–2,000', '2,000–10,000', '10,000+'];
const SW_LOCATIONS = typeof TM_MASTER_COUNTRIES !== 'undefined' ? TM_MASTER_COUNTRIES : ['UAE', 'Saudi Arabia', 'Kuwait', 'Qatar', 'Egypt'];
const SW_EXTRA_LOCATIONS = ['United Kingdom', 'United States', 'Singapore'];
const SW_ALL_LOCATIONS = [...SW_LOCATIONS, ...SW_EXTRA_LOCATIONS];
const SW_CRITERIA = ['Listed company exposure', 'Board-level presence', 'GCC market experience', 'P&L responsibility', 'Digital transformation', 'M&A experience', 'Arabic speaker', 'Founder-led background', 'Multi-country operations', 'Regulated industry', 'IPO experience', 'Family business governance'];
const SW_PROMPT_SUGGESTIONS = ['Find a CFO for a large FMCG company in Saudi Arabia with 15+ years experience', 'CTO for Agthia Group, digital transformation background, UAE-based', 'Senior supply chain leaders across GCC dairy and food sector', 'Head of Strategy for Al Rabie, FMCG background, board experience'];

// ── AI parser (simulated) ────────────────────────────────────────────────────
function swParsePrompt(text) {
  const t = text.toLowerCase();
  let client = null;
  for (const c of SW_CLIENTS) {
    const words = c.name.toLowerCase().split(/\s+/);
    if (words.some(w => w.length > 3 && t.includes(w)) || t.includes(c.name.toLowerCase())) {
      client = c;
      break;
    }
  }
  const positions = [];
  const posMap = {
    'cfo': 'CFO',
    'cto': 'CTO',
    'ceo': 'CEO',
    'coo': 'COO',
    'chro': 'CHRO',
    'cmo': 'CMO',
    'cio': 'CIO',
    'vp operations': 'VP Operations',
    'vp supply chain': 'VP Supply Chain',
    'supply chain': 'VP Supply Chain',
    'head of digital': 'Head of Digital',
    'head of strategy': 'Head of Strategy',
    'managing director': 'Managing Director'
  };
  for (const [k, v] of Object.entries(posMap)) {
    if (t.includes(k)) positions.push(v);
  }
  const seniority = [];
  if (t.includes('c-suite') || t.includes('chief') || positions.some(p => p.startsWith('C'))) seniority.push('C-Suite');
  if (t.includes('senior') || t.includes('svp') || t.includes('evp')) seniority.push('C-1 (SVP / EVP)');
  if (t.includes('director')) seniority.push('Director');
  if (t.includes('vp') || positions.some(p => p.startsWith('VP'))) seniority.push('VP');
  if (seniority.length === 0 && positions.length > 0) seniority.push('C-Suite');
  const experience = [];
  if (t.includes('15+') || t.includes('15 years') || t.includes('20')) experience.push('15+ years');else if (t.includes('10') || t.includes('senior')) experience.push('10–15 years');
  if (experience.length === 0) experience.push('10–15 years');
  const industries = [];
  const indMap = {
    'fmcg': 'FMCG',
    'food': 'Food & Retail',
    'dairy': 'Dairy',
    'retail': 'Food & Retail',
    'supply chain': 'Logistics & Supply Chain',
    'tech': 'Technology',
    'digital': 'Technology',
    'pharma': 'Pharmaceuticals',
    'health': 'Healthcare',
    'energy': 'Energy & Utilities'
  };
  for (const [k, v] of Object.entries(indMap)) {
    if (t.includes(k) && !industries.includes(v)) industries.push(v);
  }
  if (industries.length === 0) industries.push('FMCG');
  const locations = [];
  const locMap = {
    'uae': 'UAE',
    'dubai': 'UAE',
    'abu dhabi': 'UAE',
    'saudi': 'Saudi Arabia',
    'riyadh': 'Saudi Arabia',
    'jeddah': 'Saudi Arabia',
    'kuwait': 'Kuwait',
    'qatar': 'Qatar',
    'bahrain': 'Bahrain',
    'oman': 'Oman',
    'egypt': 'Egypt',
    'cairo': 'Egypt',
    'gcc': null,
    'jordan': 'Jordan',
    'lebanon': 'Lebanon'
  };
  if (t.includes('gcc') || t.includes('gulf')) {
    locations.push('UAE', 'Saudi Arabia', 'Kuwait', 'Qatar', 'Bahrain', 'Oman');
  } else {
    for (const [k, v] of Object.entries(locMap)) {
      if (v && t.includes(k) && !locations.includes(v)) locations.push(v);
    }
  }
  if (locations.length === 0) locations.push('UAE', 'Saudi Arabia');
  const criteria = [];
  if (t.includes('board')) criteria.push('Board-level presence');
  if (t.includes('listed') || t.includes('public')) criteria.push('Listed company exposure');
  if (t.includes('gcc') || t.includes('gulf') || t.includes('middle east')) criteria.push('GCC market experience');
  if (t.includes('p&l') || t.includes('profit')) criteria.push('P&L responsibility');
  if (t.includes('digital') || t.includes('transformation')) criteria.push('Digital transformation');
  if (t.includes('m&a') || t.includes('acquisition')) criteria.push('M&A experience');
  if (t.includes('arabic')) criteria.push('Arabic speaker');
  if (criteria.length === 0) criteria.push('GCC market experience', 'P&L responsibility');
  return {
    client,
    positions,
    seniority,
    experience,
    industries,
    locations,
    criteria
  };
}

// ── Stepper ──────────────────────────────────────────────────────────────────
const SW_STEPS = [{
  num: 1,
  label: 'Describe search'
}, {
  num: 2,
  label: 'Client'
}, {
  num: 3,
  label: 'Company'
}, {
  num: 4,
  label: 'Position & experience'
}, {
  num: 5,
  label: 'Location'
}, {
  num: 6,
  label: 'Criteria'
}];
function SwStepper({
  current,
  onGoTo,
  completedSteps
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "sw-stepper"
  }, SW_STEPS.map((s, i) => {
    const completed = completedSteps.has(s.num);
    const active = s.num === current;
    const past = s.num < current;
    const clickable = completed || past;
    return /*#__PURE__*/React.createElement(React.Fragment, {
      key: s.num
    }, i > 0 && /*#__PURE__*/React.createElement("div", {
      className: "sw-stepper__line",
      style: {
        background: s.num <= current ? 'var(--primary)' : 'var(--border)'
      }
    }), /*#__PURE__*/React.createElement("button", {
      className: "sw-stepper__step",
      onClick: clickable ? () => onGoTo(s.num) : undefined,
      style: {
        cursor: clickable ? 'pointer' : 'default',
        opacity: s.num > current && !completed ? 0.4 : 1
      }
    }, /*#__PURE__*/React.createElement("span", {
      className: cx('sw-stepper__circle', active && 'is-active', (past || completed) && !active && 'is-done')
    }, (past || completed) && !active ? /*#__PURE__*/React.createElement(Icon, {
      name: "Check",
      size: 14,
      color: "#fff"
    }) : s.num), /*#__PURE__*/React.createElement("span", {
      className: cx('sw-stepper__label', active && 'is-active', past && 'is-past')
    }, s.label)));
  }));
}

// ── Toggle chip ──────────────────────────────────────────────────────────────
function SwChip({
  label,
  selected,
  onClick,
  aiSuggested
}) {
  return /*#__PURE__*/React.createElement("button", {
    className: cx('sw-chip', selected && 'is-sel'),
    onClick: onClick
  }, aiSuggested && !selected && /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 11,
    color: "var(--ai)"
  }), /*#__PURE__*/React.createElement("span", null, label));
}
function SwAddInput({
  placeholder,
  onAdd
}) {
  const [val, setVal] = React.useState('');
  const submit = () => {
    if (val.trim()) {
      onAdd(val.trim());
      setVal('');
    }
  };
  return /*#__PURE__*/React.createElement("span", {
    className: "sw-addinput"
  }, /*#__PURE__*/React.createElement("input", {
    value: val,
    onChange: e => setVal(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter') submit();
    },
    placeholder: placeholder
  }), /*#__PURE__*/React.createElement("button", {
    className: cx('sw-addinput__btn', val.trim() && 'is-ready'),
    onClick: submit
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 12,
    color: val.trim() ? '#fff' : 'var(--muted-foreground)'
  })));
}
function SwSection({
  text,
  icon,
  aiCount
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "sw-section"
  }, icon && /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 14,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "sw-section__text"
  }, text), aiCount > 0 && /*#__PURE__*/React.createElement("span", {
    className: "sw-section__ai"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 10,
    color: "var(--ai)"
  }), aiCount, " AI suggested"));
}

// ── Step 1: Describe ─────────────────────────────────────────────────────────
function SwStep1({
  prompt,
  setPrompt,
  onContinue
}) {
  const suggestions = SW_PROMPT_SUGGESTIONS;
  return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h2", {
    className: "sw-title"
  }, "Describe your search"), /*#__PURE__*/React.createElement("p", {
    className: "sw-subtitle"
  }, "Tell us what you're looking for. AI will extract the details and pre-fill the next steps."), /*#__PURE__*/React.createElement("span", {
    className: "sw-ai-badge"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 12,
    color: "var(--ai)"
  }), "AI intelligence"), /*#__PURE__*/React.createElement("textarea", {
    className: "sw-textarea",
    value: prompt,
    onChange: e => setPrompt(e.target.value),
    autoFocus: true,
    rows: 4,
    placeholder: "e.g. \"Find me a CFO for a large FMCG company in Saudi Arabia, 15+ years experience, board-level presence\"",
    onKeyDown: e => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey) && prompt.trim()) onContinue();
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-chips-row",
    style: {
      marginTop: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "sw-chips-label"
  }, "Try"), suggestions.map(s => /*#__PURE__*/React.createElement("button", {
    key: s,
    className: "sw-suggest",
    onClick: () => setPrompt(s)
  }, s))), /*#__PURE__*/React.createElement("div", {
    className: "sw-footer-row",
    style: {
      marginTop: 18
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "sw-hint"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-kbd"
  }, "\u2318"), /*#__PURE__*/React.createElement("span", {
    className: "tm-kbd"
  }, "\u21B5"), " to continue"), /*#__PURE__*/React.createElement(Button, {
    onClick: onContinue,
    disabled: !prompt.trim()
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 15
  }), "Parse & continue")));
}

// ── Step 2: Client ───────────────────────────────────────────────────────────
function SwStep2({
  selectedClient,
  setSelectedClient,
  customClients,
  setCustomClients
}) {
  const [search, setSearch] = React.useState('');
  const [dropOpen, setDropOpen] = React.useState(false);
  const [creating, setCreating] = React.useState(false);
  const [newName, setNewName] = React.useState('');
  const [newDomain, setNewDomain] = React.useState('');
  const dropRef = React.useRef(null);
  React.useEffect(() => {
    const h = e => {
      if (dropRef.current && !dropRef.current.contains(e.target)) setDropOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);
  const allClients = [...SW_CLIENTS, ...customClients];
  const filtered = allClients.filter(c => c.name.toLowerCase().includes(search.toLowerCase()) || (c.domain || '').toLowerCase().includes(search.toLowerCase()));
  const addClient = () => {
    if (newName.trim()) {
      const nc = {
        name: newName.trim(),
        domain: newDomain.trim(),
        isNew: true
      };
      setCustomClients(prev => [...prev, nc]);
      setSelectedClient(nc);
      setCreating(false);
      setNewName('');
      setNewDomain('');
      setDropOpen(false);
    }
  };
  const clientInitials = name => name.split(' ').slice(0, 2).map(w => w[0] || '').join('').toUpperCase();
  return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h2", {
    className: "sw-title"
  }, "Select client"), /*#__PURE__*/React.createElement("p", {
    className: "sw-subtitle"
  }, "Providing a client improves results. Confidential."), selectedClient && /*#__PURE__*/React.createElement("div", {
    className: "sw-client-sel"
  }, /*#__PURE__*/React.createElement("span", {
    className: "sw-client-av"
  }, clientInitials(selectedClient.name)), /*#__PURE__*/React.createElement("div", {
    className: "sw-client-info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "sw-client-name"
  }, selectedClient.name), selectedClient.domain && /*#__PURE__*/React.createElement("div", {
    className: "sw-client-domain"
  }, selectedClient.domain)), selectedClient.isNew && /*#__PURE__*/React.createElement("span", {
    className: "sw-client-new"
  }, "New"), /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13,
    color: "var(--ai)"
  }), /*#__PURE__*/React.createElement("button", {
    className: "sw-client-x",
    onClick: () => setSelectedClient(null)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 14,
    color: "var(--muted-foreground)"
  }))), /*#__PURE__*/React.createElement("div", {
    className: "sw-dropdown",
    ref: dropRef
  }, /*#__PURE__*/React.createElement("div", {
    className: "sw-dropdown__trigger",
    onClick: () => setDropOpen(true)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 14,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    value: search,
    onChange: e => {
      setSearch(e.target.value);
      setDropOpen(true);
    },
    placeholder: "Search clients\u2026",
    onClick: e => e.stopPropagation(),
    className: "sw-dropdown__input"
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronDown",
    size: 14,
    color: "var(--muted-foreground)"
  })), dropOpen && /*#__PURE__*/React.createElement("div", {
    className: "sw-dropdown__list"
  }, filtered.map(c => /*#__PURE__*/React.createElement("button", {
    key: c.name,
    className: cx('sw-dropdown__item', selectedClient?.name === c.name && 'is-sel'),
    onClick: () => {
      setSelectedClient(c);
      setDropOpen(false);
      setSearch('');
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "sw-dropdown__av"
  }, clientInitials(c.name)), /*#__PURE__*/React.createElement("div", {
    className: "sw-dropdown__info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "sw-dropdown__name"
  }, c.name), c.domain && /*#__PURE__*/React.createElement("div", {
    className: "sw-dropdown__domain"
  }, c.domain)), c.isNew && /*#__PURE__*/React.createElement("span", {
    className: "sw-client-new"
  }, "New"))), /*#__PURE__*/React.createElement("div", {
    className: "sw-dropdown__sep"
  }), !creating ? /*#__PURE__*/React.createElement("button", {
    className: "sw-dropdown__create",
    onClick: () => setCreating(true)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 14
  }), "Create new client") : /*#__PURE__*/React.createElement("div", {
    className: "sw-dropdown__form"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      fontWeight: 600,
      marginBottom: 6
    }
  }, "New client"), /*#__PURE__*/React.createElement("input", {
    className: "sw-input",
    value: newName,
    onChange: e => setNewName(e.target.value),
    placeholder: "Company name",
    autoFocus: true
  }), /*#__PURE__*/React.createElement("input", {
    className: "sw-input",
    value: newDomain,
    onChange: e => setNewDomain(e.target.value),
    placeholder: "Domain (optional)",
    onKeyDown: e => {
      if (e.key === 'Enter') addClient();
    },
    style: {
      marginTop: 6
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6,
      justifyContent: 'flex-end',
      marginTop: 8
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm",
    onClick: () => setCreating(false)
  }, "Cancel"), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: addClient,
    disabled: !newName.trim()
  }, "Add client"))))));
}

// ── Step 3: Company ──────────────────────────────────────────────────────────
function SwStep3({
  parsed,
  industries,
  setIndustries,
  revenue,
  setRevenue,
  employees,
  setEmployees
}) {
  const toggle = (arr, setArr, item) => setArr(arr.includes(item) ? arr.filter(i => i !== item) : [...arr, item]);
  const aiInd = parsed.industries || [];
  return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h2", {
    className: "sw-title"
  }, "Company"), /*#__PURE__*/React.createElement("p", {
    className: "sw-subtitle"
  }, "Define the kind of companies candidates should come from."), /*#__PURE__*/React.createElement(SwSection, {
    text: "Industry",
    icon: "Factory",
    aiCount: aiInd.length
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-chips-wrap"
  }, SW_INDUSTRIES.map(ind => /*#__PURE__*/React.createElement(SwChip, {
    key: ind,
    label: ind,
    selected: industries.includes(ind),
    aiSuggested: aiInd.includes(ind),
    onClick: () => toggle(industries, setIndustries, ind)
  })), /*#__PURE__*/React.createElement(SwAddInput, {
    placeholder: "Add industry\u2026",
    onAdd: v => setIndustries(p => [...p, v])
  })), /*#__PURE__*/React.createElement(SwSection, {
    text: "Revenue band",
    icon: "DollarSign"
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-chips-wrap"
  }, SW_REVENUE.map(r => /*#__PURE__*/React.createElement(SwChip, {
    key: r,
    label: r,
    selected: revenue.includes(r),
    onClick: () => toggle(revenue, setRevenue, r)
  }))), /*#__PURE__*/React.createElement(SwSection, {
    text: "Employee count band",
    icon: "Users"
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-chips-wrap"
  }, SW_EMPLOYEES.map(e => /*#__PURE__*/React.createElement(SwChip, {
    key: e,
    label: e,
    selected: employees.includes(e),
    onClick: () => toggle(employees, setEmployees, e)
  }))));
}

// ── Step 4: Position & Experience ────────────────────────────────────────────
function SwStep4Position({
  parsed,
  positions,
  setPositions,
  seniority,
  setSeniority,
  experience,
  setExperience
}) {
  const toggle = (arr, setArr, item) => setArr(arr.includes(item) ? arr.filter(i => i !== item) : [...arr, item]);
  const aiPos = parsed.positions || [];
  return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h2", {
    className: "sw-title"
  }, "Position & experience"), /*#__PURE__*/React.createElement("p", {
    className: "sw-subtitle"
  }, "Select the roles, seniority, and experience you're targeting."), /*#__PURE__*/React.createElement(SwSection, {
    text: "Position titles",
    icon: "Briefcase",
    aiCount: aiPos.length
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-chips-wrap"
  }, SW_POSITIONS.slice(0, 12).map(p => /*#__PURE__*/React.createElement(SwChip, {
    key: p,
    label: p,
    selected: positions.includes(p),
    aiSuggested: aiPos.includes(p),
    onClick: () => toggle(positions, setPositions, p)
  })), /*#__PURE__*/React.createElement(SwAddInput, {
    placeholder: "Add custom\u2026",
    onAdd: v => setPositions(p => [...p, v])
  })), /*#__PURE__*/React.createElement(SwSection, {
    text: "Seniority level",
    icon: "TrendingUp"
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-chips-wrap"
  }, SW_SENIORITY.map(s => /*#__PURE__*/React.createElement(SwChip, {
    key: s,
    label: s,
    selected: seniority.includes(s),
    onClick: () => toggle(seniority, setSeniority, s)
  }))), /*#__PURE__*/React.createElement(SwSection, {
    text: "Experience",
    icon: "Clock"
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-chips-wrap"
  }, SW_EXPERIENCE.map(e => /*#__PURE__*/React.createElement(SwChip, {
    key: e,
    label: e,
    selected: experience.includes(e),
    onClick: () => toggle(experience, setExperience, e)
  }))));
}

// ── Step 5: Location ─────────────────────────────────────────────────────────
function SwStep5Location({
  parsed,
  locations,
  setLocations,
  remoteOk,
  setRemoteOk
}) {
  const aiLoc = parsed.locations || [];
  const toggle = loc => setLocations(locations.includes(loc) ? locations.filter(l => l !== loc) : [...locations, loc]);
  return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h2", {
    className: "sw-title"
  }, "Locations"), /*#__PURE__*/React.createElement("p", {
    className: "sw-subtitle"
  }, "Where should candidates be based? Add locations or mark as remote-friendly."), /*#__PURE__*/React.createElement(SwSection, {
    text: "Target locations",
    icon: "MapPin",
    aiCount: aiLoc.length
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-chips-wrap"
  }, SW_ALL_LOCATIONS.map(loc => /*#__PURE__*/React.createElement(SwChip, {
    key: loc,
    label: loc,
    selected: locations.includes(loc),
    aiSuggested: aiLoc.includes(loc),
    onClick: () => toggle(loc)
  })), /*#__PURE__*/React.createElement(SwAddInput, {
    placeholder: "Add location\u2026",
    onAdd: v => setLocations(p => [...p, v])
  })), /*#__PURE__*/React.createElement("div", {
    className: cx('sw-toggle-row', remoteOk && 'is-on'),
    onClick: () => setRemoteOk(!remoteOk)
  }, /*#__PURE__*/React.createElement("div", {
    className: cx('sw-toggle-track', remoteOk && 'is-on')
  }, /*#__PURE__*/React.createElement("div", {
    className: "sw-toggle-thumb"
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 600
    }
  }, "Remote / flexible"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11.5,
      color: 'var(--muted-foreground)'
    }
  }, "Include candidates open to remote or hybrid arrangements"))));
}

// ── Step 6: Criteria ─────────────────────────────────────────────────────────
function SwStep6Criteria({
  parsed,
  criteria,
  setCriteria
}) {
  const aiCrit = parsed.criteria || [];
  const toggle = c => setCriteria(criteria.includes(c) ? criteria.filter(x => x !== c) : [...criteria, c]);
  return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h2", {
    className: "sw-title"
  }, "Search criteria"), /*#__PURE__*/React.createElement("p", {
    className: "sw-subtitle"
  }, "Add must-have or nice-to-have qualifications. AI has suggested a few based on your brief."), /*#__PURE__*/React.createElement(SwSection, {
    text: "Criteria",
    icon: "ClipboardList",
    aiCount: aiCrit.length
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-chips-wrap"
  }, SW_CRITERIA.map(c => /*#__PURE__*/React.createElement(SwChip, {
    key: c,
    label: c,
    selected: criteria.includes(c),
    aiSuggested: aiCrit.includes(c),
    onClick: () => toggle(c)
  })), /*#__PURE__*/React.createElement(SwAddInput, {
    placeholder: "Add criteria\u2026",
    onAdd: v => setCriteria(p => [...p, v])
  })), criteria.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "sw-summary"
  }, /*#__PURE__*/React.createElement("div", {
    className: "sw-summary__head"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "CheckCircle2",
    size: 14,
    color: "#059669"
  }), criteria.length, " criteria selected"), /*#__PURE__*/React.createElement("div", {
    className: "sw-summary__body"
  }, criteria.join(' · '))));
}

// ── Main Wizard ──────────────────────────────────────────────────────────────
function SearchWizard({
  onSubmit,
  isModal,
  onClose
}) {
  const [step, setStep] = React.useState(1);
  const [prompt, setPrompt] = React.useState('');
  const [parsed, setParsed] = React.useState({});
  const [completed, setCompleted] = React.useState(new Set());
  const [wizPhase, setWizPhase] = React.useState('wizard'); // wizard | loading | success

  const [selectedClient, setSelectedClient] = React.useState(null);
  const [customClients, setCustomClients] = React.useState([]);
  const [positions, setPositions] = React.useState([]);
  const [seniority, setSeniority] = React.useState([]);
  const [experience, setExperience] = React.useState([]);
  const [industries, setIndustries] = React.useState([]);
  const [revenue, setRevenue] = React.useState([]);
  const [employees, setEmployees] = React.useState([]);
  const [locations, setLocations] = React.useState([]);
  const [remoteOk, setRemoteOk] = React.useState(false);
  const [criteria, setCriteria] = React.useState([]);
  const handleParse = () => {
    if (!prompt.trim()) return;
    const p = swParsePrompt(prompt);
    setParsed(p);
    if (p.client) setSelectedClient(p.client);
    setPositions(p.positions || []);
    setSeniority(p.seniority || []);
    setExperience(p.experience || []);
    setIndustries(p.industries || []);
    setLocations(p.locations || []);
    setCriteria(p.criteria || []);
    setCompleted(new Set([1]));
    setStep(2);
  };
  const goNext = () => {
    setCompleted(prev => new Set([...prev, step]));
    if (step < 6) setStep(step + 1);
  };
  const goBack = () => {
    if (step > 1) setStep(step - 1);
  };
  const handleCreate = () => {
    setWizPhase('loading');
    setTimeout(() => {
      setWizPhase('success');
      setTimeout(() => {
        if (onSubmit) onSubmit(prompt.trim());
        if (onClose) onClose();
      }, 1200);
    }, 1600);
  };
  if (wizPhase === 'loading') {
    return /*#__PURE__*/React.createElement("div", {
      className: cx('sw-card', isModal && 'sw-card--modal')
    }, /*#__PURE__*/React.createElement("div", {
      className: "sw-loading"
    }, /*#__PURE__*/React.createElement("div", {
      className: "sw-loading__spinner"
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Loader2",
      size: 32,
      color: "var(--primary)"
    })), /*#__PURE__*/React.createElement("h2", {
      className: "sw-loading__title"
    }, "Creating your search map\u2026"), /*#__PURE__*/React.createElement("p", {
      className: "sw-loading__sub"
    }, "AI is parsing your brief and building the initial universe.")));
  }
  if (wizPhase === 'success') {
    return /*#__PURE__*/React.createElement("div", {
      className: cx('sw-card', isModal && 'sw-card--modal')
    }, /*#__PURE__*/React.createElement("div", {
      className: "sw-success"
    }, /*#__PURE__*/React.createElement("div", {
      className: "sw-success__ic"
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Check",
      size: 28,
      color: "#059669"
    })), /*#__PURE__*/React.createElement("h2", {
      className: "sw-success__title"
    }, "Search map created"), /*#__PURE__*/React.createElement("p", {
      className: "sw-success__sub"
    }, "AI is building your company universe. This usually takes 30\u201360 seconds.")));
  }
  const steps = {
    1: /*#__PURE__*/React.createElement(SwStep1, {
      prompt: prompt,
      setPrompt: setPrompt,
      onContinue: handleParse
    }),
    2: /*#__PURE__*/React.createElement(SwStep2, {
      selectedClient: selectedClient,
      setSelectedClient: setSelectedClient,
      customClients: customClients,
      setCustomClients: setCustomClients
    }),
    3: /*#__PURE__*/React.createElement(SwStep3, {
      parsed: parsed,
      industries: industries,
      setIndustries: setIndustries,
      revenue: revenue,
      setRevenue: setRevenue,
      employees: employees,
      setEmployees: setEmployees
    }),
    4: /*#__PURE__*/React.createElement(SwStep4Position, {
      parsed: parsed,
      positions: positions,
      setPositions: setPositions,
      seniority: seniority,
      setSeniority: setSeniority,
      experience: experience,
      setExperience: setExperience
    }),
    5: /*#__PURE__*/React.createElement(SwStep5Location, {
      parsed: parsed,
      locations: locations,
      setLocations: setLocations,
      remoteOk: remoteOk,
      setRemoteOk: setRemoteOk
    }),
    6: /*#__PURE__*/React.createElement(SwStep6Criteria, {
      parsed: parsed,
      criteria: criteria,
      setCriteria: setCriteria
    })
  };
  return /*#__PURE__*/React.createElement("div", {
    className: cx('sw-card', isModal && 'sw-card--modal')
  }, isModal && /*#__PURE__*/React.createElement("div", {
    className: "sw-modal-head"
  }, /*#__PURE__*/React.createElement("div", {
    className: "sw-modal-title"
  }, /*#__PURE__*/React.createElement("span", {
    className: "sw-modal-ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14,
    color: "var(--ai)"
  })), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 15,
      fontWeight: 700
    }
  }, "New search map")), /*#__PURE__*/React.createElement("button", {
    className: "tm-usidebar__ibtn",
    onClick: onClose
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 16
  }))), /*#__PURE__*/React.createElement(SwStepper, {
    current: step,
    onGoTo: setStep,
    completedSteps: completed
  }), /*#__PURE__*/React.createElement("div", {
    className: "sw-content"
  }, steps[step]), step > 1 && /*#__PURE__*/React.createElement("div", {
    className: "sw-nav"
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: goBack
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowLeft",
    size: 14
  }), "Back"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8
    }
  }, step < 6 ? /*#__PURE__*/React.createElement(Button, {
    onClick: goNext
  }, "Continue", /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14
  })) : /*#__PURE__*/React.createElement(Button, {
    onClick: handleCreate,
    style: {
      background: '#059669',
      borderColor: '#047857'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 15
  }), "Create search map"))));
}

// ── Entry points ─────────────────────────────────────────────────────────────
function SearchWizardPage({
  onSubmit
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "sw-page"
  }, /*#__PURE__*/React.createElement("div", {
    className: "sw-page__brand"
  }, /*#__PURE__*/React.createElement("span", {
    className: "sw-page__dot"
  }, "AL"), /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10.5
    }
  }, "ALAC \xB7 Global Talent Map")), /*#__PURE__*/React.createElement("h1", {
    className: "sw-page__title"
  }, "Start your first search map"), /*#__PURE__*/React.createElement("p", {
    className: "sw-page__sub"
  }, "Describe the talent you're looking for. AI will identify companies, map decision-makers, and build your universe."), /*#__PURE__*/React.createElement(SearchWizard, {
    onSubmit: onSubmit,
    isModal: false
  }));
}
function SearchWizardModal({
  onSubmit,
  onClose
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__overlay tm-fadein",
    onClick: onClose
  }, /*#__PURE__*/React.createElement("div", {
    onClick: e => e.stopPropagation(),
    style: {
      width: '100%',
      maxWidth: 720,
      margin: '0 auto'
    }
  }, /*#__PURE__*/React.createElement(SearchWizard, {
    onSubmit: onSubmit,
    isModal: true,
    onClose: onClose
  })));
}
Object.assign(window, {
  SearchWizardPage,
  SearchWizardModal
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/search-wizard.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/settings.jsx
try { (() => {
/* global React, Icon, Button, Avatar, Pill, cx, MembersSection, RolesSection, OrgGeneralSection, BillingSection */
// ── Settings: shell (left sub-nav + role gating) + Account sections ──────────

const TM_USER = {
  name: 'Yara Mansour',
  email: 'yara.mansour@alacpartners.com',
  title: 'Principal Consultant',
  initials: 'YM'
};
function Switch({
  on,
  onChange
}) {
  return /*#__PURE__*/React.createElement("button", {
    className: cx('tm-switch', on ? 'on' : 'off'),
    onClick: () => onChange(!on),
    role: "switch",
    "aria-checked": on
  }, /*#__PURE__*/React.createElement("span", {
    className: "k"
  }));
}
function FieldRow({
  label,
  hint,
  children
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-frow"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-frow__l"
  }, label, hint && /*#__PURE__*/React.createElement("small", null, hint)), /*#__PURE__*/React.createElement("div", {
    className: "tm-frow__c"
  }, children));
}
function SetCard({
  title,
  sub,
  pad,
  children
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-set-card', pad && 'pad')
  }, title && /*#__PURE__*/React.createElement("h3", {
    className: "tm-set-cardh",
    style: {
      paddingTop: pad ? 0 : 16
    }
  }, title), sub && /*#__PURE__*/React.createElement("p", {
    className: "tm-set-cardsub"
  }, sub), children);
}
function SetSeg({
  value,
  options,
  onChange
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-seg"
  }, options.map(o => /*#__PURE__*/React.createElement("button", {
    key: o.v,
    className: cx(value === o.v && 'is-on'),
    onClick: () => onChange(o.v)
  }, o.l)));
}

// ── Account → Profile ────────────────────────────────────────────────────────
function ProfileSection() {
  const [name, setName] = React.useState(TM_USER.name);
  const [title, setTitle] = React.useState(TM_USER.title);
  const [phone, setPhone] = React.useState('+971 50 123 4567');
  const [tz, setTz] = React.useState('Asia/Dubai (GST)');
  const [lang, setLang] = React.useState('English');
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-set-inner tm-fadein"
  }, /*#__PURE__*/React.createElement("h1", {
    className: "tm-set-title"
  }, "Profile"), /*#__PURE__*/React.createElement("p", {
    className: "tm-set-sub"
  }, "How you appear to your team across the Global Talent Map."), /*#__PURE__*/React.createElement(SetCard, {
    pad: true
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 18
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-avatar-lg"
  }, TM_USER.initials, /*#__PURE__*/React.createElement("span", {
    className: "tm-avatar-cam"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Camera",
    size: 14
  }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 14,
      fontWeight: 600
    }
  }, name), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      marginBottom: 8
    }
  }, title), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 13
  }), "Upload photo"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm"
  }, "Remove"))))), /*#__PURE__*/React.createElement(SetCard, null, /*#__PURE__*/React.createElement(FieldRow, {
    label: "Full name"
  }, /*#__PURE__*/React.createElement("input", {
    className: "tm-set-input",
    value: name,
    onChange: e => setName(e.target.value)
  })), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Job title"
  }, /*#__PURE__*/React.createElement("input", {
    className: "tm-set-input",
    value: title,
    onChange: e => setTitle(e.target.value)
  })), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Email",
    hint: "Used for sign-in and notifications"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13
    }
  }, TM_USER.email), /*#__PURE__*/React.createElement(Pill, {
    tone: "verified"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 10
  }), "Verified"))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Phone"
  }, /*#__PURE__*/React.createElement("input", {
    className: "tm-set-input",
    value: phone,
    onChange: e => setPhone(e.target.value)
  })), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Timezone"
  }, /*#__PURE__*/React.createElement("select", {
    className: "tm-set-select",
    value: tz,
    onChange: e => setTz(e.target.value)
  }, /*#__PURE__*/React.createElement("option", null, "Asia/Dubai (GST)"), /*#__PURE__*/React.createElement("option", null, "Asia/Riyadh (AST)"), /*#__PURE__*/React.createElement("option", null, "Africa/Cairo (EET)"), /*#__PURE__*/React.createElement("option", null, "Europe/London (GMT)"))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Language"
  }, /*#__PURE__*/React.createElement("select", {
    className: "tm-set-select",
    value: lang,
    onChange: e => setLang(e.target.value)
  }, /*#__PURE__*/React.createElement("option", null, "English"), /*#__PURE__*/React.createElement("option", null, "\u0627\u0644\u0639\u0631\u0628\u064A\u0629"), /*#__PURE__*/React.createElement("option", null, "Fran\xE7ais")))), /*#__PURE__*/React.createElement("div", {
    className: "tm-set-actions"
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost"
  }, "Cancel"), /*#__PURE__*/React.createElement(Button, null, "Save changes")));
}

// ── Account → Security ───────────────────────────────────────────────────────
function SecuritySection() {
  const [twoFA, setTwoFA] = React.useState(true);
  const sessions = [{
    ic: 'Monitor',
    dev: 'Chrome · macOS',
    loc: 'Dubai, UAE',
    when: 'Active now',
    cur: true
  }, {
    ic: 'Smartphone',
    dev: 'Safari · iPhone',
    loc: 'Dubai, UAE',
    when: '2 hours ago'
  }, {
    ic: 'Monitor',
    dev: 'Edge · Windows',
    loc: 'Riyadh, SA',
    when: '3 days ago'
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-set-inner tm-fadein"
  }, /*#__PURE__*/React.createElement("h1", {
    className: "tm-set-title"
  }, "Security"), /*#__PURE__*/React.createElement("p", {
    className: "tm-set-sub"
  }, "Protect your account and review where you're signed in."), /*#__PURE__*/React.createElement(SetCard, {
    title: "Password",
    sub: "Last changed 3 months ago.",
    pad: true
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Lock",
    size: 13
  }), "Change password")), /*#__PURE__*/React.createElement(SetCard, {
    pad: true
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'flex-start',
      gap: 16
    }
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("h3", {
    className: "tm-set-cardh"
  }, "Two-factor authentication"), /*#__PURE__*/React.createElement("p", {
    className: "tm-set-cardsub",
    style: {
      marginBottom: 0
    }
  }, "Require a one-time code at sign-in. ", twoFA ? 'Currently enabled via authenticator app.' : 'Currently disabled.')), /*#__PURE__*/React.createElement(Switch, {
    on: twoFA,
    onChange: setTwoFA
  }))), /*#__PURE__*/React.createElement(SetCard, {
    title: "Active sessions",
    sub: "Sign out of sessions you don't recognise.",
    pad: true
  }, sessions.map((s, i) => /*#__PURE__*/React.createElement("div", {
    className: "tm-listrow",
    key: i
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-listrow__ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: s.ic,
    size: 17
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 500,
      display: 'flex',
      alignItems: 'center',
      gap: 8
    }
  }, s.dev, s.cur && /*#__PURE__*/React.createElement(Pill, {
    tone: "verified"
  }, "This device")), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, s.loc, " \xB7 ", s.when)), !s.cur && /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm"
  }, "Revoke"))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 14
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "LogOut",
    size: 13
  }), "Sign out all other sessions"))));
}

// ── Account → Notifications ──────────────────────────────────────────────────
function NotificationsSection() {
  const EVENTS = [['New executive matches', 'When AI surfaces new executives in your projects'], ['Project shared with me', 'When a teammate gives you access'], ['Enrichment complete', 'When a bulk enrichment run finishes'], ['Weekly mapping digest', 'A summary of activity across your projects'], ['Comments & mentions', 'When someone @mentions you on a profile']];
  const [state, setState] = React.useState(EVENTS.map(() => ({
    email: true,
    app: true
  })));
  const set = (i, k, v) => setState(s => s.map((r, j) => j === i ? {
    ...r,
    [k]: v
  } : r));
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-set-inner tm-fadein"
  }, /*#__PURE__*/React.createElement("h1", {
    className: "tm-set-title"
  }, "Notifications"), /*#__PURE__*/React.createElement("p", {
    className: "tm-set-sub"
  }, "Choose how you want to be notified. Applies to this account only."), /*#__PURE__*/React.createElement(SetCard, {
    pad: true
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-notif-head"
  }, /*#__PURE__*/React.createElement("span", null, "Event"), /*#__PURE__*/React.createElement("span", null, "Email"), /*#__PURE__*/React.createElement("span", null, "In-app")), EVENTS.map(([t, d], i) => /*#__PURE__*/React.createElement("div", {
    className: "tm-notif-row",
    key: t
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontWeight: 500
    }
  }, t), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, d)), /*#__PURE__*/React.createElement("div", {
    className: "tm-cc"
  }, /*#__PURE__*/React.createElement(Switch, {
    on: state[i].email,
    onChange: v => set(i, 'email', v)
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-cc"
  }, /*#__PURE__*/React.createElement(Switch, {
    on: state[i].app,
    onChange: v => set(i, 'app', v)
  }))))));
}

// ── Account → Preferences ────────────────────────────────────────────────────
function PreferencesSection({
  theme,
  onTheme
}) {
  const [density, setDensity] = React.useState('comfortable');
  const [view, setView] = React.useState('map');
  const [metric, setMetric] = React.useState('revenue');
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-set-inner tm-fadein"
  }, /*#__PURE__*/React.createElement("h1", {
    className: "tm-set-title"
  }, "Preferences"), /*#__PURE__*/React.createElement("p", {
    className: "tm-set-sub"
  }, "Tune how the Global Talent Map looks and opens for you."), /*#__PURE__*/React.createElement(SetCard, null, /*#__PURE__*/React.createElement(FieldRow, {
    label: "Appearance",
    hint: "Light, dark, or follow your system"
  }, /*#__PURE__*/React.createElement(SetSeg, {
    value: theme,
    onChange: v => onTheme(v),
    options: [{
      v: 'light',
      l: 'Light'
    }, {
      v: 'dark',
      l: 'Dark'
    }]
  })), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Density"
  }, /*#__PURE__*/React.createElement(SetSeg, {
    value: density,
    onChange: setDensity,
    options: [{
      v: 'comfortable',
      l: 'Comfortable'
    }, {
      v: 'compact',
      l: 'Compact'
    }]
  })), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Default view",
    hint: "Where a project opens"
  }, /*#__PURE__*/React.createElement("select", {
    className: "tm-set-select",
    value: view,
    onChange: e => setView(e.target.value)
  }, /*#__PURE__*/React.createElement("option", {
    value: "map"
  }, "Map"), /*#__PURE__*/React.createElement("option", {
    value: "table"
  }, "Table"), /*#__PURE__*/React.createElement("option", {
    value: "dashboard"
  }, "Dashboard"))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Map scaling",
    hint: "Size company nodes by"
  }, /*#__PURE__*/React.createElement(SetSeg, {
    value: metric,
    onChange: setMetric,
    options: [{
      v: 'revenue',
      l: 'Revenue'
    }, {
      v: 'employees',
      l: 'Employees'
    }]
  }))));
}

// ── Shell ────────────────────────────────────────────────────────────────────
function SettingsScreen({
  theme,
  onTheme,
  onSignOut
}) {
  const [section, setSection] = React.useState('profile');
  const [role, setRole] = React.useState('admin'); // demo: member | admin
  const isAdmin = role === 'admin';
  React.useEffect(() => {
    if (!isAdmin && ['org', 'members', 'roles', 'billing'].includes(section)) setSection('profile');
  }, [isAdmin, section]);
  const account = [{
    id: 'profile',
    icon: 'User',
    label: 'Profile'
  }, {
    id: 'security',
    icon: 'ShieldCheck',
    label: 'Security'
  }, {
    id: 'notifications',
    icon: 'Bell',
    label: 'Notifications'
  }, {
    id: 'preferences',
    icon: 'SlidersHorizontal',
    label: 'Preferences'
  }];
  const org = [{
    id: 'org',
    icon: 'Building2',
    label: 'General'
  }, {
    id: 'members',
    icon: 'Users',
    label: 'Members'
  }, {
    id: 'roles',
    icon: 'KeyRound',
    label: 'Roles & permissions'
  }, {
    id: 'billing',
    icon: 'CreditCard',
    label: 'Plan & billing'
  }];
  const Link = s => /*#__PURE__*/React.createElement("button", {
    key: s.id,
    className: cx('tm-set-link', section === s.id && 'is-on'),
    onClick: () => setSection(s.id)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: s.icon,
    size: 16
  }), s.label);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-settings"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-set-nav"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-set-nav__user"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: TM_USER.name,
    tone: "neutral",
    size: 36
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-set-nav__name"
  }, TM_USER.name), /*#__PURE__*/React.createElement("div", {
    className: "tm-set-nav__email"
  }, TM_USER.email))), /*#__PURE__*/React.createElement("div", {
    className: "tm-set-group"
  }, "Account"), account.map(Link), isAdmin && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-set-group"
  }, "Organization"), org.map(Link)), /*#__PURE__*/React.createElement("div", {
    className: "tm-set-rolebar"
  }, /*#__PURE__*/React.createElement("span", {
    className: "lbl"
  }, "Demo role"), /*#__PURE__*/React.createElement(SetSeg, {
    value: role,
    onChange: setRole,
    options: [{
      v: 'member',
      l: 'Member'
    }, {
      v: 'admin',
      l: 'Admin'
    }]
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-set-link",
    style: {
      marginTop: 10
    },
    onClick: onSignOut
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "LogOut",
    size: 16
  }), "Sign out"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-set-main"
  }, section === 'profile' && /*#__PURE__*/React.createElement(ProfileSection, null), section === 'security' && /*#__PURE__*/React.createElement(SecuritySection, null), section === 'notifications' && /*#__PURE__*/React.createElement(NotificationsSection, null), section === 'preferences' && /*#__PURE__*/React.createElement(PreferencesSection, {
    theme: theme,
    onTheme: onTheme
  }), section === 'org' && /*#__PURE__*/React.createElement(OrgGeneralSection, null), section === 'members' && /*#__PURE__*/React.createElement(MembersSection, null), section === 'roles' && /*#__PURE__*/React.createElement(RolesSection, null), section === 'billing' && /*#__PURE__*/React.createElement(BillingSection, null)));
}
Object.assign(window, {
  SettingsScreen,
  Switch,
  FieldRow,
  SetCard,
  SetSeg,
  TM_USER
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/settings.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/settings_org.jsx
try { (() => {
/* global React, Icon, Button, Avatar, Pill, cx, Switch, FieldRow, SetCard, SetSeg */
// ── Settings: Organization (admin) sections — General, Members, Roles, Billing ─

const ROLES = ['Owner', 'Admin', 'Member', 'Viewer'];
const TM_MEMBERS = [{
  name: 'Yara Mansour',
  email: 'yara.mansour@alacpartners.com',
  role: 'Owner',
  status: 'Active',
  seen: 'Active now',
  you: true
}, {
  name: 'Omar Haddad',
  email: 'omar.haddad@alacpartners.com',
  role: 'Admin',
  status: 'Active',
  seen: '2 hours ago'
}, {
  name: 'Layla Aziz',
  email: 'layla.aziz@alacpartners.com',
  role: 'Member',
  status: 'Active',
  seen: 'Yesterday'
}, {
  name: 'Tariq Saleh',
  email: 'tariq.saleh@alacpartners.com',
  role: 'Member',
  status: 'Active',
  seen: '4 days ago'
}, {
  name: 'Nadia Fawzy',
  email: 'nadia.fawzy@alacpartners.com',
  role: 'Viewer',
  status: 'Active',
  seen: '1 week ago'
}];
const TM_INVITES = [{
  email: 'sami.khan@alacpartners.com',
  role: 'Member'
}, {
  email: 'rana.eid@alacpartners.com',
  role: 'Viewer'
}];
function statusTone(s) {
  return s === 'Active' ? 'verified' : 'inferred';
}
function MembersSection() {
  const [members, setMembers] = React.useState(TM_MEMBERS);
  const [invites] = React.useState(TM_INVITES);
  const [inviteEmail, setInviteEmail] = React.useState('');
  const [inviteRole, setInviteRole] = React.useState('Member');
  const used = members.length + invites.length;
  const changeRole = (i, role) => setMembers(m => m.map((x, j) => j === i ? {
    ...x,
    role
  } : x));
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-set-inner tm-fadein",
    style: {
      maxWidth: 820
    }
  }, /*#__PURE__*/React.createElement("h1", {
    className: "tm-set-title"
  }, "Members"), /*#__PURE__*/React.createElement("p", {
    className: "tm-set-sub"
  }, "Invite teammates and manage who can access the workspace."), /*#__PURE__*/React.createElement("div", {
    className: "tm-seats"
  }, /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("strong", {
    style: {
      color: 'var(--foreground)'
    }
  }, used), " of 10 seats used"), /*#__PURE__*/React.createElement("span", {
    className: "tm-seats__bar"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: used / 10 * 100 + '%'
    }
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-link"
  }, "Need more seats?")), /*#__PURE__*/React.createElement("div", {
    className: "tm-invite"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserPlus",
    size: 16,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    className: "tm-set-input",
    style: {
      flex: 1,
      maxWidth: 'none'
    },
    placeholder: "name@alacpartners.com",
    value: inviteEmail,
    onChange: e => setInviteEmail(e.target.value)
  }), /*#__PURE__*/React.createElement("select", {
    className: "tm-set-select",
    value: inviteRole,
    onChange: e => setInviteRole(e.target.value)
  }, ROLES.filter(r => r !== 'Owner').map(r => /*#__PURE__*/React.createElement("option", {
    key: r
  }, r))), /*#__PURE__*/React.createElement(Button, {
    disabled: !inviteEmail.includes('@')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Send",
    size: 14
  }), "Send invite")), invites.length > 0 && /*#__PURE__*/React.createElement(SetCard, {
    title: `Pending invites (${invites.length})`,
    pad: true
  }, invites.map(inv => /*#__PURE__*/React.createElement("div", {
    className: "tm-listrow",
    key: inv.email
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-listrow__ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Mail",
    size: 16
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 500
    }
  }, inv.email), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, "Invited as ", inv.role, " \xB7 awaiting acceptance")), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm"
  }, "Resend"), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm"
  }, "Revoke")))), /*#__PURE__*/React.createElement(SetCard, {
    pad: true
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-mtable__head"
  }, /*#__PURE__*/React.createElement("span", null, "Member"), /*#__PURE__*/React.createElement("span", null, "Role"), /*#__PURE__*/React.createElement("span", null, "Status"), /*#__PURE__*/React.createElement("span", null, "Last active"), /*#__PURE__*/React.createElement("span", null)), members.map((m, i) => /*#__PURE__*/React.createElement("div", {
    className: "tm-mrow",
    key: m.email
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-member"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: m.name,
    tone: m.you ? 'primary' : 'neutral',
    size: 34
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-member__nm"
  }, m.name, m.you && /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontWeight: 400
    }
  }, " \xB7 You")), /*#__PURE__*/React.createElement("div", {
    className: "tm-member__em"
  }, m.email))), /*#__PURE__*/React.createElement("span", null, m.role === 'Owner' ? /*#__PURE__*/React.createElement(Pill, {
    tone: "neutral",
    style: {
      fontWeight: 600
    }
  }, "Owner") : /*#__PURE__*/React.createElement("select", {
    className: "tm-set-select",
    style: {
      minWidth: 120,
      padding: '5px 8px'
    },
    value: m.role,
    onChange: e => changeRole(i, e.target.value)
  }, ROLES.filter(r => r !== 'Owner').map(r => /*#__PURE__*/React.createElement("option", {
    key: r
  }, r)))), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(Pill, {
    tone: statusTone(m.status)
  }, m.status)), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)'
    }
  }, m.seen), m.you ? /*#__PURE__*/React.createElement("span", null) : /*#__PURE__*/React.createElement("button", {
    className: "tm-proj-del"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Trash2",
    size: 14
  }))))));
}
function RolesSection() {
  const PERMS = [['View projects & map', [1, 1, 1, 1]], ['Create & run searches', [1, 1, 1, 0]], ['Edit company universe', [1, 1, 1, 0]], ['Export data to Excel', [1, 1, 1, 0]], ['Invite & manage members', [1, 1, 0, 0]], ['Manage roles & permissions', [1, 1, 0, 0]], ['Delete projects', [1, 1, 0, 0]], ['Manage plan & billing', [1, 0, 0, 0]]];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-set-inner tm-fadein",
    style: {
      maxWidth: 820
    }
  }, /*#__PURE__*/React.createElement("h1", {
    className: "tm-set-title"
  }, "Roles & permissions"), /*#__PURE__*/React.createElement("p", {
    className: "tm-set-sub"
  }, "What each role can do. Assign roles per member on the Members tab."), /*#__PURE__*/React.createElement(SetCard, {
    pad: true
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-perm-head"
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      fontWeight: 600,
      textTransform: 'uppercase',
      letterSpacing: '.05em',
      color: 'var(--muted-foreground)'
    }
  }, "Permission"), ['Owner', 'Admin', 'Member', 'Viewer'].map(r => /*#__PURE__*/React.createElement("span", {
    className: "role",
    key: r
  }, r))), PERMS.map(([label, cells]) => /*#__PURE__*/React.createElement("div", {
    className: "tm-perm-row",
    key: label
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontWeight: 500
    }
  }, label), cells.map((c, i) => /*#__PURE__*/React.createElement("span", {
    className: "cell",
    key: i
  }, c ? /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 16,
    color: "var(--success)"
  }) : /*#__PURE__*/React.createElement("span", {
    style: {
      width: 12,
      height: 2,
      borderRadius: 2,
      background: 'var(--border)'
    }
  })))))));
}
function OrgGeneralSection() {
  const [name, setName] = React.useState('ALAC Partners');
  const [domain] = React.useState('alacpartners.com');
  const [defRole, setDefRole] = React.useState('Member');
  const [require2FA, setRequire2FA] = React.useState(false);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-set-inner tm-fadein"
  }, /*#__PURE__*/React.createElement("h1", {
    className: "tm-set-title"
  }, "Organization"), /*#__PURE__*/React.createElement("p", {
    className: "tm-set-sub"
  }, "Workspace-wide settings for ALAC Partners."), /*#__PURE__*/React.createElement(SetCard, {
    pad: true
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 18
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: 64,
      height: 64,
      borderRadius: 14,
      background: 'var(--ink)',
      color: '#fff',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontSize: 20,
      fontWeight: 700,
      letterSpacing: '.04em',
      flexShrink: 0
    }
  }, "AL"), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 14,
      fontWeight: 600,
      marginBottom: 6
    }
  }, "Workspace logo"), /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Upload",
    size: 13
  }), "Upload logo")))), /*#__PURE__*/React.createElement(SetCard, null, /*#__PURE__*/React.createElement(FieldRow, {
    label: "Organization name"
  }, /*#__PURE__*/React.createElement("input", {
    className: "tm-set-input",
    value: name,
    onChange: e => setName(e.target.value)
  })), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Primary domain",
    hint: "Used to auto-suggest invites"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13
    }
  }, domain), /*#__PURE__*/React.createElement(Pill, {
    tone: "verified"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 10
  }), "Verified"))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Default role",
    hint: "Assigned to newly invited members"
  }, /*#__PURE__*/React.createElement("select", {
    className: "tm-set-select",
    value: defRole,
    onChange: e => setDefRole(e.target.value)
  }, /*#__PURE__*/React.createElement("option", null, "Member"), /*#__PURE__*/React.createElement("option", null, "Viewer"), /*#__PURE__*/React.createElement("option", null, "Admin"))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Require 2FA",
    hint: "Enforce two-factor for all members"
  }, /*#__PURE__*/React.createElement(Switch, {
    on: require2FA,
    onChange: setRequire2FA
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-set-actions"
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "ghost"
  }, "Cancel"), /*#__PURE__*/React.createElement(Button, null, "Save changes")));
}
function BillingSection() {
  const invoices = [{
    d: 'May 1, 2026',
    a: '$392.00',
    s: 'Paid'
  }, {
    d: 'Apr 1, 2026',
    a: '$392.00',
    s: 'Paid'
  }, {
    d: 'Mar 1, 2026',
    a: '$343.00',
    s: 'Paid'
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-set-inner tm-fadein"
  }, /*#__PURE__*/React.createElement("h1", {
    className: "tm-set-title"
  }, "Plan & billing"), /*#__PURE__*/React.createElement("p", {
    className: "tm-set-sub"
  }, "Manage your subscription and payment details."), /*#__PURE__*/React.createElement(SetCard, {
    pad: true
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'flex-start',
      gap: 16
    }
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("span", {
    className: "tm-pill-plan"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 12
  }), "Team"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 22,
      fontWeight: 700,
      margin: '12px 0 2px'
    }
  }, "$49 ", /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      fontWeight: 400,
      color: 'var(--muted-foreground)'
    }
  }, "/ seat / month")), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)'
    }
  }, "8 of 10 seats used \xB7 renews Jun 1, 2026")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm"
  }, "Manage seats"), /*#__PURE__*/React.createElement(Button, {
    size: "sm"
  }, "Upgrade plan")))), /*#__PURE__*/React.createElement(SetCard, {
    title: "Payment method",
    pad: true
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-listrow",
    style: {
      borderBottom: 'none'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-listrow__ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "CreditCard",
    size: 16
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 500
    }
  }, "Visa ending 4242"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, "Expires 08 / 27")), /*#__PURE__*/React.createElement(Button, {
    variant: "ghost",
    size: "sm"
  }, "Update"))), /*#__PURE__*/React.createElement(SetCard, {
    title: "Invoices",
    pad: true
  }, invoices.map(iv => /*#__PURE__*/React.createElement("div", {
    className: "tm-invoice",
    key: iv.d
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontWeight: 500
    }
  }, iv.d), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 12
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-mono"
  }, iv.a), /*#__PURE__*/React.createElement(Pill, {
    tone: "verified"
  }, iv.s), /*#__PURE__*/React.createElement("button", {
    className: "tm-link"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 13
  })))))));
}
Object.assign(window, {
  MembersSection,
  RolesSection,
  OrgGeneralSection,
  BillingSection
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/settings_org.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/sourcing-card.jsx
try { (() => {
/* global React, Icon, Avatar, Pill, cx, initials */
// ── Sourcing Company Card ─────────────────────────────────────────────────
// One card per company, with a list of key executives nested inside.
// Inspired by Pin's candidate card — but our card is the COMPANY and the
// executives nested underneath are the candidates ALAC can recruit (for a
// CEO mandate: VP / C-1 level inside that company, etc.).

function pct(c) {
  return Math.round(c);
}
function MatchScore({
  score
}) {
  const tone = score >= 80 ? 'high' : score >= 65 ? 'mid' : 'low';
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-src-card__score', `is-${tone}`)
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__score-n"
  }, pct(score)), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__score-l"
  }, "Fit"));
}
function CriteriaLine({
  label,
  value,
  met,
  preferred
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-src-card__crit', met ? 'is-met' : 'is-miss')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: met ? 'Check' : 'Minus',
    size: 11,
    color: met ? 'var(--success)' : 'var(--muted-foreground)'
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__crit-label"
  }, label), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__crit-sep"
  }, "\u2014"), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__crit-value"
  }, value), preferred && /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__crit-pref"
  }, "preferred"));
}
function ExecRow({
  e,
  isPrimaryRole,
  onSelectExec
}) {
  const stop = fn => ev => {
    ev.stopPropagation();
    fn && fn();
  };
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-src-card__exec', isPrimaryRole && 'is-primary'),
    role: onSelectExec ? 'button' : undefined,
    onClick: onSelectExec ? stop(() => onSelectExec(e)) : undefined,
    title: onSelectExec ? 'Open executive profile' : undefined
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: e.name,
    tone: "primary",
    size: 26
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__exec-info"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__exec-name"
  }, e.name, e.verified && /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__verified",
    title: "Verified"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "BadgeCheck",
    size: 11,
    color: "#059669"
  })), isPrimaryRole && /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__exec-tag"
  }, "target role")), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__exec-meta"
  }, /*#__PURE__*/React.createElement("span", null, e.title), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__exec-dot"
  }, "\xB7"), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__exec-level"
  }, e.level), e.tenure && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__exec-dot"
  }, "\xB7"), /*#__PURE__*/React.createElement("span", null, e.tenure)))), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-card__exec-btn",
    title: "Open executive profile",
    onClick: stop(() => onSelectExec && onSelectExec(e))
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronRight",
    size: 13
  })));
}
function tenureForExec(level, i) {
  // mock tenure derivable from level + index
  const seed = [3, 5, 7, 4, 2, 6, 8, 9][(i + level.length) % 8];
  return seed + 'yr · in role ' + (seed >= 6 ? '4yr' : '2yr');
}
function SourcingCompanyCard({
  c,
  criteria,
  // [{ key, label, getValue: (c)=>string, met: (c)=>bool, preferred?:bool }]
  targetRole,
  // 'CFO' | 'CEO' | ...
  primaryRoleKeywords,
  // ['CFO', 'Chief Financial']
  status,
  // 'universe' | 'shortlisted' | 'approved' | 'declined' | 'manualAdded'
  isOpen,
  // currently selected in the flyover
  onSelect,
  // (c) => open flyover on the company
  onSelectExec,
  // (c, exec) => open flyover on an executive
  onShortlist,
  onApprove,
  onDecline,
  onComment,
  onAdd,
  comments = 0
}) {
  // Card body is one big click target; action buttons stopPropagation
  // so they continue to function as quick actions.
  const stop = fn => ev => {
    ev.stopPropagation();
    fn && fn();
  };
  const [execsExpanded, setExecsExpanded] = React.useState(false);

  // Score executives by primary role match → puts target role first
  const execs = React.useMemo(() => {
    const arr = c.execs.map((e, i) => ({
      ...e,
      tenure: tenureForExec(e.level, i),
      isPrimaryRole: primaryRoleKeywords.some(k => e.title.toLowerCase().includes(k.toLowerCase()))
    }));
    arr.sort((a, b) => Number(b.isPrimaryRole) - Number(a.isPrimaryRole));
    return arr;
  }, [c.execs, primaryRoleKeywords]);
  const visibleExecs = execsExpanded ? execs : execs.slice(0, 4);
  const moreCount = execs.length - visibleExecs.length;
  const criteriaResults = React.useMemo(() => criteria.map(cr => ({
    ...cr,
    value: cr.getValue(c),
    met: cr.met(c)
  })), [c, criteria]);
  const metCount = criteriaResults.filter(r => r.met).length;
  const totalCount = criteriaResults.length;
  const isDeclined = status === 'declined';
  const isApproved = status === 'approved';
  const isShortlisted = status === 'shortlisted';
  return /*#__PURE__*/React.createElement("article", {
    className: cx('tm-src-card', isDeclined && 'is-declined', isApproved && 'is-approved', isOpen && 'is-open'),
    role: onSelect ? 'button' : undefined,
    onClick: onSelect ? () => onSelect(c) : undefined
  }, /*#__PURE__*/React.createElement("header", {
    className: "tm-src-card__head"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__id"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: c.name,
    shape: "square",
    size: 44,
    tone: "neutral"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__id-info"
  }, /*#__PURE__*/React.createElement("h3", {
    className: "tm-src-card__name"
  }, c.name), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__loc"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, c.city, ", ", c.country), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__dot"
  }, "\xB7"), /*#__PURE__*/React.createElement("span", null, c.sector), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-card__link",
    title: "View website"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ExternalLink",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "website")), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-card__link",
    title: "LinkedIn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Linkedin",
    size: 11
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__badges"
  }, /*#__PURE__*/React.createElement(Pill, {
    tone: c.relevance === 'Direct' ? 'direct' : c.relevance === 'Adjacent' ? 'adjacent' : 'inferred'
  }, c.relevance), isApproved && /*#__PURE__*/React.createElement(Pill, {
    tone: "verified"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 9
  }), " Approved"), isShortlisted && /*#__PURE__*/React.createElement(Pill, {
    tone: "ai"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Star",
    size: 9
  }), " Shortlisted"), isDeclined && /*#__PURE__*/React.createElement(Pill, {
    tone: "danger"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 9
  }), " Declined")))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__act"
  }, /*#__PURE__*/React.createElement(MatchScore, {
    score: c.confidence
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-card__act-btn",
    onClick: stop(onComment),
    title: "Add comment"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MessageSquare",
    size: 15
  }), /*#__PURE__*/React.createElement("span", null, "Comment"), comments > 0 && /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__act-n"
  }, comments)), /*#__PURE__*/React.createElement("button", {
    className: cx('tm-src-card__act-btn', isApproved && 'is-on'),
    onClick: stop(onApprove),
    title: "Add this company to the universe"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 15
  }), /*#__PURE__*/React.createElement("span", null, isApproved ? 'In universe' : 'Add to universe')), /*#__PURE__*/React.createElement("button", {
    className: cx('tm-src-card__act-btn', 'is-shortlist', isShortlisted && 'is-on'),
    onClick: stop(onShortlist),
    title: "Shortlist this company"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Star",
    size: 15
  }), /*#__PURE__*/React.createElement("span", null, isShortlisted ? 'Shortlisted' : 'Shortlist')), /*#__PURE__*/React.createElement("button", {
    className: cx('tm-src-card__act-btn', 'is-decline', isDeclined && 'is-on'),
    onClick: stop(onDecline),
    title: "Decline this company"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "XCircle",
    size: 15
  }), /*#__PURE__*/React.createElement("span", null, isDeclined ? 'Declined' : 'Decline')), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-card__act-more",
    title: "More",
    onClick: stop()
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MoreVertical",
    size: 15
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__grid"
  }, /*#__PURE__*/React.createElement("section", {
    className: "tm-src-card__section"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__sec-head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__sec-label"
  }, "Criteria"), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__sec-sub"
  }, /*#__PURE__*/React.createElement("b", {
    style: {
      color: metCount === totalCount ? 'var(--success)' : 'var(--foreground)'
    }
  }, metCount), /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)'
    }
  }, " of ", totalCount, " met"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__crit-list"
  }, criteriaResults.map(r => /*#__PURE__*/React.createElement(CriteriaLine, {
    key: r.key,
    label: r.label,
    value: r.value,
    met: r.met,
    preferred: r.preferred
  })))), /*#__PURE__*/React.createElement("section", {
    className: "tm-src-card__section"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__sec-head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__sec-label"
  }, "Scale snapshot")), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__scale"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__scale-row"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "DollarSign",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__scale-l"
  }, "Revenue"), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__scale-v tm-mono"
  }, c.revenue)), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__scale-row"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__scale-l"
  }, "Employees"), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__scale-v tm-mono"
  }, c.employees)), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__scale-row"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Globe",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__scale-l"
  }, "Region"), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__scale-v"
  }, c.country, " (GCC)")), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__scale-row"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__scale-l"
  }, "Sector"), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__scale-v"
  }, c.sector))))), /*#__PURE__*/React.createElement("section", {
    className: "tm-src-card__execs"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__sec-head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__sec-label"
  }, "Key executives", targetRole && /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__sec-target"
  }, "\xB7 target ", /*#__PURE__*/React.createElement("b", null, targetRole))), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-card__sec-sub"
  }, /*#__PURE__*/React.createElement("b", null, execs.length), /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)'
    }
  }, " mapped"), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-card__enrich",
    title: "Enrich more executives",
    onClick: stop()
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Zap",
    size: 11
  }), "Enrich"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-card__exec-list"
  }, visibleExecs.map(e => /*#__PURE__*/React.createElement(ExecRow, {
    key: e.id,
    e: e,
    isPrimaryRole: e.isPrimaryRole,
    onSelectExec: onSelectExec ? exec => onSelectExec(c, exec) : undefined
  }))), moreCount > 0 && /*#__PURE__*/React.createElement("button", {
    className: "tm-src-card__exec-more",
    onClick: stop(() => setExecsExpanded(true))
  }, "Show ", moreCount, " more \xB7 including N-2 / VP level"), execsExpanded && execs.length > 4 && /*#__PURE__*/React.createElement("button", {
    className: "tm-src-card__exec-more",
    onClick: stop(() => setExecsExpanded(false))
  }, "Collapse")), /*#__PURE__*/React.createElement("p", {
    className: "tm-src-card__summary"
  }, c.summary));
}
Object.assign(window, {
  SourcingCompanyCard
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/sourcing-card.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/sourcing-criteria.jsx
try { (() => {
/* global React, Icon, Button, cx */
// ── Sourcing Criteria — summary bar (compact) + editor (modal) ─────────────
// Concept from Pin's "+12 criteria" chip + criteria editor modal.
// Adapted for company-first search: position, sectors, HQ, revenue, employees,
// ownership, plus AI evaluation questions.

/* ── Summary bar (top of page) ────────────────────────────────────────────── */
function SourcingCriteriaBar({
  criteria,
  extraCount,
  onEdit,
  onTalentReport
}) {
  const chips = criteria.slice(0, 5);
  const more = extraCount > 0 ? extraCount : Math.max(0, criteria.length - chips.length);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-src-crbar"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-src-crbar__field",
    onClick: onEdit
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-crbar__spark"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13,
    color: "var(--ai)"
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-crbar__chips"
  }, chips.map((cr, i) => /*#__PURE__*/React.createElement(React.Fragment, {
    key: cr.key
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-crbar__chip"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-crbar__chip-l"
  }, cr.label), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-crbar__chip-v"
  }, cr.short)), i < chips.length - 1 && /*#__PURE__*/React.createElement("span", {
    className: "tm-src-crbar__sep"
  }, "\xB7"))), more > 0 && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("span", {
    className: "tm-src-crbar__sep"
  }, "\xB7"), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-crbar__more"
  }, "+", more, " criteria"))), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-crbar__edit-hint"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Pencil",
    size: 11
  }), "Edit")), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-crbar__report",
    onClick: onTalentReport
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "FileText",
    size: 14
  }), /*#__PURE__*/React.createElement("span", null, "Universe report")));
}

/* ── Helpers ──────────────────────────────────────────────────────────────── */
function Chip({
  on,
  label,
  onClick,
  removable,
  pinned
}) {
  return /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: cx('tm-src-cred__chip', on && 'is-on', pinned && 'is-pinned'),
    onClick: onClick
  }, pinned && /*#__PURE__*/React.createElement(Icon, {
    name: "Pin",
    size: 9,
    className: "tm-src-cred__chip-pin"
  }), /*#__PURE__*/React.createElement("span", null, label), removable && on && /*#__PURE__*/React.createElement("span", {
    className: "tm-src-cred__chip-x"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 9
  })));
}
function FieldRow({
  label,
  children,
  optional
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__row"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__row-l"
  }, /*#__PURE__*/React.createElement("span", null, label), optional && /*#__PURE__*/React.createElement("span", {
    className: "tm-src-cred__opt"
  }, "optional")), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__row-r"
  }, children));
}

/* ── Editor (full overlay modal) ──────────────────────────────────────────── */
function SourcingCriteriaEditor({
  open,
  onClose,
  onApply,
  initial,
  // { position, sectors, countries, revenue, employees, ownership, founderLed, questions }
  options // { sectors, countries, revenue, employees }
}) {
  const [state, setState] = React.useState(initial);
  const [aiPrompt, setAiPrompt] = React.useState('');
  React.useEffect(() => {
    if (open) setState(initial);
  }, [open, initial]);
  if (!open) return null;
  const toggleArr = (key, value) => {
    setState(s => {
      const cur = s[key] || [];
      return {
        ...s,
        [key]: cur.includes(value) ? cur.filter(v => v !== value) : [...cur, value]
      };
    });
  };
  const setField = (key, value) => setState(s => ({
    ...s,
    [key]: value
  }));
  const setQuestion = (i, value) => {
    setState(s => {
      const qs = [...(s.questions || [])];
      qs[i] = value;
      return {
        ...s,
        questions: qs
      };
    });
  };
  const addQuestion = () => setField('questions', [...(state.questions || []), '']);
  const removeQuestion = i => setField('questions', state.questions.filter((_, j) => j !== i));
  const clearAll = () => setState({
    position: '',
    sectors: [],
    countries: [],
    revenue: [],
    employees: [],
    ownership: 'any',
    founderLed: 'any',
    questions: []
  });
  const activeCount = (state.position ? 1 : 0) + (state.sectors?.length || 0) + (state.countries?.length || 0) + (state.revenue?.length || 0) + (state.employees?.length || 0) + (state.ownership !== 'any' ? 1 : 0) + (state.founderLed !== 'any' ? 1 : 0) + (state.questions?.filter(q => q.trim()).length || 0);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__scrim",
    onClick: onClose
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred",
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__head"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__head-l"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "SlidersHorizontal",
    size: 16,
    color: "var(--primary)"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__title"
  }, "Search criteria"), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__sub"
  }, "Tune what counts as a match. ALAC re-runs sourcing instantly."))), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-cred__close",
    onClick: onClose
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 16
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__ai"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14,
    color: "var(--ai)"
  }), /*#__PURE__*/React.createElement("input", {
    className: "tm-src-cred__ai-input",
    placeholder: "Ask ALAC to add or change any criteria for you\u2026",
    value: aiPrompt,
    onChange: e => setAiPrompt(e.target.value),
    onKeyDown: e => {
      if (e.key === 'Enter' && aiPrompt.trim()) {
        // mock: parse a couple of common hints
        const q = aiPrompt.toLowerCase();
        if (q.includes('public')) setField('ownership', 'public');
        if (q.includes('private')) setField('ownership', 'private');
        if (q.includes('founder')) setField('founderLed', 'preferred');
        if (q.includes('uae')) toggleArr('countries', 'UAE');
        setAiPrompt('');
      }
    }
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-cred__ai-hist",
    title: "Recent prompts"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "History",
    size: 13
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__tools"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-src-cred__opt-btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "Criteria options"), /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronDown",
    size: 11
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-cred__active"
  }, activeCount, " criteria active")), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__body"
  }, /*#__PURE__*/React.createElement(FieldRow, {
    label: "Target role"
  }, /*#__PURE__*/React.createElement("input", {
    className: "tm-src-cred__text",
    value: state.position || '',
    placeholder: "e.g. Chief Financial Officer \xB7 C-Suite",
    onChange: e => setField('position', e.target.value)
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__hint"
  }, "ALAC will surface this role + N-1 candidates inside each company.")), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Sectors"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__chips"
  }, options.sectors.map(s => /*#__PURE__*/React.createElement(Chip, {
    key: s,
    label: s,
    on: state.sectors.includes(s),
    pinned: state.sectors.includes(s),
    removable: true,
    onClick: () => toggleArr('sectors', s)
  })))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "HQ location"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__chips"
  }, options.countries.map(s => /*#__PURE__*/React.createElement(Chip, {
    key: s,
    label: s,
    on: state.countries.includes(s),
    pinned: state.countries.includes(s),
    removable: true,
    onClick: () => toggleArr('countries', s)
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__hint"
  }, "Include adjacent: ", /*#__PURE__*/React.createElement("button", {
    className: "tm-src-cred__link"
  }, "+200km radius"))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Revenue band"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__chips"
  }, options.revenue.map(s => /*#__PURE__*/React.createElement(Chip, {
    key: s,
    label: s,
    on: state.revenue.includes(s),
    removable: true,
    onClick: () => toggleArr('revenue', s)
  })))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Employees",
    optional: true
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__chips"
  }, options.employees.map(s => /*#__PURE__*/React.createElement(Chip, {
    key: s,
    label: s,
    on: state.employees.includes(s),
    removable: true,
    onClick: () => toggleArr('employees', s)
  })))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Ownership",
    optional: true
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__chips"
  }, [{
    v: 'any',
    l: 'Any'
  }, {
    v: 'public',
    l: 'Public'
  }, {
    v: 'private',
    l: 'Private'
  }, {
    v: 'pe-backed',
    l: 'PE-backed'
  }, {
    v: 'family',
    l: 'Family-owned'
  }].map(o => /*#__PURE__*/React.createElement(Chip, {
    key: o.v,
    label: o.l,
    on: state.ownership === o.v,
    onClick: () => setField('ownership', o.v)
  })))), /*#__PURE__*/React.createElement(FieldRow, {
    label: "Founder-led",
    optional: true
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__chips"
  }, [{
    v: 'any',
    l: 'Any'
  }, {
    v: 'preferred',
    l: 'Preferred'
  }, {
    v: 'required',
    l: 'Required'
  }, {
    v: 'exclude',
    l: 'Exclude'
  }].map(o => /*#__PURE__*/React.createElement(Chip, {
    key: o.v,
    label: o.l,
    on: state.founderLed === o.v,
    onClick: () => setField('founderLed', o.v)
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__row tm-src-cred__row--qs"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__row-l"
  }, /*#__PURE__*/React.createElement("span", null, "AI evaluation"), /*#__PURE__*/React.createElement("span", {
    className: "tm-src-cred__opt"
  }, "questions")), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__row-r"
  }, (state.questions || []).map((q, i) => /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__q",
    key: i
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "GripVertical",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("textarea", {
    className: "tm-src-cred__q-ta",
    rows: 2,
    value: q,
    placeholder: "e.g. Has this company expanded into adjacent FMCG categories in the last 5 years?",
    onChange: e => setQuestion(i, e.target.value)
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-cred__q-rm",
    onClick: () => removeQuestion(i)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 12
  })))), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-cred__q-add",
    onClick: addQuestion
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 12
  }), /*#__PURE__*/React.createElement("span", null, "Add evaluation question")), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__hint"
  }, "Questions evaluate companies, not filter them. ALAC scores each company against every question.")))), /*#__PURE__*/React.createElement("div", {
    className: "tm-src-cred__foot"
  }, /*#__PURE__*/React.createElement(Button, {
    onClick: () => {
      onApply(state);
      onClose();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14
  }), "Source companies"), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-cred__cancel",
    onClick: onClose
  }, "Cancel"), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-src-cred__clear",
    onClick: clearAll
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Trash2",
    size: 12
  }), "Clear all"))));
}
Object.assign(window, {
  SourcingCriteriaBar,
  SourcingCriteriaEditor
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/sourcing-criteria.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/sourcing-flyover.jsx
try { (() => {
/* global React, Icon, Pill, Avatar, cx, initials */
// ── Sourcing flyover ─────────────────────────────────────────────────────────
// Right-side slide-over panel for the AI sourcing screen.
//
// Two views, push-navigation:
//   1. Company  — clicked from a SourcingCompanyCard body
//   2. Executive — clicked from any exec row inside the company view, or
//      from a card's nested executive row (clicking past the card → straight
//      to exec, still keeps the company in the back-stack)
//
// Width: 440px. Mounted by universe.jsx on top of the sourcing screen,
// with a translucent scrim that closes on click.

// ── Deterministic mock generators ────────────────────────────────────────────
// Given a numeric id we synthesize a plausible LinkedIn-style profile that
// stays stable across renders (no random shuffling). Real data would replace
// these but the shape mirrors what enrichment usually produces.

function seedFor(id) {
  let h = (id || 1) * 2654435761;
  return () => {
    h = h ^ h << 13 | 0;
    h = h ^ h >>> 17 | 0;
    h = h ^ h << 5 | 0;
    return Math.abs(h);
  };
}
function pick(rng, arr) {
  return arr[rng() % arr.length];
}
const PRIOR_COMPANIES = ['Unilever Arabia', 'PepsiCo MEA', 'Nestlé Middle East', 'Mars GCC', 'Procter & Gamble', 'Mondelez International', 'Coca-Cola HBC', 'Heineken', 'Reckitt MEA', 'Kraft Heinz', 'Danone Africa & MENA', 'Diageo', 'L\'Oréal Levant', 'Henkel MEA', 'Mars Wrigley'];
const PRIOR_TITLES = ['VP Finance', 'Group Controller', 'Head of FP&A', 'Regional CFO', 'Director of Strategy', 'COO, Middle East', 'GM, Gulf', 'Commercial Director', 'Head of Treasury', 'Director, Corporate Development'];
const SCHOOLS = [['INSEAD', 'MBA · Finance', '2008'], ['London Business School', 'MBA', '2010'], ['Wharton', 'MBA · Strategy', '2007'], ['American University of Beirut', 'BSc Economics', '2002'], ['American University in Cairo', 'BBA Finance', '2001'], ['King Fahd University', 'BSc Industrial Engineering', '2000'], ['IE Business School', 'Executive MBA', '2013'], ['Harvard Business School', 'AMP', '2015']];
const LANG_SETS = [['Arabic — native', 'English — fluent'], ['Arabic — native', 'English — fluent', 'French — professional'], ['English — native', 'Arabic — professional'], ['Arabic — native', 'English — fluent', 'Urdu — conversational']];
const ACTIVITY_TEMPLATES = [['Yousef', 'added a note', 'Reviewed with Adam — keep on the list, push exec comp benchmark.'], ['Adam', 'tagged off-limits', 'Engaged on a parallel mandate (CFO at competitor).'], ['Yousef', 'sent first-touch', 'Email via personal LinkedIn — open rate confirmed.'], ['Maya', 'logged a call', 'Spoke with EA, set up a confidential conversation Thursday.']];

// Plausible per-company news items keyed by id (stable)
function newsFor(c) {
  const rng = seedFor((c.id || 1) * 7);
  const lines = [[`${c.name} appoints new Group Controller`, 'Reuters · 2 weeks ago'], [`${c.name} reports ${c.id % 2 === 0 ? '+12%' : '+8%'} YoY revenue in H1`, 'Zawya · 1 month ago'], [`${c.name} expands into ${c.id % 3 === 0 ? 'Egypt' : c.id % 3 === 1 ? 'Oman' : 'Iraq'}`, 'Argaam · 6 weeks ago'], [`${c.name} confirms long-term capex plan through 2028`, 'Bloomberg · 2 months ago'], [`Moody\u2019s upgrades ${c.name} to ${pick(rng, ['Baa1', 'Baa2', 'A3'])}`, 'Reuters · 10 weeks ago']];
  return [lines[rng() % lines.length], lines[rng() % lines.length], lines[rng() % lines.length]].filter((v, i, a) => a.findIndex(x => x[0] === v[0]) === i).slice(0, 3);
}
function profileFor(exec, company) {
  const rng = seedFor((exec.id || 1) * 31);
  const yrsInRole = rng() % 6 + 2;
  const reports = rng() % 9 + 3;
  const scope = pick(rng, ['P&L of $1.2B across GCC', 'P&L of $850M across MENA', 'Group reporting, treasury, and IR', 'FP&A, treasury, tax and IR', 'Group consolidation and audit committee']);
  const compMin = (rng() % 25 + 55) * 10; // 550–800 (×1000)
  const compMax = compMin + (rng() % 30 + 25) * 10;
  const bonus = rng() % 25 + 35; // 35–60 %
  const langs = LANG_SETS[rng() % LANG_SETS.length];
  const edu = [SCHOOLS[rng() % SCHOOLS.length], SCHOOLS[rng() % SCHOOLS.length]].filter((v, i, a) => a.findIndex(x => x[0] === v[0]) === i);
  const careerLen = 3 + rng() % 2;
  const career = [];
  let endYear = 2025 - yrsInRole;
  for (let i = 0; i < careerLen; i++) {
    const span = 2 + rng() % 5;
    const startYear = endYear - span;
    career.push({
      title: pick(rng, PRIOR_TITLES),
      company: pick(rng, PRIOR_COMPANIES),
      from: startYear,
      to: endYear
    });
    endYear = startYear;
  }
  const conf = exec.verified ? 86 + rng() % 10 : 52 + rng() % 18;
  const offLimits = exec.id % 13 === 0;
  const avail = pick(rng, ['Open to confidential approach', 'Not actively looking', 'Recently appointed', 'Open to move']);

  // AI summary — short, third-person, no fluff
  const summary = `${exec.name.split(' ')[0]} has spent the last ${yrsInRole} years as ${company.name}\u2019s ${exec.title.toLowerCase()}, owning ${scope.toLowerCase()}. Prior career across regional and global FMCG groups, with deep exposure to the GCC consumer market. ${exec.verified ? 'Contact details verified within the last 90 days.' : 'Public profile only \u2014 verification pending.'}`;

  // Activity log — deterministic 2 items
  const acts = [ACTIVITY_TEMPLATES[rng() % ACTIVITY_TEMPLATES.length], ACTIVITY_TEMPLATES[rng() % ACTIVITY_TEMPLATES.length]].filter((v, i, a) => a.findIndex(x => x[2] === v[2]) === i);
  return {
    yrsInRole,
    reports,
    scope,
    compMin,
    compMax,
    bonus,
    langs,
    edu,
    career,
    conf,
    offLimits,
    avail,
    summary,
    acts
  };
}

// AI rationale per company (deterministic, paragraph)
function rationaleFor(c, targetRole) {
  const rng = seedFor((c.id || 1) * 19);
  const role = targetRole || 'CFO';
  return `${c.name} matches the brief on three vectors: scale (${c.revenue} revenue, ${c.employees} employees), geography (${c.country}), and sector fit (${c.sector}). ${c.relevance === 'Direct' ? 'It is a direct competitor in the target category.' : c.relevance === 'Adjacent' ? 'It sits in an adjacent category where ' + role + 's commonly rotate into the target sector.' : 'ALAC inferred this from sector-adjacent talent flows and recent ' + role + ' moves.'} ${rng() % 2 ? 'Succession signal at the ' + role + ' seat is moderate.' : 'No public succession signal detected at the ' + role + ' seat \u2014 sourcing N-1 is recommended.'}`;
}

// ── Small primitives ────────────────────────────────────────────────────────
function Section({
  icon,
  label,
  action,
  children
}) {
  return /*#__PURE__*/React.createElement("section", {
    className: "tm-sfly__sec"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__sec-head"
  }, icon && /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 12
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__sec-l"
  }, label), action), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__sec-body"
  }, children));
}
function KV({
  label,
  value,
  mono
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__kv"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__kv-l"
  }, label), /*#__PURE__*/React.createElement("span", {
    className: cx('tm-sfly__kv-v', mono && 'tm-mono')
  }, value));
}
function LinkBtn({
  icon,
  label,
  href
}) {
  return /*#__PURE__*/React.createElement("a", {
    className: "tm-sfly__lnk",
    href: href || '#',
    onClick: e => e.preventDefault(),
    title: label
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 12
  }), /*#__PURE__*/React.createElement("span", null, label));
}

// ── Company view ────────────────────────────────────────────────────────────
function CompanyDetail({
  company,
  criteriaResults,
  targetRole,
  status,
  onShowExec,
  primaryRoleKeywords
}) {
  const founded = 1968 + (company.id || 1) * 7 % 40;
  const ownership = company.id % 3 === 0 ? 'Private' : company.id % 3 === 1 ? 'Public — Tadawul' : 'PE-backed';
  const ticker = company.id % 3 === 1 ? `${company.name.replace(/[^A-Z]/g, '').slice(0, 4) || 'CO'}.SE` : '—';
  const ceo = company.execs.find(e => /chief executive|ceo|managing director/i.test(e.title))?.name || '—';
  const news = React.useMemo(() => newsFor(company), [company.id]);
  const ranked = React.useMemo(() => {
    return [...company.execs].sort((a, b) => {
      const aP = primaryRoleKeywords.some(k => a.title.toLowerCase().includes(k.toLowerCase()));
      const bP = primaryRoleKeywords.some(k => b.title.toLowerCase().includes(k.toLowerCase()));
      return Number(bP) - Number(aP);
    });
  }, [company.execs, primaryRoleKeywords]);
  const metCount = criteriaResults?.filter(r => r.met).length || 0;
  const totalCount = criteriaResults?.length || 0;
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__head"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hrow"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: company.name,
    shape: "square",
    size: 48,
    tone: "neutral"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hinfo"
  }, /*#__PURE__*/React.createElement("h2", {
    className: "tm-sfly__hname"
  }, company.name), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hmeta"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, company.city, ", ", company.country), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__hdot"
  }, "\xB7"), /*#__PURE__*/React.createElement("span", null, company.sector)), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hbadges"
  }, /*#__PURE__*/React.createElement(Pill, {
    tone: company.relevance === 'Direct' ? 'direct' : company.relevance === 'Adjacent' ? 'adjacent' : 'inferred'
  }, company.relevance), /*#__PURE__*/React.createElement(Pill, {
    tone: "neutral"
  }, Math.round(company.confidence), "% fit"), status === 'approved' && /*#__PURE__*/React.createElement(Pill, {
    tone: "verified"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 9
  }), " In universe"), status === 'shortlisted' && /*#__PURE__*/React.createElement(Pill, {
    tone: "ai"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Star",
    size: 9
  }), " Shortlisted"), status === 'declined' && /*#__PURE__*/React.createElement(Pill, {
    tone: "danger"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 9
  }), " Declined")))), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hlinks"
  }, /*#__PURE__*/React.createElement(LinkBtn, {
    icon: "Globe",
    label: "Website"
  }), /*#__PURE__*/React.createElement(LinkBtn, {
    icon: "Linkedin",
    label: "LinkedIn"
  }), /*#__PURE__*/React.createElement(LinkBtn, {
    icon: "Database",
    label: "Crunchbase"
  }), /*#__PURE__*/React.createElement(LinkBtn, {
    icon: "Newspaper",
    label: "News"
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__body"
  }, /*#__PURE__*/React.createElement(Section, {
    icon: "Sparkles",
    label: "AI rationale",
    action: /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__sec-meta"
    }, "vs. brief")
  }, /*#__PURE__*/React.createElement("p", {
    className: "tm-sfly__para"
  }, rationaleFor(company, targetRole))), /*#__PURE__*/React.createElement(Section, {
    icon: "Building2",
    label: "Key facts"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__facts"
  }, /*#__PURE__*/React.createElement(KV, {
    label: "Revenue",
    value: company.revenue,
    mono: true
  }), /*#__PURE__*/React.createElement(KV, {
    label: "Employees",
    value: company.employees,
    mono: true
  }), /*#__PURE__*/React.createElement(KV, {
    label: "Ownership",
    value: ownership
  }), /*#__PURE__*/React.createElement(KV, {
    label: "Founded",
    value: founded,
    mono: true
  }), /*#__PURE__*/React.createElement(KV, {
    label: "Ticker",
    value: ticker,
    mono: true
  }), /*#__PURE__*/React.createElement(KV, {
    label: "HQ",
    value: `${company.city}, ${company.country}`
  }), /*#__PURE__*/React.createElement(KV, {
    label: "CEO",
    value: ceo
  }), /*#__PURE__*/React.createElement(KV, {
    label: "Sector",
    value: company.sector
  }))), /*#__PURE__*/React.createElement(Section, {
    icon: "ListChecks",
    label: "Criteria fit",
    action: /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__sec-meta"
    }, /*#__PURE__*/React.createElement("b", {
      style: {
        color: metCount === totalCount ? 'var(--success)' : 'var(--foreground)'
      }
    }, metCount), /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)'
      }
    }, " of ", totalCount, " met"))
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__fit"
  }, (criteriaResults || []).map(r => /*#__PURE__*/React.createElement("div", {
    key: r.key,
    className: cx('tm-sfly__fit-row', r.met ? 'is-met' : 'is-miss')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: r.met ? 'Check' : 'Minus',
    size: 11,
    color: r.met ? 'var(--success)' : 'var(--muted-foreground)'
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__fit-l"
  }, r.label), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__fit-v"
  }, r.value))))), /*#__PURE__*/React.createElement(Section, {
    icon: "FileText",
    label: "Business summary"
  }, /*#__PURE__*/React.createElement("p", {
    className: "tm-sfly__para"
  }, company.summary)), /*#__PURE__*/React.createElement(Section, {
    icon: "Users",
    label: "Key executives",
    action: /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__sec-meta"
    }, /*#__PURE__*/React.createElement("b", null, ranked.length), /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)'
      }
    }, " mapped"))
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__execs"
  }, ranked.map(e => {
    const isPrimary = primaryRoleKeywords.some(k => e.title.toLowerCase().includes(k.toLowerCase()));
    return /*#__PURE__*/React.createElement("button", {
      key: e.id,
      className: cx('tm-sfly__exec', isPrimary && 'is-primary'),
      onClick: () => onShowExec(e),
      type: "button"
    }, /*#__PURE__*/React.createElement(Avatar, {
      name: e.name,
      size: 30,
      tone: e.enriched ? 'enriched' : 'primary'
    }), /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__exec-info"
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__exec-name"
    }, e.name, e.verified && /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__verified",
      title: "Verified"
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "BadgeCheck",
      size: 11,
      color: "#059669"
    })), isPrimary && /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__exec-tag"
    }, "target role")), /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__exec-meta"
    }, /*#__PURE__*/React.createElement("span", null, e.title), /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__hdot"
    }, "\xB7"), /*#__PURE__*/React.createElement("span", null, e.level))), /*#__PURE__*/React.createElement(Icon, {
      name: "ChevronRight",
      size: 13,
      color: "var(--muted-foreground)"
    }));
  }))), /*#__PURE__*/React.createElement(Section, {
    icon: "Newspaper",
    label: "Recent news & signals"
  }, /*#__PURE__*/React.createElement("ul", {
    className: "tm-sfly__news"
  }, news.map(([title, src], i) => /*#__PURE__*/React.createElement("li", {
    key: i,
    className: "tm-sfly__news-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__news-bullet"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__news-body"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__news-t"
  }, title), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__news-s"
  }, src)), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUpRight",
    size: 11,
    color: "var(--muted-foreground)"
  }))))), /*#__PURE__*/React.createElement(Section, {
    icon: "MessageSquare",
    label: "Activity & notes",
    action: /*#__PURE__*/React.createElement("button", {
      className: "tm-sfly__sec-act",
      type: "button"
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Plus",
      size: 11
    }), "Add note")
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__notes"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__note"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: "Yousef Iman",
    size: 22,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__note-body"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__note-head"
  }, /*#__PURE__*/React.createElement("b", null, "Yousef"), " added a note ", /*#__PURE__*/React.createElement("span", null, "\xB7 2 days ago")), /*#__PURE__*/React.createElement("p", null, "Long-term watch. ALAC placed their previous group CFO; current incumbent is past the 5-year mark."))), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__note"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: "Adam Salim",
    size: 22,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__note-body"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__note-head"
  }, /*#__PURE__*/React.createElement("b", null, "Adam"), " tagged a relationship ", /*#__PURE__*/React.createElement("span", null, "\xB7 last week")), /*#__PURE__*/React.createElement("p", null, "Direct line to the audit committee chair through ALAC alumni \u2014 useful for backchannel reference.")))))));
}

// ── Executive view ──────────────────────────────────────────────────────────
function ExecutiveDetail({
  exec,
  company,
  onBack
}) {
  const p = React.useMemo(() => profileFor(exec, company), [exec.id, company.id]);
  const confTone = p.conf >= 80 ? ['High', 'var(--success)'] : p.conf >= 60 ? ['Medium', '#b45309'] : ['Low', '#b91c1c'];
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__head"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-sfly__back",
    onClick: onBack,
    type: "button"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowLeft",
    size: 12
  }), /*#__PURE__*/React.createElement("span", null, company.name)), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hrow"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: exec.name,
    size: 48,
    tone: exec.enriched ? 'enriched' : 'primary'
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hinfo"
  }, /*#__PURE__*/React.createElement("h2", {
    className: "tm-sfly__hname"
  }, exec.name, exec.verified && /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__verified",
    title: "Verified"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "BadgeCheck",
    size: 13,
    color: "#059669"
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hmeta"
  }, /*#__PURE__*/React.createElement("span", null, exec.title), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__hdot"
  }, "\xB7"), /*#__PURE__*/React.createElement("span", null, company.name)), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hmeta"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, company.city, ", ", company.country)), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hbadges"
  }, /*#__PURE__*/React.createElement(Pill, {
    tone: "neutral"
  }, exec.level), exec.enriched ? /*#__PURE__*/React.createElement(Pill, {
    tone: "verified"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 9
  }), " Enriched") : /*#__PURE__*/React.createElement(Pill, {
    tone: "neutral"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 9
  }), " Public profile"), p.offLimits && /*#__PURE__*/React.createElement(Pill, {
    tone: "danger"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldAlert",
    size: 9
  }), " Off-limits")))), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__hlinks"
  }, /*#__PURE__*/React.createElement(LinkBtn, {
    icon: "Linkedin",
    label: "LinkedIn"
  }), /*#__PURE__*/React.createElement(LinkBtn, {
    icon: "Mail",
    label: "Email"
  }), /*#__PURE__*/React.createElement(LinkBtn, {
    icon: "Phone",
    label: "Phone"
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__body"
  }, /*#__PURE__*/React.createElement(Section, {
    icon: "Sparkles",
    label: "AI summary"
  }, /*#__PURE__*/React.createElement("p", {
    className: "tm-sfly__para"
  }, p.summary)), /*#__PURE__*/React.createElement(Section, {
    icon: "Briefcase",
    label: "Current role"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__role"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__role-row"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 11,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__role-l"
  }, "In role"), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__role-v tm-mono"
  }, p.yrsInRole, " years")), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__role-row"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Network",
    size: 11,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__role-l"
  }, "Direct reports"), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__role-v tm-mono"
  }, p.reports)), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__role-row tm-sfly__role-row--wide"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Target",
    size: 11,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__role-l"
  }, "Scope"), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__role-v"
  }, p.scope)))), /*#__PURE__*/React.createElement(Section, {
    icon: "History",
    label: "Career history"
  }, /*#__PURE__*/React.createElement("ol", {
    className: "tm-sfly__career"
  }, /*#__PURE__*/React.createElement("li", {
    className: "tm-sfly__career-row is-current"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-bullet"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-body"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-t"
  }, exec.title), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-c"
  }, company.name), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-y tm-mono"
  }, 2025 - p.yrsInRole, " \u2014 present \xB7 ", p.yrsInRole, "y"))), p.career.map((c, i) => /*#__PURE__*/React.createElement("li", {
    key: i,
    className: "tm-sfly__career-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-bullet"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-body"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-t"
  }, c.title), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-c"
  }, c.company), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__career-y tm-mono"
  }, c.from, " \u2014 ", c.to, " \xB7 ", c.to - c.from, "y")))))), /*#__PURE__*/React.createElement(Section, {
    icon: "GraduationCap",
    label: "Education"
  }, /*#__PURE__*/React.createElement("ul", {
    className: "tm-sfly__edu"
  }, p.edu.map(([school, prog, year], i) => /*#__PURE__*/React.createElement("li", {
    key: i,
    className: "tm-sfly__edu-row"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "GraduationCap",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__edu-body"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__edu-s"
  }, school), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__edu-p"
  }, prog, " \xB7 ", year)))))), /*#__PURE__*/React.createElement(Section, {
    icon: "DollarSign",
    label: "Compensation estimate",
    action: exec.enriched ? /*#__PURE__*/React.createElement("span", {
      className: "tm-sfly__sec-meta"
    }, "Verified 2024") : /*#__PURE__*/React.createElement("button", {
      className: "tm-sfly__sec-act",
      type: "button"
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Zap",
      size: 11
    }), " Enrich")
  }, exec.enriched ? /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__comp"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__comp-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__comp-l"
  }, "Base"), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__comp-v tm-mono"
  }, "$", p.compMin, "K\u2013$", p.compMax, "K")), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__comp-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__comp-l"
  }, "Bonus target"), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__comp-v tm-mono"
  }, p.bonus, "% of base")), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__comp-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__comp-l"
  }, "LTI"), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__comp-v"
  }, "Equity-equivalent \xB7 3yr vest"))) : /*#__PURE__*/React.createElement("p", {
    className: "tm-sfly__placeholder"
  }, "Compensation data not yet enriched. Run enrichment to pull verified base & bonus bands.")), /*#__PURE__*/React.createElement(Section, {
    icon: "Languages",
    label: "Languages"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__langs"
  }, p.langs.map((l, i) => /*#__PURE__*/React.createElement("span", {
    key: i,
    className: "tm-sfly__lang"
  }, l)))), /*#__PURE__*/React.createElement(Section, {
    icon: "ShieldCheck",
    label: "Verification & data confidence"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__verify"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__verify-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__verify-l"
  }, "Data confidence"), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__verify-meter"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__verify-bar",
    style: {
      width: p.conf + '%',
      background: confTone[1]
    }
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-sfly__verify-v tm-mono",
    style: {
      color: confTone[1]
    }
  }, confTone[0], " (", Math.round(p.conf / 10), "/10)")), /*#__PURE__*/React.createElement("ul", {
    className: "tm-sfly__verify-list"
  }, /*#__PURE__*/React.createElement("li", null, /*#__PURE__*/React.createElement(Icon, {
    name: exec.verified ? 'Check' : 'Minus',
    size: 11,
    color: exec.verified ? 'var(--success)' : 'var(--muted-foreground)'
  }), " Email verified ", exec.verified ? '· last 90 days' : '· pending'), /*#__PURE__*/React.createElement("li", null, /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 11,
    color: "var(--success)"
  }), " LinkedIn profile matched"), /*#__PURE__*/React.createElement("li", null, /*#__PURE__*/React.createElement(Icon, {
    name: exec.enriched ? 'Check' : 'Minus',
    size: 11,
    color: exec.enriched ? 'var(--success)' : 'var(--muted-foreground)'
  }), " Compensation ", exec.enriched ? 'cross-checked vs. ALAC benchmarks' : 'not enriched'), /*#__PURE__*/React.createElement("li", null, /*#__PURE__*/React.createElement(Icon, {
    name: "Minus",
    size: 11,
    color: "var(--muted-foreground)"
  }), " Direct mobile not held")))), /*#__PURE__*/React.createElement(Section, {
    icon: p.offLimits ? 'ShieldAlert' : 'CircleCheck',
    label: "Availability & off-limits"
  }, /*#__PURE__*/React.createElement("div", {
    className: cx('tm-sfly__avail', p.offLimits && 'is-off')
  }, p.offLimits ? /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(Icon, {
    name: "ShieldAlert",
    size: 13,
    color: "#b91c1c"
  }), /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement("b", null, "Off-limits."), " Engaged on a parallel ALAC mandate \u2014 do not approach.")) : /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(Icon, {
    name: "CircleCheck",
    size: 13,
    color: "var(--success)"
  }), /*#__PURE__*/React.createElement("span", null, p.avail, " \u2014 no active ALAC engagement.")))), /*#__PURE__*/React.createElement(Section, {
    icon: "MessageSquare",
    label: "Activity & notes",
    action: /*#__PURE__*/React.createElement("button", {
      className: "tm-sfly__sec-act",
      type: "button"
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Plus",
      size: 11
    }), "Add note")
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__notes"
  }, p.acts.map(([who, verb, body], i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: "tm-sfly__note"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: who,
    size: 22,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__note-body"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__note-head"
  }, /*#__PURE__*/React.createElement("b", null, who), " ", verb, " ", /*#__PURE__*/React.createElement("span", null, "\xB7 ", i === 0 ? '2 days ago' : 'last week')), /*#__PURE__*/React.createElement("p", null, body))))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__foot"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-sfly__foot-secondary",
    type: "button"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Send",
    size: 12
  }), "Add to outreach"), /*#__PURE__*/React.createElement("button", {
    className: "tm-sfly__foot-primary",
    type: "button",
    disabled: p.offLimits
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserPlus",
    size: 12
  }), "Add to pipeline")));
}

// ── Shell ───────────────────────────────────────────────────────────────────
function SourcingFlyover({
  open,
  view,
  // 'company' | 'executive'
  company,
  exec,
  status,
  criteriaResults,
  targetRole,
  primaryRoleKeywords,
  onClose,
  onShowExec,
  onBack
}) {
  // Escape to close
  React.useEffect(() => {
    if (!open) return;
    const onKey = e => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open || !company) return null;
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-sfly__scrim",
    onClick: onClose
  }), /*#__PURE__*/React.createElement("aside", {
    className: cx('tm-sfly', `is-${view}`),
    role: "dialog",
    "aria-label": view === 'company' ? company.name : exec && exec.name
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-sfly__close",
    onClick: onClose,
    title: "Close \xB7 Esc",
    type: "button"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 14
  })), view === 'company' && /*#__PURE__*/React.createElement(CompanyDetail, {
    company: company,
    criteriaResults: criteriaResults,
    targetRole: targetRole,
    status: status,
    primaryRoleKeywords: primaryRoleKeywords || [],
    onShowExec: onShowExec
  }), view === 'executive' && exec && /*#__PURE__*/React.createElement(ExecutiveDetail, {
    company: company,
    exec: exec,
    onBack: onBack
  })));
}
Object.assign(window, {
  SourcingFlyover
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/sourcing-flyover.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/sourcing-sidebar.jsx
try { (() => {
/* global React, ReactDOM, Icon, cx, Tooltip */
// ── Unified mandate sidebar ──────────────────────────────────────────────────
// One sidebar everywhere a mandate is open (Overview, AI Agent, Position,
// Strategy, Sourcing, Map, Candidates, Outreach, Inbox, Reports, Settings).
// Concept lifted from Pin — adapted for company-first executive search.
//
// • Collapsible: 256px expanded ↔ 48px icon rail
// • Mandate selector at top (gradient pill, client + name)
// • Sourcing has a dropdown sub-list of company flow states + AI search runs
//   — only when the active section IS Sourcing (user requirement)

// ── Menu structure ───────────────────────────────────────────────────────────
// `views` lists every internal view name that should mark this item active.
const SIDEBAR_MENU = [{
  id: 'overview',
  icon: 'LayoutDashboard',
  label: 'Overview',
  views: ['overview']
}, {
  id: 'aiAgent',
  icon: 'Bot',
  label: 'AI Agent',
  views: ['aiAgent']
}, {
  id: 'position',
  icon: 'Briefcase',
  label: 'Position',
  views: ['position']
}, {
  id: 'strategy',
  icon: 'Compass',
  label: 'Strategy',
  views: ['strategy']
}, {
  id: 'sourcing',
  icon: 'Telescope',
  label: 'Mapping',
  views: ['sourcing'],
  hasSub: true
}, {
  id: 'map',
  icon: 'Map',
  label: 'Map',
  views: ['map']
}, {
  id: 'candidates',
  icon: 'Users',
  label: 'Candidates',
  views: ['candidates', 'table']
}, null,
// divider
{
  id: 'outreach',
  icon: 'Send',
  label: 'Outreach',
  views: ['outreach']
}, {
  id: 'inbox',
  icon: 'Inbox',
  label: 'Inbox',
  views: ['inbox']
}, {
  id: 'reports',
  icon: 'PieChart',
  label: 'Reports',
  views: ['reports', 'dashboard']
}];
const SIDEBAR_BOTTOM = [{
  id: 'settings',
  icon: 'Settings2',
  label: 'Mandate settings',
  views: ['settings']
}];
function viewToSection(view) {
  for (const item of [...SIDEBAR_MENU, ...SIDEBAR_BOTTOM]) {
    if (item && item.views.includes(view)) return item.id;
  }
  return 'overview';
}

// ── Mandate picker dropdown ──────────────────────────────────────────────────
// Grouped by client (Client › Search Map) — mirrors MapPickerDropdown so the
// app-wide picker behaves identically wherever it appears.
function MandatePickerDrop({
  maps,
  activeMap,
  anchorRect,
  onSelect,
  onSelectAll,
  onClose,
  onNewSearch
}) {
  const gbc = window.groupByClient || function (m) {
    return {
      clientGroups: [],
      unassigned: m
    };
  };
  const {
    clientGroups,
    unassigned
  } = gbc(maps || []);
  const [expanded, setExpanded] = React.useState(() => {
    const auto = new Set();
    clientGroups.forEach(cg => {
      if (activeMap && cg.maps.some(m => m.id === activeMap.id)) auto.add(cg.clientId);else if (cg.maps.some(m => m.draft || m.active)) auto.add(cg.clientId);
    });
    if (auto.size === 0 && clientGroups.length > 0) auto.add(clientGroups[0].clientId);
    return auto;
  });
  const toggle = id => setExpanded(s => {
    const n = new Set(s);
    n.has(id) ? n.delete(id) : n.add(id);
    return n;
  });
  if (!anchorRect) return null;
  const MapRow = m => /*#__PURE__*/React.createElement("button", {
    key: m.id,
    className: cx('tm-mappick__map', activeMap && activeMap.id === m.id && 'is-active'),
    onClick: () => {
      onSelect(m);
      onClose();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Target",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, m.name));
  const dropStyle = {
    position: 'fixed',
    left: anchorRect.left,
    top: anchorRect.bottom + 4,
    width: Math.max(anchorRect.width, 272),
    minWidth: 272,
    zIndex: 101
  };
  const content = /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__scrim",
    onClick: onClose
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__drop",
    style: dropStyle
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__head"
  }, "Switch search map"), /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__list"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx('tm-mappick__all', !activeMap && 'is-active'),
    onClick: () => {
      onSelectAll();
      onClose();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Layers",
    size: 14
  }), /*#__PURE__*/React.createElement("span", null, "All search maps")), /*#__PURE__*/React.createElement("div", {
    className: "tm-mappick__div"
  }), clientGroups.map(cg => {
    const isOpen = expanded.has(cg.clientId);
    return /*#__PURE__*/React.createElement(React.Fragment, {
      key: cg.clientId
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-mappick__client",
      onClick: () => toggle(cg.clientId)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: isOpen ? 'ChevronDown' : 'ChevronRight',
      size: 11
    }), /*#__PURE__*/React.createElement(Icon, {
      name: "Building2",
      size: 12,
      color: "var(--muted-foreground)"
    }), /*#__PURE__*/React.createElement("span", {
      className: "tm-mappick__cname"
    }, cg.name), /*#__PURE__*/React.createElement("span", {
      className: "tm-mappick__cn"
    }, cg.maps.length)), isOpen && cg.maps.map(MapRow));
  }), unassigned.length > 0 && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("button", {
    className: "tm-mappick__client",
    style: {
      opacity: .7
    },
    onClick: () => toggle('__ua')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: expanded.has('__ua') ? 'ChevronDown' : 'ChevronRight',
    size: 11
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "Inbox",
    size: 12,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-mappick__cname"
  }, "Unassigned"), /*#__PURE__*/React.createElement("span", {
    className: "tm-mappick__cn"
  }, unassigned.length)), expanded.has('__ua') && unassigned.map(MapRow))), /*#__PURE__*/React.createElement("button", {
    className: "tm-mappick__new",
    onClick: () => {
      onClose();
      onNewSearch();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 13
  }), "New search map")));
  return ReactDOM.createPortal(content, document.body);
}

// ── Sidebar row primitives ───────────────────────────────────────────────────
function NavRow({
  icon,
  label,
  active,
  onClick,
  badge,
  badgeTone,
  collapsed,
  iconSize,
  disabled,
  hint
}) {
  if (collapsed) {
    return /*#__PURE__*/React.createElement(Tooltip, {
      label: disabled && hint ? `${label} — ${hint}` : label,
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: cx('tm-msb__ibtn', active && 'is-active', disabled && 'is-disabled'),
      onClick: disabled ? undefined : onClick,
      "aria-disabled": disabled || undefined
    }, /*#__PURE__*/React.createElement(Icon, {
      name: icon,
      size: iconSize || 16
    }), typeof badge === 'number' && badge > 0 && /*#__PURE__*/React.createElement("span", {
      className: cx('tm-msb__ibtn-dot', badgeTone && `is-${badgeTone}`)
    })));
  }
  return /*#__PURE__*/React.createElement("button", {
    className: cx('tm-msb__row', active && 'is-active', disabled && 'is-disabled'),
    onClick: disabled ? undefined : onClick,
    "aria-disabled": disabled || undefined,
    title: disabled && hint ? hint : undefined
  }, icon && /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 14
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-msb__label"
  }, label), disabled ? /*#__PURE__*/React.createElement("span", {
    className: "tm-msb__lockic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Lock",
    size: 11
  })) : typeof badge === 'number' && /*#__PURE__*/React.createElement("span", {
    className: cx('tm-msb__count', badgeTone && `is-${badgeTone}`)
  }, badge));
}
function SubRow({
  label,
  badge,
  badgeTone,
  active,
  onClick
}) {
  return /*#__PURE__*/React.createElement("button", {
    className: cx('tm-msb__subrow', active && 'is-active'),
    onClick: onClick
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-msb__sublabel"
  }, label), typeof badge === 'number' && /*#__PURE__*/React.createElement("span", {
    className: cx('tm-msb__count', badgeTone && `is-${badgeTone}`)
  }, badge));
}

// ── Main sidebar component ───────────────────────────────────────────────────
function MandateSidebar({
  // Navigation
  view,
  onView,
  // Collapse
  collapsed,
  onToggle,
  // Mandate context
  mandateName,
  clientName,
  maps,
  activeMap,
  onSelectMandate,
  onSelectAll,
  onNewSearch,
  onBack,
  onSearch,
  // Sourcing sub-state (only used when section==='sourcing')
  sourcingCounts,
  // { universe, shortlisted, declined, aiSourced }
  activeSubState,
  // 'aiSearch' | 'universe' | 'shortlisted' | 'declined'
  onSubState,
  aiSearches,
  // [{id, label, count, active, when}]
  activeSearchId,
  onSelectSearch,
  onAddSearch,
  // adds a NEW AI sourcing run inside this mandate
  // User / theme
  theme,
  onTheme,
  userName = 'Yousef Iman',
  userInitials = 'YI',
  // CRM (separate phase — not a mandate view)
  onCrm,
  crmActive
}) {
  const [pickerOpen, setPickerOpen] = React.useState(false);
  const [pickerRect, setPickerRect] = React.useState(null);
  const projBtnRef = React.useRef(null);
  const activeSection = viewToSection(view);
  const sourcingActive = activeSection === 'sourcing';
  const isAllMaps = !activeMap;
  // In the "All search maps" context several sections only make sense scoped
  // to a single mandate — keep them visible but disable interaction so the
  // menu shape stays identical.
  const ALL_MAPS_DISABLED = new Set(['map', 'candidates', 'outreach', 'inbox', 'reports']);
  const openPicker = () => {
    if (projBtnRef.current) setPickerRect(projBtnRef.current.getBoundingClientRect());
    setPickerOpen(p => !p);
  };
  React.useEffect(() => {
    const onKey = e => {
      if (e.key === 'Escape') setPickerOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  // ── Collapsed shell ────────────────────────────────────────────────────────
  if (collapsed) {
    return /*#__PURE__*/React.createElement("aside", {
      className: "tm-msb is-collapsed"
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-msb__c-top"
    }, /*#__PURE__*/React.createElement(Tooltip, {
      label: "Expand sidebar",
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-msb__ibtn",
      onClick: onToggle
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "PanelLeftOpen",
      size: 16
    }))), /*#__PURE__*/React.createElement(Tooltip, {
      label: "New search \xB7 \u2318N",
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-msb__ibtn",
      onClick: onNewSearch
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Plus",
      size: 16
    }))), /*#__PURE__*/React.createElement(Tooltip, {
      label: "Search \xB7 \u2318K",
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-msb__ibtn",
      onClick: onSearch
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Search",
      size: 16
    })))), /*#__PURE__*/React.createElement("div", {
      className: "tm-msb__c-div"
    }), /*#__PURE__*/React.createElement(Tooltip, {
      label: activeMap ? mandateName || 'Switch mandate' : 'All search maps',
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      ref: projBtnRef,
      className: cx('tm-msb__c-proj', !activeMap && 'is-all', pickerOpen && 'is-active'),
      onClick: openPicker
    }, /*#__PURE__*/React.createElement(Icon, {
      name: activeMap ? 'Target' : 'Layers',
      size: 14,
      color: "#fff"
    }))), pickerOpen && /*#__PURE__*/React.createElement(MandatePickerDrop, {
      maps: maps,
      activeMap: activeMap,
      anchorRect: pickerRect,
      onSelect: onSelectMandate,
      onSelectAll: onSelectAll,
      onClose: () => setPickerOpen(false),
      onNewSearch: onNewSearch
    }), /*#__PURE__*/React.createElement("div", {
      className: "tm-msb__c-div"
    }), /*#__PURE__*/React.createElement("div", {
      className: "tm-msb__c-nav"
    }, SIDEBAR_MENU.map((item, i) => {
      if (!item) return /*#__PURE__*/React.createElement("div", {
        key: 'div' + i,
        className: "tm-msb__c-div"
      });
      const disabled = isAllMaps && ALL_MAPS_DISABLED.has(item.id);
      return /*#__PURE__*/React.createElement(NavRow, {
        key: item.id,
        icon: item.icon,
        label: item.label,
        active: activeSection === item.id,
        onClick: () => onView(item.views[0]),
        disabled: disabled,
        hint: "open a search map first",
        collapsed: true
      });
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1
      }
    }), /*#__PURE__*/React.createElement(NavRow, {
      icon: "Users",
      label: "CRM",
      active: !!crmActive,
      onClick: onCrm,
      collapsed: true
    }), SIDEBAR_BOTTOM.map(item => /*#__PURE__*/React.createElement(NavRow, {
      key: item.id,
      icon: item.icon,
      label: item.label,
      active: activeSection === item.id && !crmActive,
      onClick: () => onView(item.views[0]),
      collapsed: true
    })), /*#__PURE__*/React.createElement("div", {
      className: "tm-msb__c-div"
    }), /*#__PURE__*/React.createElement(Tooltip, {
      label: userName,
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-msb__c-avt"
    }, userInitials)), /*#__PURE__*/React.createElement(Tooltip, {
      label: theme === 'dark' ? 'Light mode' : 'Dark mode',
      side: "right"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-msb__ibtn",
      onClick: onTheme
    }, /*#__PURE__*/React.createElement(Icon, {
      name: theme === 'dark' ? 'Sun' : 'Moon',
      size: 15
    }))), /*#__PURE__*/React.createElement("div", {
      style: {
        height: 6
      }
    }));
  }

  // ── Expanded shell ─────────────────────────────────────────────────────────
  return /*#__PURE__*/React.createElement("aside", {
    className: "tm-msb"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-msb__topbar"
  }, /*#__PURE__*/React.createElement(Tooltip, {
    label: "Back to all searches",
    side: "bottom"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-msb__ibtn",
    onClick: onBack
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowLeft",
    size: 15
  }))), /*#__PURE__*/React.createElement(Tooltip, {
    label: "New search \xB7 \u2318N",
    side: "bottom"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-msb__ibtn",
    onClick: onNewSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 15
  }))), /*#__PURE__*/React.createElement(Tooltip, {
    label: "Search \xB7 \u2318K",
    side: "bottom"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-msb__ibtn",
    onClick: onSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 14
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement(Tooltip, {
    label: "Collapse sidebar",
    side: "bottom"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-msb__ibtn",
    onClick: onToggle
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "PanelLeftClose",
    size: 15
  })))), /*#__PURE__*/React.createElement("button", {
    ref: projBtnRef,
    className: cx('tm-msb__proj', !activeMap && 'is-all', pickerOpen && 'is-open'),
    onClick: openPicker
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-msb__proj-ic', !activeMap && 'is-all')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: activeMap ? 'Target' : 'Layers',
    size: 14,
    color: "#fff"
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-msb__proj-info"
  }, activeMap && clientName && /*#__PURE__*/React.createElement("span", {
    className: "tm-msb__proj-client"
  }, clientName), /*#__PURE__*/React.createElement("span", {
    className: "tm-msb__proj-name"
  }, activeMap ? mandateName || activeMap.name : 'All search maps')), /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronsUpDown",
    size: 12,
    color: "var(--muted-foreground)"
  })), pickerOpen && /*#__PURE__*/React.createElement(MandatePickerDrop, {
    maps: maps,
    activeMap: activeMap,
    anchorRect: pickerRect,
    onSelect: onSelectMandate,
    onSelectAll: onSelectAll,
    onClose: () => setPickerOpen(false),
    onNewSearch: onNewSearch
  }), /*#__PURE__*/React.createElement("nav", {
    className: "tm-msb__nav"
  }, SIDEBAR_MENU.map((item, i) => {
    if (!item) return /*#__PURE__*/React.createElement("div", {
      key: 'div' + i,
      className: "tm-msb__divider"
    });
    const isActive = activeSection === item.id;

    // Sourcing — show sub-list when active AND a map is selected
    if (item.id === 'sourcing') {
      const c = sourcingCounts || {};
      return /*#__PURE__*/React.createElement(React.Fragment, {
        key: item.id
      }, /*#__PURE__*/React.createElement(NavRow, {
        icon: item.icon,
        label: item.label,
        active: isActive,
        onClick: () => onView('sourcing'),
        badge: isAllMaps ? undefined : c.aiSourced
      }), isActive && sourcingActive && !isAllMaps && /*#__PURE__*/React.createElement("div", {
        className: "tm-msb__sublist"
      }, /*#__PURE__*/React.createElement("div", {
        className: "tm-msb__eyebrow"
      }, "AI sourced"), (aiSearches || []).map(s => /*#__PURE__*/React.createElement("button", {
        key: s.id,
        className: cx('tm-msb__search', activeSubState === 'aiSearch' && s.id === activeSearchId && 'is-active'),
        onClick: () => onSelectSearch && onSelectSearch(s.id),
        title: s.label
      }, /*#__PURE__*/React.createElement("span", {
        className: "tm-msb__search-dot",
        "data-active": s.id === activeSearchId || undefined
      }), /*#__PURE__*/React.createElement("span", {
        className: "tm-msb__search-label"
      }, s.label), typeof s.count === 'number' && s.count > 0 && /*#__PURE__*/React.createElement("span", {
        className: "tm-msb__count"
      }, s.count))), /*#__PURE__*/React.createElement("div", {
        className: "tm-msb__eyebrow",
        style: {
          marginTop: 8
        }
      }, "Triage"), /*#__PURE__*/React.createElement(SubRow, {
        label: "In universe",
        badge: c.universe,
        active: activeSubState === 'universe',
        onClick: () => onSubState('universe')
      }), /*#__PURE__*/React.createElement(SubRow, {
        label: "Shortlisted",
        badge: c.shortlisted,
        badgeTone: "ai",
        active: activeSubState === 'shortlisted',
        onClick: () => onSubState('shortlisted')
      }), /*#__PURE__*/React.createElement(SubRow, {
        label: "Declined",
        badge: c.declined,
        badgeTone: "danger",
        active: activeSubState === 'declined',
        onClick: () => onSubState('declined')
      })));
    }
    return /*#__PURE__*/React.createElement(NavRow, {
      key: item.id,
      icon: item.icon,
      label: item.label,
      active: isActive,
      onClick: () => onView(item.views[0]),
      disabled: isAllMaps && ALL_MAPS_DISABLED.has(item.id),
      hint: "open a search map first"
    });
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-msb__bottom"
  }, /*#__PURE__*/React.createElement(NavRow, {
    icon: "Users",
    label: "CRM",
    active: !!crmActive,
    onClick: onCrm
  }), SIDEBAR_BOTTOM.map(item => /*#__PURE__*/React.createElement(NavRow, {
    key: item.id,
    icon: item.icon,
    label: item.label,
    active: activeSection === item.id && !crmActive,
    onClick: () => onView(item.views[0])
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-msb__foot"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-msb__avt"
  }, userInitials), /*#__PURE__*/React.createElement("span", {
    className: "tm-msb__uname"
  }, userName), /*#__PURE__*/React.createElement(Tooltip, {
    label: theme === 'dark' ? 'Light mode' : 'Dark mode',
    side: "top"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-msb__ibtn",
    onClick: onTheme
  }, /*#__PURE__*/React.createElement(Icon, {
    name: theme === 'dark' ? 'Sun' : 'Moon',
    size: 13
  })))));
}
Object.assign(window, {
  MandateSidebar,
  SIDEBAR_MENU,
  SIDEBAR_BOTTOM,
  viewToSection
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/sourcing-sidebar.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/table-config.jsx
try { (() => {
/* global React */
// ── Enhanced Table View — Configuration ──────────────────────────────────────

const TABLE_TITLE_OPTIONS = ['CEO', 'CFO', 'COO', 'CTO', 'CHRO', 'CMO', 'Managing Director', 'VP Operations', 'VP Strategy', 'VP Sales', 'Partner', 'Principal', 'Director', 'General Manager', 'Head of Department', 'Board Member', 'Founder'];
const TABLE_COL_DEFS = [{
  id: 'country',
  label: 'Country',
  width: 'minmax(100px, 140px)',
  sortable: true,
  fixed: true
}, {
  id: 'company',
  label: 'Company',
  width: 'minmax(140px, 1.5fr)',
  sortable: true,
  fixed: true
}, {
  id: 'executive',
  label: 'Executive',
  width: 'minmax(110px, 1fr)',
  sortable: true
}, {
  id: 'title',
  label: 'Title',
  width: 'minmax(110px, 1fr)',
  sortable: true
}, {
  id: 'sector',
  label: 'Sector',
  width: 'minmax(120px, 1.2fr)',
  sortable: true
}, {
  id: 'revenue',
  label: 'Revenue',
  width: 'minmax(70px, 90px)',
  sortable: true
}, {
  id: 'employees',
  label: 'Employees',
  width: 'minmax(70px, 90px)',
  sortable: true
}, {
  id: 'notes',
  label: 'Notes',
  width: 'minmax(60px, 0.8fr)',
  sortable: true
}];
const DENSITY_CONFIG = {
  compact: {
    label: 'Compact',
    py: 5,
    fs: 11
  },
  default: {
    label: 'Default',
    py: 9,
    fs: 12
  },
  spacious: {
    label: 'Spacious',
    py: 14,
    fs: 13
  }
};
const GROUP_BY_OPTIONS = [{
  id: null,
  label: 'None'
}, {
  id: 'country',
  label: 'Country'
}, {
  id: 'company',
  label: 'Company'
}, {
  id: 'sector',
  label: 'Sector'
}, {
  id: 'title',
  label: 'Title'
}, {
  id: 'revenue',
  label: 'Revenue'
}];
Object.assign(window, {
  TABLE_TITLE_OPTIONS,
  TABLE_COL_DEFS,
  DENSITY_CONFIG,
  GROUP_BY_OPTIONS
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/table-config.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/table-modals.jsx
try { (() => {
/* global React, Icon, Button, TABLE_TITLE_OPTIONS */
// ── Enhanced Table View — Modals & Floating Menus ────────────────────────────

function FloatingMenu({
  anchorEl,
  onClose,
  children,
  width = 220
}) {
  const [pos, setPos] = React.useState(null);
  React.useEffect(() => {
    if (!anchorEl) {
      setPos(null);
      return;
    }
    const r = anchorEl.getBoundingClientRect();
    setPos({
      top: r.bottom + 4,
      left: Math.min(r.left, window.innerWidth - width - 16)
    });
  }, [anchorEl, width]);
  if (!pos) return null;
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'fixed',
      inset: 0,
      zIndex: 70
    },
    onClick: onClose
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'fixed',
      top: pos.top,
      left: pos.left,
      zIndex: 71,
      width,
      background: 'var(--popover)',
      border: '1px solid var(--border)',
      borderRadius: 10,
      boxShadow: 'var(--shadow-lg)',
      maxHeight: 340,
      overflowY: 'auto'
    },
    onClick: e => e.stopPropagation()
  }, children));
}
function ModalOverlay({
  onClose,
  children,
  width = 420
}) {
  React.useEffect(() => {
    const h = e => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', h);
    return () => document.removeEventListener('keydown', h);
  }, [onClose]);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'fixed',
      inset: 0,
      background: 'rgba(0,0,0,.35)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 80,
      padding: 16
    },
    onClick: onClose
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width,
      maxWidth: '100%',
      background: 'var(--popover)',
      border: '1px solid var(--border)',
      borderRadius: 14,
      boxShadow: 'var(--shadow-lg)',
      overflow: 'hidden'
    },
    onClick: e => e.stopPropagation()
  }, children));
}
function ModalHead({
  title,
  subtitle,
  onClose
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '16px 20px 12px',
      borderBottom: '1px solid var(--border)',
      display: 'flex',
      alignItems: 'flex-start',
      justifyContent: 'space-between'
    }
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 15,
      fontWeight: 600
    }
  }, title), subtitle && /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)',
      marginTop: 2
    }
  }, subtitle)), /*#__PURE__*/React.createElement("button", {
    onClick: onClose,
    style: {
      background: 'none',
      border: 'none',
      cursor: 'pointer',
      color: 'var(--muted-foreground)',
      padding: 4,
      borderRadius: 6
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 16
  })));
}
function FieldLabel({
  children
}) {
  return /*#__PURE__*/React.createElement("label", {
    style: {
      fontSize: 12,
      fontWeight: 500,
      marginBottom: 6,
      display: 'block',
      color: 'var(--foreground)'
    }
  }, children);
}
function ModalFoot({
  children
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '12px 20px 16px',
      display: 'flex',
      justifyContent: 'flex-end',
      gap: 10,
      borderTop: '1px solid color-mix(in srgb, var(--border) 50%, transparent)'
    }
  }, children);
}

/* ── Add / Edit Executive ─────────────────────────────────────────────────── */
function AddExecModal({
  onClose,
  onSave,
  initial,
  companyName
}) {
  const [name, setName] = React.useState(initial?.name || '');
  const [title, setTitle] = React.useState(initial?.title || '');
  const [customMode, setCustomMode] = React.useState(false);
  const [customTitle, setCustomTitle] = React.useState('');
  const ref = React.useRef(null);
  React.useEffect(() => {
    setTimeout(() => ref.current?.focus(), 60);
  }, []);
  const save = () => {
    const t = customMode ? customTitle.trim() : title;
    if (name.trim()) onSave({
      name: name.trim(),
      title: t
    });
  };
  return /*#__PURE__*/React.createElement(ModalOverlay, {
    onClose: onClose
  }, /*#__PURE__*/React.createElement(ModalHead, {
    title: initial?.name ? 'Edit Executive' : 'Add Executive',
    subtitle: companyName,
    onClose: onClose
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: 20,
      display: 'flex',
      flexDirection: 'column',
      gap: 16
    }
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(FieldLabel, null, "Name"), /*#__PURE__*/React.createElement("input", {
    ref: ref,
    className: "tm-input",
    value: name,
    onChange: e => setName(e.target.value),
    placeholder: "e.g. Sarah Johnson",
    onKeyDown: e => e.key === 'Enter' && save()
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(FieldLabel, null, "Title"), !customMode ? /*#__PURE__*/React.createElement("select", {
    className: "tm-input",
    value: title,
    style: {
      cursor: 'pointer'
    },
    onChange: e => e.target.value === '__other' ? setCustomMode(true) : setTitle(e.target.value)
  }, /*#__PURE__*/React.createElement("option", {
    value: ""
  }, "Select a title\u2026"), TABLE_TITLE_OPTIONS.map(t => /*#__PURE__*/React.createElement("option", {
    key: t,
    value: t
  }, t)), /*#__PURE__*/React.createElement("option", {
    value: "__other"
  }, "Other (custom)\u2026")) : /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("input", {
    className: "tm-input",
    value: customTitle,
    onChange: e => setCustomTitle(e.target.value),
    placeholder: "Enter custom title",
    autoFocus: true,
    onKeyDown: e => e.key === 'Enter' && save()
  }), /*#__PURE__*/React.createElement("button", {
    onClick: () => {
      setCustomMode(false);
      if (customTitle.trim()) setTitle(customTitle.trim());
    },
    style: {
      background: 'none',
      border: '1px solid var(--border)',
      borderRadius: 6,
      padding: '6px 10px',
      cursor: 'pointer',
      fontSize: 11,
      whiteSpace: 'nowrap',
      color: 'var(--muted-foreground)',
      fontFamily: 'var(--font-sans)'
    }
  }, "\u2190 List")))), /*#__PURE__*/React.createElement(ModalFoot, null, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm",
    onClick: onClose
  }, "Cancel"), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: save,
    disabled: !name.trim()
  }, /*#__PURE__*/React.createElement(Icon, {
    name: initial?.name ? 'Check' : 'Plus',
    size: 13
  }), initial?.name ? 'Update' : 'Add')));
}

/* ── Notes Editor ─────────────────────────────────────────────────────────── */
function NotesModal({
  onClose,
  onSave,
  initial,
  companyName
}) {
  const [text, setText] = React.useState(initial || '');
  const ref = React.useRef(null);
  React.useEffect(() => {
    setTimeout(() => ref.current?.focus(), 60);
  }, []);
  return /*#__PURE__*/React.createElement(ModalOverlay, {
    onClose: onClose,
    width: 480
  }, /*#__PURE__*/React.createElement(ModalHead, {
    title: "Notes",
    subtitle: companyName,
    onClose: onClose
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: 20
    }
  }, /*#__PURE__*/React.createElement("textarea", {
    ref: ref,
    value: text,
    onChange: e => setText(e.target.value),
    style: {
      width: '100%',
      minHeight: 120,
      resize: 'vertical',
      fontFamily: 'var(--font-sans)',
      fontSize: 13,
      color: 'var(--foreground)',
      background: 'var(--muted)',
      border: '1px solid transparent',
      borderRadius: 8,
      padding: 12,
      outline: 'none',
      lineHeight: 1.5,
      boxSizing: 'border-box'
    },
    placeholder: "Add notes about this company or executive\u2026"
  })), /*#__PURE__*/React.createElement(ModalFoot, null, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm",
    onClick: onClose
  }, "Cancel"), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: () => onSave(text)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Save",
    size: 13
  }), "Save")));
}

/* ── New Company ──────────────────────────────────────────────────────────── */
function NewCompanyModal({
  onClose,
  onSave
}) {
  const [name, setName] = React.useState('');
  const [country, setCountry] = React.useState('');
  const [sector, setSector] = React.useState('');
  const ref = React.useRef(null);
  React.useEffect(() => {
    setTimeout(() => ref.current?.focus(), 60);
  }, []);
  const countries = ['Saudi Arabia', 'United Arab Emirates', 'Kuwait', 'Qatar', 'Oman', 'Bahrain', 'Egypt', 'Jordan', 'Lebanon'];
  const doSave = () => name.trim() && onSave({
    name: name.trim(),
    country,
    sector
  });
  return /*#__PURE__*/React.createElement(ModalOverlay, {
    onClose: onClose
  }, /*#__PURE__*/React.createElement(ModalHead, {
    title: "New Company",
    onClose: onClose
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: 20,
      display: 'flex',
      flexDirection: 'column',
      gap: 16
    }
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(FieldLabel, null, "Company Name"), /*#__PURE__*/React.createElement("input", {
    ref: ref,
    className: "tm-input",
    value: name,
    onChange: e => setName(e.target.value),
    placeholder: "e.g. Acme Holdings",
    onKeyDown: e => e.key === 'Enter' && doSave()
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(FieldLabel, null, "Country"), /*#__PURE__*/React.createElement("select", {
    className: "tm-input",
    value: country,
    onChange: e => setCountry(e.target.value),
    style: {
      cursor: 'pointer'
    }
  }, /*#__PURE__*/React.createElement("option", {
    value: ""
  }, "Select country\u2026"), countries.map(c => /*#__PURE__*/React.createElement("option", {
    key: c,
    value: c
  }, c)))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(FieldLabel, null, "Sector ", /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontWeight: 400
    }
  }, "(optional)")), /*#__PURE__*/React.createElement("input", {
    className: "tm-input",
    value: sector,
    onChange: e => setSector(e.target.value),
    placeholder: "e.g. Capital Markets"
  }))), /*#__PURE__*/React.createElement(ModalFoot, null, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm",
    onClick: onClose
  }, "Cancel"), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    onClick: doSave,
    disabled: !name.trim()
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 13
  }), "Add Company")));
}
Object.assign(window, {
  FloatingMenu,
  ModalOverlay,
  AddExecModal,
  NotesModal,
  NewCompanyModal
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/table-modals.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/tableview.jsx
try { (() => {
/* global React, Icon, Button, Avatar, cx,
   TABLE_COL_DEFS, DENSITY_CONFIG, GROUP_BY_OPTIONS,
   FloatingMenu, AddExecModal, NotesModal, NewCompanyModal */
// ── Enhanced Table View v2 ───────────────────────────────────────────────────

/* ── Small primitives ─────────────────────────────────────────────────────── */
function Chk({
  checked,
  indeterminate
}) {
  const on = checked || indeterminate;
  return /*#__PURE__*/React.createElement("span", {
    className: "tm-tv__chk",
    "data-on": on || undefined
  }, checked && /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 10,
    color: "#fff",
    strokeWidth: 3
  }), indeterminate && !checked && /*#__PURE__*/React.createElement(Icon, {
    name: "Minus",
    size: 10,
    color: "#fff",
    strokeWidth: 3
  }));
}
function Toggle({
  on,
  onClick
}) {
  return /*#__PURE__*/React.createElement("button", {
    className: cx('tm-tv__toggle', on && 'is-on'),
    onClick: onClick
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-tv__toggle-dot"
  }));
}
function SortIc({
  active,
  dir
}) {
  if (!active) return /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronsUpDown",
    size: 11,
    style: {
      opacity: .3
    }
  });
  return /*#__PURE__*/React.createElement(Icon, {
    name: dir === 'asc' ? 'ChevronUp' : 'ChevronDown',
    size: 11
  });
}
function TBtn({
  icon,
  label,
  active,
  onClick,
  loading,
  badge
}) {
  return /*#__PURE__*/React.createElement("button", {
    className: cx('tm-tv__tbtn', active && 'is-active', loading && 'is-enriching'),
    onClick: onClick
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 14,
    className: loading ? 'tm-spin' : ''
  }), label, badge && /*#__PURE__*/React.createElement("span", {
    className: "tm-tv__tbtn-badge"
  }, badge));
}

/* ── Row context menu ─────────────────────────────────────────────────────── */
function RowContextMenu({
  anchorPos,
  onClose,
  onArchive,
  onDelete,
  onAddExec,
  onEditExec,
  hasExec,
  onAddToPipeline,
  inPipeline
}) {
  const menuRef = React.useRef(null);
  const [pos, setPos] = React.useState({
    x: 0,
    y: 0
  });
  React.useLayoutEffect(() => {
    if (!anchorPos || !menuRef.current) return;
    const el = menuRef.current;
    const pad = 8;
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const w = el.offsetWidth;
    const h = el.offsetHeight;
    let x = anchorPos.x;
    let y = anchorPos.y;
    if (x + w + pad > vw) x = Math.max(pad, vw - w - pad);
    if (y + h + pad > vh) y = Math.max(pad, vh - h - pad);
    setPos({
      x,
      y
    });
  }, [anchorPos]);
  if (!anchorPos) return null;
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'fixed',
      inset: 0,
      zIndex: 70
    },
    onClick: onClose
  }), /*#__PURE__*/React.createElement("div", {
    ref: menuRef,
    className: "tm-tv__ctx",
    style: {
      top: pos.y,
      left: pos.x
    }
  }, hasExec && /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__ctx-item",
    onClick: onEditExec
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Pencil",
    size: 13
  }), "Edit executive"), hasExec && /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__ctx-item",
    onClick: onAddToPipeline
  }, /*#__PURE__*/React.createElement(Icon, {
    name: inPipeline ? 'KanbanSquare' : 'UserPlus',
    size: 13
  }), inPipeline ? 'View in pipeline' : 'Add to pipeline'), /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__ctx-item",
    onClick: onAddExec
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "UserPlus",
    size: 13
  }), "Add executive row"), /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__ctx-sep"
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__ctx-item",
    onClick: onArchive
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Archive",
    size: 13
  }), "Archive"), /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__ctx-item is-danger",
    onClick: onDelete
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Trash2",
    size: 13
  }), "Delete")));
}

/* ── Confirm delete modal ─────────────────────────────────────────────────── */
function ConfirmModal({
  title,
  message,
  confirmLabel,
  danger,
  onConfirm,
  onClose
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__overlay",
    onClick: onClose
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__confirm",
    onClick: e => e.stopPropagation()
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__confirm-icon",
    "data-danger": danger || undefined
  }, /*#__PURE__*/React.createElement(Icon, {
    name: danger ? 'Trash2' : 'Archive',
    size: 20
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 15,
      fontWeight: 600,
      marginTop: 12
    }
  }, title), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 12,
      color: 'var(--muted-foreground)',
      marginTop: 4,
      lineHeight: 1.5,
      textAlign: 'center'
    }
  }, message), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 10,
      marginTop: 16,
      width: '100%'
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm",
    onClick: onClose,
    style: {
      flex: 1
    }
  }, "Cancel"), /*#__PURE__*/React.createElement("button", {
    className: cx('tm-tv__confirm-btn', danger && 'is-danger'),
    onClick: onConfirm
  }, confirmLabel))));
}

/* ── Bulk action bar ──────────────────────────────────────────────────────── */
function BulkBar({
  count,
  onArchive,
  onDelete,
  onClear
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__bulk"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-tv__bulk-count"
  }, count, " selected"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__bulk-btn",
    onClick: onArchive
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Archive",
    size: 13
  }), "Archive"), /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__bulk-btn is-danger",
    onClick: onDelete
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Trash2",
    size: 13
  }), "Delete")), /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__bulk-x",
    onClick: onClear
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 14
  })));
}

/* ════════════════════════════════════════════════════════════════════════════ */
function TableView({
  companies,
  onSelectCompany,
  onSelectExec,
  pipelineNames,
  onAddToPipeline,
  onGoToPipeline
}) {
  /* ── state ── */
  const [rows, setRows] = React.useState(() => companies.map(c => ({
    id: 'r-' + c.id,
    cid: c.id,
    company: c.name,
    country: c.country,
    sector: c.sector,
    revenue: c.revenue,
    employees: c.employees,
    executive: '',
    title: '',
    notes: '',
    archived: false
  })));
  const [groupBy, setGroupBy] = React.useState(null);
  const [colVis, setColVis] = React.useState({
    executive: true,
    title: true,
    sector: true,
    revenue: true,
    employees: true,
    notes: true
  });
  const [density, setDensity] = React.useState('default');
  const [sortCol, setSortCol] = React.useState('country');
  const [sortDir, setSortDir] = React.useState('asc');
  const [selected, setSelected] = React.useState(() => new Set());
  const [menu, setMenu] = React.useState(null);
  const [modal, setModal] = React.useState(null);
  const [enriching, setEnriching] = React.useState(false);
  const [collapsed, setCollapsed] = React.useState(() => new Set());
  const [ctxMenu, setCtxMenu] = React.useState(null);
  const [showArchived, setShowArchived] = React.useState(false);
  const [confirmAction, setConfirmAction] = React.useState(null);

  /* ── derived ── */
  const activeRows = rows.filter(r => showArchived ? r.archived : !r.archived);
  const visCols = TABLE_COL_DEFS.filter(c => c.fixed || colVis[c.id]);
  const gridCols = '36px ' + visCols.map(c => c.width).join(' ') + ' 36px';
  const d = DENSITY_CONFIG[density];
  const hiddenCount = Object.values(colVis).filter(v => !v).length;
  const archivedCount = rows.filter(r => r.archived).length;

  /* ── sort ── */
  const sorted = React.useMemo(() => [...activeRows].sort((a, b) => {
    let va = String(a[sortCol] || '').toLowerCase();
    let vb = String(b[sortCol] || '').toLowerCase();
    if (sortCol === 'revenue' || sortCol === 'employees') {
      const na = parseFloat(va.replace(/[^0-9.]/g, '')) || 0;
      const nb = parseFloat(vb.replace(/[^0-9.]/g, '')) || 0;
      return sortDir === 'asc' ? na - nb : nb - na;
    }
    return sortDir === 'asc' ? va.localeCompare(vb) : vb.localeCompare(va);
  }), [activeRows, sortCol, sortDir]);

  /* ── group ── */
  const groups = React.useMemo(() => {
    if (!groupBy) return [{
      key: null,
      rows: sorted
    }];
    const m = new Map();
    sorted.forEach(r => {
      const k = r[groupBy] || '—';
      if (!m.has(k)) m.set(k, []);
      m.get(k).push(r);
    });
    return [...m.entries()].map(([key, rws]) => ({
      key,
      rows: rws
    }));
  }, [sorted, groupBy]);

  /* ── handlers ── */
  const toggleSort = id => {
    if (sortCol === id) setSortDir(p => p === 'asc' ? 'desc' : 'asc');else {
      setSortCol(id);
      setSortDir('asc');
    }
  };
  const toggleRow = id => setSelected(p => {
    const n = new Set(p);
    n.has(id) ? n.delete(id) : n.add(id);
    return n;
  });
  const someSelected = selected.size > 0 && selected.size < activeRows.length;
  const allSelected = activeRows.length > 0 && selected.size === activeRows.length;
  const toggleAll = () => setSelected(allSelected || someSelected ? new Set() : new Set(activeRows.map(r => r.id)));
  const toggleGroup = k => setCollapsed(p => {
    const n = new Set(p);
    n.has(k) ? n.delete(k) : n.add(k);
    return n;
  });
  const openMenu = (type, e) => setMenu(m => m?.type === type ? null : {
    type,
    el: e.currentTarget
  });
  const closeMenu = () => setMenu(null);

  /* row mutations */
  const saveExec = (rowId, data) => {
    setRows(p => p.map(r => r.id === rowId ? {
      ...r,
      executive: data.name,
      title: data.title
    } : r));
    setModal(null);
  };
  const saveNotes = (rowId, text) => {
    setRows(p => p.map(r => r.id === rowId ? {
      ...r,
      notes: text
    } : r));
    setModal(null);
  };
  const addExecRow = cid => {
    const base = rows.find(r => r.cid === cid);
    if (!base) return;
    const nr = {
      ...base,
      id: 'r-' + cid + '-' + Date.now(),
      executive: '',
      title: '',
      notes: '',
      archived: false
    };
    setRows(p => {
      const idx = p.reduce((a, r, i) => r.cid === cid ? i : a, -1);
      const n = [...p];
      n.splice(idx + 1, 0, nr);
      return n;
    });
    setModal({
      type: 'exec',
      rowId: nr.id,
      companyName: base.company
    });
  };
  const addCompany = data => {
    const id = Date.now();
    setRows(p => [...p, {
      id: 'r-' + id,
      cid: id,
      company: data.name,
      country: data.country,
      sector: data.sector || '',
      revenue: '',
      employees: '',
      executive: '',
      title: '',
      notes: '',
      archived: false
    }]);
    setModal(null);
  };

  /* archive / delete */
  const archiveRows = ids => {
    setRows(p => p.map(r => ids.has(r.id) ? {
      ...r,
      archived: true
    } : r));
    setSelected(p => {
      const n = new Set(p);
      ids.forEach(i => n.delete(i));
      return n;
    });
  };
  const restoreRows = ids => {
    setRows(p => p.map(r => ids.has(r.id) ? {
      ...r,
      archived: false
    } : r));
  };
  const deleteRows = ids => {
    setRows(p => p.filter(r => !ids.has(r.id)));
    setSelected(p => {
      const n = new Set(p);
      ids.forEach(i => n.delete(i));
      return n;
    });
  };
  const doConfirmedAction = () => {
    if (!confirmAction) return;
    const {
      action,
      ids
    } = confirmAction;
    if (action === 'archive') archiveRows(ids);else if (action === 'delete') deleteRows(ids);
    setConfirmAction(null);
  };

  /* enrichment */
  const enrichAll = () => {
    if (enriching) return;
    setEnriching(true);
    setTimeout(() => {
      setRows(prev => {
        const result = [];
        prev.forEach(row => {
          if (row.executive) {
            result.push(row);
            return;
          }
          const co = companies.find(c => c.id === row.cid);
          if (!co || !co.execs.length) {
            result.push(row);
            return;
          }
          result.push({
            ...row,
            executive: co.execs[0].name,
            title: co.execs[0].title
          });
          co.execs.slice(1).forEach((e, i) => result.push({
            ...row,
            id: row.id + '-e' + (i + 1),
            executive: e.name,
            title: e.title,
            notes: '',
            archived: false
          }));
        });
        return result;
      });
      setEnriching(false);
    }, 1500);
  };

  /* context menu */
  const handleRowContext = (e, row) => {
    e.preventDefault();
    setCtxMenu({
      x: e.clientX,
      y: e.clientY,
      row
    });
  };

  /* ── cell renderer ── */
  const cell = (col, row) => {
    const v = row[col.id];
    const pad = {
      padding: '0 10px'
    };
    if (col.id === 'company') return /*#__PURE__*/React.createElement("span", {
      key: col.id,
      style: {
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        fontWeight: 600,
        ...pad
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        width: 7,
        height: 7,
        borderRadius: '50%',
        background: row.archived ? 'var(--muted-foreground)' : 'var(--primary)',
        flexShrink: 0
      }
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        opacity: row.archived ? .5 : 1
      }
    }, v));
    if (col.id === 'executive') return /*#__PURE__*/React.createElement("span", {
      key: col.id,
      style: {
        padding: '2px 10px',
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-tv__cell-edit",
      style: {
        flex: 1,
        minWidth: 0,
        display: 'flex',
        alignItems: 'center',
        gap: 6
      },
      onClick: () => setModal({
        type: 'exec',
        rowId: row.id,
        companyName: row.company,
        initial: row.executive ? {
          name: row.executive,
          title: row.title
        } : null
      })
    }, v ? /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(Avatar, {
      name: v,
      size: 20,
      tone: "primary"
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, v)) : /*#__PURE__*/React.createElement("span", {
      className: "tm-tv__cell-empty"
    }, "\u2014")), v && (pipelineNames && pipelineNames.has(v) ? /*#__PURE__*/React.createElement(InPipelineBadge, {
      onClick: () => onGoToPipeline && onGoToPipeline()
    }) : /*#__PURE__*/React.createElement(AddToPipelineBtn, {
      label: false,
      onClick: () => onAddToPipeline && onAddToPipeline({
        name: v,
        title: row.title,
        company: row.company
      })
    })));
    if (col.id === 'title') return /*#__PURE__*/React.createElement("span", {
      key: col.id,
      className: "tm-tv__cell-edit",
      style: {
        padding: '2px 10px',
        color: v ? 'var(--muted-foreground)' : undefined
      },
      onClick: () => setModal({
        type: 'exec',
        rowId: row.id,
        companyName: row.company,
        initial: row.executive ? {
          name: row.executive,
          title: row.title
        } : null
      })
    }, v || /*#__PURE__*/React.createElement("span", {
      className: "tm-tv__cell-empty"
    }, "\u2014"));
    if (col.id === 'notes') return /*#__PURE__*/React.createElement("span", {
      key: col.id,
      className: "tm-tv__cell-edit",
      style: {
        padding: '2px 10px'
      },
      onClick: () => setModal({
        type: 'notes',
        rowId: row.id,
        companyName: row.company,
        initial: row.notes
      })
    }, v ? /*#__PURE__*/React.createElement("span", {
      style: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        maxWidth: '100%'
      }
    }, v) : /*#__PURE__*/React.createElement("span", {
      className: "tm-tv__cell-empty"
    }, "\u2014"));
    if (col.id === 'country') return /*#__PURE__*/React.createElement("span", {
      key: col.id,
      style: {
        color: 'var(--muted-foreground)',
        ...pad
      }
    }, v);
    if (col.id === 'sector') return /*#__PURE__*/React.createElement("span", {
      key: col.id,
      title: v,
      style: {
        color: 'var(--muted-foreground)',
        ...pad,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, v || /*#__PURE__*/React.createElement("span", {
      className: "tm-tv__cell-empty"
    }, "\u2014"));
    if (col.id === 'revenue' || col.id === 'employees') return /*#__PURE__*/React.createElement("span", {
      key: col.id,
      className: "tm-mono",
      style: {
        color: 'var(--muted-foreground)',
        ...pad
      }
    }, v || '—');
    return /*#__PURE__*/React.createElement("span", {
      key: col.id,
      style: pad
    }, v || '—');
  };

  /* ══════════════════════════ RENDER ══════════════════════════ */
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-tv"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__toolbar"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__toolbar-left"
  }, /*#__PURE__*/React.createElement(TBtn, {
    icon: "LayoutGrid",
    label: "Group",
    active: !!groupBy,
    badge: groupBy ? GROUP_BY_OPTIONS.find(o => o.id === groupBy)?.label : null,
    onClick: e => openMenu('group', e)
  }), /*#__PURE__*/React.createElement(TBtn, {
    icon: "Columns3",
    label: "Columns",
    badge: hiddenCount > 0 ? visCols.length - 2 + '/' + (TABLE_COL_DEFS.length - 2) : null,
    onClick: e => openMenu('columns', e)
  }), /*#__PURE__*/React.createElement(TBtn, {
    icon: "AlignJustify",
    label: DENSITY_CONFIG[density].label,
    onClick: e => openMenu('density', e)
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__toolbar-sep"
  }), /*#__PURE__*/React.createElement(TBtn, {
    icon: "Sparkles",
    label: enriching ? 'Enriching…' : 'Fill Sectors',
    loading: enriching,
    onClick: enrichAll
  }), /*#__PURE__*/React.createElement(TBtn, {
    icon: "Plus",
    label: "New Company",
    onClick: () => setModal({
      type: 'company'
    })
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__toolbar-right"
  }, archivedCount > 0 && /*#__PURE__*/React.createElement("button", {
    className: cx('tm-tv__tbtn', showArchived && 'is-active'),
    onClick: () => setShowArchived(p => !p)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Archive",
    size: 14
  }), showArchived ? 'Active' : 'Archived', /*#__PURE__*/React.createElement("span", {
    className: "tm-tv__tbtn-badge"
  }, archivedCount)), /*#__PURE__*/React.createElement("span", {
    className: "tm-tv__row-count"
  }, activeRows.length, " rows"))), enriching && /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__enrichbar"
  }), selected.size > 0 && /*#__PURE__*/React.createElement(BulkBar, {
    count: selected.size,
    onArchive: () => showArchived ? restoreRows(selected) : setConfirmAction({
      action: 'archive',
      ids: new Set(selected)
    }),
    onDelete: () => setConfirmAction({
      action: 'delete',
      ids: new Set(selected)
    }),
    onClear: () => setSelected(new Set())
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__rows"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__head",
    style: {
      gridTemplateColumns: gridCols
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      justifyContent: 'center',
      cursor: 'pointer'
    },
    onClick: toggleAll
  }, /*#__PURE__*/React.createElement(Chk, {
    checked: allSelected,
    indeterminate: someSelected
  })), visCols.map(col => /*#__PURE__*/React.createElement("span", {
    key: col.id,
    onClick: () => col.sortable && toggleSort(col.id)
  }, col.label, /*#__PURE__*/React.createElement(SortIc, {
    active: sortCol === col.id,
    dir: sortDir
  }))), /*#__PURE__*/React.createElement("span", null)), groups.map(g => /*#__PURE__*/React.createElement(React.Fragment, {
    key: g.key || '__all'
  }, g.key && /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__group",
    onClick: () => toggleGroup(g.key)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: collapsed.has(g.key) ? 'ChevronRight' : 'ChevronDown',
    size: 14
  }), /*#__PURE__*/React.createElement("span", null, g.key), /*#__PURE__*/React.createElement("span", {
    className: "tm-tv__group-count"
  }, g.rows.length)), !collapsed.has(g.key) && g.rows.map(row => /*#__PURE__*/React.createElement("div", {
    key: row.id,
    className: cx('tm-tv__row', selected.has(row.id) && 'is-selected'),
    style: {
      gridTemplateColumns: gridCols,
      fontSize: d.fs,
      padding: d.py + 'px 0'
    },
    onContextMenu: e => handleRowContext(e, row)
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      justifyContent: 'center',
      cursor: 'pointer'
    },
    onClick: () => toggleRow(row.id)
  }, /*#__PURE__*/React.createElement(Chk, {
    checked: selected.has(row.id)
  })), visCols.map(col => cell(col, row)), /*#__PURE__*/React.createElement("span", {
    className: "tm-tv__row-actions"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__row-action-btn",
    title: "More actions",
    onClick: e => setCtxMenu({
      x: e.clientX,
      y: e.clientY,
      row
    })
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MoreVertical",
    size: 14
  }))))))), activeRows.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: showArchived ? 'Archive' : 'Table2',
    size: 32,
    style: {
      opacity: .25
    }
  }), /*#__PURE__*/React.createElement("div", null, showArchived ? 'No archived rows' : 'No data yet'))), menu?.type === 'group' && /*#__PURE__*/React.createElement(FloatingMenu, {
    anchorEl: menu.el,
    onClose: closeMenu,
    width: 220
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__menu"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__menu-label"
  }, "Group by"), GROUP_BY_OPTIONS.map(o => /*#__PURE__*/React.createElement("button", {
    key: String(o.id),
    className: cx('tm-tv__menu-item', groupBy === o.id && 'is-on'),
    onClick: () => {
      setGroupBy(o.id);
      setCollapsed(new Set());
      closeMenu();
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: o.id === null ? 'Minus' : o.id === 'country' ? 'Globe' : o.id === 'company' ? 'Building2' : o.id === 'sector' ? 'Briefcase' : o.id === 'title' ? 'UserCog' : o.id === 'revenue' ? 'DollarSign' : 'LayoutGrid',
    size: 14
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1
    }
  }, o.label), groupBy === o.id && /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 14,
    style: {
      color: 'var(--primary)'
    }
  }))))), menu?.type === 'columns' && /*#__PURE__*/React.createElement(FloatingMenu, {
    anchorEl: menu.el,
    onClose: closeMenu,
    width: 240
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__menu"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__menu-label"
  }, "Visible columns"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6,
      padding: '2px 10px 8px'
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__menu-mini",
    onClick: () => setColVis({
      executive: true,
      title: true,
      sector: true,
      revenue: true,
      employees: true,
      notes: true
    })
  }, "Show all"), /*#__PURE__*/React.createElement("button", {
    className: "tm-tv__menu-mini",
    onClick: () => setColVis({
      executive: false,
      title: false,
      sector: false,
      revenue: false,
      employees: false,
      notes: false
    })
  }, "Hide all")), TABLE_COL_DEFS.filter(c => !c.fixed).map(col => /*#__PURE__*/React.createElement("button", {
    key: col.id,
    className: "tm-tv__menu-item",
    onClick: () => setColVis(p => ({
      ...p,
      [col.id]: !p[col.id]
    }))
  }, /*#__PURE__*/React.createElement(Icon, {
    name: col.id === 'executive' ? 'User' : col.id === 'title' ? 'BadgeCheck' : col.id === 'sector' ? 'Briefcase' : col.id === 'revenue' ? 'DollarSign' : col.id === 'employees' ? 'Users' : col.id === 'notes' ? 'StickyNote' : 'Columns3',
    size: 14
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1
    }
  }, col.label), /*#__PURE__*/React.createElement(Toggle, {
    on: colVis[col.id]
  }))))), menu?.type === 'density' && /*#__PURE__*/React.createElement(FloatingMenu, {
    anchorEl: menu.el,
    onClose: closeMenu,
    width: 200
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__menu"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-tv__menu-label"
  }, "Row density"), Object.entries(DENSITY_CONFIG).map(([k, cfg]) => /*#__PURE__*/React.createElement("button", {
    key: k,
    className: cx('tm-tv__menu-item', density === k && 'is-on'),
    onClick: () => {
      setDensity(k);
      closeMenu();
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-tv__density-preview",
    "data-level": k
  }, /*#__PURE__*/React.createElement("span", null), /*#__PURE__*/React.createElement("span", null), /*#__PURE__*/React.createElement("span", null)), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1
    }
  }, cfg.label), density === k && /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 14,
    style: {
      color: 'var(--primary)'
    }
  }))))), /*#__PURE__*/React.createElement(RowContextMenu, {
    anchorPos: ctxMenu,
    hasExec: !!ctxMenu?.row?.executive,
    inPipeline: !!(ctxMenu?.row?.executive && pipelineNames && pipelineNames.has(ctxMenu.row.executive)),
    onAddToPipeline: () => {
      const r = ctxMenu.row;
      setCtxMenu(null);
      if (pipelineNames && pipelineNames.has(r.executive)) {
        onGoToPipeline && onGoToPipeline();
      } else {
        onAddToPipeline && onAddToPipeline({
          name: r.executive,
          title: r.title,
          company: r.company
        });
      }
    },
    onClose: () => setCtxMenu(null),
    onEditExec: () => {
      const r = ctxMenu.row;
      setCtxMenu(null);
      setModal({
        type: 'exec',
        rowId: r.id,
        companyName: r.company,
        initial: {
          name: r.executive,
          title: r.title
        }
      });
    },
    onAddExec: () => {
      const cid = ctxMenu.row.cid;
      setCtxMenu(null);
      addExecRow(cid);
    },
    onArchive: () => {
      const id = ctxMenu.row.id;
      setCtxMenu(null);
      if (showArchived) restoreRows(new Set([id]));else archiveRows(new Set([id]));
    },
    onDelete: () => {
      const id = ctxMenu.row.id;
      setCtxMenu(null);
      setConfirmAction({
        action: 'delete',
        ids: new Set([id])
      });
    }
  }), modal?.type === 'exec' && /*#__PURE__*/React.createElement(AddExecModal, {
    companyName: modal.companyName,
    initial: modal.initial,
    onSave: data => saveExec(modal.rowId, data),
    onClose: () => setModal(null)
  }), modal?.type === 'notes' && /*#__PURE__*/React.createElement(NotesModal, {
    companyName: modal.companyName,
    initial: modal.initial,
    onSave: text => saveNotes(modal.rowId, text),
    onClose: () => setModal(null)
  }), modal?.type === 'company' && /*#__PURE__*/React.createElement(NewCompanyModal, {
    onSave: addCompany,
    onClose: () => setModal(null)
  }), confirmAction && /*#__PURE__*/React.createElement(ConfirmModal, {
    danger: confirmAction.action === 'delete',
    title: confirmAction.action === 'delete' ? `Delete ${confirmAction.ids.size} row${confirmAction.ids.size > 1 ? 's' : ''}?` : `Archive ${confirmAction.ids.size} row${confirmAction.ids.size > 1 ? 's' : ''}?`,
    message: confirmAction.action === 'delete' ? 'This action cannot be undone. The data will be permanently removed.' : 'Archived rows can be restored later from the archived view.',
    confirmLabel: confirmAction.action === 'delete' ? 'Delete' : 'Archive',
    onConfirm: doConfirmedAction,
    onClose: () => setConfirmAction(null)
  }));
}
Object.assign(window, {
  TableView
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/tableview.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/universe-chat-old-version.jsx
try { (() => {
/* global React, Icon, cx */
// ── Universe AI Chat — full-featured right sidebar panel ────────────────────

const AI_MODELS = [{
  id: 'alac-scout',
  label: 'ALAC Scout',
  desc: 'Fast, free — basic queries',
  free: true,
  icon: 'Zap'
}, {
  id: 'alac-analyst',
  label: 'ALAC Analyst',
  desc: 'Deep analysis — free tier',
  free: true,
  icon: 'Brain'
}, {
  id: 'alac-strategist',
  label: 'ALAC Strategist',
  desc: 'Advanced reasoning',
  free: false,
  icon: 'Sparkles'
}, {
  id: 'alac-executive',
  label: 'ALAC Executive',
  desc: 'Full capabilities + data',
  free: false,
  icon: 'Crown'
}];
function UniverseChat({
  query,
  companyCount,
  onSelectAllDirect,
  onSelectAll,
  onDeselectAll,
  open,
  onClose
}) {
  const [messages, setMessages] = React.useState([]);
  const [input, setInput] = React.useState('');
  const [typing, setTyping] = React.useState(false);
  const [selectedModel, setSelectedModel] = React.useState('alac-analyst');
  const [modelDropOpen, setModelDropOpen] = React.useState(false);
  const [attachments, setAttachments] = React.useState([]);
  const [isRecording, setIsRecording] = React.useState(false);
  const [panelWidth, setPanelWidth] = React.useState(() => {
    try {
      const w = parseInt(localStorage.getItem('tm-chat-width'));
      return w > 300 ? w : 400;
    } catch {
      return 400;
    }
  });
  const [isResizing, setIsResizing] = React.useState(false);
  const bodyRef = React.useRef(null);
  const inputRef = React.useRef(null);
  const fileInputRef = React.useRef(null);
  const resizeRef = React.useRef(null);
  const modelDropRef = React.useRef(null);

  // Welcome message
  React.useEffect(() => {
    setMessages([{
      role: 'ai',
      text: `I've analysed your search "${query}" and found ${companyCount} companies. I can help you:\n• Select companies by criteria\n• Find more in specific sectors or regions\n• Explain relevance and confidence scores\n• Analyse uploaded documents for context`,
      time: Date.now(),
      model: selectedModel
    }]);
  }, [query, companyCount]);
  React.useEffect(() => {
    if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
  }, [messages, typing]);
  React.useEffect(() => {
    if (open && inputRef.current) inputRef.current.focus();
  }, [open]);

  // Close model dropdown on outside click
  React.useEffect(() => {
    const handler = e => {
      if (modelDropRef.current && !modelDropRef.current.contains(e.target)) setModelDropOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Resize logic
  React.useEffect(() => {
    if (!isResizing) return;
    const onMove = e => {
      const newW = window.innerWidth - e.clientX;
      const clamped = Math.max(248, Math.min(700, newW));
      setPanelWidth(clamped);
    };
    const onUp = () => {
      setIsResizing(false);
      try {
        localStorage.setItem('tm-chat-width', String(panelWidth));
      } catch {}
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    return () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isResizing, panelWidth]);

  // ── Send message ──────────────────────────────────────────
  const send = () => {
    const txt = input.trim();
    if (!txt && attachments.length === 0) return;
    if (typing) return;
    const userMsg = {
      role: 'user',
      text: txt,
      time: Date.now(),
      attachments: attachments.length > 0 ? [...attachments] : undefined
    };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setAttachments([]);
    setTyping(true);
    const lower = txt.toLowerCase();
    let response = '';
    let action = null;
    if (lower.includes('select all') && lower.includes('direct')) {
      response = 'Done — selected all companies with Direct relevance. Review them via the View Selected button.';
      action = onSelectAllDirect;
    } else if (lower.includes('select all')) {
      response = `Selected all ${companyCount} companies. You can refine using the filters on the left.`;
      action = onSelectAll;
    } else if (lower.includes('deselect') || lower.includes('clear selection')) {
      response = 'Selection cleared. Toggle individual companies or use filters to rebuild your selection.';
      action = onDeselectAll;
    } else if (lower.includes('add more') || lower.includes('find more') || lower.includes('more companies')) {
      response = 'To expand the universe, try broadening your sector filters or lowering the confidence threshold. I can also re-run the search with adjusted parameters if you describe what you\'re looking for.';
    } else if (lower.includes('confidence') || lower.includes('score')) {
      response = 'Confidence scores reflect how closely a company matches your search criteria. Direct matches score 75–95%, Adjacent 55–80%, and AI Inferred 40–65%. Use the Confidence slider in the filters to set a minimum threshold.';
    } else if (lower.includes('export') || lower.includes('download')) {
      response = 'You can export your confirmed universe from the workspace view. First confirm your selection, then use the Export button in the top bar.';
    } else if (lower.includes('help') || lower.includes('what can')) {
      response = 'I can help with:\n• "Select all direct companies"\n• "Deselect all"\n• Questions about confidence scores\n• Suggestions for expanding your universe\n• Explaining company relevance\n• Analysing uploaded documents';
    } else if (userMsg.attachments && userMsg.attachments.length > 0) {
      const fileNames = userMsg.attachments.map(a => a.name).join(', ');
      response = `I've received your file(s): ${fileNames}. I'm analysing the content to identify relevant companies and cross-reference with your current universe. This may surface additional matches or adjust confidence scores.`;
    } else {
      response = `Good question. Based on your search "${query}", I'd suggest:\n1. Use the sector filter to focus on core industries\n2. Set confidence ≥ 70% for high-quality matches\n3. Review Adjacent companies — they often reveal hidden talent pools\n\nWould you like me to select companies matching specific criteria?`;
    }
    setTimeout(() => {
      setTyping(false);
      setMessages(prev => [...prev, {
        role: 'ai',
        text: response,
        time: Date.now(),
        model: selectedModel
      }]);
      if (action) action();
    }, 900 + Math.random() * 600);
  };

  // ── File upload ───────────────────────────────────────────
  const handleFileSelect = e => {
    const files = Array.from(e.target.files);
    const newAttachments = files.map(f => ({
      name: f.name,
      size: f.size,
      type: f.type,
      icon: f.type.includes('pdf') ? 'FileText' : f.type.includes('sheet') || f.type.includes('csv') ? 'Table' : f.type.includes('image') ? 'Image' : 'File'
    }));
    setAttachments(prev => [...prev, ...newAttachments]);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };
  const removeAttachment = idx => setAttachments(prev => prev.filter((_, i) => i !== idx));

  // ── Voice toggle ──────────────────────────────────────────
  const toggleVoice = () => {
    if (isRecording) {
      setIsRecording(false);
      setInput(prev => prev + (prev ? ' ' : '') + 'Find companies in healthcare sector with revenue above $1B');
    } else {
      setIsRecording(true);
      setTimeout(() => setIsRecording(false), 3000);
    }
  };

  // ── Quick actions ─────────────────────────────────────────
  const quickActions = [{
    label: 'Select all direct',
    cmd: 'Select all direct companies',
    icon: 'CheckCircle'
  }, {
    label: 'Explain confidence',
    cmd: 'What are confidence scores?',
    icon: 'HelpCircle'
  }, {
    label: 'Expand universe',
    cmd: 'Find more companies in adjacent sectors',
    icon: 'Plus'
  }, {
    label: 'Export guide',
    cmd: 'How do I export?',
    icon: 'Download'
  }];
  const fireQuick = cmd => {
    setInput(cmd);
    setTimeout(() => {
      setMessages(prev => [...prev, {
        role: 'user',
        text: cmd,
        time: Date.now()
      }]);
      setInput('');
      setTyping(true);
      const lower = cmd.toLowerCase();
      let response = '';
      let action = null;
      if (lower.includes('select all') && lower.includes('direct')) {
        response = 'Done — selected all companies with Direct relevance.';
        action = onSelectAllDirect;
      } else if (lower.includes('confidence') || lower.includes('score')) {
        response = 'Confidence scores reflect how closely a company matches your search. Direct: 75–95%, Adjacent: 55–80%, AI Inferred: 40–65%.';
      } else if (lower.includes('help') || lower.includes('what can')) {
        response = 'I can help with:\n• "Select all direct companies"\n• "Deselect all"\n• Questions about confidence scores\n• Expanding your universe\n• Explaining relevance';
      } else if (lower.includes('find more') || lower.includes('expand')) {
        response = 'I\'ll scan adjacent sectors for companies that share talent profiles with your current universe. Try adding new sectors or countries via the scope filters — I\'ll auto-fetch matching companies.';
      } else if (lower.includes('export')) {
        response = 'To export: Confirm your universe first, then use the Export button in the top bar. Supported formats include CSV, Excel, and CRM-ready JSON.';
      }
      setTimeout(() => {
        setTyping(false);
        setMessages(prev => [...prev, {
          role: 'ai',
          text: response,
          time: Date.now(),
          model: selectedModel
        }]);
        if (action) action();
      }, 900 + Math.random() * 600);
    }, 80);
  };
  if (!open) return null;
  const curModel = AI_MODELS.find(m => m.id === selectedModel) || AI_MODELS[0];
  const formatSize = bytes => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-uc",
    style: {
      width: panelWidth
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__resize",
    onMouseDown: e => {
      e.preventDefault();
      setIsResizing(true);
    },
    ref: resizeRef,
    title: "Drag to resize"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__resize-grip"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__head"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__sparkle"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14,
    color: "#7c3aed"
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontWeight: 600,
      fontSize: 13
    }
  }, "AI Assistant"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 10,
      color: 'var(--muted-foreground)',
      marginTop: 1
    }
  }, curModel.label, " \xB7 ", curModel.free ? 'Free' : 'Pro'))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 4,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__head-btn",
    onClick: () => setMessages([{
      role: 'ai',
      text: 'Chat cleared. How can I help?',
      time: Date.now(),
      model: selectedModel
    }]),
    title: "New chat"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 14
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__close",
    onClick: onClose,
    title: "Close AI Assistant"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "PanelRightClose",
    size: 14
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__body",
    ref: bodyRef
  }, messages.map((msg, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: cx('tm-uc__msg', msg.role === 'ai' ? 'is-ai' : 'is-user')
  }, msg.role === 'ai' && /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__avatar"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 12,
    color: "#7c3aed"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__bubble-wrap"
  }, msg.role === 'ai' && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-tag"
  }, (AI_MODELS.find(m => m.id === msg.model) || curModel).label), msg.attachments && msg.attachments.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__msg-attachments"
  }, msg.attachments.map((a, j) => /*#__PURE__*/React.createElement("div", {
    key: j,
    className: "tm-uc__msg-file"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: a.icon,
    size: 12,
    color: "var(--primary)"
  }), /*#__PURE__*/React.createElement("span", null, a.name)))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__bubble"
  }, msg.text.split('\n').map((line, j) => /*#__PURE__*/React.createElement("div", {
    key: j,
    style: {
      minHeight: line ? undefined : 6
    }
  }, line))), msg.role === 'ai' && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__actions"
  }, /*#__PURE__*/React.createElement("button", {
    title: "Copy"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Copy",
    size: 12
  })), /*#__PURE__*/React.createElement("button", {
    title: "Regenerate"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "RefreshCw",
    size: 12
  })), /*#__PURE__*/React.createElement("button", {
    title: "Good response"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ThumbsUp",
    size: 12
  })), /*#__PURE__*/React.createElement("button", {
    title: "Bad response"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ThumbsDown",
    size: 12
  })))))), typing && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__msg is-ai"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__avatar"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 12,
    color: "#7c3aed"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__bubble-wrap"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-tag"
  }, curModel.label), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__bubble"
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-dot",
    style: {
      animationDelay: '0s'
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-dot",
    style: {
      animationDelay: '.15s'
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-dot",
    style: {
      animationDelay: '.3s'
    }
  })))))), messages.length <= 1 && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__quick"
  }, quickActions.map((qa, i) => /*#__PURE__*/React.createElement("button", {
    key: i,
    className: "tm-uc__quick-btn",
    onClick: () => fireQuick(qa.cmd)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: qa.icon,
    size: 11
  }), qa.label))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__composer"
  }, /*#__PURE__*/React.createElement("div", {
    className: cx('tm-uc__box', (input.trim() || attachments.length > 0) && 'has-content')
  }, attachments.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__attach-list"
  }, attachments.map((a, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: "tm-uc__attach-chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: a.icon,
    size: 12
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__attach-name"
  }, a.name), /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__attach-size"
  }, formatSize(a.size)), /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__attach-rm",
    onClick: () => removeAttachment(i)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 10
  }))))), /*#__PURE__*/React.createElement("textarea", {
    ref: inputRef,
    className: "tm-uc__textarea",
    placeholder: "Describe what you want to find\u2026",
    value: input,
    onChange: e => {
      setInput(e.target.value);
      e.target.style.height = 'auto';
      e.target.style.height = Math.min(e.target.scrollHeight, 120) + 'px';
    },
    onKeyDown: e => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        send();
      }
    },
    rows: 1
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__toolbar"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__tool-left"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__tool-btn",
    onClick: () => fileInputRef.current?.click(),
    title: "Add attachment"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 15
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__tool-btn",
    onClick: () => fileInputRef.current?.click(),
    title: "Upload document"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Paperclip",
    size: 14
  })), /*#__PURE__*/React.createElement("input", {
    ref: fileInputRef,
    type: "file",
    multiple: true,
    accept: ".pdf,.csv,.xlsx,.xls,.doc,.docx,.txt,.png,.jpg,.jpeg",
    style: {
      display: 'none'
    },
    onChange: handleFileSelect
  }), /*#__PURE__*/React.createElement("button", {
    className: cx('tm-uc__tool-btn', isRecording && 'is-recording'),
    onClick: toggleVoice,
    title: isRecording ? 'Stop recording' : 'Voice input'
  }, /*#__PURE__*/React.createElement(Icon, {
    name: isRecording ? 'MicOff' : 'Mic',
    size: 14
  }), isRecording && /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__rec-pulse"
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__tool-right"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-sel",
    ref: modelDropRef
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__model-btn",
    onClick: () => setModelDropOpen(p => !p)
  }, /*#__PURE__*/React.createElement("span", null, curModel.label), /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronDown",
    size: 11
  })), modelDropOpen && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-drop"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-drop-label"
  }, "Select model"), AI_MODELS.map(m => /*#__PURE__*/React.createElement("button", {
    key: m.id,
    className: cx('tm-uc__model-opt', selectedModel === m.id && 'is-active'),
    onClick: () => {
      setSelectedModel(m.id);
      setModelDropOpen(false);
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__model-opt-icon"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: m.icon,
    size: 13,
    color: selectedModel === m.id ? '#7c3aed' : 'var(--muted-foreground)'
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-opt-info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-opt-name"
  }, m.label, !m.free && /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__pro-badge"
  }, "PRO")), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-opt-desc"
  }, m.desc)), selectedModel === m.id && /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 13,
    color: "#7c3aed"
  }))))), /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__send",
    onClick: send,
    disabled: !input.trim() && attachments.length === 0 || typing
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUp",
    size: 14
  }), /*#__PURE__*/React.createElement("span", null, "Send")))))));
}
Object.assign(window, {
  UniverseChat
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/universe-chat-old-version.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/universe-chat.jsx
try { (() => {
/* global React, Icon, cx */
// ── Universe AI Chat — full-featured right sidebar panel ────────────────────

const AI_MODELS = [{
  id: 'alac-scout',
  label: 'ALAC Scout',
  desc: 'Fast, free — basic queries',
  free: true,
  icon: 'Zap'
}, {
  id: 'alac-analyst',
  label: 'ALAC Analyst',
  desc: 'Deep analysis — free tier',
  free: true,
  icon: 'Brain'
}, {
  id: 'alac-strategist',
  label: 'ALAC Strategist',
  desc: 'Advanced reasoning',
  free: false,
  icon: 'Sparkles'
}, {
  id: 'alac-executive',
  label: 'ALAC Executive',
  desc: 'Full capabilities + data',
  free: false,
  icon: 'Crown'
}];
function UniverseChat({
  query,
  companyCount,
  onSelectAllDirect,
  onSelectAll,
  onDeselectAll,
  open,
  onClose
}) {
  const [messages, setMessages] = React.useState([]);
  const [input, setInput] = React.useState('');
  const [typing, setTyping] = React.useState(false);
  const [selectedModel, setSelectedModel] = React.useState('alac-analyst');
  const [modelDropOpen, setModelDropOpen] = React.useState(false);
  const [attachments, setAttachments] = React.useState([]);
  const [isRecording, setIsRecording] = React.useState(false);
  const [panelWidth, setPanelWidth] = React.useState(() => {
    try {
      const w = parseInt(localStorage.getItem('tm-chat-width'));
      return w > 300 ? w : 400;
    } catch {
      return 400;
    }
  });
  const [isResizing, setIsResizing] = React.useState(false);
  const bodyRef = React.useRef(null);
  const inputRef = React.useRef(null);
  const fileInputRef = React.useRef(null);
  const resizeRef = React.useRef(null);
  const modelDropRef = React.useRef(null);

  // Welcome message
  React.useEffect(() => {
    setMessages([{
      role: 'ai',
      text: `I've analysed your search "${query}" and found ${companyCount} companies. I can help you:\n• Select companies by criteria\n• Find more in specific sectors or regions\n• Explain relevance and confidence scores\n• Analyse uploaded documents for context`,
      time: Date.now(),
      model: selectedModel
    }]);
  }, [query, companyCount]);
  React.useEffect(() => {
    if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
  }, [messages, typing]);
  React.useEffect(() => {
    if (open && inputRef.current) inputRef.current.focus();
  }, [open]);

  // Close model dropdown on outside click
  React.useEffect(() => {
    const handler = e => {
      if (modelDropRef.current && !modelDropRef.current.contains(e.target)) setModelDropOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Resize logic
  React.useEffect(() => {
    if (!isResizing) return;
    const onMove = e => {
      const newW = window.innerWidth - e.clientX;
      const clamped = Math.max(248, Math.min(700, newW));
      setPanelWidth(clamped);
    };
    const onUp = () => {
      setIsResizing(false);
      try {
        localStorage.setItem('tm-chat-width', String(panelWidth));
      } catch {}
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    return () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isResizing, panelWidth]);

  // ── Send message ──────────────────────────────────────────
  const send = () => {
    const txt = input.trim();
    if (!txt && attachments.length === 0) return;
    if (typing) return;
    const userMsg = {
      role: 'user',
      text: txt,
      time: Date.now(),
      attachments: attachments.length > 0 ? [...attachments] : undefined
    };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setAttachments([]);
    setTyping(true);
    const lower = txt.toLowerCase();
    let response = '';
    let action = null;
    if (lower.includes('select all') && lower.includes('direct')) {
      response = 'Done — selected all companies with Direct relevance. Review them via the View Selected button.';
      action = onSelectAllDirect;
    } else if (lower.includes('select all')) {
      response = `Selected all ${companyCount} companies. You can refine using the filters on the left.`;
      action = onSelectAll;
    } else if (lower.includes('deselect') || lower.includes('clear selection')) {
      response = 'Selection cleared. Toggle individual companies or use filters to rebuild your selection.';
      action = onDeselectAll;
    } else if (lower.includes('add more') || lower.includes('find more') || lower.includes('more companies')) {
      response = 'To expand the universe, try broadening your sector filters or lowering the confidence threshold. I can also re-run the search with adjusted parameters if you describe what you\'re looking for.';
    } else if (lower.includes('confidence') || lower.includes('score')) {
      response = 'Confidence scores reflect how closely a company matches your search criteria. Direct matches score 75–95%, Adjacent 55–80%, and AI Inferred 40–65%. Use the Confidence slider in the filters to set a minimum threshold.';
    } else if (lower.includes('export') || lower.includes('download')) {
      response = 'You can export your confirmed universe from the workspace view. First confirm your selection, then use the Export button in the top bar.';
    } else if (lower.includes('help') || lower.includes('what can')) {
      response = 'I can help with:\n• "Select all direct companies"\n• "Deselect all"\n• Questions about confidence scores\n• Suggestions for expanding your universe\n• Explaining company relevance\n• Analysing uploaded documents';
    } else if (userMsg.attachments && userMsg.attachments.length > 0) {
      const fileNames = userMsg.attachments.map(a => a.name).join(', ');
      response = `I've received your file(s): ${fileNames}. I'm analysing the content to identify relevant companies and cross-reference with your current universe. This may surface additional matches or adjust confidence scores.`;
    } else {
      response = `Good question. Based on your search "${query}", I'd suggest:\n1. Use the sector filter to focus on core industries\n2. Set confidence ≥ 70% for high-quality matches\n3. Review Adjacent companies — they often reveal hidden talent pools\n\nWould you like me to select companies matching specific criteria?`;
    }
    setTimeout(() => {
      setTyping(false);
      setMessages(prev => [...prev, {
        role: 'ai',
        text: response,
        time: Date.now(),
        model: selectedModel
      }]);
      if (action) action();
    }, 900 + Math.random() * 600);
  };

  // ── File upload ───────────────────────────────────────────
  const handleFileSelect = e => {
    const files = Array.from(e.target.files);
    const newAttachments = files.map(f => ({
      name: f.name,
      size: f.size,
      type: f.type,
      icon: f.type.includes('pdf') ? 'FileText' : f.type.includes('sheet') || f.type.includes('csv') ? 'Table' : f.type.includes('image') ? 'Image' : 'File'
    }));
    setAttachments(prev => [...prev, ...newAttachments]);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };
  const removeAttachment = idx => setAttachments(prev => prev.filter((_, i) => i !== idx));

  // ── Voice toggle ──────────────────────────────────────────
  const toggleVoice = () => {
    if (isRecording) {
      setIsRecording(false);
      setInput(prev => prev + (prev ? ' ' : '') + 'Find companies in healthcare sector with revenue above $1B');
    } else {
      setIsRecording(true);
      setTimeout(() => setIsRecording(false), 3000);
    }
  };

  // ── Quick actions ─────────────────────────────────────────
  const quickActions = [{
    label: 'Select all direct',
    cmd: 'Select all direct companies',
    icon: 'CheckCircle'
  }, {
    label: 'Explain confidence',
    cmd: 'What are confidence scores?',
    icon: 'HelpCircle'
  }, {
    label: 'Expand universe',
    cmd: 'Find more companies in adjacent sectors',
    icon: 'Plus'
  }, {
    label: 'Export guide',
    cmd: 'How do I export?',
    icon: 'Download'
  }];
  const fireQuick = cmd => {
    setInput(cmd);
    setTimeout(() => {
      setMessages(prev => [...prev, {
        role: 'user',
        text: cmd,
        time: Date.now()
      }]);
      setInput('');
      setTyping(true);
      const lower = cmd.toLowerCase();
      let response = '';
      let action = null;
      if (lower.includes('select all') && lower.includes('direct')) {
        response = 'Done — selected all companies with Direct relevance.';
        action = onSelectAllDirect;
      } else if (lower.includes('confidence') || lower.includes('score')) {
        response = 'Confidence scores reflect how closely a company matches your search. Direct: 75–95%, Adjacent: 55–80%, AI Inferred: 40–65%.';
      } else if (lower.includes('help') || lower.includes('what can')) {
        response = 'I can help with:\n• "Select all direct companies"\n• "Deselect all"\n• Questions about confidence scores\n• Expanding your universe\n• Explaining relevance';
      } else if (lower.includes('find more') || lower.includes('expand')) {
        response = 'I\'ll scan adjacent sectors for companies that share talent profiles with your current universe. Try adding new sectors or countries via the scope filters — I\'ll auto-fetch matching companies.';
      } else if (lower.includes('export')) {
        response = 'To export: Confirm your universe first, then use the Export button in the top bar. Supported formats include CSV, Excel, and CRM-ready JSON.';
      }
      setTimeout(() => {
        setTyping(false);
        setMessages(prev => [...prev, {
          role: 'ai',
          text: response,
          time: Date.now(),
          model: selectedModel
        }]);
        if (action) action();
      }, 900 + Math.random() * 600);
    }, 80);
  };
  if (!open) return null;
  const curModel = AI_MODELS.find(m => m.id === selectedModel) || AI_MODELS[0];
  const formatSize = bytes => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  };
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-uc",
    style: {
      width: panelWidth
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__resize",
    onMouseDown: e => {
      e.preventDefault();
      setIsResizing(true);
    },
    ref: resizeRef,
    title: "Drag to resize"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__resize-grip"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__head"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__sparkle"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14,
    color: "#7c3aed"
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontWeight: 600,
      fontSize: 13
    }
  }, "AI Assistant"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 10,
      color: 'var(--muted-foreground)',
      marginTop: 1
    }
  }, curModel.label, " \xB7 ", curModel.free ? 'Free' : 'Pro'))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 4,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__head-btn",
    onClick: () => setMessages([{
      role: 'ai',
      text: 'Chat cleared. How can I help?',
      time: Date.now(),
      model: selectedModel
    }]),
    title: "New chat"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 14
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__close",
    onClick: onClose,
    title: "Close AI Assistant"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "PanelRightClose",
    size: 14
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__body",
    ref: bodyRef
  }, messages.map((msg, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: cx('tm-uc__msg', msg.role === 'ai' ? 'is-ai' : 'is-user')
  }, msg.role === 'ai' && /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__avatar"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 12,
    color: "#7c3aed"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__bubble-wrap"
  }, msg.role === 'ai' && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-tag"
  }, (AI_MODELS.find(m => m.id === msg.model) || curModel).label), msg.attachments && msg.attachments.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__msg-attachments"
  }, msg.attachments.map((a, j) => /*#__PURE__*/React.createElement("div", {
    key: j,
    className: "tm-uc__msg-file"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: a.icon,
    size: 12,
    color: "var(--primary)"
  }), /*#__PURE__*/React.createElement("span", null, a.name)))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__bubble"
  }, msg.text.split('\n').map((line, j) => /*#__PURE__*/React.createElement("div", {
    key: j,
    style: {
      minHeight: line ? undefined : 6
    }
  }, line))), msg.role === 'ai' && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__actions"
  }, /*#__PURE__*/React.createElement("button", {
    title: "Copy"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Copy",
    size: 12
  })), /*#__PURE__*/React.createElement("button", {
    title: "Regenerate"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "RefreshCw",
    size: 12
  })), /*#__PURE__*/React.createElement("button", {
    title: "Good response"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ThumbsUp",
    size: 12
  })), /*#__PURE__*/React.createElement("button", {
    title: "Bad response"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ThumbsDown",
    size: 12
  })))))), typing && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__msg is-ai"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__avatar"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 12,
    color: "#7c3aed"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__bubble-wrap"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-tag"
  }, curModel.label), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__bubble"
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-dot",
    style: {
      animationDelay: '0s'
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-dot",
    style: {
      animationDelay: '.15s'
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-dot",
    style: {
      animationDelay: '.3s'
    }
  })))))), messages.length <= 1 && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__quick"
  }, quickActions.map((qa, i) => /*#__PURE__*/React.createElement("button", {
    key: i,
    className: "tm-uc__quick-btn",
    onClick: () => fireQuick(qa.cmd)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: qa.icon,
    size: 11
  }), qa.label))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__composer"
  }, /*#__PURE__*/React.createElement("div", {
    className: cx('tm-uc__box', (input.trim() || attachments.length > 0) && 'has-content')
  }, attachments.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__attach-list"
  }, attachments.map((a, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: "tm-uc__attach-chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: a.icon,
    size: 12
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__attach-name"
  }, a.name), /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__attach-size"
  }, formatSize(a.size)), /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__attach-rm",
    onClick: () => removeAttachment(i)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 10
  }))))), /*#__PURE__*/React.createElement("textarea", {
    ref: inputRef,
    className: "tm-uc__textarea",
    placeholder: "Describe what you want to find\u2026",
    value: input,
    onChange: e => {
      setInput(e.target.value);
      e.target.style.height = 'auto';
      e.target.style.height = Math.min(e.target.scrollHeight, 120) + 'px';
    },
    onKeyDown: e => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        send();
      }
    },
    rows: 1
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__toolbar"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__tool-left"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__tool-btn",
    onClick: () => fileInputRef.current?.click(),
    title: "Add attachment"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 15
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__tool-btn",
    onClick: () => fileInputRef.current?.click(),
    title: "Upload document"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Paperclip",
    size: 14
  })), /*#__PURE__*/React.createElement("input", {
    ref: fileInputRef,
    type: "file",
    multiple: true,
    accept: ".pdf,.csv,.xlsx,.xls,.doc,.docx,.txt,.png,.jpg,.jpeg",
    style: {
      display: 'none'
    },
    onChange: handleFileSelect
  }), /*#__PURE__*/React.createElement("button", {
    className: cx('tm-uc__tool-btn', isRecording && 'is-recording'),
    onClick: toggleVoice,
    title: isRecording ? 'Stop recording' : 'Voice input'
  }, /*#__PURE__*/React.createElement(Icon, {
    name: isRecording ? 'MicOff' : 'Mic',
    size: 14
  }), isRecording && /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__rec-pulse"
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__tool-right"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-sel",
    ref: modelDropRef
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__model-btn",
    onClick: () => setModelDropOpen(p => !p)
  }, /*#__PURE__*/React.createElement("span", null, curModel.label), /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronDown",
    size: 11
  })), modelDropOpen && /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-drop"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-drop-label"
  }, "Select model"), AI_MODELS.map(m => /*#__PURE__*/React.createElement("button", {
    key: m.id,
    className: cx('tm-uc__model-opt', selectedModel === m.id && 'is-active'),
    onClick: () => {
      setSelectedModel(m.id);
      setModelDropOpen(false);
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__model-opt-icon"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: m.icon,
    size: 13,
    color: selectedModel === m.id ? '#7c3aed' : 'var(--muted-foreground)'
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-opt-info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-opt-name"
  }, m.label, !m.free && /*#__PURE__*/React.createElement("span", {
    className: "tm-uc__pro-badge"
  }, "PRO")), /*#__PURE__*/React.createElement("div", {
    className: "tm-uc__model-opt-desc"
  }, m.desc)), selectedModel === m.id && /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 13,
    color: "#7c3aed"
  }))))), /*#__PURE__*/React.createElement("button", {
    className: "tm-uc__send",
    onClick: send,
    disabled: !input.trim() && attachments.length === 0 || typing
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUp",
    size: 14
  }), /*#__PURE__*/React.createElement("span", null, "Send")))))));
}
Object.assign(window, {
  UniverseChat
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/universe-chat.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/universe-filters-old-version.jsx
try { (() => {
/* global React, Icon, cx, TM_MASTER_COUNTRIES, TM_MASTER_SECTORS, TM_MASTER_REVENUE, TM_MASTER_RELEVANCE */
// ── Universe Filters v2 — scope-based, collapsible, accordion ────────────────

function ScopeSection({
  title,
  icon,
  defaultOpen,
  children,
  checkedCount,
  totalCount
}) {
  const [open, setOpen] = React.useState(defaultOpen);
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-uf__section', open && 'is-open')
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uf__section-head",
    onClick: () => setOpen(!open)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 13,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, title), /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__section-ct"
  }, /*#__PURE__*/React.createElement("b", null, checkedCount), "/", totalCount), /*#__PURE__*/React.createElement(Icon, {
    name: open ? 'ChevronUp' : 'ChevronDown',
    size: 12,
    style: {
      opacity: .45
    }
  })), open && /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__section-body"
  }, children));
}
function ScopeCheckbox({
  label,
  count,
  checked,
  onChange,
  loading,
  tone
}) {
  return /*#__PURE__*/React.createElement("label", {
    className: cx('tm-uf__cb', checked && 'is-on'),
    onClick: e => {
      e.preventDefault();
      onChange();
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-uf__cb-box', checked && 'is-on')
  }, checked && !loading && /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 9,
    color: "#fff"
  }), loading && /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-spin"
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-label"
  }, label), tone && /*#__PURE__*/React.createElement("span", {
    className: cx('tm-uf__cb-dot', 'is-' + tone)
  }), loading ? /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-loading"
  }, "fetching\u2026") : count > 0 ? /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-count"
  }, count) : /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-zero"
  }, "0"));
}

/* ── Collapsed inline bar (shown above table when sidebar is hidden) ───────── */
function FiltersCollapsedBar({
  totalActive,
  onToggle
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-uf-bar"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uf-bar__btn",
    onClick: onToggle
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "SlidersHorizontal",
    size: 13
  }), /*#__PURE__*/React.createElement("span", null, "Scope & Filters"), totalActive > 0 && /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__active-badge"
  }, totalActive), /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronDown",
    size: 12,
    style: {
      opacity: .5
    }
  })));
}

/* ── Full sidebar ──────────────────────────────────────────────────────────── */
function UniverseFilters({
  companies,
  scope,
  onScopeChange,
  collapsed,
  onToggleCollapse,
  loadingScopes,
  filters,
  onFilterChange,
  onClearAll
}) {
  const sectorCounts = React.useMemo(() => {
    const m = {};
    companies.forEach(c => {
      m[c.sector] = (m[c.sector] || 0) + 1;
    });
    return m;
  }, [companies]);
  const countryCounts = React.useMemo(() => {
    const m = {};
    companies.forEach(c => {
      m[c.country] = (m[c.country] || 0) + 1;
    });
    return m;
  }, [companies]);
  const revenueCounts = React.useMemo(() => {
    const m = {};
    companies.forEach(c => {
      m[c.revenue] = (m[c.revenue] || 0) + 1;
    });
    return m;
  }, [companies]);
  const relevanceCounts = React.useMemo(() => {
    const m = {};
    companies.forEach(c => {
      m[c.relevance] = (m[c.relevance] || 0) + 1;
    });
    return m;
  }, [companies]);
  const relTone = r => r === 'Direct' ? 'direct' : r === 'Adjacent' ? 'adjacent' : 'inferred';
  const toggleScope = (key, value) => {
    const cur = scope[key] || [];
    const next = cur.includes(value) ? cur.filter(v => v !== value) : [...cur, value];
    onScopeChange({
      ...scope,
      [key]: next
    }, key, value, !cur.includes(value));
  };
  const totalActive = (scope.sectors || []).length + (scope.countries || []).length + (scope.revenue || []).length + (scope.relevance || []).length + (filters.minConfidence > 0 ? 1 : 0);

  // When collapsed, render nothing (the parent renders FiltersCollapsedBar inline)
  if (collapsed) return null;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-uf"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__head"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 7
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "SlidersHorizontal",
    size: 14
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      fontWeight: 600
    }
  }, "Scope & Filters"), totalActive > 0 && /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__active-badge"
  }, totalActive)), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6,
      alignItems: 'center'
    }
  }, totalActive > 0 && /*#__PURE__*/React.createElement("button", {
    className: "tm-uf__clear",
    onClick: onClearAll
  }, "Reset"), /*#__PURE__*/React.createElement("button", {
    className: "tm-uf__collapse-btn",
    onClick: onToggleCollapse,
    title: "Hide filters"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "PanelLeftClose",
    size: 14
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__body"
  }, /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Relevance",
    icon: "Layers",
    defaultOpen: false,
    checkedCount: (scope.relevance || []).length,
    totalCount: TM_MASTER_RELEVANCE.length
  }, TM_MASTER_RELEVANCE.map(type => /*#__PURE__*/React.createElement(ScopeCheckbox, {
    key: type,
    label: type,
    count: relevanceCounts[type] || 0,
    tone: relTone(type),
    checked: (scope.relevance || []).includes(type),
    loading: (loadingScopes || []).includes('relevance:' + type),
    onChange: () => toggleScope('relevance', type)
  }))), /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Sector",
    icon: "Building2",
    defaultOpen: false,
    checkedCount: (scope.sectors || []).length,
    totalCount: TM_MASTER_SECTORS.length
  }, TM_MASTER_SECTORS.map(sector => /*#__PURE__*/React.createElement(ScopeCheckbox, {
    key: sector,
    label: sector,
    count: sectorCounts[sector] || 0,
    checked: (scope.sectors || []).includes(sector),
    loading: (loadingScopes || []).includes('sectors:' + sector),
    onChange: () => toggleScope('sectors', sector)
  }))), /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Country (GCC + MENA)",
    icon: "MapPin",
    defaultOpen: false,
    checkedCount: (scope.countries || []).length,
    totalCount: TM_MASTER_COUNTRIES.length
  }, TM_MASTER_COUNTRIES.map(country => /*#__PURE__*/React.createElement(ScopeCheckbox, {
    key: country,
    label: country,
    count: countryCounts[country] || 0,
    checked: (scope.countries || []).includes(country),
    loading: (loadingScopes || []).includes('countries:' + country),
    onChange: () => toggleScope('countries', country)
  }))), /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Revenue Band",
    icon: "TrendingUp",
    defaultOpen: false,
    checkedCount: (scope.revenue || []).length,
    totalCount: TM_MASTER_REVENUE.length
  }, TM_MASTER_REVENUE.map(band => /*#__PURE__*/React.createElement(ScopeCheckbox, {
    key: band,
    label: band,
    count: revenueCounts[band] || 0,
    checked: (scope.revenue || []).includes(band),
    loading: (loadingScopes || []).includes('revenue:' + band),
    onChange: () => toggleScope('revenue', band)
  }))), /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Confidence Score",
    icon: "Target",
    defaultOpen: true,
    checkedCount: filters.minConfidence > 0 ? 1 : 0,
    totalCount: 1
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__range"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__range-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__range-label"
  }, "Minimum"), /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__range-value"
  }, filters.minConfidence || 0, "%")), /*#__PURE__*/React.createElement("input", {
    type: "range",
    min: "0",
    max: "95",
    step: "5",
    value: filters.minConfidence || 0,
    className: "tm-uf__slider",
    onChange: e => onFilterChange({
      ...filters,
      minConfidence: parseInt(e.target.value)
    })
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__range-row",
    style: {
      fontSize: 10,
      color: 'var(--muted-foreground)'
    }
  }, /*#__PURE__*/React.createElement("span", null, "0%"), /*#__PURE__*/React.createElement("span", null, "100%"))))));
}
Object.assign(window, {
  UniverseFilters,
  FiltersCollapsedBar
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/universe-filters-old-version.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/universe-filters.jsx
try { (() => {
/* global React, Icon, cx, TM_MASTER_COUNTRIES, TM_MASTER_SECTORS, TM_MASTER_REVENUE, TM_MASTER_RELEVANCE */
// ── Universe Filters v2 — scope-based, collapsible, accordion ────────────────

function ScopeSection({
  title,
  icon,
  defaultOpen,
  children,
  checkedCount,
  totalCount
}) {
  const [open, setOpen] = React.useState(defaultOpen);
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-uf__section', open && 'is-open')
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uf__section-head",
    onClick: () => setOpen(!open)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: icon,
    size: 13,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, title), /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__section-ct"
  }, /*#__PURE__*/React.createElement("b", null, checkedCount), "/", totalCount), /*#__PURE__*/React.createElement(Icon, {
    name: open ? 'ChevronUp' : 'ChevronDown',
    size: 12,
    style: {
      opacity: .45
    }
  })), open && /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__section-body"
  }, children));
}
function ScopeCheckbox({
  label,
  count,
  checked,
  onChange,
  loading,
  tone
}) {
  return /*#__PURE__*/React.createElement("label", {
    className: cx('tm-uf__cb', checked && 'is-on'),
    onClick: e => {
      e.preventDefault();
      onChange();
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-uf__cb-box', checked && 'is-on')
  }, checked && !loading && /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 9,
    color: "#fff"
  }), loading && /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-spin"
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-label"
  }, label), tone && /*#__PURE__*/React.createElement("span", {
    className: cx('tm-uf__cb-dot', 'is-' + tone)
  }), loading ? /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-loading"
  }, "fetching\u2026") : count > 0 ? /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-count"
  }, count) : /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__cb-zero"
  }, "0"));
}

/* ── Collapsed inline bar (shown above table when sidebar is hidden) ───────── */
function FiltersCollapsedBar({
  totalActive,
  onToggle
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-uf-bar"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-uf-bar__btn",
    onClick: onToggle
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "SlidersHorizontal",
    size: 13
  }), /*#__PURE__*/React.createElement("span", null, "Scope & Filters"), totalActive > 0 && /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__active-badge"
  }, totalActive), /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronDown",
    size: 12,
    style: {
      opacity: .5
    }
  })));
}

/* ── Full sidebar ──────────────────────────────────────────────────────────── */
function UniverseFilters({
  companies,
  scope,
  onScopeChange,
  collapsed,
  onToggleCollapse,
  loadingScopes,
  filters,
  onFilterChange,
  onClearAll
}) {
  const sectorCounts = React.useMemo(() => {
    const m = {};
    companies.forEach(c => {
      m[c.sector] = (m[c.sector] || 0) + 1;
    });
    return m;
  }, [companies]);
  const countryCounts = React.useMemo(() => {
    const m = {};
    companies.forEach(c => {
      m[c.country] = (m[c.country] || 0) + 1;
    });
    return m;
  }, [companies]);
  const revenueCounts = React.useMemo(() => {
    const m = {};
    companies.forEach(c => {
      m[c.revenue] = (m[c.revenue] || 0) + 1;
    });
    return m;
  }, [companies]);
  const relevanceCounts = React.useMemo(() => {
    const m = {};
    companies.forEach(c => {
      m[c.relevance] = (m[c.relevance] || 0) + 1;
    });
    return m;
  }, [companies]);
  const relTone = r => r === 'Direct' ? 'direct' : r === 'Adjacent' ? 'adjacent' : 'inferred';
  const toggleScope = (key, value) => {
    const cur = scope[key] || [];
    const next = cur.includes(value) ? cur.filter(v => v !== value) : [...cur, value];
    onScopeChange({
      ...scope,
      [key]: next
    }, key, value, !cur.includes(value));
  };
  const totalActive = (scope.sectors || []).length + (scope.countries || []).length + (scope.revenue || []).length + (scope.relevance || []).length + (filters.minConfidence > 0 ? 1 : 0);

  // When collapsed, render nothing (the parent renders FiltersCollapsedBar inline)
  if (collapsed) return null;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-uf"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__head"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 7
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "SlidersHorizontal",
    size: 14
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      fontWeight: 600
    }
  }, "Scope & Filters"), totalActive > 0 && /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__active-badge"
  }, totalActive)), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 6,
      alignItems: 'center'
    }
  }, totalActive > 0 && /*#__PURE__*/React.createElement("button", {
    className: "tm-uf__clear",
    onClick: onClearAll
  }, "Reset"), /*#__PURE__*/React.createElement("button", {
    className: "tm-uf__collapse-btn",
    onClick: onToggleCollapse,
    title: "Hide filters"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "PanelLeftClose",
    size: 14
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__body"
  }, /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Relevance",
    icon: "Layers",
    defaultOpen: false,
    checkedCount: (scope.relevance || []).length,
    totalCount: TM_MASTER_RELEVANCE.length
  }, TM_MASTER_RELEVANCE.map(type => /*#__PURE__*/React.createElement(ScopeCheckbox, {
    key: type,
    label: type,
    count: relevanceCounts[type] || 0,
    tone: relTone(type),
    checked: (scope.relevance || []).includes(type),
    loading: (loadingScopes || []).includes('relevance:' + type),
    onChange: () => toggleScope('relevance', type)
  }))), /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Sector",
    icon: "Building2",
    defaultOpen: false,
    checkedCount: (scope.sectors || []).length,
    totalCount: TM_MASTER_SECTORS.length
  }, TM_MASTER_SECTORS.map(sector => /*#__PURE__*/React.createElement(ScopeCheckbox, {
    key: sector,
    label: sector,
    count: sectorCounts[sector] || 0,
    checked: (scope.sectors || []).includes(sector),
    loading: (loadingScopes || []).includes('sectors:' + sector),
    onChange: () => toggleScope('sectors', sector)
  }))), /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Country (GCC + MENA)",
    icon: "MapPin",
    defaultOpen: false,
    checkedCount: (scope.countries || []).length,
    totalCount: TM_MASTER_COUNTRIES.length
  }, TM_MASTER_COUNTRIES.map(country => /*#__PURE__*/React.createElement(ScopeCheckbox, {
    key: country,
    label: country,
    count: countryCounts[country] || 0,
    checked: (scope.countries || []).includes(country),
    loading: (loadingScopes || []).includes('countries:' + country),
    onChange: () => toggleScope('countries', country)
  }))), /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Revenue Band",
    icon: "TrendingUp",
    defaultOpen: false,
    checkedCount: (scope.revenue || []).length,
    totalCount: TM_MASTER_REVENUE.length
  }, TM_MASTER_REVENUE.map(band => /*#__PURE__*/React.createElement(ScopeCheckbox, {
    key: band,
    label: band,
    count: revenueCounts[band] || 0,
    checked: (scope.revenue || []).includes(band),
    loading: (loadingScopes || []).includes('revenue:' + band),
    onChange: () => toggleScope('revenue', band)
  }))), /*#__PURE__*/React.createElement(ScopeSection, {
    title: "Confidence Score",
    icon: "Target",
    defaultOpen: true,
    checkedCount: filters.minConfidence > 0 ? 1 : 0,
    totalCount: 1
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__range"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__range-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__range-label"
  }, "Minimum"), /*#__PURE__*/React.createElement("span", {
    className: "tm-uf__range-value"
  }, filters.minConfidence || 0, "%")), /*#__PURE__*/React.createElement("input", {
    type: "range",
    min: "0",
    max: "95",
    step: "5",
    value: filters.minConfidence || 0,
    className: "tm-uf__slider",
    onChange: e => onFilterChange({
      ...filters,
      minConfidence: parseInt(e.target.value)
    })
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-uf__range-row",
    style: {
      fontSize: 10,
      color: 'var(--muted-foreground)'
    }
  }, /*#__PURE__*/React.createElement("span", null, "0%"), /*#__PURE__*/React.createElement("span", null, "100%"))))));
}
Object.assign(window, {
  UniverseFilters,
  FiltersCollapsedBar
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/universe-filters.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/universe-old-version.jsx
try { (() => {
/* global React, Icon, Button, Pill, Avatar, cx, TM_COMPANIES, TM_RATIONALE, TM_FETCHABLE_POOL, initials,
   UniverseFilters, FiltersCollapsedBar, UniverseChat, UniverseSelectedPanel */
// ── Universe v2: enterprise-grade discovery + scope-based filter + chat ──────

function relTone(r) {
  return r === 'Direct' ? 'direct' : r === 'Adjacent' ? 'adjacent' : 'inferred';
}
function UniverseView({
  query,
  resume = false,
  savedSelected,
  onConfirm,
  onReset,
  onSaveDraft
}) {
  const [revealed, setRevealed] = React.useState(0);
  const [streaming, setStreaming] = React.useState(true);
  const [accepted, setAccepted] = React.useState(() => new Set());
  const [draftSaved, setDraftSaved] = React.useState(false);
  const [showSelected, setShowSelected] = React.useState(false);

  // Extra companies fetched when scope is expanded
  const [extraCompanies, setExtraCompanies] = React.useState([]);
  const [loadingScopes, setLoadingScopes] = React.useState([]);

  // Scope: which dimensions are "in scope" (checked = show, unchecked = hidden)
  const [scope, setScope] = React.useState({
    sectors: [],
    countries: [],
    revenue: [],
    relevance: []
  });

  // Subtractive filters (confidence, search)
  const [filters, setFilters] = React.useState({
    minConfidence: 0,
    search: ''
  });

  // Sidebar collapsed
  const [sidebarCollapsed, setSidebarCollapsed] = React.useState(false);

  // AI chat sidebar
  const [chatOpen, setChatOpen] = React.useState(false);

  // Pagination & sort
  const [page, setPage] = React.useState(1);
  const [pageSize, setPageSize] = React.useState(10);
  const [sortCol, setSortCol] = React.useState(null);
  const [sortDir, setSortDir] = React.useState('asc');

  // ── Streaming ────────────────────────────────────────────────
  React.useEffect(() => {
    if (resume) {
      setRevealed(TM_COMPANIES.length);
      setStreaming(false);
      const acc = savedSelected && savedSelected.size ? new Set(savedSelected) : new Set(TM_COMPANIES.filter(c => c.relevance === 'Direct').map(c => c.id));
      setAccepted(acc);
      return;
    }
    setRevealed(0);
    setStreaming(true);
    const acc = new Set();
    let i = 0;
    const tick = () => {
      i += 1;
      setRevealed(i);
      if (i <= TM_COMPANIES.length) {
        const c = TM_COMPANIES[i - 1];
        if (c.relevance === 'Direct') acc.add(c.id);
        setAccepted(new Set(acc));
      }
      if (i >= TM_COMPANIES.length) {
        setStreaming(false);
        return;
      }
      timer = setTimeout(tick, 220);
    };
    let timer = setTimeout(tick, 280);
    return () => clearTimeout(timer);
  }, [query, resume]);

  // ── Init scope from revealed data ───────────────────────────
  const baseCompanies = TM_COMPANIES.slice(0, revealed);
  React.useEffect(() => {
    if (!streaming && baseCompanies.length > 0) {
      const sectors = [...new Set(baseCompanies.map(c => c.sector))];
      const countries = [...new Set(baseCompanies.map(c => c.country))];
      const revenue = [...new Set(baseCompanies.map(c => c.revenue))];
      const relevance = [...new Set(baseCompanies.map(c => c.relevance))];
      setScope({
        sectors,
        countries,
        revenue,
        relevance
      });
    }
  }, [streaming]);

  // All live companies = base + extras
  const allCompanies = React.useMemo(() => [...baseCompanies, ...extraCompanies], [baseCompanies, extraCompanies]);

  // ── Scope change handler (with fetch simulation) ────────────
  const handleScopeChange = (newScope, changedKey, changedValue, isAdding) => {
    setScope(newScope);
    if (isAdding) {
      // Check if we have companies for this dimension already
      const hasExisting = allCompanies.some(c => c[dimField(changedKey)] === changedValue);
      if (!hasExisting) {
        // Fetch from pool
        const scopeTag = changedKey + ':' + changedValue;
        setLoadingScopes(prev => [...prev, scopeTag]);
        const poolMatches = TM_FETCHABLE_POOL.filter(c => {
          return c[dimField(changedKey)] === changedValue && !allCompanies.some(e => e.id === c.id) && !extraCompanies.some(e => e.id === c.id);
        });

        // Simulate AI fetch delay
        setTimeout(() => {
          if (poolMatches.length > 0) {
            setExtraCompanies(prev => [...prev, ...poolMatches]);
          }
          setLoadingScopes(prev => prev.filter(s => s !== scopeTag));
        }, 1200 + Math.random() * 800);
      }
    }
  };

  // ── Filter by scope + subtractive ───────────────────────────
  const filtered = React.useMemo(() => allCompanies.filter(c => {
    // Scope: only show companies matching checked scope dimensions
    if (scope.sectors.length && !scope.sectors.includes(c.sector)) return false;
    if (scope.countries.length && !scope.countries.includes(c.country)) return false;
    if (scope.revenue.length && !scope.revenue.includes(c.revenue)) return false;
    if (scope.relevance.length && !scope.relevance.includes(c.relevance)) return false;
    // Subtractive
    if (c.confidence < (filters.minConfidence || 0)) return false;
    if (filters.search) {
      const q = filters.search.toLowerCase();
      return c.name.toLowerCase().includes(q) || c.sector.toLowerCase().includes(q) || c.country.toLowerCase().includes(q);
    }
    return true;
  }), [allCompanies, scope, filters]);
  const sorted = React.useMemo(() => {
    if (!sortCol) return filtered;
    const arr = [...filtered],
      dir = sortDir === 'asc' ? 1 : -1;
    arr.sort((a, b) => {
      const va = a[sortCol],
        vb = b[sortCol];
      if (typeof va === 'number') return (va - vb) * dir;
      return String(va).localeCompare(String(vb)) * dir;
    });
    return arr;
  }, [filtered, sortCol, sortDir]);
  const totalPages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const cp = Math.min(page, totalPages);
  const paged = sorted.slice((cp - 1) * pageSize, cp * pageSize);
  React.useEffect(() => {
    setPage(1);
  }, [scope, filters]);

  // ── Counts ───────────────────────────────────────────────────
  const acceptedCount = [...accepted].filter(id => allCompanies.some(c => c.id === id)).length;
  const directCount = filtered.filter(c => c.relevance === 'Direct').length;
  const adjacentCount = filtered.filter(c => c.relevance === 'Adjacent').length;
  const inferredCount = filtered.filter(c => c.relevance === 'AI Inferred').length;

  // ── Actions ──────────────────────────────────────────────────
  const toggle = id => setAccepted(p => {
    const n = new Set(p);
    n.has(id) ? n.delete(id) : n.add(id);
    return n;
  });
  const selectAll = () => setAccepted(p => {
    const n = new Set(p);
    filtered.forEach(c => n.add(c.id));
    return n;
  });
  const deselectAll = () => setAccepted(new Set());
  const selectAllDirect = () => setAccepted(p => {
    const n = new Set(p);
    filtered.filter(c => c.relevance === 'Direct').forEach(c => n.add(c.id));
    return n;
  });
  const handleSort = col => {
    if (sortCol === col) setSortDir(d => d === 'asc' ? 'desc' : 'asc');else {
      setSortCol(col);
      setSortDir('asc');
    }
  };
  const clearScope = () => {
    const sectors = [...new Set(baseCompanies.map(c => c.sector))];
    const countries = [...new Set(baseCompanies.map(c => c.country))];
    const revenue = [...new Set(baseCompanies.map(c => c.revenue))];
    const relevance = [...new Set(baseCompanies.map(c => c.relevance))];
    setScope({
      sectors,
      countries,
      revenue,
      relevance
    });
    setFilters({
      minConfidence: 0,
      search: ''
    });
    setExtraCompanies([]);
  };
  const doSaveDraft = () => {
    setDraftSaved(true);
    onSaveDraft && onSaveDraft({
      query,
      companies: filtered.length,
      selected: acceptedCount
    });
  };
  const allPageSelected = paged.length > 0 && paged.every(c => accepted.has(c.id));
  const somePageSelected = paged.some(c => accepted.has(c.id));
  const togglePageAll = () => {
    if (allPageSelected) setAccepted(p => {
      const n = new Set(p);
      paged.forEach(c => n.delete(c.id));
      return n;
    });else setAccepted(p => {
      const n = new Set(p);
      paged.forEach(c => n.add(c.id));
      return n;
    });
  };
  const columns = [{
    key: 'name',
    label: 'Company'
  }, {
    key: 'sector',
    label: 'Sector'
  }, {
    key: 'country',
    label: 'Country'
  }, {
    key: 'revenue',
    label: 'Revenue'
  }, {
    key: 'relevance',
    label: 'Relevance'
  }, {
    key: 'confidence',
    label: 'Conf.'
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2 tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__bar"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__bar-l"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Globe",
    size: 15,
    color: "var(--primary)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 14,
      fontWeight: 700
    }
  }, "Company Universe"), /*#__PURE__*/React.createElement("span", {
    className: "tm-uv2__bar-query",
    title: query
  }, query), streaming ? /*#__PURE__*/React.createElement(Pill, {
    tone: "direct",
    style: {
      background: '#ecfdf5'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-ping"
  }, /*#__PURE__*/React.createElement("b", null), /*#__PURE__*/React.createElement("i", null)), " Discovering \xB7 ", baseCompanies.length) : /*#__PURE__*/React.createElement(Pill, {
    tone: "neutral"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "CheckCircle",
    size: 11
  }), " ", allCompanies.length, " total"), loadingScopes.length > 0 && /*#__PURE__*/React.createElement(Pill, {
    tone: "inferred",
    style: {
      background: '#fffbeb'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Loader2",
    size: 11,
    className: "tm-spin"
  }), " Fetching\u2026")), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__bar-r"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx('tm-uv2__sel-btn', !sidebarCollapsed && 'is-active'),
    onClick: () => setSidebarCollapsed(p => !p)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "SlidersHorizontal",
    size: 13
  }), /*#__PURE__*/React.createElement("span", null, "Filters"), (() => {
    const ta = (scope.sectors || []).length + (scope.countries || []).length + (scope.revenue || []).length + (scope.relevance || []).length + (filters.minConfidence > 0 ? 1 : 0);
    return ta > 0 ? /*#__PURE__*/React.createElement("span", {
      className: "tm-uf__active-badge"
    }, ta) : null;
  })()), /*#__PURE__*/React.createElement("button", {
    className: "tm-uv2__sel-btn",
    onClick: () => setShowSelected(true)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ListChecks",
    size: 14
  }), /*#__PURE__*/React.createElement("span", null, "View Selected"), /*#__PURE__*/React.createElement("span", {
    className: "tm-uv2__sel-n"
  }, acceptedCount)), /*#__PURE__*/React.createElement("button", {
    className: cx('tm-uv2__sel-btn tm-uv2__ai-btn', chatOpen && 'is-active'),
    onClick: () => setChatOpen(p => !p)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13
  }), /*#__PURE__*/React.createElement("span", null, "AI Assistant")), streaming && /*#__PURE__*/React.createElement(Button, {
    variant: "destructive",
    size: "sm",
    onClick: () => {
      setRevealed(TM_COMPANIES.length);
      setStreaming(false);
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Square",
    size: 12
  }), " Stop"), !streaming && /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm",
    onClick: doSaveDraft
  }, /*#__PURE__*/React.createElement(Icon, {
    name: draftSaved ? 'Check' : 'Save',
    size: 13
  }), " ", draftSaved ? 'Saved' : 'Save draft'), /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    size: "sm",
    onClick: onReset
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "RotateCcw",
    size: 13
  }), " New search"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__body"
  }, /*#__PURE__*/React.createElement(UniverseFilters, {
    companies: allCompanies,
    scope: scope,
    onScopeChange: handleScopeChange,
    collapsed: sidebarCollapsed,
    onToggleCollapse: () => setSidebarCollapsed(p => !p),
    loadingScopes: loadingScopes,
    filters: filters,
    onFilterChange: setFilters,
    onClearAll: clearScope
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__main"
  }, baseCompanies.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__rationale"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: streaming ? 'Loader2' : 'Sparkles',
    size: 13,
    color: "#7c3aed",
    className: streaming ? 'tm-spin' : ''
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)',
      lineHeight: 1.4
    }
  }, streaming ? 'AI is analysing markets…' : TM_RATIONALE)), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__toolbar"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__search"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Search",
    size: 13,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("input", {
    placeholder: "Search companies\u2026",
    value: filters.search,
    onChange: e => setFilters(f => ({
      ...f,
      search: e.target.value
    }))
  }), filters.search && /*#__PURE__*/React.createElement("button", {
    className: "tm-uv2__search-x",
    onClick: () => setFilters(f => ({
      ...f,
      search: ''
    }))
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 11
  }))), /*#__PURE__*/React.createElement("span", {
    className: "tm-uv2__count"
  }, filtered.length === allCompanies.length ? /*#__PURE__*/React.createElement("span", null, allCompanies.length, " companies") : /*#__PURE__*/React.createElement("span", null, filtered.length, " of ", allCompanies.length, " companies"), ' · ', /*#__PURE__*/React.createElement("b", null, acceptedCount), " selected", extraCompanies.length > 0 && /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6,
      color: 'var(--ai)'
    }
  }, "(+", extraCompanies.length, " fetched)"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__table"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__thead"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uv2__th-chk",
    onClick: togglePageAll,
    style: {
      cursor: 'pointer'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-tv__chk'),
    "data-on": allPageSelected || undefined
  }, allPageSelected ? /*#__PURE__*/React.createElement(Icon, {
    name: "Check",
    size: 10,
    color: "#fff"
  }) : somePageSelected ? /*#__PURE__*/React.createElement(Icon, {
    name: "Minus",
    size: 10,
    color: "var(--primary)"
  }) : null)), columns.map(col => /*#__PURE__*/React.createElement("button", {
    key: col.key,
    className: cx('tm-uv2__th', sortCol === col.key && 'is-sorted'),
    onClick: () => handleSort(col.key)
  }, col.label, sortCol === col.key && /*#__PURE__*/React.createElement(Icon, {
    name: sortDir === 'asc' ? 'ChevronUp' : 'ChevronDown',
    size: 11
  })))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__rows"
  }, paged.map(c => {
    const on = accepted.has(c.id);
    const isNew = extraCompanies.some(e => e.id === c.id);
    return /*#__PURE__*/React.createElement("div", {
      key: c.id,
      className: cx('tm-uv2__row', on && 'is-on', isNew && 'is-new')
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-uv2__row-chk",
      onClick: () => toggle(c.id),
      style: {
        cursor: 'pointer'
      }
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-tv__chk",
      "data-on": on || undefined
    }, on && /*#__PURE__*/React.createElement(Icon, {
      name: "Check",
      size: 10,
      color: "#fff"
    }))), /*#__PURE__*/React.createElement("div", {
      className: "tm-uv2__row-co"
    }, /*#__PURE__*/React.createElement(Avatar, {
      name: c.name,
      shape: "square",
      tone: "neutral",
      size: 26
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-co"
    }, c.name, isNew && /*#__PURE__*/React.createElement("span", {
      className: "tm-uv2__new-badge"
    }, "NEW")), /*#__PURE__*/React.createElement("div", {
      className: "tm-geo"
    }, c.city))), /*#__PURE__*/React.createElement("span", {
      className: "tm-uv2__cell"
    }, c.sector), /*#__PURE__*/React.createElement("span", {
      className: "tm-uv2__cell"
    }, c.country), /*#__PURE__*/React.createElement("span", {
      className: "tm-uv2__cell tm-mono"
    }, c.revenue), /*#__PURE__*/React.createElement("span", {
      className: "tm-uv2__cell-pill"
    }, /*#__PURE__*/React.createElement(Pill, {
      tone: relTone(c.relevance)
    }, c.relevance)), /*#__PURE__*/React.createElement("span", {
      className: "tm-uv2__cell-conf"
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-track"
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        width: c.confidence + '%'
      }
    })), /*#__PURE__*/React.createElement("span", {
      className: "tm-mono",
      style: {
        fontSize: 10,
        width: 26,
        textAlign: 'right'
      }
    }, c.confidence, "%")));
  }), (streaming || loadingScopes.length > 0) && Array.from({
    length: 2
  }).map((_, i) => /*#__PURE__*/React.createElement("div", {
    key: 'skel' + i,
    className: "tm-uv2__row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uv2__row-chk"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-skel",
    style: {
      width: 16,
      height: 16,
      borderRadius: 4
    }
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__row-co"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-skel",
    style: {
      width: 26,
      height: 26,
      borderRadius: 6
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-skel",
    style: {
      height: 11,
      width: '55%'
    }
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-skel",
    style: {
      height: 10,
      width: '65%'
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-skel",
    style: {
      height: 10,
      width: '50%'
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-skel",
    style: {
      height: 10,
      width: '45%'
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-skel",
    style: {
      height: 16,
      width: 54,
      borderRadius: 9999
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-skel",
    style: {
      height: 6,
      width: 28
    }
  }))), !streaming && loadingScopes.length === 0 && paged.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "SearchX",
    size: 28,
    color: "var(--border)"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      marginTop: 8
    }
  }, "No companies match your scope"), /*#__PURE__*/React.createElement("button", {
    className: "tm-uv2__chip-clear",
    onClick: clearScope,
    style: {
      marginTop: 6
    }
  }, "Reset scope"))), !streaming && totalPages > 1 && /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__pag"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-uv2__pag-info"
  }, (cp - 1) * pageSize + 1, "\u2013", Math.min(cp * pageSize, sorted.length), " of ", sorted.length), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__pag-btns"
  }, /*#__PURE__*/React.createElement("button", {
    disabled: cp <= 1,
    onClick: () => setPage(1)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronsLeft",
    size: 13
  })), /*#__PURE__*/React.createElement("button", {
    disabled: cp <= 1,
    onClick: () => setPage(p => p - 1)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronLeft",
    size: 13
  })), Array.from({
    length: totalPages
  }, (_, i) => i + 1).filter(p => p === 1 || p === totalPages || Math.abs(p - cp) <= 1).reduce((acc, p, i, arr) => {
    if (i > 0 && p - arr[i - 1] > 1) acc.push('...');
    acc.push(p);
    return acc;
  }, []).map((p, i) => typeof p === 'number' ? /*#__PURE__*/React.createElement("button", {
    key: p,
    className: cx(p === cp && 'is-on'),
    onClick: () => setPage(p)
  }, p) : /*#__PURE__*/React.createElement("span", {
    key: 'e' + i,
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)'
    }
  }, "\u2026")), /*#__PURE__*/React.createElement("button", {
    disabled: cp >= totalPages,
    onClick: () => setPage(p => p + 1)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronRight",
    size: 13
  })), /*#__PURE__*/React.createElement("button", {
    disabled: cp >= totalPages,
    onClick: () => setPage(totalPages)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ChevronsRight",
    size: 13
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__pag-size"
  }, /*#__PURE__*/React.createElement("span", null, "Rows:"), [10, 25, 50].map(n => /*#__PURE__*/React.createElement("button", {
    key: n,
    className: cx(pageSize === n && 'is-on'),
    onClick: () => {
      setPageSize(n);
      setPage(1);
    }
  }, n)))))), /*#__PURE__*/React.createElement(UniverseChat, {
    query: query,
    companyCount: allCompanies.length,
    onSelectAllDirect: selectAllDirect,
    onSelectAll: selectAll,
    onDeselectAll: deselectAll,
    open: chatOpen,
    onClose: () => setChatOpen(false)
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__foot"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-uv2__foot-stats"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-foot-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "v"
  }, acceptedCount), /*#__PURE__*/React.createElement("div", {
    className: "l"
  }, "selected")), /*#__PURE__*/React.createElement("div", {
    className: "tm-foot-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "v",
    style: {
      color: 'var(--success)'
    }
  }, directCount), /*#__PURE__*/React.createElement("div", {
    className: "l"
  }, "direct")), /*#__PURE__*/React.createElement("div", {
    className: "tm-foot-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "v",
    style: {
      color: 'var(--info)'
    }
  }, adjacentCount), /*#__PURE__*/React.createElement("div", {
    className: "l"
  }, "adjacent")), /*#__PURE__*/React.createElement("div", {
    className: "tm-foot-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "v",
    style: {
      color: 'var(--warning)'
    }
  }, inferredCount), /*#__PURE__*/React.createElement("div", {
    className: "l"
  }, "inferred"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 10,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "outline",
    onClick: doSaveDraft,
    disabled: streaming
  }, /*#__PURE__*/React.createElement(Icon, {
    name: draftSaved ? 'Check' : 'Save',
    size: 16
  }), " ", draftSaved ? 'Draft saved' : 'Save draft'), /*#__PURE__*/React.createElement(Button, {
    disabled: acceptedCount === 0,
    onClick: () => onConfirm([...accepted])
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Lock",
    size: 16
  }), " Confirm universe (", acceptedCount, ")"))), /*#__PURE__*/React.createElement(UniverseSelectedPanel, {
    open: showSelected,
    companies: allCompanies,
    selectedIds: accepted,
    onToggle: toggle,
    onClose: () => setShowSelected(false),
    onDeselectAll: deselectAll
  }));
}

// Helper: map scope key to company field name
function dimField(key) {
  if (key === 'sectors') return 'sector';
  if (key === 'countries') return 'country';
  if (key === 'revenue') return 'revenue';
  if (key === 'relevance') return 'relevance';
  return key;
}
Object.assign(window, {
  UniverseView
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/universe-old-version.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/universe-selected-old-version.jsx
try { (() => {
/* global React, Icon, Pill, Avatar, cx, initials */
// ── Universe Selected Panel — right slide-over overlay ───────────────────────

function relTone$(r) {
  return r === 'Direct' ? 'direct' : r === 'Adjacent' ? 'adjacent' : 'inferred';
}
function UniverseSelectedPanel({
  open,
  companies,
  selectedIds,
  onToggle,
  onClose,
  onDeselectAll
}) {
  const selected = React.useMemo(() => companies.filter(c => selectedIds.has(c.id)), [companies, selectedIds]);
  const grouped = React.useMemo(() => {
    const m = {};
    selected.forEach(c => {
      (m[c.sector] = m[c.sector] || []).push(c);
    });
    return Object.entries(m).sort((a, b) => b[1].length - a[1].length);
  }, [selected]);
  const countrySummary = React.useMemo(() => {
    const m = {};
    selected.forEach(c => {
      m[c.country] = (m[c.country] || 0) + 1;
    });
    return Object.entries(m).sort((a, b) => b[1] - a[1]);
  }, [selected]);
  return /*#__PURE__*/React.createElement(React.Fragment, null, open && /*#__PURE__*/React.createElement("div", {
    className: "tm-us__backdrop",
    onClick: onClose
  }), /*#__PURE__*/React.createElement("div", {
    className: cx('tm-us', open && 'is-open')
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-us__head"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 15,
      fontWeight: 700,
      display: 'flex',
      alignItems: 'center',
      gap: 8
    }
  }, "Selected Companies", /*#__PURE__*/React.createElement("span", {
    className: "tm-us__count"
  }, selected.length))), /*#__PURE__*/React.createElement("button", {
    className: "tm-us__close",
    onClick: onClose
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 16
  }))), selected.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-us__countries"
  }, countrySummary.map(([country, n]) => /*#__PURE__*/React.createElement("span", {
    key: country,
    className: "tm-us__country-chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 9
  }), " ", country, " ", /*#__PURE__*/React.createElement("b", null, n)))), selected.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-us__stats"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-us__stat"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-v",
    style: {
      color: 'var(--success)'
    }
  }, selected.filter(c => c.relevance === 'Direct').length), /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-l"
  }, "Direct")), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__stat"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-v",
    style: {
      color: 'var(--info)'
    }
  }, selected.filter(c => c.relevance === 'Adjacent').length), /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-l"
  }, "Adjacent")), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__stat"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-v",
    style: {
      color: 'var(--warning)'
    }
  }, selected.filter(c => c.relevance === 'AI Inferred').length), /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-l"
  }, "Inferred")), /*#__PURE__*/React.createElement("button", {
    className: "tm-us__deselect-all",
    onClick: onDeselectAll
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "XCircle",
    size: 12
  }), " Deselect all")), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__body"
  }, grouped.map(([sector, cos]) => /*#__PURE__*/React.createElement("div", {
    key: sector,
    className: "tm-us__group"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-us__group-head"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 11,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, sector), /*#__PURE__*/React.createElement("span", {
    className: "tm-us__group-n"
  }, cos.length)), cos.map(c => /*#__PURE__*/React.createElement("div", {
    key: c.id,
    className: "tm-us__item"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: c.name,
    shape: "square",
    tone: "neutral",
    size: 26
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__item-info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-us__item-name"
  }, c.name), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__item-geo"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 8
  }), " ", c.city, ", ", c.country, /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6
    }
  }, "\xB7"), /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6
    }
  }, c.revenue))), /*#__PURE__*/React.createElement(Pill, {
    tone: relTone$(c.relevance),
    style: {
      fontSize: 9,
      padding: '1px 7px'
    }
  }, c.confidence, "%"), /*#__PURE__*/React.createElement("button", {
    className: "tm-us__remove",
    onClick: () => onToggle(c.id),
    title: "Remove"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 11
  })))))), selected.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-us__empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MousePointerClick",
    size: 28,
    color: "var(--border)"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 500,
      marginTop: 10
    }
  }, "No companies selected"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)',
      marginTop: 3
    }
  }, "Toggle companies in the table to add them here")))));
}
Object.assign(window, {
  UniverseSelectedPanel
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/universe-selected-old-version.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/universe-selected.jsx
try { (() => {
/* global React, Icon, Pill, Avatar, cx, initials */
// ── Universe Selected Panel — right slide-over overlay ───────────────────────

function relTone$(r) {
  return r === 'Direct' ? 'direct' : r === 'Adjacent' ? 'adjacent' : 'inferred';
}
function UniverseSelectedPanel({
  open,
  companies,
  selectedIds,
  onToggle,
  onClose,
  onDeselectAll
}) {
  const selected = React.useMemo(() => companies.filter(c => selectedIds.has(c.id)), [companies, selectedIds]);
  const grouped = React.useMemo(() => {
    const m = {};
    selected.forEach(c => {
      (m[c.sector] = m[c.sector] || []).push(c);
    });
    return Object.entries(m).sort((a, b) => b[1].length - a[1].length);
  }, [selected]);
  const countrySummary = React.useMemo(() => {
    const m = {};
    selected.forEach(c => {
      m[c.country] = (m[c.country] || 0) + 1;
    });
    return Object.entries(m).sort((a, b) => b[1] - a[1]);
  }, [selected]);
  return /*#__PURE__*/React.createElement(React.Fragment, null, open && /*#__PURE__*/React.createElement("div", {
    className: "tm-us__backdrop",
    onClick: onClose
  }), /*#__PURE__*/React.createElement("div", {
    className: cx('tm-us', open && 'is-open')
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-us__head"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 15,
      fontWeight: 700,
      display: 'flex',
      alignItems: 'center',
      gap: 8
    }
  }, "Selected Companies", /*#__PURE__*/React.createElement("span", {
    className: "tm-us__count"
  }, selected.length))), /*#__PURE__*/React.createElement("button", {
    className: "tm-us__close",
    onClick: onClose
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 16
  }))), selected.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-us__countries"
  }, countrySummary.map(([country, n]) => /*#__PURE__*/React.createElement("span", {
    key: country,
    className: "tm-us__country-chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 9
  }), " ", country, " ", /*#__PURE__*/React.createElement("b", null, n)))), selected.length > 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-us__stats"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-us__stat"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-v",
    style: {
      color: 'var(--success)'
    }
  }, selected.filter(c => c.relevance === 'Direct').length), /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-l"
  }, "Direct")), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__stat"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-v",
    style: {
      color: 'var(--info)'
    }
  }, selected.filter(c => c.relevance === 'Adjacent').length), /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-l"
  }, "Adjacent")), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__stat"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-v",
    style: {
      color: 'var(--warning)'
    }
  }, selected.filter(c => c.relevance === 'AI Inferred').length), /*#__PURE__*/React.createElement("span", {
    className: "tm-us__stat-l"
  }, "Inferred")), /*#__PURE__*/React.createElement("button", {
    className: "tm-us__deselect-all",
    onClick: onDeselectAll
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "XCircle",
    size: 12
  }), " Deselect all")), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__body"
  }, grouped.map(([sector, cos]) => /*#__PURE__*/React.createElement("div", {
    key: sector,
    className: "tm-us__group"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-us__group-head"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 11,
    color: "var(--muted-foreground)"
  }), /*#__PURE__*/React.createElement("span", null, sector), /*#__PURE__*/React.createElement("span", {
    className: "tm-us__group-n"
  }, cos.length)), cos.map(c => /*#__PURE__*/React.createElement("div", {
    key: c.id,
    className: "tm-us__item"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: c.name,
    shape: "square",
    tone: "neutral",
    size: 26
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__item-info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-us__item-name"
  }, c.name), /*#__PURE__*/React.createElement("div", {
    className: "tm-us__item-geo"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 8
  }), " ", c.city, ", ", c.country, /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6
    }
  }, "\xB7"), /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 6
    }
  }, c.revenue))), /*#__PURE__*/React.createElement(Pill, {
    tone: relTone$(c.relevance),
    style: {
      fontSize: 9,
      padding: '1px 7px'
    }
  }, c.confidence, "%"), /*#__PURE__*/React.createElement("button", {
    className: "tm-us__remove",
    onClick: () => onToggle(c.id),
    title: "Remove"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "X",
    size: 11
  })))))), selected.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-us__empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MousePointerClick",
    size: 28,
    color: "var(--border)"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 500,
      marginTop: 10
    }
  }, "No companies selected"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11,
      color: 'var(--muted-foreground)',
      marginTop: 3
    }
  }, "Toggle companies in the table to add them here")))));
}
Object.assign(window, {
  UniverseSelectedPanel
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/universe-selected.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/universe.jsx
try { (() => {
/* global React, Icon, Button, Pill, Avatar, cx, initials, TM_COMPANIES, TM_RATIONALE,
   TM_MASTER_SECTORS, TM_MASTER_COUNTRIES, TM_MASTER_REVENUE,
   SourcingCompanyCard, SourcingCriteriaBar, SourcingCriteriaEditor,
   SourcingFlyover, WorkspaceTopBar */
// ── Sourcing screen content (no sidebar — app.jsx provides MandateSidebar) ──
// Renders the criteria summary bar + sub-state tabs + company-card stream.
// All cross-screen state (statusMap, criteria, activeSubState, aiSearches…)
// is owned by app.jsx and threaded down as props, so the sidebar persists.

const TM_MASTER_EMPLOYEES = ['1K–5K', '5K–10K', '10K–50K', '>50K'];

// ── Default criteria derived from query ─────────────────────────────────────
function inferRoleFromQuery(query) {
  const q = (query || '').toLowerCase();
  if (q.includes('ceo')) return {
    position: 'Chief Executive Officer',
    keywords: ['CEO', 'Chief Executive', 'Managing Director', 'Group CEO']
  };
  if (q.includes('cto')) return {
    position: 'Chief Technology Officer',
    keywords: ['CTO', 'Chief Technology', 'VP Engineering']
  };
  if (q.includes('coo')) return {
    position: 'Chief Operating Officer',
    keywords: ['COO', 'Chief Operating', 'VP Operations']
  };
  if (q.includes('hr') || q.includes('people') || q.includes('chro')) return {
    position: 'Chief People Officer',
    keywords: ['CHRO', 'CPO', 'Chief People', 'HR Director']
  };
  return {
    position: 'Chief Financial Officer',
    keywords: ['CFO', 'Chief Financial', 'Finance Director']
  };
}
function defaultCriteriaFor(query) {
  const r = inferRoleFromQuery(query);
  return {
    position: r.position,
    sectors: ['FMCG', 'Food & Retail', 'Dairy'],
    countries: ['Saudi Arabia', 'UAE', 'Kuwait'],
    revenue: ['$500M–1B', '$1B–5B', '>$5B'],
    employees: ['5K–10K', '10K–50K', '>50K'],
    ownership: 'any',
    founderLed: 'preferred',
    questions: ['Has this company expanded into adjacent FMCG categories in the last 5 years?', 'Is the current CFO publicly disclosed and tenured ≥ 3 years (succession signal)?']
  };
}
function buildCardCriteria(crit) {
  return [{
    key: 'sector',
    label: 'Sector',
    getValue: c => c.sector,
    met: c => !crit.sectors.length || crit.sectors.includes(c.sector)
  }, {
    key: 'country',
    label: 'HQ',
    getValue: c => c.country,
    met: c => !crit.countries.length || crit.countries.includes(c.country)
  }, {
    key: 'revenue',
    label: 'Revenue',
    getValue: c => c.revenue,
    met: c => !crit.revenue.length || crit.revenue.includes(c.revenue)
  }, {
    key: 'employees',
    label: 'Employees',
    getValue: c => c.employees,
    met: c => !crit.employees.length || crit.employees.includes(c.employees)
  }, {
    key: 'ownership',
    label: 'Ownership',
    getValue: c => c.id % 3 === 0 ? 'Private' : c.id % 3 === 1 ? 'Public' : 'PE-backed',
    met: c => crit.ownership === 'any' || crit.ownership === (c.id % 3 === 0 ? 'private' : c.id % 3 === 1 ? 'public' : 'pe-backed'),
    preferred: true
  }, {
    key: 'founder',
    label: 'Founder-led',
    getValue: c => c.id % 2 === 0 ? 'Yes — founding family active' : 'No — professional management',
    met: c => crit.founderLed === 'any' || crit.founderLed === 'preferred' || crit.founderLed === 'required' && c.id % 2 === 0,
    preferred: true
  }];
}
function summaryChips(crit) {
  const arr = [];
  if (crit.position) arr.push({
    key: 'pos',
    label: 'Role',
    short: crit.position
  });
  if (crit.sectors?.length) arr.push({
    key: 'sec',
    label: 'Sectors',
    short: crit.sectors.slice(0, 2).join(' / ') + (crit.sectors.length > 2 ? ` +${crit.sectors.length - 2}` : '')
  });
  if (crit.countries?.length) arr.push({
    key: 'co',
    label: 'HQ',
    short: crit.countries.slice(0, 2).join(' · ') + (crit.countries.length > 2 ? ` +${crit.countries.length - 2}` : '')
  });
  if (crit.revenue?.length) arr.push({
    key: 'rev',
    label: 'Revenue',
    short: crit.revenue[0] + (crit.revenue.length > 1 ? ` +${crit.revenue.length - 1}` : '')
  });
  if (crit.founderLed && crit.founderLed !== 'any') arr.push({
    key: 'fl',
    label: 'Founder-led',
    short: crit.founderLed
  });
  return arr;
}
function countActive(crit) {
  return (crit.position ? 1 : 0) + (crit.sectors?.length || 0) + (crit.countries?.length || 0) + (crit.revenue?.length || 0) + (crit.employees?.length || 0) + (crit.ownership !== 'any' ? 1 : 0) + (crit.founderLed !== 'any' ? 1 : 0) + (crit.questions?.filter(q => q.trim()).length || 0);
}

// ── Main view ────────────────────────────────────────────────────────────────
function UniverseView({
  query,
  resume = false,
  mandateName,
  clientName,
  // Lifted state from app.jsx
  companies,
  statusMap,
  setStatus,
  activeSubState,
  onSubState,
  criteria,
  setCriteria,
  // Multi-search state
  activeSearch,
  // the currently selected AI sourcing run (or null)
  aiSearches,
  // [{id, label, count, ...}]
  onSelectSearch,
  onAddSearch,
  // Actions
  onConfirm,
  onSaveDraft
}) {
  // ── Local UI state ────────────────────────────────────────────────────────
  const [revealed, setRevealed] = React.useState(0);
  const [streaming, setStreaming] = React.useState(true);
  const [editorOpen, setEditorOpen] = React.useState(false);
  const [compact, setCompact] = React.useState(false);

  // Right-side flyover state. `mode` is 'company' | 'executive'. When an
  // executive is being viewed we keep the company on hand for the back arrow.
  const [flyover, setFlyover] = React.useState({
    open: false,
    mode: 'company',
    companyId: null,
    execId: null
  });
  const openCompany = React.useCallback(c => setFlyover({
    open: true,
    mode: 'company',
    companyId: c.id,
    execId: null
  }), []);
  const openExec = React.useCallback((c, e) => setFlyover({
    open: true,
    mode: 'executive',
    companyId: c.id,
    execId: e.id
  }), []);
  const backToCompany = React.useCallback(() => setFlyover(f => ({
    ...f,
    mode: 'company',
    execId: null
  })), []);
  const closeFlyover = React.useCallback(() => setFlyover(f => ({
    ...f,
    open: false
  })), []);

  // Derived: primary role keywords for ranking executives inside each card
  const primaryRoleKeywords = React.useMemo(() => inferRoleFromQuery(criteria.position || query).keywords, [criteria.position, query]);

  // ── Streaming reveal ──────────────────────────────────────────────────────
  React.useEffect(() => {
    if (resume) {
      setRevealed(companies.length);
      setStreaming(false);
      return;
    }
    setRevealed(0);
    setStreaming(true);
    let i = 0,
      timer;
    const tick = () => {
      i += 1;
      setRevealed(i);
      if (i >= companies.length) {
        setStreaming(false);
        return;
      }
      timer = setTimeout(tick, 180);
    };
    timer = setTimeout(tick, 240);
    return () => clearTimeout(timer);
  }, [query, resume, companies.length]);

  // ── Derived companies ─────────────────────────────────────────────────────
  const allRevealed = companies.slice(0, revealed);
  const isBucket = activeSubState === 'universe' || activeSubState === 'shortlisted' || activeSubState === 'declined';
  const showCriteriaBar = !isBucket; // criteria belong to AI sourced runs only

  const visible = React.useMemo(() => {
    let arr = allRevealed;
    if (activeSubState === 'shortlisted') arr = arr.filter(c => statusMap[c.id] === 'shortlisted');else if (activeSubState === 'declined') arr = arr.filter(c => statusMap[c.id] === 'declined');else if (activeSubState === 'universe') arr = arr.filter(c => statusMap[c.id] === 'approved' || statusMap[c.id] === 'in_universe');else {
      // 'aiSearch' — only the active search's companies, and only those still
      // un-triaged. Anything moved to universe/shortlist/declined drops out.
      const searchIds = activeSearch && activeSearch.companyIds ? new Set(activeSearch.companyIds) : null;
      arr = arr.filter(c => (!searchIds || searchIds.has(c.id)) && !statusMap[c.id]);
    }
    arr = [...arr].sort((a, b) => b.confidence - a.confidence);
    return arr;
  }, [allRevealed, activeSubState, statusMap, activeSearch]);

  // ── Counts (also computed in app.jsx for sidebar — recompute here for sub-tabs) ──
  const counts = React.useMemo(() => {
    const c = {
      universe: 0,
      shortlisted: 0,
      declined: 0,
      aiSourced: 0
    };
    for (const co of allRevealed) {
      const s = statusMap[co.id];
      if (s === 'declined') c.declined++;else if (s === 'shortlisted') c.shortlisted++;else if (s === 'approved' || s === 'in_universe') c.universe++;else c.aiSourced++;
    }
    return c;
  }, [allRevealed, statusMap]);
  const cardCriteria = React.useMemo(() => buildCardCriteria(criteria), [criteria]);
  const chips = React.useMemo(() => summaryChips(criteria), [criteria]);
  const totalActive = countActive(criteria);
  const extraCount = Math.max(0, totalActive - chips.length);

  // ── Confirm + actions ─────────────────────────────────────────────────────
  const approvedIds = allRevealed.filter(c => statusMap[c.id] === 'approved' || statusMap[c.id] === 'shortlisted').map(c => c.id);
  const canConfirm = approvedIds.length > 0;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-ws--sourcing tm-fadein"
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: activeSubState === 'universe' ? 'In universe' : activeSubState === 'shortlisted' ? 'Shortlisted' : activeSubState === 'declined' ? 'Declined' : activeSearch ? 'AI sourced · ' + activeSearch.label : 'AI sourced',
    title: mandateName || query || 'New executive search',
    subtitle: isBucket ? /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("b", null, visible.length), ' ', activeSubState === 'universe' ? 'companies in your universe' : activeSubState === 'shortlisted' ? 'companies shortlisted' : 'companies declined', /*#__PURE__*/React.createElement("span", {
      className: "tm-ws__sub-dot"
    }, "\xB7"), /*#__PURE__*/React.createElement("span", null, "across ", (aiSearches || []).length || 1, " sourcing run", (aiSearches || []).length === 1 ? '' : 's')) : /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("b", null, counts.aiSourced), " awaiting triage", /*#__PURE__*/React.createElement("span", {
      className: "tm-ws__sub-dot"
    }, "\xB7"), /*#__PURE__*/React.createElement("b", null, companies.reduce((s, c) => s + (c.execs?.length || 0), 0)), " executives mapped", streaming && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("span", {
      className: "tm-ws__sub-dot"
    }, "\xB7"), /*#__PURE__*/React.createElement("span", {
      className: "tm-ws__sub-stream"
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-ping"
    }, /*#__PURE__*/React.createElement("b", null), /*#__PURE__*/React.createElement("i", null)), " Discovering\u2026")))
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ws__view-toggle"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx(!compact && 'is-on'),
    onClick: () => setCompact(false)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "LayoutList",
    size: 12
  }), " Detailed"), /*#__PURE__*/React.createElement("button", {
    className: cx(compact && 'is-on'),
    onClick: () => setCompact(true)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Rows3",
    size: 12
  }), " Compact")), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 13
  }), "Export"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn-i",
    title: "More"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MoreVertical",
    size: 15
  }))), showCriteriaBar && /*#__PURE__*/React.createElement("div", {
    className: "tm-src__crbar-wrap"
  }, /*#__PURE__*/React.createElement(SourcingCriteriaBar, {
    criteria: chips,
    extraCount: extraCount,
    onEdit: () => setEditorOpen(true),
    onTalentReport: () => window.showToast && window.showToast('Universe report — coming soon')
  })), !isBucket && (aiSearches?.length || 0) > 1 && /*#__PURE__*/React.createElement("div", {
    className: "tm-src__runs"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src__runs-l"
  }, "Sourcing runs"), aiSearches.map(s => /*#__PURE__*/React.createElement("button", {
    key: s.id,
    className: cx('tm-src__run', activeSearch && s.id === activeSearch.id && 'is-on'),
    onClick: () => onSelectSearch && onSelectSearch(s.id),
    title: s.label
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-src__run-dot",
    "data-on": activeSearch && s.id === activeSearch.id || undefined
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-src__run-l"
  }, s.label), typeof s.count === 'number' && /*#__PURE__*/React.createElement("span", {
    className: "tm-src__run-n"
  }, s.count))), onAddSearch && /*#__PURE__*/React.createElement("button", {
    className: "tm-src__run tm-src__run--add",
    onClick: onAddSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 11
  }), /*#__PURE__*/React.createElement("span", null, "New run"))), /*#__PURE__*/React.createElement("div", {
    className: cx('tm-src__list', compact && 'is-compact')
  }, visible.map(c => /*#__PURE__*/React.createElement(SourcingCompanyCard, {
    key: c.id,
    c: c,
    criteria: cardCriteria,
    targetRole: criteria.position,
    primaryRoleKeywords: primaryRoleKeywords,
    status: statusMap[c.id] || 'universe',
    isOpen: flyover.open && flyover.companyId === c.id,
    onSelect: openCompany,
    onSelectExec: openExec,
    onShortlist: () => setStatus(c.id, 'shortlisted'),
    onApprove: () => setStatus(c.id, 'approved'),
    onDecline: () => setStatus(c.id, 'declined'),
    onAdd: () => window.showToast && window.showToast(`Added ${c.execs.length} executives from ${c.name} to outreach`),
    onComment: () => window.showToast && window.showToast('Comment thread opening')
  })), streaming && visible.length === 0 && Array.from({
    length: 3
  }).map((_, i) => /*#__PURE__*/React.createElement("div", {
    key: 'sk' + i,
    className: "tm-src-card tm-src-card--skel"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-skel",
    style: {
      height: 44,
      width: 44,
      borderRadius: 7
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-skel",
    style: {
      height: 14,
      width: '60%'
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-skel",
    style: {
      height: 10,
      width: '40%'
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-skel",
    style: {
      height: 100,
      width: '100%'
    }
  }))), !streaming && visible.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-src__empty"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "SearchX",
    size: 32,
    color: "var(--border)"
  }), /*#__PURE__*/React.createElement("h3", null, activeSubState === 'universe' ? 'No companies in your universe yet' : activeSubState === 'shortlisted' ? 'Nothing shortlisted yet' : activeSubState === 'declined' ? 'Nothing declined' : 'No more companies to triage'), /*#__PURE__*/React.createElement("p", null, activeSubState === 'universe' ? 'Triage companies from an AI sourcing run — use “Add to universe” on any card.' : activeSubState === 'shortlisted' ? 'Star companies from an AI sourcing run to shortlist them here.' : activeSubState === 'declined' ? 'Decline companies from an AI sourcing run to send them here.' : 'Every card from this run has been triaged. Widen the criteria, start a new run, or jump to In universe.'), !isBucket ? /*#__PURE__*/React.createElement("button", {
    className: "tm-src__empty-btn",
    onClick: () => setEditorOpen(true)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "SlidersHorizontal",
    size: 13
  }), " Adjust criteria") : /*#__PURE__*/React.createElement("button", {
    className: "tm-src__empty-btn",
    onClick: () => onSubState && onSubState('aiSearch')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13
  }), " Back to AI sourced"))), (() => {
    const co = companies.find(x => x.id === flyover.companyId);
    if (!co) return null;
    const ex = flyover.mode === 'executive' ? co.execs.find(x => x.id === flyover.execId) : null;
    const results = cardCriteria.map(cr => ({
      ...cr,
      value: cr.getValue(co),
      met: cr.met(co)
    }));
    return /*#__PURE__*/React.createElement(SourcingFlyover, {
      open: flyover.open,
      view: flyover.mode,
      company: co,
      exec: ex,
      status: statusMap[co.id] || 'universe',
      criteriaResults: results,
      targetRole: criteria.position,
      primaryRoleKeywords: primaryRoleKeywords,
      onClose: closeFlyover,
      onShowExec: e => openExec(co, e),
      onBack: backToCompany
    });
  })(), /*#__PURE__*/React.createElement(SourcingCriteriaEditor, {
    open: editorOpen,
    onClose: () => setEditorOpen(false),
    onApply: s => setCriteria(s),
    initial: criteria,
    options: {
      sectors: TM_MASTER_SECTORS || ['FMCG', 'Food & Retail', 'Dairy', 'Food Service', 'Agri & Dairy'],
      countries: TM_MASTER_COUNTRIES || ['Saudi Arabia', 'UAE', 'Kuwait', 'Qatar', 'Bahrain', 'Oman', 'Egypt'],
      revenue: TM_MASTER_REVENUE || ['<$100M', '$100M–500M', '$500M–1B', '$1B–5B', '>$5B'],
      employees: TM_MASTER_EMPLOYEES
    }
  }));
}
Object.assign(window, {
  UniverseView,
  defaultCriteriaFor,
  inferRoleFromQuery
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/universe.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/views.jsx
try { (() => {
/* global React, Icon, cx */
// ── Dashboard: Executive Briefing ────────────────────────────────────────────

const DB_SECTIONS = [{
  id: 'progress',
  n: '01',
  label: 'Mapping progress'
}, {
  id: 'market',
  n: '02',
  label: 'Shape of the market'
}, {
  id: 'geography',
  n: '03',
  label: 'Where talent sits'
}, {
  id: 'interest',
  n: '04',
  label: 'Interest & status'
}, {
  id: 'quality',
  n: '05',
  label: 'Data quality'
}, {
  id: 'actions',
  n: '·',
  label: 'Next actions'
}];

/* ── tiny chart helpers ─────────────────────────────────────────────────────── */
function DBRing({
  pct,
  size = 96,
  stroke = 8,
  color = 'var(--primary)'
}) {
  const r = (size - stroke) / 2,
    c = 2 * Math.PI * r,
    off = c - pct / 100 * c;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'relative',
      width: size,
      height: size,
      flexShrink: 0
    }
  }, /*#__PURE__*/React.createElement("svg", {
    width: size,
    height: size,
    style: {
      transform: 'rotate(-90deg)'
    }
  }, /*#__PURE__*/React.createElement("circle", {
    cx: size / 2,
    cy: size / 2,
    r: r,
    fill: "none",
    stroke: "var(--db-fill)",
    strokeWidth: stroke
  }), /*#__PURE__*/React.createElement("circle", {
    cx: size / 2,
    cy: size / 2,
    r: r,
    fill: "none",
    stroke: color,
    strokeWidth: stroke,
    strokeDasharray: c,
    strokeDashoffset: off,
    strokeLinecap: "round",
    style: {
      transition: 'stroke-dashoffset .8s'
    }
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'absolute',
      inset: 0,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 22,
      fontWeight: 700
    }
  }, pct, "%")));
}
function DBBar({
  label,
  value,
  max,
  pct,
  color = 'var(--primary)',
  showPct
}) {
  const w = Math.max(value / max * 100, 3);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      padding: '5px 0'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "db-bar-label"
  }, label), /*#__PURE__*/React.createElement("div", {
    className: "db-bar-track"
  }, /*#__PURE__*/React.createElement("div", {
    className: "db-bar-fill",
    style: {
      width: w + '%',
      background: color
    }
  })), /*#__PURE__*/React.createElement("span", {
    className: "db-bar-val"
  }, value, showPct ? /*#__PURE__*/React.createElement("span", {
    className: "db-bar-pct"
  }, " ", pct || Math.round(w), "%") : ''));
}
function DBStackBar({
  segments,
  height = 20
}) {
  const total = segments.reduce((s, g) => s + g.n, 0);
  return /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "db-stack",
    style: {
      height
    }
  }, segments.map((s, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    style: {
      width: s.n / total * 100 + '%',
      background: s.color,
      borderRadius: i === 0 ? '4px 0 0 4px' : i === segments.length - 1 ? '0 4px 4px 0' : 0
    }
  }))), /*#__PURE__*/React.createElement("div", {
    className: "db-stack-legend"
  }, segments.map((s, i) => /*#__PURE__*/React.createElement("span", {
    key: i,
    className: "db-stack-leg-item"
  }, /*#__PURE__*/React.createElement("span", {
    className: "db-stack-dot",
    style: {
      background: s.color
    }
  }), s.label, " ", /*#__PURE__*/React.createElement("b", null, s.n)))));
}
function DBProgressChart({
  data,
  target,
  w = 600,
  h = 130
}) {
  const pl = 8,
    pr = 8,
    pt = 16,
    pb = 4,
    n = data.length,
    last = n - 1;
  const X = i => pl + i / (n - 1) * (w - pl - pr);
  const Y = v => pt + (1 - v / target) * (h - pt - pb);
  const pts = data.map((v, i) => `${X(i)},${Y(v)}`).join(' ');
  const area = `M${X(0)},${Y(0)} ` + data.map((v, i) => `L${X(i)},${Y(v)}`).join(' ') + ` L${X(last)},${Y(0)} Z`;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'relative',
      marginTop: 6
    }
  }, /*#__PURE__*/React.createElement("svg", {
    viewBox: `0 0 ${w} ${h}`,
    preserveAspectRatio: "none",
    style: {
      width: '100%',
      height: 140,
      display: 'block'
    }
  }, /*#__PURE__*/React.createElement("line", {
    x1: pl,
    y1: Y(target),
    x2: w - pr,
    y2: Y(target),
    stroke: "var(--db-line)",
    strokeWidth: "1",
    strokeDasharray: "4 4",
    vectorEffect: "non-scaling-stroke"
  }), /*#__PURE__*/React.createElement("line", {
    x1: pl,
    y1: Y(0),
    x2: w - pr,
    y2: Y(0),
    stroke: "var(--db-line)",
    strokeWidth: "1",
    vectorEffect: "non-scaling-stroke"
  }), /*#__PURE__*/React.createElement("path", {
    d: area,
    fill: "color-mix(in srgb, var(--primary) 10%, transparent)"
  }), /*#__PURE__*/React.createElement("polyline", {
    points: pts,
    fill: "none",
    stroke: "var(--primary)",
    strokeWidth: "2",
    strokeLinejoin: "round",
    vectorEffect: "non-scaling-stroke"
  }), /*#__PURE__*/React.createElement("circle", {
    cx: X(last),
    cy: Y(data[last]),
    r: "3.5",
    fill: "var(--primary)"
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      fontSize: 10,
      color: 'var(--muted-foreground)',
      marginTop: 4,
      fontFamily: 'var(--font-mono, monospace)'
    }
  }, /*#__PURE__*/React.createElement("span", null, "Day 1"), /*#__PURE__*/React.createElement("span", null, "Day ", Math.round(n / 2)), /*#__PURE__*/React.createElement("span", null, "Day ", n, " \xB7 today")));
}
function DBFunnel({
  steps
}) {
  const max = steps[0].n;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 6
    }
  }, steps.map((s, i) => {
    const w = Math.max(s.n / max * 100, 12);
    const pct = i > 0 ? Math.round(s.n / steps[i - 1].n * 100) : 100;
    return /*#__PURE__*/React.createElement("div", {
      key: i,
      className: "db-funnel-row"
    }, /*#__PURE__*/React.createElement("span", {
      className: "db-funnel-label"
    }, s.label), /*#__PURE__*/React.createElement("div", {
      className: "db-funnel-track"
    }, /*#__PURE__*/React.createElement("div", {
      className: "db-funnel-fill",
      style: {
        width: w + '%',
        opacity: 1 - i * 0.12
      }
    })), /*#__PURE__*/React.createElement("span", {
      className: "db-funnel-val"
    }, s.n), i > 0 && /*#__PURE__*/React.createElement("span", {
      className: "db-funnel-pct"
    }, pct, "%"));
  }));
}
function DBMeter({
  label,
  value,
  avg,
  max = 100
}) {
  const above = value >= avg;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      padding: '5px 0'
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "db-bar-label"
  }, label), /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'relative',
      flex: 1,
      height: 8,
      background: 'var(--db-fill)',
      borderRadius: 4
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      position: 'absolute',
      left: avg + '%',
      top: -2,
      bottom: -2,
      width: 2,
      background: 'var(--muted-foreground)',
      opacity: 0.4
    }
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'block',
      height: '100%',
      width: value + '%',
      background: above ? 'var(--success, #3f7d52)' : 'var(--warning, #c69a5a)',
      borderRadius: 4
    }
  })), /*#__PURE__*/React.createElement("span", {
    className: "db-bar-val"
  }, value, "%"));
}
function DBStat({
  value,
  label,
  tone
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "db-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "db-stat-v",
    style: tone ? {
      color: tone
    } : null
  }, value), /*#__PURE__*/React.createElement("div", {
    className: "db-stat-l"
  }, label));
}
function DBCard({
  children,
  style
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "db-card",
    style: style
  }, children);
}
function DBSecHead({
  s,
  lede,
  note
}) {
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sec-eyebrow",
    id: 'sec-' + s.id
  }, /*#__PURE__*/React.createElement("span", {
    className: "db-sec-num"
  }, s.n), /*#__PURE__*/React.createElement("span", {
    className: "db-sec-k"
  }, s.label)), lede && /*#__PURE__*/React.createElement("h2", {
    className: "db-lede",
    dangerouslySetInnerHTML: {
      __html: lede
    }
  }), note && /*#__PURE__*/React.createElement("p", {
    className: "db-note"
  }, note));
}

/* ── Main Dashboard component ─────────────────────────────────────────────── */
function Dashboard({
  companies
}) {
  const [active, setActive] = React.useState('progress');
  const scrollRef = React.useRef(null);

  // ── Derived data ──
  const totalCos = companies.length;
  const allExecs = companies.flatMap(c => c.execs);
  const totalExecs = allExecs.length;
  const countries = [...new Set(companies.map(c => c.country))];
  const enriched = allExecs.filter(e => e.enriched).length;
  const verified = allExecs.filter(e => e.verified).length;
  const enrichPct = Math.round(enriched / totalExecs * 100);
  const verifyPct = Math.round(verified / totalExecs * 100);
  const mappedPct = Math.min(Math.round(totalCos / (totalCos + 8) * 100), 100); // simulated target

  // By sector
  const bySector = {};
  companies.forEach(c => {
    bySector[c.sector] = (bySector[c.sector] || 0) + 1;
  });
  const sectors = Object.entries(bySector).sort((a, b) => b[1] - a[1]);
  const maxSector = Math.max(...sectors.map(s => s[1]), 1);

  // By country
  const byCountry = {};
  companies.forEach(c => {
    byCountry[c.country] = (byCountry[c.country] || 0) + 1;
  });
  const countryRows = Object.entries(byCountry).sort((a, b) => b[1] - a[1]);
  const maxCountry = Math.max(...countryRows.map(c => c[1]), 1);

  // By level
  const byLevel = {};
  allExecs.forEach(e => {
    byLevel[e.level] = (byLevel[e.level] || 0) + 1;
  });
  const levels = Object.entries(byLevel).sort((a, b) => b[1] - a[1]);
  const maxLevel = Math.max(...levels.map(l => l[1]), 1);

  // By relevance
  const byRel = {};
  companies.forEach(c => {
    byRel[c.relevance] = (byRel[c.relevance] || 0) + 1;
  });
  const relColors = {
    Direct: 'var(--success, #3f7d52)',
    Adjacent: 'var(--primary)',
    'AI Inferred': 'var(--warning, #c69a5a)'
  };
  const relSegs = Object.entries(byRel).map(([label, n]) => ({
    label,
    n,
    color: relColors[label] || 'var(--muted)'
  }));

  // Simulated progress data
  const progressData = Array.from({
    length: 18
  }, (_, i) => Math.round(totalExecs * Math.min((i + 1) / 18, 1) * (0.7 + Math.random() * 0.3)));
  progressData[progressData.length - 1] = totalExecs;

  // Confidence stats
  const avgConf = Math.round(companies.reduce((s, c) => s + c.confidence, 0) / totalCos);
  const highConf = companies.filter(c => c.confidence >= 75).length;
  const lowConf = companies.filter(c => c.confidence < 50).length;

  // Funnel data
  const funnel = [{
    label: 'Executives mapped',
    n: totalExecs
  }, {
    label: 'Profile enriched',
    n: enriched || Math.round(totalExecs * 0.45)
  }, {
    label: 'Verified',
    n: verified || Math.round(totalExecs * 0.35)
  }, {
    label: 'Contact captured',
    n: Math.round(totalExecs * 0.28)
  }, {
    label: 'Interested',
    n: Math.round(totalExecs * 0.18)
  }];

  // Sector × Level heatmap
  const heatData = {};
  companies.forEach(c => c.execs.forEach(e => {
    const key = c.sector + '|' + e.level;
    heatData[key] = (heatData[key] || 0) + 1;
  }));
  const heatSectors = sectors.slice(0, 6).map(s => s[0]);
  const heatLevels = ['C-Suite', 'N-1'];
  const heatMax = Math.max(...Object.values(heatData), 1);

  // ── Scroll spy ──
  React.useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const onScroll = () => {
      let cur = DB_SECTIONS[0].id;
      for (const s of DB_SECTIONS) {
        const node = document.getElementById('sec-' + s.id);
        if (node && node.offsetTop - el.scrollTop <= 140) cur = s.id;
      }
      setActive(cur);
    };
    el.addEventListener('scroll', onScroll, {
      passive: true
    });
    onScroll();
    return () => el.removeEventListener('scroll', onScroll);
  }, []);
  const jump = id => {
    const el = scrollRef.current,
      node = document.getElementById('sec-' + id);
    if (el && node) el.scrollTo({
      top: node.offsetTop - 16,
      behavior: 'smooth'
    });
  };
  const directCos = companies.filter(c => c.relevance === 'Direct').length;
  const interestedEst = Math.round(totalExecs * 0.32);
  const offLimits = Math.round(totalExecs * 0.09);
  return /*#__PURE__*/React.createElement("div", {
    className: "db-root tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "db-scroll",
    ref: scrollRef
  }, /*#__PURE__*/React.createElement("div", {
    className: "db-wrap"
  }, /*#__PURE__*/React.createElement("nav", {
    className: "db-nav"
  }, /*#__PURE__*/React.createElement("div", {
    className: "db-nav-h"
  }, "On this page"), DB_SECTIONS.map(s => /*#__PURE__*/React.createElement("button", {
    key: s.id,
    className: cx('db-nav-i', active === s.id && 'is-active'),
    onClick: () => jump(s.id)
  }, /*#__PURE__*/React.createElement("b", null, s.n), /*#__PURE__*/React.createElement("span", null, s.label)))), /*#__PURE__*/React.createElement("div", {
    className: "db-main"
  }, /*#__PURE__*/React.createElement("div", {
    className: "db-eyebrow"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 12
  }), " Talent mapping report"), /*#__PURE__*/React.createElement("h1", {
    className: "db-title"
  }, "FMCG & Food \u2014 GCC Executive Map"), /*#__PURE__*/React.createElement("div", {
    className: "db-meta"
  }, totalCos, "-company universe \xB7 ", totalExecs, " executives mapped \xB7 synced 2h ago"), /*#__PURE__*/React.createElement("hr", {
    className: "db-rule"
  }), /*#__PURE__*/React.createElement("h2", {
    className: "db-verdict"
  }, "The map is ", /*#__PURE__*/React.createElement("span", {
    className: "db-hi"
  }, mappedPct, "% complete"), ". Talent is concentrated in ", countries.length > 3 ? countries.length : 'three', " markets, and you're competitively placed for ", /*#__PURE__*/React.createElement("span", {
    className: "db-hi"
  }, interestedEst), " of mapped executives."), /*#__PURE__*/React.createElement("p", {
    className: "db-summary"
  }, directCos, " of ", totalCos, " target companies mapped directly, ", totalCos - directCos, " via adjacent or AI inference. ", enriched || Math.round(totalExecs * 0.45), " executives enriched; ", offLimits, " are off-limits. Coverage is deepest at C-Suite level and thinnest at N-1, with the strongest presence in ", countryRows[0]?.[0] || 'UAE', "."), /*#__PURE__*/React.createElement("div", {
    className: "db-ribbon"
  }, /*#__PURE__*/React.createElement(DBStat, {
    value: `${directCos}/${totalCos}`,
    label: "Mapped",
    tone: "var(--primary)"
  }), /*#__PURE__*/React.createElement(DBStat, {
    value: totalExecs,
    label: "Executives"
  }), /*#__PURE__*/React.createElement(DBStat, {
    value: interestedEst,
    label: "Interested",
    tone: "var(--success, #3f7d52)"
  }), /*#__PURE__*/React.createElement(DBStat, {
    value: offLimits,
    label: "Off-limits",
    tone: "var(--destructive)"
  }), /*#__PURE__*/React.createElement(DBStat, {
    value: avgConf + '%',
    label: "Avg confidence"
  }), /*#__PURE__*/React.createElement(DBStat, {
    value: countries.length,
    label: "Countries"
  })), /*#__PURE__*/React.createElement(DBSecHead, {
    s: DB_SECTIONS[0],
    lede: `<span class="db-hi">${totalExecs}</span> executives mapped across <span class="db-hi">${totalCos}</span> companies in <span class="db-hi">${Math.round(18)}</span> days.`,
    note: `Velocity: ${(totalExecs / 18).toFixed(1)} executives per day. Target: ${totalExecs + 20}.`
  }), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      marginBottom: 8
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "db-sub"
  }, "Mapping velocity"), /*#__PURE__*/React.createElement("span", {
    className: "db-tag"
  }, totalExecs, " mapped"), /*#__PURE__*/React.createElement("span", {
    className: "db-tag db-tag--muted"
  }, "target ", totalExecs + 20)), /*#__PURE__*/React.createElement(DBProgressChart, {
    data: progressData,
    target: totalExecs + 20
  })), /*#__PURE__*/React.createElement("div", {
    className: "db-grid-2"
  }, /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 12
    }
  }, "Completion by country"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 20
    }
  }, /*#__PURE__*/React.createElement(DBRing, {
    pct: mappedPct
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }, countryRows.map(([c, n]) => /*#__PURE__*/React.createElement(DBBar, {
    key: c,
    label: c,
    value: n,
    max: maxCountry,
    color: "var(--primary)",
    showPct: true
  }))))), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 12
    }
  }, "By relevance type"), /*#__PURE__*/React.createElement(DBStackBar, {
    segments: relSegs,
    height: 24
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 16
    }
  }, [['High confidence (≥75%)', highConf], ['Medium (50–74%)', totalCos - highConf - lowConf], ['Low (<50%)', lowConf]].map(([l, n], i) => /*#__PURE__*/React.createElement(DBBar, {
    key: i,
    label: l,
    value: n,
    max: totalCos,
    color: i === 0 ? 'var(--success, #3f7d52)' : i === 1 ? 'var(--primary)' : 'var(--warning, #c69a5a)'
  }))))), /*#__PURE__*/React.createElement(DBSecHead, {
    s: DB_SECTIONS[1],
    lede: `The universe spans <span class="db-hi">${sectors.length} sectors</span>. FMCG dominates with ${sectors[0]?.[1] || 0} companies.`
  }), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 10
    }
  }, "Sector \xD7 seniority heatmap"), /*#__PURE__*/React.createElement("div", {
    className: "db-heat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "db-heat-corner"
  }), heatLevels.map(l => /*#__PURE__*/React.createElement("div", {
    key: l,
    className: "db-heat-colh"
  }, l)), /*#__PURE__*/React.createElement("div", {
    className: "db-heat-colh",
    style: {
      color: 'var(--muted-foreground)'
    }
  }, "Total"), heatSectors.map(sec => {
    const rowTotal = heatLevels.reduce((s, l) => s + (heatData[sec + '|' + l] || 0), 0);
    return /*#__PURE__*/React.createElement(React.Fragment, {
      key: sec
    }, /*#__PURE__*/React.createElement("div", {
      className: "db-heat-rowh"
    }, sec), heatLevels.map(l => {
      const n = heatData[sec + '|' + l] || 0;
      const intensity = n / Math.max(heatMax, 1);
      return /*#__PURE__*/React.createElement("div", {
        key: l,
        className: "db-heat-cell",
        style: {
          background: n > 0 ? `color-mix(in srgb, var(--primary) ${Math.round(intensity * 60 + 10)}%, var(--db-fill))` : 'var(--db-fill)'
        }
      }, n || '—');
    }), /*#__PURE__*/React.createElement("div", {
      className: "db-heat-cell db-heat-total"
    }, rowTotal));
  }))), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 10
    }
  }, "Companies by sector"), sectors.map(([s, n]) => /*#__PURE__*/React.createElement(DBBar, {
    key: s,
    label: s,
    value: n,
    max: maxSector,
    color: "var(--primary)",
    showPct: true
  }))), /*#__PURE__*/React.createElement(DBSecHead, {
    s: DB_SECTIONS[2],
    lede: `Talent is concentrated in <span class="db-hi">${countryRows[0]?.[0]}</span> and <span class="db-hi">${countryRows[1]?.[0] || 'UAE'}</span>, which together account for ${Math.round(((countryRows[0]?.[1] || 0) + (countryRows[1]?.[1] || 0)) / totalCos * 100)}% of the universe.`
  }), /*#__PURE__*/React.createElement("div", {
    className: "db-grid-2"
  }, /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 10
    }
  }, "Companies by country"), countryRows.map(([c, n]) => {
    const pct = Math.round(n / totalCos * 100);
    return /*#__PURE__*/React.createElement(DBBar, {
      key: c,
      label: c,
      value: n,
      max: maxCountry,
      color: "var(--primary)",
      showPct: true,
      pct: pct
    });
  })), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 10
    }
  }, "Executives by seniority"), levels.map(([l, n]) => /*#__PURE__*/React.createElement(DBBar, {
    key: l,
    label: l,
    value: n,
    max: maxLevel,
    color: "var(--primary)",
    showPct: true
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 14,
      padding: '10px 12px',
      background: 'color-mix(in srgb, var(--primary) 6%, transparent)',
      borderRadius: 8,
      fontSize: 12,
      color: 'var(--muted-foreground)',
      lineHeight: 1.5
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Info",
    size: 12,
    style: {
      marginRight: 4,
      verticalAlign: -1
    }
  }), "C-Suite is the deepest layer. N-1 coverage is thin \u2014 consider prioritising."))), /*#__PURE__*/React.createElement(DBSecHead, {
    s: DB_SECTIONS[3],
    lede: `<span class="db-hi">${interestedEst}</span> executives show interest. The conversion funnel narrows at the engagement stage.`
  }), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 12
    }
  }, "Engagement funnel"), /*#__PURE__*/React.createElement(DBFunnel, {
    steps: funnel
  })), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 10
    }
  }, "Interest breakdown"), /*#__PURE__*/React.createElement(DBStackBar, {
    segments: [{
      label: 'Interested',
      n: interestedEst,
      color: 'var(--success, #3f7d52)'
    }, {
      label: 'Passive',
      n: totalExecs - interestedEst - offLimits,
      color: 'var(--muted-foreground)'
    }, {
      label: 'Off-limits',
      n: offLimits,
      color: 'var(--destructive)'
    }],
    height: 24
  })), /*#__PURE__*/React.createElement(DBSecHead, {
    s: DB_SECTIONS[4],
    lede: `Data quality averages <span class="db-hi">${avgConf}%</span> confidence. Enrichment is at ${enrichPct || 45}%.`
  }), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 10
    }
  }, "Quality metrics ", /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontWeight: 400,
      textTransform: 'none',
      letterSpacing: 0
    }
  }, "\xB7 tick = team average")), /*#__PURE__*/React.createElement(DBMeter, {
    label: "Enriched",
    value: enrichPct || 45,
    avg: 50
  }), /*#__PURE__*/React.createElement(DBMeter, {
    label: "Verified",
    value: verifyPct || 35,
    avg: 40
  }), /*#__PURE__*/React.createElement(DBMeter, {
    label: "Contact captured",
    value: 28,
    avg: 35
  }), /*#__PURE__*/React.createElement(DBMeter, {
    label: "Avg confidence",
    value: avgConf,
    avg: 70
  })), /*#__PURE__*/React.createElement("div", {
    className: "db-grid-2"
  }, /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 8
    }
  }, "Confidence distribution"), [['≥ 80%', companies.filter(c => c.confidence >= 80).length, 'var(--success, #3f7d52)'], ['60–79%', companies.filter(c => c.confidence >= 60 && c.confidence < 80).length, 'var(--primary)'], ['40–59%', companies.filter(c => c.confidence >= 40 && c.confidence < 60).length, 'var(--warning, #c69a5a)'], ['< 40%', companies.filter(c => c.confidence < 40).length, 'var(--destructive)']].map(([l, n, col]) => /*#__PURE__*/React.createElement(DBBar, {
    key: l,
    label: l,
    value: n,
    max: totalCos,
    color: col
  }))), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-sub",
    style: {
      marginBottom: 8
    }
  }, "Top confidence companies"), companies.sort((a, b) => b.confidence - a.confidence).slice(0, 5).map(c => /*#__PURE__*/React.createElement("div", {
    key: c.id,
    className: "db-co-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: "db-co-name"
  }, c.name), /*#__PURE__*/React.createElement("span", {
    className: "db-co-conf"
  }, c.confidence, "%"), /*#__PURE__*/React.createElement("div", {
    className: "db-co-bar"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: c.confidence + '%',
      background: 'var(--primary)'
    }
  })))))), /*#__PURE__*/React.createElement(DBSecHead, {
    s: DB_SECTIONS[5]
  }), /*#__PURE__*/React.createElement(DBCard, null, /*#__PURE__*/React.createElement("div", {
    className: "db-actions"
  }, [{
    icon: 'AlertCircle',
    tone: 'warn',
    text: `${totalCos - directCos} companies mapped via inference — verify relevance`,
    sub: 'Review AI-inferred and adjacent companies for accuracy'
  }, {
    icon: 'Users',
    tone: 'info',
    text: 'Deepen N-1 coverage — currently the thinnest layer',
    sub: 'Prioritise mapping VP and Director-level executives'
  }, {
    icon: 'TrendingUp',
    tone: 'ok',
    text: `${interestedEst} interested executives — begin engagement`,
    sub: 'Move interested candidates into active outreach pipeline'
  }, {
    icon: 'ShieldAlert',
    tone: 'risk',
    text: `${offLimits} off-limits contacts flagged — review compliance`,
    sub: 'Ensure off-limits designations are current and documented'
  }, {
    icon: 'BarChart3',
    tone: 'info',
    text: `Enrich remaining ${totalExecs - (enriched || Math.round(totalExecs * 0.45))} profiles`,
    sub: 'Run batch enrichment to improve data completeness'
  }].map((a, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: "db-action-row"
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('db-action-ic', 'is-' + a.tone)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: a.icon,
    size: 14
  })), /*#__PURE__*/React.createElement("div", {
    className: "db-action-body"
  }, /*#__PURE__*/React.createElement("div", {
    className: "db-action-text"
  }, a.text), /*#__PURE__*/React.createElement("div", {
    className: "db-action-sub"
  }, a.sub)), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14,
    color: "var(--muted-foreground)"
  }))))), /*#__PURE__*/React.createElement("div", {
    className: "db-footer"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 12
  }), "Report generated by AI \xB7 Last synced 2h ago \xB7 Data reflects current universe state")))));
}
Object.assign(window, {
  Dashboard
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/views.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/worklist.jsx
try { (() => {
/* global React, Icon, Button, Avatar, cx, formatAge, dueMeta, AccountAvatar, AccountTypePill,
   TM_CONTACTS, TM_ACCOUNTS, TM_BD_STAGES, TM_BD_DEALS, tmBuildContactProfile, tmBuildAccountTasks, tmFindAccountByName */
// ── Worklist: My Day (task rollup) + Business Development pipeline ────────────

const CRM_USER = 'LH'; // "me" for the My Day view
const OWNER_NAMES = {
  LH: 'Layla Hassan',
  OK: 'Omar Khalil',
  SM: 'Sara Mitchell',
  FO: 'Farah Obeid'
};

// Gather every follow-up across contacts + accounts into one worklist, tagged
// with its source record so each row links back. Reads the same per-record
// generators the detail screens use, so nothing drifts.
function tmCollectTasks() {
  const out = [];
  (window.TM_CONTACTS || []).forEach(c => {
    const p = window.tmBuildContactProfile(c);
    (p.tasks || []).forEach(t => out.push({
      ...t,
      source: c.name,
      sourceSub: c.title,
      sourceType: 'contact',
      refId: c.id
    }));
  });
  (window.TM_ACCOUNTS || []).forEach(a => {
    (window.tmBuildAccountTasks ? window.tmBuildAccountTasks(a) : []).forEach(t => out.push({
      ...t,
      source: a.name,
      sourceSub: a.type,
      sourceType: 'account',
      refId: a.id
    }));
  });
  return out;
}
const DUE_BUCKETS = [{
  id: 'overdue',
  label: 'Overdue',
  test: d => d < 0,
  accent: '#b91c1c'
}, {
  id: 'today',
  label: 'Today',
  test: d => d === 0,
  accent: '#b45309'
}, {
  id: 'week',
  label: 'This week',
  test: d => d > 0 && d <= 7,
  accent: 'var(--primary)'
}, {
  id: 'later',
  label: 'Later',
  test: d => d > 7,
  accent: 'var(--muted-foreground)'
}];
function MyDayScreen({
  onOpenContact,
  onOpenAccount
}) {
  const allTasks = React.useMemo(() => tmCollectTasks(), []);
  const [done, setDone] = React.useState(() => new Set());
  const [scope, setScope] = React.useState('mine'); // mine | team
  const [kind, setKind] = React.useState('all'); // all | contact | account
  const [showDone, setShowDone] = React.useState(false);
  const toggle = id => setDone(prev => {
    const n = new Set(prev);
    n.has(id) ? n.delete(id) : n.add(id);
    return n;
  });
  const visible = allTasks.filter(t => {
    if (scope === 'mine' && t.assignee !== CRM_USER) return false;
    if (kind !== 'all' && t.sourceType !== kind) return false;
    return true;
  });
  const openTasks = visible.filter(t => !done.has(t.id));
  const doneTasks = visible.filter(t => done.has(t.id));
  const overdue = openTasks.filter(t => t.dueDays < 0).length;
  const today = openTasks.filter(t => t.dueDays === 0).length;
  const week = openTasks.filter(t => t.dueDays > 0 && t.dueDays <= 7).length;
  const openRecord = t => {
    if (t.sourceType === 'contact') onOpenContact && onOpenContact(t.refId);else onOpenAccount && onOpenAccount(t.refId);
  };
  const todayStr = new Date().toLocaleDateString('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'short'
  });
  const TaskRow = ({
    t
  }) => {
    const dm = dueMeta(t.dueDays);
    const isDone = done.has(t.id);
    return /*#__PURE__*/React.createElement("div", {
      className: "tm-task-row"
    }, /*#__PURE__*/React.createElement("button", {
      className: "tm-cd__task-check",
      onClick: () => toggle(t.id),
      title: isDone ? 'Mark not done' : 'Mark done'
    }, /*#__PURE__*/React.createElement(Icon, {
      name: isDone ? 'CheckSquare' : 'Square',
      size: 17,
      color: isDone ? 'var(--success, #059669)' : 'var(--muted-foreground)'
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        minWidth: 0
      }
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 13.5,
        fontWeight: 500,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        textDecoration: isDone ? 'line-through' : 'none',
        color: isDone ? 'var(--muted-foreground)' : 'var(--foreground)'
      }
    }, t.title), /*#__PURE__*/React.createElement("button", {
      className: "tm-task-src",
      onClick: () => openRecord(t)
    }, /*#__PURE__*/React.createElement(Icon, {
      name: t.sourceType === 'contact' ? 'User' : 'Building2',
      size: 11
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, t.source), /*#__PURE__*/React.createElement("span", {
      style: {
        color: 'var(--muted-foreground)',
        fontWeight: 400
      }
    }, "\xB7 ", t.sourceSub))), !isDone && /*#__PURE__*/React.createElement("span", {
      className: "tm-pill",
      style: {
        background: dm.bg,
        color: dm.fg,
        fontSize: 11
      }
    }, dm.label), scope === 'team' && /*#__PURE__*/React.createElement("span", {
      className: "tm-pl__av",
      style: {
        width: 22,
        height: 22,
        fontSize: 9
      },
      title: OWNER_NAMES[t.assignee]
    }, t.assignee));
  };
  const stat = (label, val, tone) => /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__v",
    style: tone ? {
      color: tone
    } : null
  }, val), /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__l"
  }, label));
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pscreen__head"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10,
      whiteSpace: 'nowrap'
    }
  }, "CRM \xB7 ", todayStr), /*#__PURE__*/React.createElement("h1", {
    className: "tm-pscreen__title"
  }, "My Day")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 8,
      alignItems: 'center'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-seg"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx(scope === 'mine' && 'is-on'),
    onClick: () => setScope('mine')
  }, "Mine"), /*#__PURE__*/React.createElement("button", {
    className: cx(scope === 'team' && 'is-on'),
    onClick: () => setScope('team')
  }, "Team")), /*#__PURE__*/React.createElement("div", {
    className: "tm-seg"
  }, /*#__PURE__*/React.createElement("button", {
    className: cx(kind === 'all' && 'is-on'),
    onClick: () => setKind('all')
  }, "All"), /*#__PURE__*/React.createElement("button", {
    className: cx(kind === 'contact' && 'is-on'),
    onClick: () => setKind('contact')
  }, "People"), /*#__PURE__*/React.createElement("button", {
    className: cx(kind === 'account' && 'is-on'),
    onClick: () => setKind('account')
  }, "Companies")))), /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stats",
    style: {
      marginBottom: 20
    }
  }, stat('Open follow-ups', openTasks.length), stat('Overdue', overdue, overdue ? '#b91c1c' : null), stat('Due today', today, today ? '#b45309' : null), stat('This week', week)), openTasks.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-ptable__empty",
    style: {
      border: '1px solid var(--border)',
      borderRadius: 12
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "CheckCircle2",
    size: 22,
    color: "var(--success, #059669)"
  }), /*#__PURE__*/React.createElement("span", null, "All clear \u2014 no open follow-ups", scope === 'mine' ? ' for you' : '', ".")), DUE_BUCKETS.map(b => {
    const rows = openTasks.filter(t => b.test(t.dueDays)).sort((a, b2) => a.dueDays - b2.dueDays);
    if (rows.length === 0) return null;
    return /*#__PURE__*/React.createElement("div", {
      key: b.id,
      className: "tm-task-group"
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-task-grouphead"
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        width: 7,
        height: 7,
        borderRadius: '50%',
        background: b.accent
      }
    }), b.label, /*#__PURE__*/React.createElement("span", {
      className: "tm-task-groupcount"
    }, rows.length)), /*#__PURE__*/React.createElement("div", {
      className: "tm-cd__card",
      style: {
        marginTop: 8
      }
    }, rows.map(t => /*#__PURE__*/React.createElement(TaskRow, {
      key: t.id,
      t: t
    }))));
  }), doneTasks.length > 0 && /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 18
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-task-donetoggle",
    onClick: () => setShowDone(s => !s)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: showDone ? 'ChevronDown' : 'ChevronRight',
    size: 14
  }), "Completed (", doneTasks.length, ")"), showDone && /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card",
    style: {
      marginTop: 8
    }
  }, doneTasks.map(t => /*#__PURE__*/React.createElement(TaskRow, {
    key: t.id,
    t: t
  }))))));
}

// ─────────────────────────────────────────────────────────────────────────────
// Business Development pipeline
// ─────────────────────────────────────────────────────────────────────────────
const BD_TONE = {
  slate: {
    fg: 'var(--muted-foreground)',
    accent: 'var(--muted-foreground)'
  },
  blue: {
    fg: '#1d4ed8',
    accent: '#2563eb'
  },
  violet: {
    fg: '#6d28d9',
    accent: '#7c3aed'
  },
  amber: {
    fg: '#b45309',
    accent: '#d97706'
  },
  emerald: {
    fg: 'var(--success-fg, #15803d)',
    accent: 'var(--success, #059669)'
  }
};
function BdCard({
  deal,
  onClick,
  onOpenMandate
}) {
  const mandate = onOpenMandate && window.tmMandateForDeal ? window.tmMandateForDeal(deal.id) : null;
  const clickable = !!deal.account || !!mandate;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card",
    draggable: true,
    onClick: () => {
      if (mandate && onOpenMandate) onOpenMandate(mandate.id);else if (deal.account && onClick) onClick(deal.account);
    },
    onDragStart: e => {
      e.dataTransfer.setData('text/plain', deal.id);
      e.dataTransfer.effectAllowed = 'move';
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-top"
  }, /*#__PURE__*/React.createElement(AccountAvatar, {
    name: deal.company,
    size: 28
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-name"
  }, deal.company), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-role"
  }, deal.role))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6,
      margin: '8px 0 7px',
      fontSize: 11.5,
      color: 'var(--muted-foreground)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 11
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, deal.nextStep)), deal.stage !== 'Won' && deal.stage !== 'Lost' && /*#__PURE__*/React.createElement("div", {
    className: "tm-bd-prob"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-bd-prob__bar"
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: deal.probability + '%'
    }
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-bd-prob__pct"
  }, deal.probability, "%")), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__card-bottom"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__card-age",
    style: {
      fontWeight: 600,
      color: 'var(--foreground)'
    }
  }, deal.fee), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__card-age"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Clock",
    size: 11
  }), formatAge(deal.ageDays)), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__av",
    style: {
      marginLeft: 'auto'
    },
    title: OWNER_NAMES[deal.owner]
  }, deal.owner)));
}
function BdColumn({
  stage,
  deals,
  onDrop,
  onCardClick,
  onOpenMandate
}) {
  const [over, setOver] = React.useState(false);
  const colors = BD_TONE[stage.tone];
  const value = deals.reduce((s, d) => s + d.value, 0);
  return /*#__PURE__*/React.createElement("div", {
    className: cx('tm-pl__col', over && 'is-over'),
    onDragOver: e => {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      setOver(true);
    },
    onDragLeave: () => setOver(false),
    onDrop: e => {
      e.preventDefault();
      setOver(false);
      onDrop(e.dataTransfer.getData('text/plain'), stage.id);
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__col-head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__col-dot",
    style: {
      background: colors.accent
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__col-label",
    style: {
      color: colors.fg
    }
  }, stage.label), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__col-count"
  }, deals.length), value > 0 && /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: 'auto',
      fontSize: 11,
      fontWeight: 600,
      color: 'var(--muted-foreground)',
      fontVariantNumeric: 'tabular-nums'
    }
  }, "$", value, "K")), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__col-cards"
  }, deals.map(d => /*#__PURE__*/React.createElement(BdCard, {
    key: d.id,
    deal: d,
    onClick: onCardClick,
    onOpenMandate: onOpenMandate
  })), deals.length === 0 && /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__col-empty"
  }, over ? 'Drop here' : 'No deals')));
}
function BizDevScreen({
  onOpenAccount,
  onOpenMandate
}) {
  const [deals, setDeals] = React.useState(() => (window.TM_BD_DEALS || []).map(d => ({
    ...d
  })));
  const [filterOwner, setFilterOwner] = React.useState('');
  const [showLost, setShowLost] = React.useState(false);
  const stages = window.TM_BD_STAGES || [];
  const handleDrop = (dealId, newStage) => setDeals(prev => prev.map(d => d.id === dealId ? {
    ...d,
    stage: newStage,
    ageDays: 0,
    probability: newStage === 'Won' ? 100 : d.probability
  } : d));
  const owners = [...new Set((window.TM_BD_DEALS || []).map(d => d.owner))];
  const scoped = deals.filter(d => !filterOwner || d.owner === filterOwner);
  const openDeals = scoped.filter(d => d.stage !== 'Won' && d.stage !== 'Lost');
  const wonDeals = scoped.filter(d => d.stage === 'Won');
  const lostDeals = scoped.filter(d => d.stage === 'Lost');
  const openValue = openDeals.reduce((s, d) => s + d.value, 0);
  const weighted = Math.round(openDeals.reduce((s, d) => s + d.value * d.probability / 100, 0));
  const wonValue = wonDeals.reduce((s, d) => s + d.value, 0);
  const decided = wonDeals.length + lostDeals.length;
  const winRate = decided ? Math.round(wonDeals.length / decided * 100) : 0;
  const stat = (label, val, sub, tone) => /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__v",
    style: tone ? {
      color: tone
    } : null
  }, val), /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stat__l"
  }, label, sub ? /*#__PURE__*/React.createElement("span", {
    style: {
      opacity: .7
    }
  }, " \xB7 ", sub) : ''));
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-pl tm-fadein"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header-left"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-eyebrow",
    style: {
      fontSize: 10
    }
  }, "CRM"), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 10,
      color: 'var(--muted-foreground)'
    }
  }, "\xB7"), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      fontWeight: 700,
      color: 'var(--foreground)'
    }
  }, "Business Development"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__header-right"
  }, /*#__PURE__*/React.createElement(PlFilter, {
    label: "Owner",
    options: owners,
    value: filterOwner,
    onChange: setFilterOwner
  }), /*#__PURE__*/React.createElement(Button, {
    onClick: () => window.showToast && window.showToast('New deal — coming soon')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 16
  }), "New deal"))), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '14px 20px 0'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-acc-stats",
    style: {
      marginBottom: 0
    }
  }, stat('Open pipeline', '$' + openValue + 'K', openDeals.length + ' deals'), stat('Weighted', '$' + weighted + 'K', 'by probability', 'var(--primary)'), stat('Won this period', '$' + wonValue + 'K', wonDeals.length + ' mandates', 'var(--success-fg, #15803d)'), stat('Win rate', winRate + '%', wonDeals.length + '/' + decided + ' decided'))), /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__board-wrap",
    style: {
      flexDirection: 'column'
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-pl__board"
  }, stages.map(stage => /*#__PURE__*/React.createElement(BdColumn, {
    key: stage.id,
    stage: stage,
    deals: scoped.filter(d => d.stage === stage.id),
    onDrop: handleDrop,
    onCardClick: onOpenAccount,
    onOpenMandate: onOpenMandate
  }))), lostDeals.length > 0 && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '0 16px 20px'
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-task-donetoggle",
    onClick: () => setShowLost(s => !s)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: showLost ? 'ChevronDown' : 'ChevronRight',
    size: 14
  }), "Recently lost (", lostDeals.length, ")"), showLost && /*#__PURE__*/React.createElement("div", {
    className: "tm-cd__card",
    style: {
      marginTop: 8,
      maxWidth: 760
    }
  }, lostDeals.map(d => /*#__PURE__*/React.createElement("div", {
    key: d.id,
    className: "tm-bd-lost"
  }, /*#__PURE__*/React.createElement(AccountAvatar, {
    name: d.company,
    size: 26
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 13,
      fontWeight: 500
    }
  }, d.company, " \xB7 ", /*#__PURE__*/React.createElement("span", {
    style: {
      color: 'var(--muted-foreground)',
      fontWeight: 400
    }
  }, d.role)), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 11.5,
      color: 'var(--muted-foreground)'
    }
  }, d.lostReason)), /*#__PURE__*/React.createElement("span", {
    className: "tm-pill",
    style: {
      background: 'rgba(220,38,38,.10)',
      color: '#b91c1c',
      fontSize: 10.5
    }
  }, "Lost"), /*#__PURE__*/React.createElement("span", {
    className: "tm-pl__av",
    style: {
      width: 22,
      height: 22,
      fontSize: 9
    }
  }, d.owner)))))));
}
Object.assign(window, {
  MyDayScreen,
  BizDevScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/worklist.jsx", error: String((e && e.message) || e) }); }

// ui_kits/talent-map/workspace-screens.jsx
try { (() => {
/* global React, Icon, Button, Pill, Avatar, cx, initials */
// ── Stub screens for new menu items ──────────────────────────────────────────
// Lightweight placeholder screens for Overview / AI Agent / Inbox. Real
// content auto-populates from the mandate later (per design direction).

// ── Shared workspace top bar (unified style) ─────────────────────────────────
function WorkspaceTopBar({
  title,
  subtitle,
  eyebrow,
  children
}) {
  return /*#__PURE__*/React.createElement("header", {
    className: "tm-ws__topbar"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ws__topbar-l"
  }, eyebrow && /*#__PURE__*/React.createElement("div", {
    className: "tm-ws__eyebrow"
  }, eyebrow), /*#__PURE__*/React.createElement("h1", {
    className: "tm-ws__title"
  }, title, /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__title-edit",
    title: "Rename"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Pencil",
    size: 13
  }))), subtitle && /*#__PURE__*/React.createElement("div", {
    className: "tm-ws__sub"
  }, subtitle)), /*#__PURE__*/React.createElement("div", {
    className: "tm-ws__topbar-r"
  }, children));
}

// ── Overview ─────────────────────────────────────────────────────────────────
function OverviewScreen({
  mandateName,
  clientName,
  companies,
  sourcingCounts,
  onView
}) {
  const execCount = (companies || []).reduce((s, c) => s + (c.execs?.length || 0), 0);
  const counts = sourcingCounts || {};
  const verified = (companies || []).reduce((s, c) => s + (c.execs || []).filter(e => e.verified).length, 0);
  const tiles = [{
    l: 'Companies in universe',
    v: counts.universe ?? companies?.length ?? 0,
    sub: 'across 4 sectors',
    icon: 'Building2',
    tone: 'primary',
    view: 'sourcing'
  }, {
    l: 'Executives mapped',
    v: execCount,
    sub: `${verified} verified`,
    icon: 'Users',
    tone: 'info',
    view: 'candidates'
  }, {
    l: 'Shortlisted',
    v: counts.shortlisted || 0,
    sub: 'company-level',
    icon: 'Star',
    tone: 'ai',
    view: 'sourcing'
  }, {
    l: 'Approved',
    v: counts.approved || 0,
    sub: 'ready for outreach',
    icon: 'CheckCircle',
    tone: 'success',
    view: 'sourcing'
  }];
  const timeline = [{
    t: '2 hrs ago',
    ic: 'Sparkles',
    text: /*#__PURE__*/React.createElement(React.Fragment, null, "ALAC scored ", /*#__PURE__*/React.createElement("b", null, "3 new companies"), " as Direct \u2014 review in Sourcing.")
  }, {
    t: 'yesterday',
    ic: 'Star',
    text: /*#__PURE__*/React.createElement(React.Fragment, null, "You shortlisted ", /*#__PURE__*/React.createElement("b", null, "Almarai"), " and ", /*#__PURE__*/React.createElement("b", null, "Agthia Group"), ".")
  }, {
    t: '2 days ago',
    ic: 'FileText',
    text: /*#__PURE__*/React.createElement(React.Fragment, null, "Position spec drafted by AI \u2014 open ", /*#__PURE__*/React.createElement("b", null, "Position"), " to review and adjust.")
  }, {
    t: '2 days ago',
    ic: 'Telescope',
    text: /*#__PURE__*/React.createElement(React.Fragment, null, "Universe seeded from brief \u2014 ", /*#__PURE__*/React.createElement("b", null, counts.universe ?? '24', " companies"), " across GCC.")
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-fadein"
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: clientName || 'FMCG & FOOD · GCC',
    title: mandateName || 'Mandate overview',
    subtitle: `${counts.universe ?? companies?.length ?? 0} companies · ${execCount} executives · last activity 2 hours ago`
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Share2",
    size: 13
  }), " Share"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 13
  }), " Export")), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__body"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__tiles"
  }, tiles.map(t => /*#__PURE__*/React.createElement("button", {
    key: t.l,
    className: cx('tm-ov__tile', `is-${t.tone}`),
    onClick: () => onView && onView(t.view)
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-ov__tile-ic', `is-${t.tone}`)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: t.icon,
    size: 16
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__tile-v"
  }, t.v), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__tile-l"
  }, t.l), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__tile-sub"
  }, t.sub)))), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__grid"
  }, /*#__PURE__*/React.createElement("section", {
    className: "tm-ov__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__card-head"
  }, /*#__PURE__*/React.createElement("h3", null, "Mandate brief"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ov__card-act",
    onClick: () => onView && onView('position')
  }, "Open Position ", /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUpRight",
    size: 12
  }))), /*#__PURE__*/React.createElement("p", {
    className: "tm-ov__brief"
  }, "Seeking a ", /*#__PURE__*/React.createElement("b", null, "Chief Financial Officer"), " for a mid-cap FMCG & food business headquartered in the GCC. Target operating scale ", /*#__PURE__*/React.createElement("b", null, "$500M\u20135B revenue"), " with", /*#__PURE__*/React.createElement("b", null, " 5K\u201350K employees"), ". Preference for founder-led or family-managed companies with succession signals at the CFO seat."), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__chips"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Briefcase",
    size: 11
  }), " CFO"), /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 11
  }), " FMCG \xB7 Food"), /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MapPin",
    size: 11
  }), " GCC"), /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "DollarSign",
    size: 11
  }), " $500M\u20135B"), /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Users",
    size: 11
  }), " 5K\u201350K"), /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__chip"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Crown",
    size: 11
  }), " Founder-led pref"))), /*#__PURE__*/React.createElement("section", {
    className: "tm-ov__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__card-head"
  }, /*#__PURE__*/React.createElement("h3", null, "Recent activity"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ov__card-act"
  }, "See all")), /*#__PURE__*/React.createElement("ul", {
    className: "tm-ov__timeline"
  }, timeline.map((t, i) => /*#__PURE__*/React.createElement("li", {
    key: i
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__tl-ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: t.ic,
    size: 11
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__tl-body"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__tl-text"
  }, t.text), /*#__PURE__*/React.createElement("span", {
    className: "tm-ov__tl-t"
  }, t.t)))))), /*#__PURE__*/React.createElement("section", {
    className: "tm-ov__card tm-ov__card--wide"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__card-head"
  }, /*#__PURE__*/React.createElement("h3", null, "Next actions")), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__actions"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ov__action",
    onClick: () => onView && onView('sourcing')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Telescope",
    size: 16,
    color: "var(--primary)"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-t"
  }, "Review universe"), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-d"
  }, counts.universe ?? '24', " companies awaiting your decisions")), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14,
    color: "var(--muted-foreground)"
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-ov__action",
    onClick: () => onView && onView('strategy')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Compass",
    size: 16,
    color: "var(--ai)"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-t"
  }, "Refine strategy"), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-d"
  }, "AI has drafted a sourcing strategy \u2014 confirm or edit")), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14,
    color: "var(--muted-foreground)"
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-ov__action",
    onClick: () => onView && onView('outreach')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Send",
    size: 16,
    color: "var(--success)"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-t"
  }, "Plan outreach"), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-d"
  }, (counts.approved || 0) + (counts.shortlisted || 0), " companies queued")), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14,
    color: "var(--muted-foreground)"
  })))))));
}

// ── AI Agent (chat-style assistant scoped to mandate) ────────────────────────
function AIAgentScreen({
  mandateName,
  clientName
}) {
  const [messages, setMessages] = React.useState([{
    role: 'ai',
    text: `I'm your assistant for **${mandateName || 'this mandate'}**. I can search the universe, evaluate companies against your criteria, pull executive profiles, and draft outreach. Try one of the suggestions below — or just ask.`,
    time: Date.now()
  }]);
  const [input, setInput] = React.useState('');
  const [typing, setTyping] = React.useState(false);
  const bodyRef = React.useRef(null);
  React.useEffect(() => {
    if (bodyRef.current) bodyRef.current.scrollTop = bodyRef.current.scrollHeight;
  }, [messages, typing]);
  const send = text => {
    const q = (text || input).trim();
    if (!q) return;
    setMessages(m => [...m, {
      role: 'user',
      text: q,
      time: Date.now()
    }]);
    setInput('');
    setTyping(true);
    setTimeout(() => {
      setTyping(false);
      const lower = q.toLowerCase();
      let reply = `Looking at "${q}" against your current criteria. I'd start by checking founder-led FMCG groups with $1B+ revenue across Saudi Arabia and the UAE — Almarai, Agthia, Savola and Halwani all match. Want me to score them on CFO succession risk?`;
      if (lower.includes('summari') || lower.includes('summary')) {
        reply = 'Universe summary: 24 companies, 6 direct / 11 adjacent / 7 AI-inferred. 38 executives mapped, 9 with verified contacts. Concentration in Saudi (38%) and UAE (32%). Strongest CFO succession signal at Almarai (current CFO 7y tenure) and Agthia (acting CFO since Q3).';
      } else if (lower.includes('cfo') || lower.includes('finance')) {
        reply = 'Across the 24 companies in the universe I see 8 publicly disclosed CFOs and 11 N-1 finance leads. Strongest candidates to look at first: Rami Khoury (Almarai), Tariq Bahar (Savola), Fatima Al Hammadi (Al Islami). Want me to draft a longlist?';
      } else if (lower.includes('outreach') || lower.includes('reach out')) {
        reply = 'I can draft a tailored first-touch message per candidate. Tone: warm, mandate-specific, mentions the client only at meeting stage. Want plain English, or Arabic-first with English fallback?';
      }
      setMessages(m => [...m, {
        role: 'ai',
        text: reply,
        time: Date.now()
      }]);
    }, 700 + Math.random() * 500);
  };
  const quick = ['Summarise the universe', 'Who are the strongest CFO candidates?', 'Draft outreach for top 3 companies', 'What companies should I add to widen the net?'];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-fadein"
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: "AI Assistant",
    title: "Ask anything about this mandate",
    subtitle: `Scoped to ${mandateName || 'this search'} · ${clientName || ''}`
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "History",
    size: 13
  }), " History"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Trash2",
    size: 13
  }), " Clear chat")), /*#__PURE__*/React.createElement("div", {
    className: "tm-aia__body",
    ref: bodyRef
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-aia__inner"
  }, messages.map((m, i) => /*#__PURE__*/React.createElement("div", {
    key: i,
    className: cx('tm-aia__msg', `is-${m.role}`)
  }, m.role === 'ai' && /*#__PURE__*/React.createElement("span", {
    className: "tm-aia__avatar"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14,
    color: "#7c3aed"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-aia__bubble"
  }, m.text.split('\n').map((line, j) => /*#__PURE__*/React.createElement("p", {
    key: j,
    dangerouslySetInnerHTML: {
      __html: line.replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>')
    }
  }))))), typing && /*#__PURE__*/React.createElement("div", {
    className: "tm-aia__msg is-ai"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-aia__avatar"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 14,
    color: "#7c3aed"
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-aia__bubble"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-aia__dots"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-dot",
    style: {
      animationDelay: '0s'
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-dot",
    style: {
      animationDelay: '.15s'
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "tm-dot",
    style: {
      animationDelay: '.3s'
    }
  })))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-aia__composer"
  }, messages.length <= 1 && /*#__PURE__*/React.createElement("div", {
    className: "tm-aia__quick"
  }, quick.map(q => /*#__PURE__*/React.createElement("button", {
    key: q,
    className: "tm-aia__quick-btn",
    onClick: () => send(q)
  }, q))), /*#__PURE__*/React.createElement("div", {
    className: "tm-aia__box"
  }, /*#__PURE__*/React.createElement("textarea", {
    value: input,
    placeholder: "Ask anything about the mandate, universe, or executives\u2026",
    rows: 1,
    onChange: e => {
      setInput(e.target.value);
      e.target.style.height = 'auto';
      e.target.style.height = Math.min(e.target.scrollHeight, 140) + 'px';
    },
    onKeyDown: e => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        send();
      }
    }
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-aia__box-bar"
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 4
    }
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-aia__tool"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Paperclip",
    size: 14
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-aia__tool"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Mic",
    size: 14
  }))), /*#__PURE__*/React.createElement("button", {
    className: "tm-aia__send",
    onClick: () => send(),
    disabled: !input.trim() || typing
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUp",
    size: 13
  }), "Send")))));
}

// ── Inbox (mandate-scoped activity inbox) ────────────────────────────────────
function InboxScreen({
  mandateName
}) {
  const [active, setActive] = React.useState(0);
  const threads = [{
    id: 1,
    from: 'Amira Haddad',
    subject: 'Re: Confidential exploratory conversation',
    preview: 'Thanks for reaching out, Yousef. Happy to talk next week — Tuesday or Thursday after 14:00 KSA?',
    time: '14m',
    unread: true,
    channel: 'Email',
    tag: 'reply'
  }, {
    id: 2,
    from: 'Rami Khoury',
    subject: 'Re: Coffee + roles in the region',
    preview: 'Open to a confidential call. Could you share a one-pager on the client?',
    time: '2h',
    unread: true,
    channel: 'LinkedIn',
    tag: 'reply'
  }, {
    id: 3,
    from: 'Fatima Al Hammadi',
    subject: 'Auto-decline · Out of office',
    preview: "I'm out of office until 30 June. For urgent matters please contact my EA.",
    time: '5h',
    unread: false,
    channel: 'Email',
    tag: 'auto'
  }, {
    id: 4,
    from: 'Marcos Tavares',
    subject: 'Sequence step 2 sent',
    preview: 'Follow-up email delivered — open rate 67% across this batch.',
    time: '1d',
    unread: false,
    channel: 'Outreach',
    tag: 'system'
  }, {
    id: 5,
    from: 'Hamad Al Ketbi',
    subject: 'Re: NFPC — happy to chat',
    preview: 'Glad you reached out. Schedule is tight but I can do a 30-min call next Wednesday.',
    time: '2d',
    unread: false,
    channel: 'Email',
    tag: 'reply'
  }];
  const t = threads[active];
  const unreadCount = threads.filter(t => t.unread).length;
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-fadein"
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: "Inbox",
    title: "Conversations",
    subtitle: `${threads.length} threads · ${unreadCount} unread · scoped to ${mandateName || 'mandate'}`
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ws__filter"
  }, /*#__PURE__*/React.createElement("button", {
    className: "is-on"
  }, "All"), /*#__PURE__*/React.createElement("button", null, "Replies"), /*#__PURE__*/React.createElement("button", null, "System"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__body"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__list"
  }, threads.map((thr, i) => /*#__PURE__*/React.createElement("button", {
    key: thr.id,
    className: cx('tm-ix__item', i === active && 'is-on', thr.unread && 'is-unread'),
    onClick: () => setActive(i)
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: thr.from,
    size: 32,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__item-info"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__item-top"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-ix__item-from"
  }, thr.from), /*#__PURE__*/React.createElement("span", {
    className: "tm-ix__item-time"
  }, thr.time)), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__item-subj"
  }, thr.subject), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__item-pre"
  }, thr.preview), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__item-tags"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-ix__chan"
  }, thr.channel), thr.tag === 'reply' && /*#__PURE__*/React.createElement("span", {
    className: "tm-ix__tag is-reply"
  }, "Reply"), thr.tag === 'auto' && /*#__PURE__*/React.createElement("span", {
    className: "tm-ix__tag is-auto"
  }, "Auto"), thr.tag === 'system' && /*#__PURE__*/React.createElement("span", {
    className: "tm-ix__tag is-system"
  }, "System")))))), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__thread"
  }, t && /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__th-head"
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: t.from,
    size: 36,
    tone: "primary"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__th-from"
  }, t.from), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__th-meta"
  }, t.channel, " \xB7 ", t.time, " ago")), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Star",
    size: 13
  }), " Star"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Archive",
    size: 13
  }), " Archive")), /*#__PURE__*/React.createElement("h2", {
    className: "tm-ix__th-subject"
  }, t.subject), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__th-body"
  }, /*#__PURE__*/React.createElement("p", null, t.preview), /*#__PURE__*/React.createElement("p", null, "Best,", /*#__PURE__*/React.createElement("br", null), t.from.split(' ')[0])), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__th-reply"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__th-reply-suggest"
  }, /*#__PURE__*/React.createElement("span", null, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 11,
    color: "var(--ai)"
  }), " Suggested reply"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ix__th-reply-use"
  }, "Use draft")), /*#__PURE__*/React.createElement("textarea", {
    placeholder: "Reply to this thread\u2026",
    rows: 3
  }), /*#__PURE__*/React.createElement("div", {
    className: "tm-ix__th-reply-foot"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Paperclip",
    size: 12
  }), " Attach"), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement(Button, null, /*#__PURE__*/React.createElement(Icon, {
    name: "Send",
    size: 13
  }), " Send")))))));
}

// ── Outreach (lightweight) ───────────────────────────────────────────────────
function OutreachScreen({
  mandateName,
  companies,
  sourcingCounts
}) {
  const sequences = [{
    id: 1,
    name: 'Direct CFO — first touch',
    stage: 'Active',
    in: 8,
    replied: 3,
    open: 67,
    sent: 18
  }, {
    id: 2,
    name: 'Adjacent CFO — soft probe',
    stage: 'Active',
    in: 5,
    replied: 1,
    open: 52,
    sent: 11
  }, {
    id: 3,
    name: 'Approved · Step 2 nudge',
    stage: 'Paused',
    in: 4,
    replied: 0,
    open: 38,
    sent: 4
  }];
  const totalQueued = (sourcingCounts?.shortlisted || 0) + (sourcingCounts?.approved || 0);
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-fadein"
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: "Outreach",
    title: "Sequences",
    subtitle: `${sequences.length} sequences · ${totalQueued} candidates queued`
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Pause",
    size: 13
  }), " Pause all"), /*#__PURE__*/React.createElement(Button, null, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 13
  }), " New sequence")), /*#__PURE__*/React.createElement("div", {
    className: "tm-out__body"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-out__list"
  }, sequences.map(s => /*#__PURE__*/React.createElement("article", {
    key: s.id,
    className: "tm-out__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-out__card-head"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Send",
    size: 14,
    color: "var(--primary)"
  }), /*#__PURE__*/React.createElement("h3", null, s.name), /*#__PURE__*/React.createElement(Pill, {
    tone: s.stage === 'Active' ? 'direct' : 'neutral'
  }, s.stage), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1
    }
  }), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn-i"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "MoreVertical",
    size: 14
  }))), /*#__PURE__*/React.createElement("div", {
    className: "tm-out__stats"
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("b", null, s.in), /*#__PURE__*/React.createElement("span", null, "in sequence")), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("b", null, s.sent), /*#__PURE__*/React.createElement("span", null, "messages sent")), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("b", {
    className: "tm-mono"
  }, s.open, "%"), /*#__PURE__*/React.createElement("span", null, "open rate")), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("b", null, s.replied), /*#__PURE__*/React.createElement("span", null, "replies"))), /*#__PURE__*/React.createElement("div", {
    className: "tm-out__steps"
  }, /*#__PURE__*/React.createElement("span", null, "Step 1 \xB7 Day 0"), /*#__PURE__*/React.createElement("span", null, "\u2014"), /*#__PURE__*/React.createElement("span", null, "Step 2 \xB7 Day 3"), /*#__PURE__*/React.createElement("span", null, "\u2014"), /*#__PURE__*/React.createElement("span", null, "Step 3 \xB7 Day 7")))))));
}

// ── Position (auto-populated) ────────────────────────────────────────────────
function PositionScreen({
  mandateName
}) {
  const sections = [{
    h: 'Role & seniority',
    c: 'Chief Financial Officer · C-Suite · reports to Group CEO and Audit Committee. Direct reports include Group Controller, Head of FP&A, Treasury, and Investor Relations.'
  }, {
    h: 'Mandate context',
    c: `${mandateName || 'This search'} is part of a broader leadership refresh in the GCC FMCG segment, driven by the company's regional expansion strategy and IPO readiness.`
  }, {
    h: 'Must-haves',
    c: '• 12+ years senior finance leadership\n• FMCG, food, or consumer-adjacent experience\n• Track record managing P&L > $500M\n• Strong investor-relations / capital markets exposure (IPO bonus)\n• Comfortable operating in GCC business culture'
  }, {
    h: 'Nice-to-haves',
    c: '• Big-4 or top-tier consulting pedigree\n• Arabic + English\n• PE-backed company experience\n• Founder-led / family-business exposure'
  }, {
    h: 'Compensation',
    c: '$650K–$900K base · 30–60% bonus · LTI / equity-equivalent for the right candidate · Riyadh-based with regional travel.'
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-fadein"
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: "Position spec",
    title: "Chief Financial Officer",
    subtitle: "Auto-drafted from brief \xB7 last refined 2 hours ago"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13
  }), " Regenerate"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Pencil",
    size: 13
  }), " Edit"), /*#__PURE__*/React.createElement(Button, {
    variant: "outline"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 13
  }), " Export PDF")), /*#__PURE__*/React.createElement("div", {
    className: "tm-doc"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-doc__banner"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13,
    color: "var(--ai)"
  }), /*#__PURE__*/React.createElement("span", null, "ALAC generated this spec from your brief. Edits propagate to Sourcing criteria.")), sections.map(s => /*#__PURE__*/React.createElement("section", {
    key: s.h,
    className: "tm-doc__sec"
  }, /*#__PURE__*/React.createElement("h2", null, s.h), s.c.split('\n').map((line, i) => /*#__PURE__*/React.createElement("p", {
    key: i
  }, line))))));
}

// ── Strategy (auto-populated) ────────────────────────────────────────────────
function StrategyScreen({
  mandateName
}) {
  const moves = [{
    h: 'Anchor on direct FMCG leaders',
    desc: 'Primary list: Almarai, Agthia, IFFCO, NADEC, SADAFCO. Map current CFO + N-1 succession bench at each.',
    count: '5 companies · 12 execs'
  }, {
    h: 'Sweep adjacent food & retail',
    desc: 'Savola, Americana, Halwani, Juhayna. CFOs often rotate between FMCG and quick-service. Watch for movers.',
    count: '4 companies · 8 execs'
  }, {
    h: 'Bench from PE/family conglomerates',
    desc: 'Mezzan, NFPC, BRF ME. PE-backed firms generate strong CFO talent every 3–5 years.',
    count: '3 companies · 6 execs'
  }, {
    h: 'Off-limits & exclusions',
    desc: 'Direct competitors of client and 2 firms with current ALAC engagements. Auto-applied as filters.',
    count: '2 companies excluded'
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-fadein"
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: "Strategy",
    title: "Sourcing strategy",
    subtitle: "Auto-drafted plan \xB7 14 companies in scope \xB7 re-runs nightly"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13
  }), " Regenerate"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Pencil",
    size: 13
  }), " Edit")), /*#__PURE__*/React.createElement("div", {
    className: "tm-doc"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-doc__banner"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Sparkles",
    size: 13,
    color: "var(--ai)"
  }), /*#__PURE__*/React.createElement("span", null, "ALAC drafted this strategy from your position spec and historical placements. It refreshes when the universe changes.")), moves.map((m, i) => /*#__PURE__*/React.createElement("section", {
    key: i,
    className: "tm-strat__move"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-strat__move-n"
  }, String(i + 1).padStart(2, '0')), /*#__PURE__*/React.createElement("div", {
    className: "tm-strat__move-body"
  }, /*#__PURE__*/React.createElement("h3", null, m.h), /*#__PURE__*/React.createElement("p", null, m.desc), /*#__PURE__*/React.createElement("span", {
    className: "tm-strat__move-meta"
  }, m.count))))));
}

// ── Reports (lightweight wrapper around existing Dashboard) ─────────────────
function ReportsScreen({
  companies,
  mandateName
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-fadein"
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: "Reports",
    title: "Mandate report",
    subtitle: "Universe progress \xB7 talent depth \xB7 coverage by sector and country"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Calendar",
    size: 13
  }), " All time"), /*#__PURE__*/React.createElement(Button, {
    variant: "outline"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Download",
    size: 13
  }), " Export")), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minHeight: 0,
      overflow: 'auto'
    }
  }, window.Dashboard ? /*#__PURE__*/React.createElement(window.Dashboard, {
    companies: companies
  }) : /*#__PURE__*/React.createElement("div", {
    style: {
      padding: 24,
      color: 'var(--muted-foreground)'
    }
  }, "Loading\u2026")));
}

// ── All-search-maps Overview ────────────────────────────────────────────────
// Account-level aggregate dashboard shown when the user is in the
// "All search maps" context and the active section is Overview.
function AllSearchesOverview({
  projects,
  onOpen,
  onNewSearch,
  onView
}) {
  const gbc = window.groupByClient || (() => ({
    clientGroups: [],
    unassigned: []
  }));
  const gcn = window.getClientName || (() => '');
  const totalMaps = projects.length;
  const totalCompanies = projects.reduce((s, p) => s + (p.companies || 0), 0);
  const totalExecs = projects.reduce((s, p) => s + (p.execs || 0), 0);
  const clients = new Set(projects.map(p => p.clientId).filter(Boolean));
  const totalClients = clients.size;
  const {
    clientGroups
  } = gbc(projects);
  const recent = [...projects].sort((a, b) => (a.ageDays || 0) - (b.ageDays || 0)).slice(0, 6);
  const topClients = [...clientGroups].sort((a, b) => b.maps.length - a.maps.length).slice(0, 5);
  const maxMaps = topClients.length ? topClients[0].maps.length : 1;
  const tiles = [{
    l: 'Search maps',
    v: totalMaps,
    sub: 'active in account',
    icon: 'Layers',
    tone: 'primary'
  }, {
    l: 'Clients',
    v: totalClients,
    sub: 'with open maps',
    icon: 'Building2',
    tone: 'info'
  }, {
    l: 'Companies tracked',
    v: totalCompanies,
    sub: 'across all maps',
    icon: 'Briefcase',
    tone: 'ai'
  }, {
    l: 'Executives mapped',
    v: totalExecs,
    sub: 'verified + inferred',
    icon: 'Users',
    tone: 'success'
  }];
  return /*#__PURE__*/React.createElement("div", {
    className: "tm-ws tm-fadein"
  }, /*#__PURE__*/React.createElement(WorkspaceTopBar, {
    eyebrow: "Workspace",
    title: "All search maps",
    subtitle: `${totalMaps} maps · ${totalClients} clients · ${totalCompanies} companies · ${totalExecs} executives`
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ws__btn",
    onClick: onNewSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 13
  }), " New search map")), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__body"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__tiles"
  }, tiles.map(t => /*#__PURE__*/React.createElement("div", {
    key: t.l,
    className: cx('tm-ov__tile', `is-${t.tone}`)
  }, /*#__PURE__*/React.createElement("span", {
    className: cx('tm-ov__tile-ic', `is-${t.tone}`)
  }, /*#__PURE__*/React.createElement(Icon, {
    name: t.icon,
    size: 16
  })), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__tile-v"
  }, t.v), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__tile-l"
  }, t.l), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__tile-sub"
  }, t.sub)))), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__grid"
  }, /*#__PURE__*/React.createElement("section", {
    className: "tm-ov__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__card-head"
  }, /*#__PURE__*/React.createElement("h3", null, "Recent search maps"), /*#__PURE__*/React.createElement("button", {
    className: "tm-ov__card-act",
    onClick: () => onView && onView('overview')
  }, "See all ", /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowUpRight",
    size: 12
  }))), /*#__PURE__*/React.createElement("ul", {
    className: "tm-aso__recent"
  }, recent.map(p => /*#__PURE__*/React.createElement("li", {
    key: p.id
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-aso__recent-row",
    onClick: () => onOpen && onOpen(p)
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-aso__recent-ic"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Target",
    size: 13
  })), /*#__PURE__*/React.createElement("span", {
    className: "tm-aso__recent-main"
  }, /*#__PURE__*/React.createElement("span", {
    className: "tm-aso__recent-name"
  }, p.name), /*#__PURE__*/React.createElement("span", {
    className: "tm-aso__recent-meta"
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Building2",
    size: 10
  }), gcn(p.clientId) || 'Unassigned', /*#__PURE__*/React.createElement("span", {
    className: "tm-pickrow__dot"
  }), p.companies, " companies", /*#__PURE__*/React.createElement("span", {
    className: "tm-pickrow__dot"
  }), p.when)), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14,
    color: "var(--muted-foreground)"
  })))))), /*#__PURE__*/React.createElement("section", {
    className: "tm-ov__card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__card-head"
  }, /*#__PURE__*/React.createElement("h3", null, "Top clients")), /*#__PURE__*/React.createElement("ul", {
    className: "tm-aso__clients"
  }, topClients.map(cg => {
    const cc = cg.maps.reduce((s, m) => s + (m.companies || 0), 0);
    const pct = Math.max(8, Math.round(cg.maps.length / maxMaps * 100));
    return /*#__PURE__*/React.createElement("li", {
      key: cg.clientId,
      className: "tm-aso__client"
    }, /*#__PURE__*/React.createElement("div", {
      className: "tm-aso__client-row"
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-aso__client-name"
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "Building2",
      size: 11,
      color: "var(--muted-foreground)"
    }), cg.name), /*#__PURE__*/React.createElement("span", {
      className: "tm-aso__client-meta"
    }, cg.maps.length, " ", cg.maps.length === 1 ? 'map' : 'maps', " \xB7 ", cc, " companies")), /*#__PURE__*/React.createElement("span", {
      className: "tm-aso__client-bar"
    }, /*#__PURE__*/React.createElement("span", {
      className: "tm-aso__client-bar-fill",
      style: {
        width: pct + '%'
      }
    })));
  }), topClients.length === 0 && /*#__PURE__*/React.createElement("li", {
    className: "tm-aso__empty"
  }, "No clients yet"))), /*#__PURE__*/React.createElement("section", {
    className: "tm-ov__card tm-ov__card--wide"
  }, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__card-head"
  }, /*#__PURE__*/React.createElement("h3", null, "Quick actions")), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__actions"
  }, /*#__PURE__*/React.createElement("button", {
    className: "tm-ov__action",
    onClick: onNewSearch
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Plus",
    size: 16,
    color: "var(--primary)"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-t"
  }, "New search map"), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-d"
  }, "Describe a market \u2014 AI builds the universe")), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14,
    color: "var(--muted-foreground)"
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-ov__action",
    onClick: () => onView && onView('sourcing')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Telescope",
    size: 16,
    color: "var(--ai)"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-t"
  }, "Continue sourcing"), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-d"
  }, "Pick a map to review its universe")), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14,
    color: "var(--muted-foreground)"
  })), /*#__PURE__*/React.createElement("button", {
    className: "tm-ov__action",
    onClick: () => onView && onView('position')
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "Briefcase",
    size: 16,
    color: "var(--success)"
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-t"
  }, "Review positions"), /*#__PURE__*/React.createElement("div", {
    className: "tm-ov__action-d"
  }, "Audit briefs across open maps")), /*#__PURE__*/React.createElement(Icon, {
    name: "ArrowRight",
    size: 14,
    color: "var(--muted-foreground)"
  })))))));
}
Object.assign(window, {
  WorkspaceTopBar,
  OverviewScreen,
  AIAgentScreen,
  InboxScreen,
  OutreachScreen,
  PositionScreen,
  StrategyScreen,
  ReportsScreen,
  AllSearchesOverview
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/talent-map/workspace-screens.jsx", error: String((e && e.message) || e) }); }

})();
