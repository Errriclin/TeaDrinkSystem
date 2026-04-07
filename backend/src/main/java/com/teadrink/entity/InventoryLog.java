package com.teadrink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_inventory_log")
public class InventoryLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private BigDecimal changeQty;
    private BigDecimal afterStock;
    private String bizType;
    private Long refId;
    private String typeName;
    private LocalDateTime createdAt;
}

