package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Skill 定义实体：一段可复用的指令与操作说明。
 * 可以绑定到 Agent，也可以在聊天框通过 @ 动态挂载到当次运行。
 */
@Getter
@Setter
@Entity
@Table(name = "skills")
public class SkillEntity extends BaseEntity {

    /**
     * Skill 名称，全局唯一，用于 @ 引用
     */
    @Column(name = "name", nullable = false, unique = true, length = 128)
    private String name;

    /**
     * 一句话描述这个 Skill 的用途
     */
    @Column(name = "description", nullable = true, length = 512)
    private String description;

    /**
     * Skill 正文：挂载后会注入系统提示词的详细指令内容
     */
    @Lob
    @Column(name = "content", nullable = true, columnDefinition = "LONGTEXT")
    private String content;

    /**
     * 状态：ENABLED/DISABLED
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /**
     * 来源：LOCAL 页面手工创建 / IMPORTED 从 Agent Skills 标准 zip 导入
     */
    @Column(name = "source", nullable = false, length = 32)
    private String source;

    /**
     * 导入技能解压后的存储目录；LOCAL 技能为空。
     */
    @Column(name = "bundle_path", nullable = true, length = 1024)
    private String bundlePath;
}
