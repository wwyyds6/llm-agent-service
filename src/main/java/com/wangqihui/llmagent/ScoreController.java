package com.wangqihui.llmagent;

import com.wangqihui.llmagent.common.Result;
import com.wangqihui.llmagent.entity.StudentScore;
import com.wangqihui.llmagent.mapper.StudentScoreMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/score")
public class ScoreController {

    private final StudentScoreMapper studentScoreMapper;

    public ScoreController(StudentScoreMapper studentScoreMapper) {
        this.studentScoreMapper = studentScoreMapper;
    }

    /** 按学号查全部成绩 */
    @GetMapping("/{studentNo}")
    public Result<List<StudentScore>> listByStudentNo(@PathVariable("studentNo") String studentNo) {
        return Result.ok(studentScoreMapper.findByStudentNo(studentNo));
    }

    /** 按学号查平均分 */
    @GetMapping("/{studentNo}/average")
    public Result<BigDecimal> average(@PathVariable("studentNo") String studentNo) {
        return Result.ok(studentScoreMapper.avgScoreByStudentNo(studentNo));
    }
}