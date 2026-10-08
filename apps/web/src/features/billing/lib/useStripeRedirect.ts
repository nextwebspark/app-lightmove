import { useMutation } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import type { BillingRedirect } from "../api/types";
import { goToStripe } from "./checkoutReturn";

/** Asks the API for a Checkout or Customer Portal page and sends the admin there; a refusal is a toast. */
export function useStripeRedirect<TArgs>(start: (args: TArgs) => Promise<BillingRedirect>) {
  const toast = useToast();
  return useMutation({
    mutationFn: start,
    onSuccess: ({ url }) => goToStripe(url),
    onError: (error) => toast(messageFor(error)),
  });
}
