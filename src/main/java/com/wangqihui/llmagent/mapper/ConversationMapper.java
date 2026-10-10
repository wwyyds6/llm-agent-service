package com.wangqihui.llmagent.mapper;

import com.wangqihui.llmagent.entity.Conversation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConversationMapper {

    @Insert("INSERT INTO conversation(title) VALUES(#{title})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Conversation conversation);

    @Select("SELECT * FROM conversation ORDER BY updated_at DESC")
    List<Conversation> findAll();

    @Select("SELECT * FROM conversation WHERE id = #{id}")
    Conversation findById(@Param("id") Long id);

    @Delete("DELETE FROM conversation WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Update("UPDATE conversation SET title = #{title} WHERE id = #{id}")
    int updateTitle(@Param("id") Long id, @Param("title") String title);
}