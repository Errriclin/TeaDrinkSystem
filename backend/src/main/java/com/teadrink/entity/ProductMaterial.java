package com.teadrink.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_product_material")
public class ProductMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long materialId;
    private BigDecimal consumeQty;
}

