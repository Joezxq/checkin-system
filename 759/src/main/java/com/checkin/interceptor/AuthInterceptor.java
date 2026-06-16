package com.checkin.interceptor;

import com.checkin.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 认证拦截器
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Value("${checkin.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // OPTIONS 请求直接放行
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new BusinessException(401, "未登录，请先登录");
        }

        // 应用session超时配置
        session.setMaxInactiveInterval(sessionTimeoutMinutes * 60);

        Object userType = session.getAttribute("userType");
        Object userId = session.getAttribute("userId");

        if (userType == null || userId == null) {
            throw new BusinessException(401, "未登录，请先登录");
        }

        // 验证角色权限
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/student/") && !"STUDENT".equals(userType)) {
            throw new BusinessException(403, "无权限访问");
        }
        if (uri.startsWith("/api/teacher/") && !"TEACHER".equals(userType)) {
            throw new BusinessException(403, "无权限访问");
        }

        return true;
    }
}
