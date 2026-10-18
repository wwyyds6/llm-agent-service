package com.wangqihui.llmagent.mapper;

import com.wangqihui.llmagent.entity.StudentScore;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface StudentScoreMapper {

    /** 按学号查询某学生的全部成绩 */
    @Select("SELECT * FROM student_score WHERE student_no = #{studentNo} ORDER BY id")
    List<StudentScore> findByStudentNo(@Param("studentNo") String studentNo);

    /** 按学号查平均分（学号不存在时返回 null） */
    @Select("SELECT ROUND(AVG(score), 2) FROM student_score WHERE student_no = #{studentNo}")
    BigDecimal avgScoreByStudentNo(@Param("studentNo") String studentNo);

    /** 按学号 + 课程名查询 */
    @Select("SELECT * FROM student_score WHERE student_no = #{studentNo} AND course = #{course}")
    List<StudentScore> findByStudentNoAndCourse(@Param("studentNo") String studentNo,
                                                @Param("course") String course);

    /** 按姓名或学号查询全部成绩（兼容用户用姓名或学号提问） */
    @Select("SELECT * FROM student_score WHERE student_no = #{key} OR student_name = #{key} ORDER BY id")
    List<StudentScore> findByAny(@Param("key") String key);
}