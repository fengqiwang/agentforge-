package com.agentforge.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 前端公共配置项，统一绑定 agentforge 前缀下的对外暴露属性。
 *
 * <p>替代 ConfigController 中零散的 @Value 注入。
 * 注意：Redis/DataSource 连接信息属于敏感信息，不在此处暴露。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agentforge")
public class FrontendConfigProperties {

    /** Prometheus 地址 */
    private Monitoring monitoring = new Monitoring();

    /** ChromaDB 地址 */
    private Chromadb chromadb = new Chromadb();

    @Getter
    @Setter
    public static class Monitoring {
        private String prometheusUrl;
        private String grafanaUrl;
    }

    @Getter
    @Setter
    public static class Chromadb {
        private String url;
    }
}
