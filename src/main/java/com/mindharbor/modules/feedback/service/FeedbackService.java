package com.mindharbor.modules.feedback.service;

import com.mindharbor.common.PageResult;
import com.mindharbor.modules.feedback.dto.FeedbackHandleRequest;
import com.mindharbor.modules.feedback.dto.FeedbackRequest;
import com.mindharbor.modules.feedback.vo.FeedbackVO;

/** 反馈工单业务接口：用户提交，管理员查看与处理。 */
public interface FeedbackService {

    FeedbackVO submit(FeedbackRequest request);

    PageResult<FeedbackVO> list(int page, int size, String status);

    FeedbackVO handle(long feedbackId, FeedbackHandleRequest request);
}
