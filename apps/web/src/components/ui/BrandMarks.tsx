/**
 * Other companies' marks in their own colours — where a screen names their product (a sign-in button, the
 * provider a workspace connects), the mark is what a reader recognises before the word. Decorative: the
 * name always sits beside it.
 */

/** Google's four-colour G. */
export function GoogleMark({ size = 15 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M23.5 12.3c0-.8-.1-1.6-.2-2.3H12v4.5h6.5a5.6 5.6 0 0 1-2.4 3.6v3h3.9c2.3-2.1 3.5-5.2 3.5-8.8Z" />
      <path fill="#34A853" d="M12 24c3.2 0 5.9-1.1 7.9-2.9l-3.9-3a7.2 7.2 0 0 1-10.7-3.8h-4v3.1A12 12 0 0 0 12 24Z" />
      <path fill="#FBBC05" d="M5.3 14.3a7.1 7.1 0 0 1 0-4.6V6.6h-4a12 12 0 0 0 0 10.8l4-3.1Z" />
      <path fill="#EA4335" d="M12 4.8c1.8 0 3.4.6 4.6 1.8l3.4-3.4A12 12 0 0 0 1.3 6.6l4 3.1A7.2 7.2 0 0 1 12 4.8Z" />
    </svg>
  );
}

/** Microsoft's four squares. */
export function MicrosoftMark({ size = 15 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#F25022" d="M1 1h10.5v10.5H1z" />
      <path fill="#7FBA00" d="M12.5 1H23v10.5H12.5z" />
      <path fill="#00A4EF" d="M1 12.5h10.5V23H1z" />
      <path fill="#FFB900" d="M12.5 12.5H23V23H12.5z" />
    </svg>
  );
}

/** Zoom's app tile: the white camera on Zoom blue. */
export function ZoomMark({ size = 15 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <rect width="24" height="24" rx="5.5" fill="#0B5CFF" />
      <path
        fill="#FFFFFF"
        d="M4.8 9A1.6 1.6 0 0 1 6.4 7.4h6.4a2 2 0 0 1 2 2v5.6a1.6 1.6 0 0 1-1.6 1.6H6.8a2 2 0 0 1-2-2V9Zm11 1.6 2.7-2a.6.6 0 0 1 .9.5v5.8a.6.6 0 0 1-.9.5l-2.7-2v-2.8Z"
      />
    </svg>
  );
}
