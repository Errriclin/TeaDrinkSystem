package com.teadrink.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecipeLineResponse {
    private Long id;
    private Long materialId;
    private String materialName;
    private String unit;
    private BigDecimal consumeQty;
}
