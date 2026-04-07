package com.teadrink.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PurchaseCreateRequest {
    private String supplier;

    @NotNull
    private BigDecimal totalAmount;

    @NotNull
    private List<PurchaseCreateRequestItem> items;
}

