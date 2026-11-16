import { useMutation } from "@tanstack/react-query";
import { useRef, type ClipboardEvent, type KeyboardEvent, type ReactNode } from "react";
import { useWatch, type UseFormReturn } from "react-hook-form";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, Button, CompanyLogo, Field, Input, Spinner } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate, LookedUpProfile, ProfileLookupResult } from "../api/types";
import type { CandidateForm, ParsedCandidateForm } from "../lib/candidateForm";
import { isLinkedInProfileUrl } from "../../../lib/linkedinUrls";
import { emptyValueOf, isBlankValue, lookupFillOf, type LookupFill } from "../lib/profileLookup";

/**
 * The Add form's first step: paste a LinkedIn profile and research fills the form below, photo and employer included,
 * as the plugin's capture does. A field already typed in is left alone; one an earlier lookup filled belongs to that
 * profile, so the next answer replaces it or, where it has nothing, takes it back out. Only the latest lookup counts.
 */
export function ProfileLookupStep({
  projectId,
  form,
  employerLocked,
  onOpenExisting,
  onFound,
}: {
  projectId: string;
  form: UseFormReturn<CandidateForm, unknown, ParsedCandidateForm>;
  employerLocked: boolean;
  /** Opens the executive this position already maps at the pasted profile, in place of the form. */
  onOpenExisting: (candidate: Candidate) => void;
  /** The profile research answered for, or null once the latest lookup found none. */
  onFound: (linkedinUrl: string | null) => void;
}) {
  const lastAsked = useRef<string | null>(null);
  const filled = useRef<LookupFill>({});

  const lookup = useMutation({
    mutationFn: (linkedinUrl: string) => candidatesApi.lookUpProfile(projectId, linkedinUrl),
  });

  function settle(profile: LookedUpProfile | null) {
    const fill = profile ? lookupFillOf(profile, employerLocked) : {};
    const previous = filled.current;
    const keys = new Set([...Object.keys(previous), ...Object.keys(fill)] as (keyof LookupFill)[]);
    const settled: LookupFill = {};
    for (const key of keys) {
      const current = form.getValues(key);
      const heldFromLookup = key in previous && JSON.stringify(current) === JSON.stringify(previous[key]);
      if (!isBlankValue(current) && !heldFromLookup) continue;
      if (key in fill) {
        form.setValue(key, fill[key] as never, { shouldDirty: true });
        Object.assign(settled, { [key]: fill[key] });
      } else if (heldFromLookup) {
        form.setValue(key, emptyValueOf(key) as never, { shouldDirty: true });
      }
    }
    filled.current = settled;
    onFound(profile?.linkedinUrl ?? null);
  }

  /** `again`: Enter or Look up on the address already asked, which the server answers from what it holds. */
  function ask(value: string, again = false) {
    const url = value.trim();
    if (!isLinkedInProfileUrl(url)) return;
    if (url === lastAsked.current && (!again || lookup.isPending)) return;
    lastAsked.current = url;
    // Callbacks given to mutate fire for the latest call alone; the check keeps a slower, earlier answer out even so.
    lookup.mutate(url, {
      onSuccess: (result) => {
        if (url !== lastAsked.current) return;
        settle(result.outcome === "found" ? result.profile : null);
      },
      onError: () => {
        if (url === lastAsked.current) settle(null);
      },
    });
  }

  const field = form.register("linkedinUrl");
  const typed = useWatch({ control: form.control, name: "linkedinUrl" }) ?? "";
  const error = form.formState.errors.linkedinUrl?.message;

  return (
    <section className="mb-2 border-b border-u-border pb-4">
      <Field
        label="LinkedIn profile"
        error={error}
        hint={lookup.isIdle ? "Paste their profile and Uncava fills in the rest — or skip it and type them in below." : undefined}
      >
        <div className="flex gap-2">
          <Input
            {...field}
            autoFocus
            inputMode="url"
            placeholder="linkedin.com/in/…"
            invalid={Boolean(error)}
            onBlur={(event) => {
              void field.onBlur(event);
              ask(event.target.value);
            }}
            onPaste={(event: ClipboardEvent<HTMLInputElement>) => ask(event.clipboardData.getData("text"))}
            onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => {
              if (event.key !== "Enter" || event.metaKey || event.ctrlKey) return;
              event.preventDefault();
              ask(event.currentTarget.value, true);
            }}
          />
          <Button
            type="button"
            variant="secondary"
            size="md"
            className="flex-none"
            disabled={!isLinkedInProfileUrl(typed) || lookup.isPending}
            onClick={() => ask(typed, true)}
          >
            <Icon d={ICONS.search} size={14} />
            Look up
          </Button>
        </div>
      </Field>

      <div aria-live="polite">
        {lookup.isPending && (
          <div className="flex items-center gap-2.5 rounded-lg border border-u-border bg-u-raised px-3.5 py-3 text-body text-u-text2">
            <Spinner />
            Reading their LinkedIn profile…
          </div>
        )}
        {lookup.isError && <LookupMessage>{messageFor(lookup.error)}</LookupMessage>}
        {lookup.isSuccess && (
          <LookupAnswer result={lookup.data} employerLocked={employerLocked} onOpenExisting={onOpenExisting} />
        )}
      </div>
    </section>
  );
}

