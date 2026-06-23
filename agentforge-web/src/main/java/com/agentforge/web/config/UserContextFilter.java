package com.agentforge.web.config;

import com.agentforge.common.model.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 用户上下文注入过滤器。
 *
 * <p>在每个请求中设置当前用户信息到 SecurityContext 和 request attribute，
 * 供后续 Controller / Service 通过 {@code request.getAttribute("currentUser")} 获取。
 *
 * <p>注意：当前为开发模式，硬编码 admin 用户。生产环境应替换为 JWT / Session 解析。
 */
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        UserContext user = UserContext.builder()
                .userId(1L)
                .username("admin")
                .role("ADMIN")
                .build();
        request.setAttribute("currentUser", user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
