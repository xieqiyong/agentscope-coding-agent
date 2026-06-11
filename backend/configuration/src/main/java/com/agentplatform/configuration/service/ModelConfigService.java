package com.agentplatform.configuration.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.ModelConfigEntity;
import com.agentplatform.persistence.repository.ModelConfigRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 模型配置应用服务。
 */
@Service
public class ModelConfigService {

    @Resource
    private ModelConfigRepository modelConfigRepository;

    /**
     * 查询所有模型配置（不返回密钥原文）。
     */
    public List<ModelConfigEntity> listAll() {
        return modelConfigRepository.findAll();
    }

    /**
     * 获取单个配置详情（不返回密钥原文）。
     */
    public ModelConfigEntity getById(Long id) {
        return modelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "模型配置不存在"));
    }

    /**
     * 获取默认模型配置。
     */
    public ModelConfigEntity getDefault() {
        List<ModelConfigEntity> defaults = modelConfigRepository.findByDefaultConfigTrue();
        if (defaults.isEmpty()) {
            throw new BusinessException(404, "未设置默认模型配置，请先在设置中添加");
        }
        return defaults.get(0);
    }

    /**
     * 创建模型配置。
     */
    @Transactional
    public ModelConfigEntity create(String name, String provider, String baseUrl,
                                    String modelName, String apiKey) {
        validateParams(name, provider, baseUrl, modelName);

        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setName(name);
        entity.setProvider(provider);
        entity.setBaseUrl(baseUrl);
        entity.setModelName(modelName);
        setApiKey(entity, apiKey);
        entity.setDefaultConfig(false);
        return modelConfigRepository.save(entity);
    }

    /**
     * 更新模型配置。
     */
    @Transactional
    public ModelConfigEntity update(Long id, String name, String provider, String baseUrl,
                                    String modelName, String apiKey) {
        ModelConfigEntity entity = getById(id);
        if (StringUtils.hasText(name)) entity.setName(name);
        if (StringUtils.hasText(provider)) entity.setProvider(provider);
        if (StringUtils.hasText(baseUrl)) entity.setBaseUrl(baseUrl);
        if (StringUtils.hasText(modelName)) entity.setModelName(modelName);
        if (StringUtils.hasText(apiKey)) setApiKey(entity, apiKey);
        return modelConfigRepository.save(entity);
    }

    /**
     * 删除模型配置。
     */
    @Transactional
    public void delete(Long id) {
        modelConfigRepository.deleteById(id);
    }

    /**
     * 设为默认配置。
     */
    @Transactional
    public void setDefault(Long id) {
        // 先把所有默认取消
        List<ModelConfigEntity> currentDefaults = modelConfigRepository.findByDefaultConfigTrue();
        for (ModelConfigEntity e : currentDefaults) {
            e.setDefaultConfig(false);
            modelConfigRepository.save(e);
        }
        // 再设置新的默认
        ModelConfigEntity entity = getById(id);
        entity.setDefaultConfig(true);
        modelConfigRepository.save(entity);
    }

    /**
     * 获取可解密的 API Key（给 agent-runtime 模块用）。
     * 当前 MVP 先直接存原文，后续再加密。
     */
    public String getDecryptedApiKey(Long configId) {
        ModelConfigEntity entity = getById(configId);
        return entity.getApiKeyCipher();
    }

    private void setApiKey(ModelConfigEntity entity, String apiKey) {
        if (!StringUtils.hasText(apiKey)) return;
        // MVP 阶段直接存原文，后续替换为加密存储
        entity.setApiKeyCipher(apiKey);
        entity.setApiKeyMask(maskApiKey(apiKey));
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) return "****";
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private void validateParams(String name, String provider, String baseUrl, String modelName) {
        if (!StringUtils.hasText(name)) throw new BusinessException(400, "配置名称不能为空");
        if (!StringUtils.hasText(provider)) throw new BusinessException(400, "供应商不能为空");
        if (!StringUtils.hasText(baseUrl)) throw new BusinessException(400, "模型网关地址不能为空");
        if (!StringUtils.hasText(modelName)) throw new BusinessException(400, "模型名称不能为空");
    }
}
