package com.teadrink.controller;

import com.teadrink.common.Result;
import com.teadrink.dto.MemberRechargeRequest;
import com.teadrink.entity.Member;
import com.teadrink.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MemberController {

    @Resource
    private MemberService memberService;

    /**
     * POS 查询会员：GET /api/member?phone=138...
     * 前端约定：不存在则 http 404
     */
    @GetMapping("/member")
    public ResponseEntity<?> getByPhone(@RequestParam("phone") String phone) {
        Member m = memberService.getByPhone(phone);
        if (m == null) {
            return ResponseEntity.status(404).body(Result.fail("会员不存在"));
        }
        return ResponseEntity.ok(Result.ok(m));
    }

    /**
     * 会员列表：GET /api/member/list
     */
    @GetMapping("/member/list")
    public Result<List<Member>> list() {
        return Result.ok(memberService.listAll());
    }

    /**
     * 新增会员：POST /api/member
     * 前端提交：{phone,name,balance}；等级由开卡时「初始储值」按单次金额规则自动计算
     */
    @PostMapping("/member")
    public Result<Member> create(@Valid @RequestBody Member member) {
        // 仅做演示：简单复用实体作为入参
        if (member.getPhone() == null || member.getPhone().trim().isEmpty()) {
            return Result.fail("手机号不能为空");
        }
        Member created = memberService.create(member);
        return Result.ok(created);
    }

    /**
     * 搜索会员：GET /api/member/search?keyword=xxx
     */
    @GetMapping("/member/search")
    public Result<List<Member>> search(@RequestParam("keyword") String keyword) {
        return Result.ok(memberService.search(keyword));
    }

    /**
     * 会员详情：基础信息 + 最近账务 + 最近订单
     */
    @GetMapping("/member/{id}/detail")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long memberId) {
        return Result.ok(memberService.detail(memberId));
    }

    /**
     * 会员充值：POST /api/member/{id}/recharge
     */
    @PostMapping("/member/{id}/recharge")
    public Result<Member> recharge(@PathVariable("id") Long memberId,
                                   @Valid @RequestBody MemberRechargeRequest request) {
        return Result.ok(memberService.recharge(memberId, request.getAmount(), request.getRemark()));
    }
}

