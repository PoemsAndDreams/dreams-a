package com.dreams.question.sentinel.fallback;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreams.question.common.BaseResponse;
import com.dreams.question.common.ResultUtils;
import com.dreams.question.model.dto.questionBank.QuestionBankQueryRequest;
import com.dreams.question.model.vo.QuestionBankVO;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;

/**
 * @author PoemsAndDreams
 * @description //降级
 */
@Component
public class QuestionBankFallback {
    /**
     * listQuestionBankVOByPage 降级操作：直接返回本地数据
     */
    public static BaseResponse<Page<QuestionBankVO>> handleFallback(@RequestBody QuestionBankQueryRequest questionBankQueryRequest,
                                                             HttpServletRequest request, Throwable ex) {
        // 可以返回本地数据或空数据
        return ResultUtils.success(null);
    }
}
