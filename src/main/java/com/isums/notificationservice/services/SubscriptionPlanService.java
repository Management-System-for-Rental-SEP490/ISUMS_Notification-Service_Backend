package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.SubscriptionPlanDto;
import com.isums.notificationservice.domains.dtos.UpsertSubscriptionPlanRequest;
import com.isums.notificationservice.domains.entities.SubscriptionPlan;
import com.isums.notificationservice.exceptions.ConflictException;
import com.isums.notificationservice.infrastructures.repositories.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for the landlord-managed plan catalogue. Public list (active
 * only) is exposed to tenants for the upgrade picker; full CRUD is
 * landlord/admin only — controller enforces the role gate.
 *
 * <p>Plans are referenced by stable {@code code} from payment intents,
 * so we never delete rows: deactivation flips {@code is_active=false}
 * but keeps the row for audit linkage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository repo;

    public List<SubscriptionPlanDto> listActiveForCustomers() {
        return repo.findByIsActiveTrueOrderBySortOrderAscPriceVndAsc()
                .stream().map(this::toDto).toList();
    }

    public List<SubscriptionPlanDto> listAllForAdmin() {
        return repo.findAllByOrderBySortOrderAscPriceVndAsc()
                .stream().map(this::toDto).toList();
    }

    public SubscriptionPlanDto getById(UUID id) {
        return toDto(repo.findById(id)
                .orElseThrow(() -> new ConflictException("Plan not found: " + id)));
    }

    public SubscriptionPlan getEntity(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ConflictException("Plan not found: " + id));
    }

    @Transactional
    public SubscriptionPlanDto create(UpsertSubscriptionPlanRequest req, UUID actorId) {
        repo.findByCode(req.code()).ifPresent(p -> {
            throw new ConflictException("Plan code already exists: " + req.code());
        });
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .code(req.code())
                .nameTranslations(req.nameTranslations())
                .durationDays(req.durationDays())
                .priceVnd(req.priceVnd())
                .voiceQuotaMonthly(req.voiceQuotaMonthly() != null ? req.voiceQuotaMonthly() : 100)
                .smsQuotaMonthly(req.smsQuotaMonthly() != null ? req.smsQuotaMonthly() : 200)
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .isActive(req.isActive() == null ? true : req.isActive())
                .isFeatured(Boolean.TRUE.equals(req.isFeatured()))
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        plan = repo.save(plan);
        log.info("[Plans] CREATE actor={} code={} duration={}d price={} active={}",
                actorId, plan.getCode(), plan.getDurationDays(),
                plan.getPriceVnd(), plan.getIsActive());
        return toDto(plan);
    }

    @Transactional
    public SubscriptionPlanDto update(UUID id, UpsertSubscriptionPlanRequest req, UUID actorId) {
        SubscriptionPlan p = getEntity(id);
        // code is immutable — silently ignore attempts to change so we
        // don't surprise the caller with a 400 when their form posts
        // back the existing code unchanged.
        if (req.nameTranslations() != null) p.setNameTranslations(req.nameTranslations());
        if (req.durationDays() != null)     p.setDurationDays(req.durationDays());
        if (req.priceVnd() != null)         p.setPriceVnd(req.priceVnd());
        if (req.voiceQuotaMonthly() != null) p.setVoiceQuotaMonthly(req.voiceQuotaMonthly());
        if (req.smsQuotaMonthly() != null)  p.setSmsQuotaMonthly(req.smsQuotaMonthly());
        if (req.sortOrder() != null)        p.setSortOrder(req.sortOrder());
        if (req.isActive() != null)         p.setIsActive(req.isActive());
        if (req.isFeatured() != null)       p.setIsFeatured(req.isFeatured());
        p.setUpdatedBy(actorId);
        p = repo.save(p);
        log.info("[Plans] UPDATE actor={} code={} price={} active={}",
                actorId, p.getCode(), p.getPriceVnd(), p.getIsActive());
        return toDto(p);
    }

    @Transactional
    public void deactivate(UUID id, UUID actorId) {
        SubscriptionPlan p = getEntity(id);
        p.setIsActive(false);
        p.setUpdatedBy(actorId);
        repo.save(p);
        log.info("[Plans] DEACTIVATE actor={} code={}", actorId, p.getCode());
    }

    private SubscriptionPlanDto toDto(SubscriptionPlan p) {
        return new SubscriptionPlanDto(
                p.getId(), p.getCode(), p.getNameTranslations(),
                p.getDurationDays(), p.getPriceVnd(),
                p.getVoiceQuotaMonthly(), p.getSmsQuotaMonthly(),
                p.getSortOrder(), p.getIsActive(), p.getIsFeatured(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
