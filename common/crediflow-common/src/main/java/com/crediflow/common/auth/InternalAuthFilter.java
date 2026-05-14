package com.crediflow.common.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * InternalAuthFilter - 内网认证过滤器
 * 该过滤器用于拦截和处理内网专用接口的认证请求
 */
public class InternalAuthFilter implements Filter {

    private final JwtUtils jwtUtils;

    public InternalAuthFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    /**
     * 执行过滤操作
     * @param request 请求对象
     * @param response 响应对象
     * @param chain 过滤器链
     * @throws IOException IO异常
     * @throws ServletException Servlet异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // 将请求对象转换为HttpServletRequest
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // 获取请求的URI路径
        String path = httpRequest.getRequestURI();

        // 仅拦截 /internal/ 开头的内网专用接口
        if (path.startsWith("/internal/")) {
            // 从请求头中获取内部认证令牌
            String token = httpRequest.getHeader(InternalAuthInterceptor.INTERNAL_AUTH_HEADER);
            // 检查令牌是否存在且有效
            if (!StringUtils.hasText(token) || !jwtUtils.validateInternalToken(token)) {
                // 如果令牌无效，设置响应状态为未授权
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                // 返回未授权信息
                httpResponse.getWriter().write("Internal unauthorized");
                return;
            }
        }
        
        // 继续执行过滤器链
        chain.doFilter(request, response);
    }
}
