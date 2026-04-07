package com.teadrink.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SaleOrderManageMapper {

    @Select("SELECT COUNT(1) FROM t_sale_order o")
    Long countAll();

    @Select("SELECT " +
            "  o.order_no, " +
            "  o.created_at, " +
            "  IFNULL(m.name, IFNULL(m.phone, '散客')) AS member_name, " +
            "  (SELECT IFNULL(SUM(i.quantity), 0) FROM t_sale_order_item i WHERE i.sale_order_id = o.id) AS items_count, " +
            "  o.total_amount, " +
            "  o.discount_amount, " +
            "  o.pay_amount, " +
            "  o.pay_type, " +
            "  IFNULL(u.name, '') AS cashier, " +
            "  o.status " +
            "FROM t_sale_order o " +
            "LEFT JOIN t_member m ON m.id = o.member_id " +
            "LEFT JOIN t_user u ON u.id = o.cashier_id " +
            "ORDER BY o.created_at DESC " +
            "LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> listPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT " +
            "  o.order_no, " +
            "  o.created_at, " +
            "  o.status, " +
            "  o.total_amount, " +
            "  o.discount_amount, " +
            "  o.pay_amount, " +
            "  o.pay_type, " +
            "  IFNULL(m.name, IFNULL(m.phone, '散客')) AS member_name, " +
            "  m.level AS member_level, " +
            "  IFNULL(u.name, '') AS cashier " +
            "FROM t_sale_order o " +
            "LEFT JOIN t_member m ON m.id = o.member_id " +
            "LEFT JOIN t_user u ON u.id = o.cashier_id " +
            "WHERE o.order_no = #{orderNo} " +
            "LIMIT 1")
    Map<String, Object> getDetailBase(@Param("orderNo") String orderNo);

    @Select("SELECT product_name, unit_price, quantity, subtotal " +
            "FROM t_sale_order_item i " +
            "INNER JOIN t_sale_order o ON o.id = i.sale_order_id " +
            "WHERE o.order_no = #{orderNo} " +
            "ORDER BY i.id ASC")
    List<Map<String, Object>> listDetailItems(@Param("orderNo") String orderNo);
}

