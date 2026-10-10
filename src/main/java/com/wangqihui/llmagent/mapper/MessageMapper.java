package com.wangqihui.llmagent.mapper;

import com.wangqihui.llmagent.entity.Message;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MessageMapper {

    @Insert("INSERT INTO message(conversation_id, role, content) VALUES(#{conversationId}, #{role}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Message message);

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY id ASC")
    List<Message> findByConversationId(@Param("conversationId") Long conversationId);
}