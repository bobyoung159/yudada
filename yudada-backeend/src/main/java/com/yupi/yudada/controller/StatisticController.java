package com.yupi.yudada.controller;

import cn.hutool.core.io.FileUtil;
import com.yupi.yudada.common.BaseResponse;
import com.yupi.yudada.common.ErrorCode;
import com.yupi.yudada.common.ResultUtils;
import com.yupi.yudada.constant.FileConstant;
import com.yupi.yudada.exception.BusinessException;
import com.yupi.yudada.manager.CosManager;
import com.yupi.yudada.mapper.UserAnswerMapper;
import com.yupi.yudada.model.dto.file.UploadFileRequest;
import com.yupi.yudada.model.dto.statistic.AppAnswerCountDTO;
import com.yupi.yudada.model.dto.statistic.AppAnswerResultDTO;
import com.yupi.yudada.model.entity.User;
import com.yupi.yudada.model.enums.FileUploadBizEnum;
import com.yupi.yudada.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * APP统计分析接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class StatisticController {

    @Resource
    private UserAnswerMapper userAnswerMapper;
    @GetMapping("/answer_count")
    public BaseResponse<List<AppAnswerCountDTO>> getAppAnswerCount(){
        return ResultUtils.success(userAnswerMapper.doAppAnswerCount());
    }

    @PostMapping("/answer_result_count")
    public BaseResponse<List<AppAnswerResultDTO>> getAppAnswerResultCount(Long appId){
        return ResultUtils.success(userAnswerMapper.doAppAnswerResultCount(appId));
    }
}
