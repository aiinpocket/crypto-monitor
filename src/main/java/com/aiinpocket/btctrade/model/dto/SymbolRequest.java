package com.aiinpocket.btctrade.model.dto;

import jakarta.validation.constraints.NotBlank;

public record SymbolRequest(
        @NotBlank String symbol,
        String displayName
) {}
