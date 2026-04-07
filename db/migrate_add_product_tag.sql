-- 为现有库补充商品标签字段（仅执行一次）
ALTER TABLE t_product
    ADD COLUMN product_tag VARCHAR(32) NOT NULL DEFAULT '常规商品' COMMENT '商品标签：常规商品/当季限定/地区限定/自定义'
    AFTER category;

