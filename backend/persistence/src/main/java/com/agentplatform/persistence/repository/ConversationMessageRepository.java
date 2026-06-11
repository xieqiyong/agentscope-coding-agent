package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.ConversationMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 会话消息实体：短期记忆的原始来源。
 */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, Long> {


    List<ConversationMessageEntity> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
