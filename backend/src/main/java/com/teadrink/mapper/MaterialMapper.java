package com.teadrink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teadrink.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    @Update("UPDATE t_material SET stock_quantity = stock_quantity - #{consume} " +
            "WHERE id = #{materialId} AND stock_quantity >= #{consume}")
    int deductStockIfEnough(@Param("materialId") Long materialId, @Param("consume") BigDecimal consume);

    @Select("SELECT stock_quantity FROM t_material WHERE id = #{materialId}")
    BigDecimal getStockQuantity(@Param("materialId") Long materialId);

    @Update("UPDATE t_material SET stock_quantity = stock_quantity + #{quantity} WHERE id = #{materialId}")
    int addStock(@Param("materialId") Long materialId, @Param("quantity") BigDecimal quantity);
}

