package com.teadrink.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class MemberRechargeRequest {

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private String remark;
}

