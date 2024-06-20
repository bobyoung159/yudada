package com.yupi.yudada.model.dto.statistic;

import lombok.Data;

/**
 * APP用户提交答案统计
 */
@Data
public class AppAnswerCountDTO {
    private Long appId;
    private Long answerCount;
}
