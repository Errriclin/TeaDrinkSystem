package com.teadrink.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PurchaseQueryMapper {

    @Select("SELECT p.id, p.order_no, p.supplier_name AS supplier, p.total_amount, p.status, " +
            "IFNULL(u.name, '') AS operator, p.created_at " +
            "FROM t_purchase_order p LEFT JOIN t_user u ON u.id = p.operator_id " +
            "ORDER BY p.created_at DESC, p.id DESC")
    List<Map<String, Object>> listOrders();

    @Select("SELECT p.id, p.order_no, p.supplier_name AS supplier, p.total_amount, p.status, " +
            "IFNULL(u.name, '') AS operator, p.created_at, p.inbound_at " +
            "FROM t_purchase_order p LEFT JOIN t_user u ON u.id = p.operator_id " +
            "WHERE p.id = #{id} LIMIT 1")
    Map<String, Object> detailBase(@Param("id") Long id);

    @Select("SELECT i.material_id, m.name AS material_name, i.quantity, i.unit_price, i.subtotal " +
            "FROM t_purchase_order_item i " +
            "LEFT JOIN t_material m ON m.id = i.material_id " +
            "WHERE i.purchase_order_id = #{id} ORDER BY i.id ASC")
    List<Map<String, Object>> detailItems(@Param("id") Long id);
}

