package com.dreams.question.sentinel.blockHandler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreams.question.common.BaseResponse;
import com.dreams.question.common.ErrorCode;
import com.dreams.question.common.ResultUtils;
import com.dreams.question.model.dto.question.QuestionQueryRequest;
import com.dreams.question.model.vo.QuestionVO;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;

import static com.dreams.question.sentinel.fallback.QuestionFallback.handleFallback;

/**
 * @author PoemsAndDreams
 * @description //限流
 */
@Component
public class QuestionBlockHandler {

    /**
     * listQuestionVOByPage 流控操作
     * 限流：提示“系统压力过大，请耐心等待”
     * 熔断：执行降级操作
     */
    public BaseResponse<Page<QuestionVO>> handleBlockException(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                   HttpServletRequest request, BlockException ex) {
        // 降级操作
        if (ex instanceof DegradeException) {
            return handleFallback(questionQueryRequest, request, ex);
        }
        // 限流操作
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统压力过大，请耐心等待");
    }


}