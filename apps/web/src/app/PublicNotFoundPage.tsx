import { useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { AuthLogo, buttonClassName, Card } from "../components/ui";

export function PublicNotFoundPage() {
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    heading.current?.focus();
  }, []);

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <AuthLogo />
      <Card className="w-[420px] max-w-[94vw] text-center">
        <h1 ref={heading} tabIndex={-1} className="text-title font-semibold leading-tight outline-none">
          We couldn&rsquo;t find that page
        </h1>
        <p className="mb-6 mt-2 text-sm text-u-text2">
          The address may be mistyped, or the link may be out of date.
        </p>
        <Link to="/login" className={buttonClassName("primary", "w-full")}>
          Sign in
        </Link>
        <p className="mt-4 text-note text-u-text2">
          New to Uncava?{" "}
          <Link to="/signup" className="text-u-accent hover:underline">
            Create an account
          </Link>
        </p>
      </Card>
    </div>
  );
}
