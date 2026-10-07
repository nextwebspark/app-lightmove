package app.lightmove.api.billing.plan.repository;

import app.lightmove.api.billing.plan.constant.PlanCode;
import app.lightmove.api.billing.plan.model.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, PlanCode> {
}
