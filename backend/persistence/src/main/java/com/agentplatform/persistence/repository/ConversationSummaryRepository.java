package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.ConversationSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 会话摘要实体：用于滑动窗口之外的短期压缩记忆。
 */
public interface ConversationSummaryRepository extends JpaRepository<ConversationSummaryEntity, Long> {


    List<ConversationSummaryEntity> findByConversationIdAndStatusOrderByCreatedAtDesc(Long conversationId, String status);
}
