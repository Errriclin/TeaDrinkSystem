package com.teadrink.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ProductRecipeItemRequest {
    @NotNull
    private Long materialId;

    @NotNull
    private BigDecimal consumeQty;
}
