package com.teadrink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_material")
public class Material {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String unit;
    private BigDecimal stockQuantity;
    private BigDecimal safetyStock;
    private Integer status;
    private LocalDateTime updatedAt;
}