function LookupAnswer({
  result,
  employerLocked,
  onOpenExisting,
}: {
  result: ProfileLookupResult;
  employerLocked: boolean;
  onOpenExisting: (candidate: Candidate) => void;
}) {
  if (result.outcome === "held" && result.held) {
    const { candidate } = result.held;
    return (
      <LookupMessage
        action={
          <Button type="button" variant="secondary" size="xs" onClick={() => onOpenExisting(candidate)}>
            Open profile
          </Button>
        }
      >
        {`${candidate.fullName} is already on this position.`}
      </LookupMessage>
    );
  }
  if (result.outcome === "found" && result.profile) {
    return <FoundProfile profile={result.profile} employerLocked={employerLocked} />;
  }
  if (result.outcome === "unavailable") {
    return <LookupMessage>Profile lookup isn't switched on for this workspace — fill in their details below.</LookupMessage>;
  }
  return <LookupMessage>We couldn't read that profile — check the link, or fill in their details below.</LookupMessage>;
}

function FoundProfile({ profile, employerLocked }: { profile: LookedUpProfile; employerLocked: boolean }) {
  const name = profile.fullName ?? "This profile";
  const place = [profile.locationCity, profile.locationCountry].filter(Boolean).join(", ");
  const filesEmployer = !employerLocked && profile.employerName !== null;
  return (
    <div className="rounded-lg border border-u-border bg-u-raised px-3.5 py-3">
      <div className="flex items-start gap-3">
        <Avatar id={profile.linkedinUrl} name={name} src={profile.photoDataUrl} size="xl" />
        <div className="min-w-0 flex-1">
          <div className="truncate text-lead font-semibold text-u-text">{name}</div>
          {profile.title && <div className="truncate text-body text-u-text2">{profile.title}</div>}
          {profile.employerName && (
            <div className="mt-1 flex items-center gap-1.5 text-body text-u-text2">
              <CompanyLogo name={profile.employerName} logo={profile.employerLogoUrl} size={16} />
              <span className="truncate">{profile.employerName}</span>
            </div>
          )}
          {place && <div className="mt-0.5 truncate text-note text-u-text3">{place}</div>}
        </div>
        <Icon d={ICONS.check} size={16} className="mt-1 flex-none text-u-direct" />
      </div>
      <p className="mt-2.5 text-note text-u-text3">
        {filesEmployer
          ? profile.employerTriageCompanyId
            ? `Details filled in below. ${profile.employerName} is already in this position's universe.`
            : `Details filled in below. Saving adds ${profile.employerName} to this position's universe.`
          : "Details filled in below — check them and add anything else you know."}
      </p>
    </div>
  );
}

function LookupMessage({ children, action }: { children: string; action?: ReactNode }) {
  return (
    <div className="flex items-center gap-2.5 rounded-lg bg-u-accent-tint px-3 py-2.5 text-body text-u-accent">
      <span className="min-w-0 flex-1">{children}</span>
      {action}
    </div>
  );
}
