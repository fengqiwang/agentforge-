package com.agentforge.framework.ratelimit;

  import com.agentforge.framework.ratelimit.RateLimiter;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import jakarta.servlet.*;
  import jakarta.servlet.http.HttpServletRequest;
  import jakarta.servlet.http.HttpServletResponse;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.io.IOException;
  import java.util.Map;

  /**
   * API 限流过滤器
   * 作用：在请求到达 Controller 前检查限流，超限返回 429
   *
   * 限流维度：
   * - 已登录用户：用 userId
   * - 未登录用户：用 IP 地址
   */
  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class RateLimitFilter implements Filter {

      private final RateLimiter rateLimiter;
      private final ObjectMapper objectMapper;

      @Override
      public void doFilter(ServletRequest request, ServletResponse response,
                            FilterChain chain) throws IOException, ServletException {
          HttpServletRequest httpRequest = (HttpServletRequest) request;

          // 只拦截 API 请求
          String uri = httpRequest.getRequestURI();
          if (!uri.startsWith("/api/")) {
              chain.doFilter(request, response);
              return;
          }

          // 获取限流维度：优先用 userId，否则用 IP
          String identity = getClientId(httpRequest);

          if (!rateLimiter.allowRequest(identity)) {
              log.warn("请求被限流：identity={}, uri={}", identity, uri);
              sendTooManyRequests(response);
              return;
          }

          chain.doFilter(request, response);
      }

      private String getClientId(HttpServletRequest request) {
          // 从请求属性取用户ID（如果 JWT Filter 已设置）
          Object userId = request.getAttribute("userId");
          if (userId != null) {
              return "user:" + userId;
          }

          // 回退到 IP
          String ip = request.getHeader("X-Forwarded-For");
          if (ip == null || ip.isBlank()) {
              ip = request.getHeader("X-Real-IP");
          }
          if (ip == null || ip.isBlank()) {
              ip = request.getRemoteAddr();
          }
          return "ip:" + ip;
      }

      private void sendTooManyRequests(ServletResponse response) throws IOException {
          HttpServletResponse httpResponse = (HttpServletResponse) response;
          httpResponse.setStatus(429);
          httpResponse.setContentType("application/json;charset=UTF-8");
          httpResponse.getWriter().write(
                  objectMapper.writeValueAsString(
                          Map.of("error", "请求过于频繁，请稍后再试")));
      }
  }