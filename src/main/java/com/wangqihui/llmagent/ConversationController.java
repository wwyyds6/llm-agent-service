package com.wangqihui.llmagent;

import com.wangqihui.llmagent.common.BusinessException;
import com.wangqihui.llmagent.common.Result;
import com.wangqihui.llmagent.entity.Conversation;
import com.wangqihui.llmagent.entity.Message;
import com.wangqihui.llmagent.mapper.ConversationMapper;
import com.wangqihui.llmagent.mapper.MessageMapper;
import org.springframework.web.bind.annotation.*;
import com.wangqihui.llmagent.dto.RenameRequest;
import jakarta.validation.Valid;

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
    public Result<Conversation> create() {
        Conversation c = new Conversation();
        c.setTitle("新会话");
        conversationMapper.insert(c);
        return Result.ok(c);
    }

    /** 全部会话（按更新时间倒序） */
    @GetMapping
    public Result<List<Conversation>> list() {
        return Result.ok(conversationMapper.findAll());
    }

    /** 某个会话的全部消息 */
    @GetMapping("/{id}/messages")
    public Result<List<Message>> messages(@PathVariable("id") Long id) {
        // 校验会话存在：不存在就抛业务异常，由全局异常处理器统一转成 Result
        Conversation conversation = conversationMapper.findById(id);
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在: " + id);
        }
        return Result.ok(messageMapper.findByConversationId(id));
    }

    /** 删除会话（连带删除其消息） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Conversation conversation = conversationMapper.findById(id);
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在: " + id);
        }
        messageMapper.deleteByConversationId(id);
        conversationMapper.deleteById(id);
        return Result.ok();
    }
    /** 重命名会话（演示参数校验） */
    @PutMapping("/{id}/title")
    public Result<Void> rename(@PathVariable("id") Long id,
                               @RequestBody @Valid RenameRequest request) {
        Conversation conversation = conversationMapper.findById(id);
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在: " + id);
        }
        conversationMapper.updateTitle(id, request.getTitle());
        return Result.ok();
    }
}