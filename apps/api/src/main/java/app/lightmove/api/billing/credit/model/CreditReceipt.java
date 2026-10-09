package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.CreditAction;
import app.lightmove.api.billing.credit.constant.CreditHoldStatus;
import java.util.UUID;

/** Where one charge stands: what it cost, what the grants covered, and whether it is spent yet. */
public record CreditReceipt(UUID holdId, CreditAction action, long credits, long covered, CreditHoldStatus status) {
}
