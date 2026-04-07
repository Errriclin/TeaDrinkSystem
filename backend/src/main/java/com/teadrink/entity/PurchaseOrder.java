package com.teadrink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_purchase_order")
public class PurchaseOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String supplierName;
    private BigDecimal totalAmount;
    private Integer status;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime inboundAt;
}

