package com.isums.notificationservice.domains.dtos;

import jakarta.validation.constraints.*;

/**
 * Body shape for create + update endpoints. {@code code} is immutable
 * once set so payment intents can rely on it as a stable reference;
 * to "rename", deactivate the row and create a new one.
 */
public record UpsertSubscriptionPlanRequest(
        @NotBlank @Size(max = 40) String code,
        @Size(max = 4000) String nameTranslations,
        @NotNull @Min(1)   @Max(3650) Integer durationDays,
        // 10.000đ là sàn VNPay khuyến nghị; thẻ Sacombank/ACB... reject
        // dưới mức này. Cap 50tr giữ cho landlord khỏi gõ nhầm 9 số 0.
        @NotNull
        @Min(value = 10_000,    message = "Giá tối thiểu là 10.000đ (theo hạn mức ngân hàng VNPay)")
        @Max(value = 50_000_000, message = "Giá tối đa là 50.000.000đ")
        Integer priceVnd,
        @Min(0)  Integer voiceQuotaMonthly,
        @Min(0)  Integer smsQuotaMonthly,
        Integer sortOrder,
        Boolean isActive,
        Boolean isFeatured
) {}
