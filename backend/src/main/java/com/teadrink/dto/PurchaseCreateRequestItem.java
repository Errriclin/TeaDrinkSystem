package com.teadrink.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class PurchaseCreateRequestItem {
    @NotNull
    private Long materialId;

    @NotNull
    @Min(1)
    private BigDecimal quantity;

    @NotNull
    private BigDecimal unitPrice;

    @NotNull
    private BigDecimal subtotal;
}

