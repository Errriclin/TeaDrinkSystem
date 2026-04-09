package com.teadrink.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class PurchaseCreateRequestItem {
    /** 与 materialName 二选一：下拉选择已有原料时传此字段 */
    private Long materialId;

    /** 与 materialId 二选一：手动输入名称时按名称匹配已有原料，不存在则新建 */
    private String materialName;
    /** 手动输入名称时可同时指定单位（如 g/ml/包） */
    private String materialUnit;

    @NotNull
    @Min(1)
    private BigDecimal quantity;

    @NotNull
    private BigDecimal unitPrice;

    @NotNull
    private BigDecimal subtotal;
}

