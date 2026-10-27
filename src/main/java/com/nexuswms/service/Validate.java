package com.nexuswms.service;

import jakarta.validation.*;
import java.util.stream.Collectors;

/** Service-layer Bean Validation, so rules hold no matter which caller (UI, voice, agent, test) reaches the service. */
public final class Validate {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private Validate() {}

    public static <T> T form(T bean) {
        if (bean == null) throw new BusinessException("Form data is missing.");
        var violations = VALIDATOR.validate(bean);
        if (!violations.isEmpty()) {
            throw new BusinessException(violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining("; ")));
        }
        return bean;
    }
}
