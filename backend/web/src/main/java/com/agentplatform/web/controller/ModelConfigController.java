package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.configuration.service.ModelConfigService;
import com.agentplatform.persistence.entity.ModelConfigEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型配置管理接口。
 */
@RestController
@RequestMapping("/api/model-configs")
public class ModelConfigController {

    @Resource
    private ModelConfigService modelConfigService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 查询所有模型配置（不返回密钥原文）。
     */
    @GetMapping
    public ApiResponse<List<ModelConfigEntity>> list() {
        return ApiResponse.success(modelConfigService.listAll());
    }

    /**
     * 获取默认模型配置。
     */
    @GetMapping("/default")
    public ApiResponse<ModelConfigEntity> getDefault() {
        return ApiResponse.success(modelConfigService.getDefault());
    }
    /**
     * 测试模型网关连接。
     * 前端直连第三方模型网关很容易被 CORS 拦住，所以统一由后端按运行时环境代测。
     */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test(@RequestBody Map<String, Object> body) {
        Long configId = parseLong(body.get("id"));
        ModelConfigEntity config = configId == null ? null : modelConfigService.getById(configId);

        String baseUrl = firstText(textValue(body, "baseUrl"), config == null ? null : config.getBaseUrl());
        String modelName = firstText(textValue(body, "modelName"), config == null ? null : config.getModelName());
        String apiKey = firstText(textValue(body, "apiKey"), config == null ? null : config.getApiKeyCipher());

        if (!StringUtils.hasText(baseUrl)) {
            return ApiResponse.error(400, "模型网关地址不能为空");
        }
        if (!StringUtils.hasText(modelName)) {
            return ApiResponse.error(400, "模型名称不能为空");
        }

        String endpoint = normalizeChatCompletionsUrl(baseUrl);
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", "你好，请回复连接成功"
            )));
            requestBody.put("max_tokens", 32);
            requestBody.put("stream", false);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));
            if (StringUtils.hasText(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return ApiResponse.error(response.statusCode(), "模型网关返回错误：" + extractErrorMessage(response.body()));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("endpoint", endpoint);
            result.put("status", response.statusCode());
            result.put("reply", extractReply(response.body()));
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, "模型网关地址格式不正确：" + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(502, "模型网关连接失败：" + e.getMessage());
        }
    }

    /**
     * 创建模型配置。
     */
    @PostMapping
    public ApiResponse<ModelConfigEntity> create(@RequestBody Map<String, String> body) {
        ModelConfigEntity entity = modelConfigService.create(
                body.get("name"),
                body.get("provider"),
                body.get("baseUrl"),
                body.get("modelName"),
                body.get("apiKey")
        );
        return ApiResponse.success(entity);
    }

    /**
     * 更新模型配置。
     */
    @PutMapping("/{id}")
    public ApiResponse<ModelConfigEntity> update(@PathVariable Long id,
                                                  @RequestBody Map<String, String> body) {
        ModelConfigEntity entity = modelConfigService.update(
                id,
                body.get("name"),
                body.get("provider"),
                body.get("baseUrl"),
                body.get("modelName"),
                body.get("apiKey")
        );
        return ApiResponse.success(entity);
    }

    /**
     * 删除模型配置。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        modelConfigService.delete(id);
        return ApiResponse.success(null);
    }

    /**
     * 设为默认配置。
     */
    @PutMapping("/{id}/set-default")
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        modelConfigService.setDefault(id);
        return ApiResponse.success(null);
    }
    private String normalizeChatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private String extractReply(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode first = choices.get(0);
                JsonNode content = first.path("message").path("content");
                if (!content.isMissingNode() && !content.isNull()) {
                    return content.asText();
                }
                JsonNode text = first.path("text");
                if (!text.isMissingNode() && !text.isNull()) {
                    return text.asText();
                }
            }
        } catch (Exception ignored) {
            // 兼容非标准 OpenAI 响应，下面直接返回原始片段。
        }
        return abbreviate(responseBody, 300);
    }

    private String extractErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorMessage = root.path("error").path("message");
            if (!errorMessage.isMissingNode() && !errorMessage.isNull()) {
                return errorMessage.asText();
            }
            JsonNode message = root.path("message");
            if (!message.isMissingNode() && !message.isNull()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // 兼容纯文本错误响应。
        }
        return abbreviate(responseBody, 400);
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String textValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Long.parseLong(text);
    }
}
