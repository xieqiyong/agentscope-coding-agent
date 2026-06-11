package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 工作区实体：定义 coding agent 允许操作的根目录。
 */
@Getter
@Setter
@Entity
@Table(name = "workspaces")
public class WorkspaceEntity extends BaseEntity {

    /**
     * 工作区名称
     */
    @Column(name = "name", nullable = false, length = 128)
    private String name;
    /**
     * 工作区根路径，sandbox 必须以它作为安全边界
     */
    @Column(name = "root_path", nullable = false, length = 1024)
    private String rootPath;
    /**
     * 工作区所有者
     */
    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;
    /**
     * 工作区说明
     */
    @Column(name = "description", nullable = true, length = 512)
    private String description;
    /**
     * 状态：ACTIVE/DISABLED/ARCHIVED
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
