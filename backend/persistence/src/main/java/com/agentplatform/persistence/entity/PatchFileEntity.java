package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Patch 文件实体：记录一个 patch 涉及的文件明细。
 */
@Getter
@Setter
@Entity
@Table(name = "patch_files")
public class PatchFileEntity extends BaseEntity {

    /**
     * patch ID
     */
    @Column(name = "patch_id", nullable = false)
    private Long patchId;
    /**
     * 相对工作区的文件路径
     */
    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;
    /**
     * 变更类型
     */
    @Column(name = "change_type", nullable = false, length = 32)
    private String changeType;
    /**
     * 旧内容 hash
     */
    @Column(name = "old_content_hash", nullable = true, length = 128)
    private String oldContentHash;
    /**
     * 新内容 hash
     */
    @Column(name = "new_content_hash", nullable = true, length = 128)
    private String newContentHash;
}
