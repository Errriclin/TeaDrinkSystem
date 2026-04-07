package com.teadrink.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface InventoryQueryMapper {

    /**
     * 库存列表（带 is_low）
     */
    @Select("SELECT " +
            "  m.id, m.name, m.unit, m.stock_quantity, m.safety_stock, m.status, m.updated_at, " +
            "  CASE WHEN m.status = 1 AND m.stock_quantity < m.safety_stock THEN 1 ELSE 0 END AS is_low " +
            "FROM t_material m " +
            "ORDER BY m.id ASC")
    List<Map<String, Object>> listMaterialsWithLowFlag();

    /**
     * 最近库存流水（带 material_name；quantity 字段给前端使用）
     */
    @Select("SELECT " +
            "  l.id, " +
            "  l.material_id, " +
            "  m.name AS material_name, " +
            "  l.change_qty AS quantity, " +
            "  l.after_stock, " +
            "  l.type_name, " +
            "  l.biz_type, " +
            "  l.ref_id, " +
            "  l.created_at " +
            "FROM t_inventory_log l " +
            "LEFT JOIN t_material m ON m.id = l.material_id " +
            "ORDER BY l.created_at DESC, l.id DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> recentInventoryLogs(@Param("limit") int limit);
}

