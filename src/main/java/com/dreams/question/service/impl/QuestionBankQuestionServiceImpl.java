package com.dreams.question.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dreams.question.common.ErrorCode;
import com.dreams.question.constant.CommonConstant;
import com.dreams.question.exception.BusinessException;
import com.dreams.question.exception.ThrowUtils;
import com.dreams.question.mapper.QuestionBankQuestionMapper;
import com.dreams.question.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import com.dreams.question.model.entity.Question;
import com.dreams.question.model.entity.QuestionBank;
import com.dreams.question.model.entity.QuestionBankQuestion;
import com.dreams.question.model.entity.User;
import com.dreams.question.model.vo.QuestionBankQuestionVO;
import com.dreams.question.model.vo.UserVO;
import com.dreams.question.service.QuestionBankQuestionService;
import com.dreams.question.service.QuestionBankService;
import com.dreams.question.service.QuestionService;
import com.dreams.question.service.UserService;
import com.dreams.question.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 题库题目关联服务实现
 */
@Service
@Slf4j
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion> implements QuestionBankQuestionService {

    @Resource
    private UserService userService;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    @Lazy
    private QuestionService questionService;

    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add                  对创建的数据进行校验
     */
    @Override
    public void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add) {
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR);
        // 题目和题库必须存在
        Long questionId = questionBankQuestion.getQuestionId();
        if (questionId != null) {
            Question question = questionService.getById(questionId);
            ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        Long questionBankId = questionBankQuestion.getQuestionBankId();
        if (questionBankId != null) {
            QuestionBank questionBank = questionBankService.getById(questionBankId);
            ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库不存在");
        }
    }


    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest) {
        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        if (questionBankQuestionQueryRequest == null) {
            return queryWrapper;
        }
        // todo 从对象中取值
        Long id = questionBankQuestionQueryRequest.getId();
        Long notId = questionBankQuestionQueryRequest.getNotId();
        String sortField = questionBankQuestionQueryRequest.getSortField();
        String sortOrder = questionBankQuestionQueryRequest.getSortOrder();
        Long userId = questionBankQuestionQueryRequest.getUserId();
        Long questionBankId = questionBankQuestionQueryRequest.getQuestionBankId();
        Long questionId = questionBankQuestionQueryRequest.getQuestionId();

        // todo 补充需要的查询条件

        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionBankId), "questionBankId", questionBankId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题库题目关联封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    @Override
    public QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request) {
        // 对象转封装类
        QuestionBankQuestionVO questionBankQuestionVO = QuestionBankQuestionVO.objToVo(questionBankQuestion);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = questionBankQuestion.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionBankQuestionVO.setUser(userVO);


        return questionBankQuestionVO;
    }

    /**
     * 分页获取题库题目关联封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request) {
        List<QuestionBankQuestion> questionBankQuestionList = questionBankQuestionPage.getRecords();
        Page<QuestionBankQuestionVO> questionBankQuestionVOPage = new Page<>(questionBankQuestionPage.getCurrent(), questionBankQuestionPage.getSize(), questionBankQuestionPage.getTotal());
        if (CollUtil.isEmpty(questionBankQuestionList)) {
            return questionBankQuestionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionBankQuestionVO> questionBankQuestionVOList = questionBankQuestionList.stream().map(questionBankQuestion -> {
            return QuestionBankQuestionVO.objToVo(questionBankQuestion);
        }).collect(Collectors.toList());

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionBankQuestionList.stream().map(QuestionBankQuestion::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充信息
        questionBankQuestionVOList.forEach(questionBankQuestionVO -> {
            Long userId = questionBankQuestionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionBankQuestionVO.setUser(userService.getUserVO(user));
        });
        // endregion

        questionBankQuestionVOPage.setRecords(questionBankQuestionVOList);
        return questionBankQuestionVOPage;
    }


//    /**
//     * 批量向题库添加题目
//     *
//     * @param questionIdList
//     * @param questionBankId
//     * @param loginUser
//     */
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public void batchAddQuestionsToBank(List<Long> questionIdList, Long questionBankId, User loginUser) {
//        // 参数校验
//        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表为空");
//        ThrowUtils.throwIf(questionBankId == null || questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库非法");
//        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
//        // 检查题目 id 是否存在
//        List<Question> questionList = questionService.listByIds(questionIdList);
//        // 合法的题目 id
//        List<Long> validQuestionIdList = questionList.stream()
//                .map(Question::getId)
//                .collect(Collectors.toList());
//        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR, "合法的题目列表为空");
//        // 检查题库 id 是否存在
//        QuestionBank questionBank = questionBankService.getById(questionBankId);
//        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库不存在");
//
//        // 检查哪些题目还不存在于题库中，避免重复插入
//        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
//                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
//                .in(QuestionBankQuestion::getQuestionId, validQuestionIdList);
//        List<QuestionBankQuestion> existQuestionList = this.list(lambdaQueryWrapper);
//        // 已存在于题库中的题目 id
//        Set<Long> existQuestionIdSet = existQuestionList.stream()
//                .map(QuestionBankQuestion::getId)
//                .collect(Collectors.toSet());
//         // 已存在于题库中的题目 id，不需要再次添加
//        validQuestionIdList = validQuestionIdList.stream().filter(questionId -> {
//            return !existQuestionIdSet.contains(questionId);
//        }).collect(Collectors.toList());
//        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR, "所有题目都已存在于题库中");
//
//        // 执行插入
//        for (Long questionId : validQuestionIdList) {
//            QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
//            questionBankQuestion.setQuestionBankId(questionBankId);
//            questionBankQuestion.setQuestionId(questionId);
//            questionBankQuestion.setUserId(loginUser.getId());
//            try {
//                boolean result = this.save(questionBankQuestion);
//                if (!result) {
//                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
//                }
//            } catch (DataIntegrityViolationException e) {
//                log.error("数据库唯一键冲突或违反其他完整性约束，题目 id: {}, 题库 id: {}, 错误信息: {}",
//                        questionId, questionBankId, e.getMessage());
//                throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
//            } catch (DataAccessException e) {
//                log.error("数据库连接问题、事务问题等导致操作失败，题目 id: {}, 题库 id: {}, 错误信息: {}",
//                        questionId, questionBankId, e.getMessage());
//                throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
//            } catch (Exception e) {
//                // 捕获其他异常，做通用处理
//                log.error("添加题目到题库时发生未知错误，题目 id: {}, 题库 id: {}, 错误信息: {}",
//                        questionId, questionBankId, e.getMessage());
//                throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
//            }
//
//        }
//    }

//    /**
//     * 批量向题库添加题目
//     *
//     * @param questionIdList
//     * @param questionBankId
//     * @param loginUser
//     */
//    @Override
//    public void batchAddQuestionsToBank(List<Long> questionIdList, Long questionBankId, User loginUser) {
//        // 参数校验
//        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表为空");
//        ThrowUtils.throwIf(questionBankId == null || questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库非法");
//        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
//
//        // 检查题目 id 是否存在,优化前
//        // List<Question> questionList = questionService.listByIds(questionIdList);
//
//        // 检查题目 id 是否存在
//        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = Wrappers.lambdaQuery(Question.class)
//                .select(Question::getId)
//                .in(Question::getId, questionIdList);
//        List<Question> questionList = questionService.list(questionLambdaQueryWrapper);
//
//        // 合法的题目 id，优化前
//        //List<Long> validQuestionIdList = questionList.stream()
//        //        .map(Question::getId)
//        //        .collect(Collectors.toList());
//        //ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR, "合法的题目列表为空");
//
//
//        // 合法的题目 id
//        List<Long> validQuestionIdList = questionService.listObjs(questionLambdaQueryWrapper, obj -> (Long) obj);
//        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR, "合法的题目列表为空");
//
//
//        // 检查题库 id 是否存在
//        QuestionBank questionBank = questionBankService.getById(questionBankId);
//        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库不存在");
//
//        // 检查哪些题目还不存在于题库中，避免重复插入
//        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
//                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
//                .in(QuestionBankQuestion::getQuestionId, validQuestionIdList);
//        List<QuestionBankQuestion> existQuestionList = this.list(lambdaQueryWrapper);
//        // 已存在于题库中的题目 id
//        Set<Long> existQuestionIdSet = existQuestionList.stream()
//                .map(QuestionBankQuestion::getId)
//                .collect(Collectors.toSet());
//        // 已存在于题库中的题目 id，不需要再次添加
//        validQuestionIdList = validQuestionIdList.stream().filter(questionId -> {
//            return !existQuestionIdSet.contains(questionId);
//        }).collect(Collectors.toList());
//        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR, "所有题目都已存在于题库中");
//
//        // 执行插入
//        // 分批处理避免长事务，假设每次处理 1000 条数据
//        int batchSize = 1000;
//        int totalQuestionListSize = validQuestionIdList.size();
//        for (int i = 0; i < totalQuestionListSize; i += batchSize) {
//            // 生成每批次的数据
//            List<Long> subList = validQuestionIdList.subList(i, Math.min(i + batchSize, totalQuestionListSize));
//            List<QuestionBankQuestion> questionBankQuestions = subList.stream().map(questionId -> {
//                QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
//                questionBankQuestion.setQuestionBankId(questionBankId);
//                questionBankQuestion.setQuestionId(questionId);
//                questionBankQuestion.setUserId(loginUser.getId());
//                return questionBankQuestion;
//            }).collect(Collectors.toList());
//            // 使用事务处理每批数据
//            QuestionBankQuestionService questionBankQuestionService = (QuestionBankQuestionServiceImpl) AopContext.currentProxy();
//            questionBankQuestionService.batchAddQuestionsToBankInner(questionBankQuestions);
//        }
//
//    }

//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {
//        for (QuestionBankQuestion questionBankQuestion : questionBankQuestions) {
//            long questionId = questionBankQuestion.getQuestionId();
//            long questionBankId = questionBankQuestion.getQuestionBankId();
//            try {
//                boolean result = this.save(questionBankQuestion);
//                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
//            } catch (DataIntegrityViolationException e) {
//                log.error("数据库唯一键冲突或违反其他完整性约束，题目 id: {}, 题库 id: {}, 错误信息: {}",
//                        questionId, questionBankId, e.getMessage());
//                throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
//            } catch (DataAccessException e) {
//                log.error("数据库连接问题、事务问题等导致操作失败，题目 id: {}, 题库 id: {}, 错误信息: {}",
//                        questionId, questionBankId, e.getMessage());
//                throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
//            } catch (Exception e) {
//                // 捕获其他异常，做通用处理
//                log.error("添加题目到题库时发生未知错误，题目 id: {}, 题库 id: {}, 错误信息: {}",
//                        questionId, questionBankId, e.getMessage());
//                throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
//            }
//        }
//    }


    /**
     * 批量向题库添加题目
     *
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     */
    @Override
    public void batchAddQuestionsToBank(List<Long> questionIdList, Long questionBankId, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表为空");
        ThrowUtils.throwIf(questionBankId == null || questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库非法");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 检查题目 id 是否存在
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = Wrappers.lambdaQuery(Question.class)
                .select(Question::getId)
                .in(Question::getId, questionIdList);
        List<Question> questionList = questionService.list(questionLambdaQueryWrapper);

        // 合法的题目 id
        List<Long> validQuestionIdList = questionService.listObjs(questionLambdaQueryWrapper, obj -> (Long) obj);
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR, "合法的题目列表为空");


        // 检查题库 id 是否存在
        QuestionBank questionBank = questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR, "题库不存在");

        // 检查哪些题目还不存在于题库中，避免重复插入
        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .eq(QuestionBankQuestion::getQuestionBankId, questionBankId)
                .in(QuestionBankQuestion::getQuestionId, validQuestionIdList);
        List<QuestionBankQuestion> existQuestionList = this.list(lambdaQueryWrapper);
        // 已存在于题库中的题目 id
        Set<Long> existQuestionIdSet = existQuestionList.stream()
                .map(QuestionBankQuestion::getId)
                .collect(Collectors.toSet());
        // 已存在于题库中的题目 id，不需要再次添加
        validQuestionIdList = validQuestionIdList.stream().filter(questionId -> {
            return !existQuestionIdSet.contains(questionId);
        }).collect(Collectors.toList());
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR, "所有题目都已存在于题库中");

        // 执行插入
        // 自定义线程池
        ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(
                20,                         // 核心线程数
                50,                        // 最大线程数
                60L,                       // 线程空闲存活时间
                TimeUnit.SECONDS,           // 存活时间单位
                new LinkedBlockingQueue<>(10000),  // 阻塞队列容量
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：由调用线程处理任务
        );

        // 用于保存所有批次的 CompletableFuture
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 分批处理避免长事务，假设每次处理 1000 条数据
        int batchSize = 1000;
        int totalQuestionListSize = validQuestionIdList.size();
        for (int i = 0; i < totalQuestionListSize; i += batchSize) {
            // 生成每批次的数据
            List<Long> subList = validQuestionIdList.subList(i, Math.min(i + batchSize, totalQuestionListSize));
            List<QuestionBankQuestion> questionBankQuestions = subList.stream().map(questionId -> {
                QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
                questionBankQuestion.setQuestionBankId(questionBankId);
                questionBankQuestion.setQuestionId(questionId);
                questionBankQuestion.setUserId(loginUser.getId());
                return questionBankQuestion;
            }).collect(Collectors.toList());

            QuestionBankQuestionService questionBankQuestionService = (QuestionBankQuestionServiceImpl) AopContext.currentProxy();
            // 异步处理每批数据并添加到 futures 列表
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                questionBankQuestionService.batchAddQuestionsToBankInner(questionBankQuestions);
            }, customExecutor);
            futures.add(future);
        }

        // 等待所有批次操作完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 关闭线程池
        customExecutor.shutdown();

    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionsToBankInner(List<QuestionBankQuestion> questionBankQuestions) {
        try {
            boolean result = this.saveBatch(questionBankQuestions);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        } catch (DataIntegrityViolationException e) {
            log.error("数据库唯一键冲突或违反其他完整性约束, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已存在于该题库，无法重复添加");
        } catch (DataAccessException e) {
            log.error("数据库连接问题、事务问题等导致操作失败, 错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库操作失败");
        } catch (Exception e) {
            // 捕获其他异常，做通用处理
            log.error("添加题目到题库时发生未知错误，错误信息: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemoveQuestionsFromBank(List<Long> questionIdList, Long questionBankId) {
        // 参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "题目列表为空");
        ThrowUtils.throwIf(questionBankId == null || questionBankId <= 0, ErrorCode.PARAMS_ERROR, "题库非法");
        // 执行删除关联
        for (Long questionId : questionIdList) {
            // 构造查询
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .eq(QuestionBankQuestion::getQuestionId, questionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            boolean result = this.remove(lambdaQueryWrapper);
            if (!result) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "从题库移除题目失败");
            }
        }

    }





}
