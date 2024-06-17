package com.yupi.yudada.scoring;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yupi.yudada.common.ResultUtils;
import com.yupi.yudada.config.RedissonConfig;
import com.yupi.yudada.manager.AiManager;
import com.yupi.yudada.model.dto.question.QuestionAnswerDTO;
import com.yupi.yudada.model.dto.question.QuestionContentDTO;
import com.yupi.yudada.model.entity.App;
import com.yupi.yudada.model.entity.Question;
import com.yupi.yudada.model.entity.ScoringResult;
import com.yupi.yudada.model.entity.UserAnswer;
import com.yupi.yudada.model.vo.QuestionVO;
import com.yupi.yudada.service.QuestionService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ScoringStrategyConfig(appType = 1,scoringStrategy = 1)
public class AiTestScoringStrategy implements ScoringStrategy {
    @Resource
    private QuestionService questionService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedissonClient redissonClient;

    //分布式锁的key
    private static final String AI_ANSWER_LOCK = "AI_ANSWER_LOCK";

    // region AI 生成题目功能

    private static final String AI_TEST_SYSTEM_MESSAGE = "你是一位严谨的判题专家，我会给你如下信息\n" +
            "应用名称，\n" +
            "【【【应用描述】】】\n" +
            "````````````````````````````````````\n" +
            "题目和用户回答的列表:格式为[{\"title\": \"题目\",\"answer\": \"用户回答\"}]\n" +
            "````````````````````````````````````\n" +
            "请你根据上述信息，按照以下步骤来对用户进行评价:\n" +
            "1.要求:需要给出一个明确的评价结果，包括评价名称(尽量简短)和评价描述(尽量详细，大于 200 字)\n" +
            "2.严格按照下面的 json 格式输出评价名称和评价描述\n" +
            "````````````````````````````````````\n" +
            "{\"resultName\": \"评价名称\",\"resultDesc\": \"评价描述\"}\n" +
            "````````````````````````````````````\n" +
            "3.返回格式必须为 JSON 对象";

    private final Cache<String,String> answerCacheMap =
            Caffeine.newBuilder().initialCapacity(1024)
                    //缓存5分钟移除
                    .expireAfterAccess(5L, TimeUnit.MINUTES)
                    .build();

    private String buildCacheKey(Long appId, String choicesStr){
        return DigestUtil.md5Hex(appId + ":" + choicesStr);
    }

    private String getAiTestScoringUserMessage(App app, List<QuestionContentDTO> questionContentDTOList, List<String> choices) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        List<QuestionAnswerDTO> questionAnswerDTOList = new ArrayList<>();
        for (int i = 0; i < questionContentDTOList.size(); i++) {
            QuestionAnswerDTO questionAnswerDTO = new QuestionAnswerDTO();
            questionAnswerDTO.setTitle(questionContentDTOList.get(i).getTitle());
            questionAnswerDTO.setUserAnswer(choices.get(i));
            questionAnswerDTOList.add(questionAnswerDTO);
        }
        userMessage.append(JSONUtil.toJsonStr(questionAnswerDTOList));
        return userMessage.toString();
    }
    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        //评分前检查是否已经在缓存中存在
        Long appId = app.getId();
        String jsonStr = JSONUtil.toJsonStr(choices);
        String cacheKey = buildCacheKey(appId, jsonStr);
        String answerJson = answerCacheMap.getIfPresent(cacheKey);
        if(StrUtil.isNotBlank(answerJson)){
            UserAnswer userAnswer = JSONUtil.toBean(answerJson, UserAnswer.class);
            //构造返回值，填充答案对象的属性
            userAnswer.setAppId(app.getId());
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(jsonStr);
            return userAnswer;
        }

        //定义锁
        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK + cacheKey);
        try{
            //竞争锁
            boolean res = lock.tryLock(3, 15, TimeUnit.SECONDS);
            //没抢到锁就强行返回
            if(!res){
                return null;
            }
            //根据id查询题目和题目结果信息（一个用户做了多组题，有多组答案）
            Question question = questionService.getOne(
                    //lambdaQuery中是你要查询的实体类，也就是数据库对应的表
                    Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, app.getId())
            );

            //获取题目列表
            QuestionVO questionVO = QuestionVO.objToVo(question);
            List<QuestionContentDTO> questionContent = questionVO.getQuestionContent();

            //调用ai获取结果
            String userMessage = getAiTestScoringUserMessage(app,questionContent,choices);
            String result = aiManager.doSyncStableRequest(AI_TEST_SYSTEM_MESSAGE, userMessage);

            //截取结果列表
            int start = result.indexOf("{");
            int end = result.lastIndexOf("}");
            String json = result.substring(start,end+1);

            //缓存结果
            answerCacheMap.put(cacheKey,json);

            //构造返回值，填充答案对象的属性
            UserAnswer userAnswer = JSONUtil.toBean(json,UserAnswer.class);
            userAnswer.setAppId(app.getId());
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(JSONUtil.toJsonStr(choices));
            return userAnswer;
        }finally {
            if(lock!=null && lock.isLocked()){
                //验证是否是本人
                if(lock.isHeldByCurrentThread()){
                    lock.unlock();
                }
            }
        }

    }
}
