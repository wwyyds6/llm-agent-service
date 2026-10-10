package com.wangqihui.llmagent;

import com.wangqihui.llmagent.entity.Conversation;
import com.wangqihui.llmagent.mapper.ConversationMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DbTestController {

    private final ConversationMapper conversationMapper;

    public DbTestController(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @GetMapping("/db/test")
    public String test() {
        // 1. 插入
        Conversation c = new Conversation();
        c.setTitle("测试会话");
        conversationMapper.insert(c);

        // 2. 查询
        List<Conversation> list = conversationMapper.findAll();

        return "插入成功，回填的 id=" + c.getId()
                + "，当前共 " + list.size() + " 条会话"
                + "，第一条标题=" + (list.isEmpty() ? "无" : list.get(0).getTitle());
    }
}