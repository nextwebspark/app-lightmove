import { useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { AuthLogo, buttonClassName, Card } from "../components/ui";

/** An unknown address reached without a session: say so, rather than bouncing to a sign-in nobody asked for. */
export function PublicNotFoundPage() {
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    heading.current?.focus();
  }, []);

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <AuthLogo />
      <Card className="w-[420px] max-w-[94vw] text-center">
        <h1 ref={heading} tabIndex={-1} className="text-[19px] font-semibold leading-tight outline-none">
          We couldn&rsquo;t find that page
        </h1>
        <p className="mb-6 mt-2 text-sm text-u-text2">
          The address may be mistyped, or the link may be out of date.
        </p>
        <Link to="/login" className={buttonClassName("primary", "w-full")}>
          Sign in
        </Link>
        <p className="mt-4 text-[12.5px] text-u-text2">
          New to Uncava?{" "}
          <Link to="/signup" className="text-u-accent hover:underline">
            Create an account
          </Link>
        </p>
      </Card>
    </div>
  );
}
