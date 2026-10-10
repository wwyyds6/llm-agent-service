package com.wangqihui.llmagent;

import com.wangqihui.llmagent.entity.Conversation;
import com.wangqihui.llmagent.entity.Message;
import com.wangqihui.llmagent.mapper.ConversationMapper;
import com.wangqihui.llmagent.mapper.MessageMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/conversation")
public class ConversationController {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public ConversationController(ConversationMapper conversationMapper, MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    /** 新建会话 */
    @PostMapping
    public Conversation create() {
        Conversation c = new Conversation();
        c.setTitle("新会话");
        conversationMapper.insert(c);
        return c;
    }

    /** 全部会话（按更新时间倒序） */
    @GetMapping
    public List<Conversation> list() {
        return conversationMapper.findAll();
    }

    /** 某个会话的全部消息（前端刷新页面时恢复历史用） */
    @GetMapping("/{id}/messages")
    public List<Message> messages(@PathVariable("id") Long id) {
        return messageMapper.findByConversationId(id);
    }
}
