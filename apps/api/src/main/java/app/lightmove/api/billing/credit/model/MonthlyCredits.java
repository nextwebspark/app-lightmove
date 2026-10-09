package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.ContactCreditLevel;

/** The plan credits granted for the current billing month, and what of them is left unspent and unheld. */
public record MonthlyCredits(long granted, long remaining) {

    public long usedPercent() {
        return granted == 0 ? 0 : (granted - remaining) * 100 / granted;
    }

    /** @param available every credit the workspace can still spend, bought and given ones included */
    public ContactCreditLevel levelAt(long available) {
        if (available <= 0) {
            return ContactCreditLevel.OUT;
        }
        if (usedPercent() >= 90) {
            return ContactCreditLevel.NINETY;
        }
        return usedPercent() >= 80 ? ContactCreditLevel.EIGHTY : ContactCreditLevel.OK;
    }
}
