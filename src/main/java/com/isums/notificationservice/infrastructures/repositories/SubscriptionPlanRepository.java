package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    /** Active plans for the FE picker — sorted by curator's preferred order. */
    List<SubscriptionPlan> findByIsActiveTrueOrderBySortOrderAscPriceVndAsc();

    /** All plans (active + retired) — admin / landlord-only catalogue view. */
    List<SubscriptionPlan> findAllByOrderBySortOrderAscPriceVndAsc();

    Optional<SubscriptionPlan> findByCode(String code);
}
