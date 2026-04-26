package com.teadrink.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockAdjustRequest {
    /** 可空；与 safety_stock 至少一项有有效修改 */
    @JsonProperty("delta_qty")
    private BigDecimal deltaQty;

    @JsonProperty("safety_stock")
    private BigDecimal safetyStock;

    private String remark;
}

