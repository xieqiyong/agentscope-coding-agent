package com.agentplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Jackson 全局配置。
 * 统一时区为东八区，统一日期格式，确保所有模块序列化行为一致。
 * 使用 com.fasterxml（Jackson 2.x），供 AgentScope 等依赖 Jackson 2 的模块注入。
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 时区：东八区
        mapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        // 日期格式
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        // 不把日期序列化为时间戳数字
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
