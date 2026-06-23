package com.agentforge.safety.auth;

import com.agentforge.common.model.UserContext;
import com.agentforge.framework.ratelimit.RateLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Profile("dev")
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Phase 1: 固定admin用户，所有请求都通过
        UserContext user = UserContext.builder()
                .userId(1L)
                .username("admin")
                .role("ADMIN")
                .build();
        request.setAttribute("currentUser", user);
        filterChain.doFilter(request, response);
    }


    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);  // 在其他 Filter 之后执行
        return registration;
    }
}
