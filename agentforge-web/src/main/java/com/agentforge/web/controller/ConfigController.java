package com.agentforge.web.controller;

import com.agentforge.web.config.FrontendConfigProperties;
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

    private final FrontendConfigProperties frontendConfig;

    public ConfigController(FrontendConfigProperties frontendConfig) {
        this.frontendConfig = frontendConfig;
    }

    @GetMapping
    public Map<String, Object> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("prometheusUrl", frontendConfig.getMonitoring().getPrometheusUrl());
        m.put("grafanaUrl", frontendConfig.getMonitoring().getGrafanaUrl());
        m.put("chromadbUrl", frontendConfig.getChromadb().getUrl());
        return m;
    }
}
