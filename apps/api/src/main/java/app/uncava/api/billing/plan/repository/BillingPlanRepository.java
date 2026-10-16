package app.uncava.api.billing.plan.repository;

import app.uncava.api.billing.plan.constant.PlanCode;
import app.uncava.api.billing.plan.model.BillingPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, PlanCode> {

    List<BillingPlan> findAllByOrderBySortOrder();
}
