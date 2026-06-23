package com.agentforge.web.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公共服务配置端点——前端页面从此拉取服务器地址，
 * 避免硬编码 IP/端口在 Vue 源码中。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${agentforge.monitoring.prometheus-url}")
    private String prometheusUrl;

    @Value("${agentforge.monitoring.grafana-url}")
    private String grafanaUrl;

    @Value("${agentforge.chromadb.url}")
    private String chromadbUrl;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private String redisPort;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @GetMapping
    public Map<String, Object> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("prometheusUrl", prometheusUrl);
        m.put("grafanaUrl", grafanaUrl);
        m.put("chromadbUrl", chromadbUrl);
        m.put("redisHost", redisHost);
        m.put("redisPort", redisPort);
        m.put("mysqlJdbcUrl", datasourceUrl);
        return m;
    }
}
