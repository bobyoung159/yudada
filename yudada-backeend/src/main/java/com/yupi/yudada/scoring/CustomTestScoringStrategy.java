package com.yupi.yudada.scoring;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yupi.yudada.model.dto.question.QuestionContentDTO;
import com.yupi.yudada.model.entity.App;
import com.yupi.yudada.model.entity.Question;
import com.yupi.yudada.model.entity.ScoringResult;
import com.yupi.yudada.model.entity.UserAnswer;
import com.yupi.yudada.model.vo.QuestionVO;
import com.yupi.yudada.service.AppService;
import com.yupi.yudada.service.QuestionService;
import com.yupi.yudada.service.ScoringResultService;

import javax.annotation.Resource;
import java.sql.Wrapper;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ScoringStrategyConfig(appType = 1,scoringStrategy = 0)
public class CustomTestScoringStrategy implements ScoringStrategy {
    @Resource
    private QuestionService questionService;
    @Resource
    private ScoringResultService scoringResultService;
    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        //根据id查询题目和题目结果信息（一个用户做了多组题，有多组答案）
        Question question = questionService.getOne(
                //lambdaQuery中是你要查询的实体类，也就是数据库对应的表
                Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, app.getId())
        );
        List<ScoringResult> scoringResultList = scoringResultService.list(
                Wrappers.lambdaQuery(ScoringResult.class)
                        .eq(ScoringResult::getAppId, app.getId())
        );
        //统计用户每个选择对应的属性信息
        Map<String,Integer> optionCount = new HashMap<>();
        //获取题目列表
        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContent = questionVO.getQuestionContent();

        //遍历题目
        for (QuestionContentDTO questionContentDTO : questionContent) {
            for (String answer : choices) {
                for (QuestionContentDTO.Option option : questionContentDTO.getOptions()) {
                    if(option.getKey().equals(answer)){
                        String result = option.getResult();
                        if(!optionCount.containsKey(result)){
                            optionCount.put(result,0);
                        }
                        optionCount.put(result,optionCount.get(result)+1);
                    }
                }
            }
        }
        //遍历每种评分结果，计算那个结果的分更高
        //初始化最高分和对应评分结果
        int maxScore = 0;
        ScoringResult maxScoreResult = scoringResultList.get(0);

        for (ScoringResult scoringResult : scoringResultList) {
            List<String> resultProp  = JSONUtil.toList(scoringResult.getResultProp(),String.class);
            //计算当前评分结果的分数,[I,E]
            int score = resultProp.stream()
                    .mapToInt(prop -> optionCount.getOrDefault(prop,0)).sum();
            if(score > maxScore){
                maxScore = score;
                maxScoreResult = scoringResult;
            }
        }

        //构造返回值，填充答案对象的属性
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setAppId(app.getId());
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultId(maxScoreResult.getId());
        userAnswer.setResultName(maxScoreResult.getResultName());
        userAnswer.setResultDesc(maxScoreResult.getResultDesc());
        userAnswer.setResultPicture(maxScoreResult.getResultPicture());

        return userAnswer;
    }
}
