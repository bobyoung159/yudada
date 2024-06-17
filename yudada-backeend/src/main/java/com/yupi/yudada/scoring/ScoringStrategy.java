package com.yupi.yudada.scoring;

import com.yupi.yudada.model.entity.App;
import com.yupi.yudada.model.entity.UserAnswer;

import java.util.List;

public interface ScoringStrategy {
    UserAnswer doScore(List<String> choices, App app) throws Exception;
}
