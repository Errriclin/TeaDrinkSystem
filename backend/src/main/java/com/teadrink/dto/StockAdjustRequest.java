package com.teadrink.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class StockAdjustRequest {
    @NotNull
    private BigDecimal deltaQty;

    private String remark;
}

