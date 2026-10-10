package com.wangqihui.llmagent.mapper;

import com.wangqihui.llmagent.entity.ToolCallLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ToolCallLogMapper {

    @Insert("INSERT INTO tool_call_log(message_id, tool_name, arguments, result, cost_ms, success) "
            + "VALUES(#{messageId}, #{toolName}, #{arguments}, #{result}, #{costMs}, #{success})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ToolCallLog log);

    @Select("SELECT * FROM tool_call_log ORDER BY id DESC LIMIT #{limit}")
    List<ToolCallLog> findRecent(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM tool_call_log WHERE tool_name = #{toolName}")
    int countByToolName(@Param("toolName") String toolName);
}