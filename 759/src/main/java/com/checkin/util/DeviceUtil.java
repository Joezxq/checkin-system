package com.checkin.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * 设备 ID 工具类
 */
@Component
public class DeviceUtil {

    @Value("${checkin.session.device-cookie-name:CHECKIN_DEVICE_ID}")
    private String deviceCookieName;

    /**
     * 获取设备 ID（从 Cookie）
     */
    public String getDeviceId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (deviceCookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 生成并设置设备 ID
     */
    public String generateAndSetDeviceId(HttpServletResponse response) {
        String deviceId = UUID.randomUUID().toString();
        Cookie cookie = new Cookie(deviceCookieName, deviceId);
        cookie.setPath("/");
        cookie.setMaxAge(365 * 24 * 60 * 60); // 1年
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return deviceId;
    }

    /**
     * 获取或生成设备 ID
     */
    public String getOrGenerateDeviceId(HttpServletRequest request, HttpServletResponse response) {
        String deviceId = getDeviceId(request);
        if (deviceId == null) {
            deviceId = generateAndSetDeviceId(response);
        }
        return deviceId;
    }
}
