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

public class InternalAuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // 仅拦截 /internal/ 开头的内网专用接口
        if (path.startsWith("/internal/")) {
            String token = httpRequest.getHeader(InternalAuthInterceptor.INTERNAL_AUTH_HEADER);
            if (!StringUtils.hasText(token) || !JwtUtils.validateInternalToken(token)) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.getWriter().write("Internal unauthorized");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }
}
