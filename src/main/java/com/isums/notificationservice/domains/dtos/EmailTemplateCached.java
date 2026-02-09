package com.isums.notificationservice.domains.dtos;

import java.util.List;

public record EmailTemplateCached(
        int version,
        String subjectTpl,
        String htmlTpl,
        String textTpl,
        List<String> allowedVars
) {}