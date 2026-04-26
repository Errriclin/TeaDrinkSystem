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
     * 规则：启用时，(1) 已设安全库存(大于0) 则 当前库存小于等于安全库存 即预警（含刚好等于线）；
     * (2) 安全库存为 0 视为未设阈值，仅当库存小于等于 0 时标为需关注。
     */
    @Select("SELECT " +
            "  m.id, m.name, m.unit, m.stock_quantity, m.safety_stock, m.status, m.updated_at, " +
            "  CASE WHEN m.status = 1 AND ( " +
            "    (m.safety_stock > 0 AND m.stock_quantity <= m.safety_stock) " +
            "    OR (m.safety_stock = 0 AND m.stock_quantity <= 0) " +
            "  ) THEN 1 ELSE 0 END AS is_low " +
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

