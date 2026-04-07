package com.teadrink.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SaleOrderCreateRequest {

    private Long memberId;

    @NotNull
    private BigDecimal totalAmount;

    @NotNull
    private BigDecimal discountAmount;

    @NotNull
    private BigDecimal payAmount;

    @NotNull
    private Integer payType;

    @NotNull
    private List<SaleOrderCreateRequestItem> items;
}

