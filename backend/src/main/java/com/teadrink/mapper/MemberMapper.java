package com.teadrink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teadrink.entity.Member;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberMapper extends BaseMapper<Member> {
}

