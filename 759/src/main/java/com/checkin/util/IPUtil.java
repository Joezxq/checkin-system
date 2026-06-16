package com.checkin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * IP 工具类
 */
@Component
public class IPUtil {

    private static final Logger logger = LoggerFactory.getLogger(IPUtil.class);

    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    @Value("${checkin.network.enable-forwarded-header:false}")
    private boolean enableForwardedHeader;

    @Value("${checkin.network.trusted-proxies:}")
    private List<String> trustedProxies;

    @Value("${checkin.network.server-ip-override:}")
    private String serverIpOverride;

    @Value("${checkin.network.subnet-mask:255.255.255.0}")
    private String subnetMask;

    private String cachedServerIp;

    /**
     * 获取客户端真实 IP
     */
    public String getClientIP(HttpServletRequest request) {
        String ip = null;

        // 如果启用了 X-Forwarded-For 解析
        if (enableForwardedHeader) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isEmpty()) {
                // 获取第一个 IP
                String[] ips = forwardedFor.split(",");
                for (String ipAddr : ips) {
                    ipAddr = ipAddr.trim();
                    if (isValidIPv4(ipAddr) && !"unknown".equalsIgnoreCase(ipAddr)) {
                        ip = ipAddr;
                        break;
                    }
                }

                // 验证请求是否来自可信代理
                String remoteAddr = request.getRemoteAddr();
                if (ip != null && !trustedProxies.isEmpty() && !trustedProxies.contains(remoteAddr)) {
                    logger.warn("X-Forwarded-For 头来自不可信代理: {}", remoteAddr);
                    ip = null;
                }
            }
        }

        // 默认使用 remoteAddr
        if (ip == null) {
            ip = request.getRemoteAddr();
        }

        // 将 IPv6 环回地址转换为 IPv4 环回地址（本地开发时浏览器可能使用 IPv6）
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return ip;
    }

    /**
     * 获取服务器 IP
     */
    public String getServerIP() {
        // 如果手动配置了服务器 IP，直接返回
        if (serverIpOverride != null && !serverIpOverride.isEmpty()) {
            return serverIpOverride;
        }

        // 使用缓存
        if (cachedServerIp != null) {
            return cachedServerIp;
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                // 跳过回环接口和虚拟接口
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();

                    // 只取 IPv4 地址，且非 127.0.0.1
                    if (isValidIPv4(ip) && !ip.startsWith("127.")) {
                        cachedServerIp = ip;
                        logger.info("自动识别服务器 IP: {}", ip);
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("获取服务器 IP 失败", e);
        }

        // 兜底返回 localhost
        logger.warn("无法获取服务器 IP，使用默认值 127.0.0.1");
        return "127.0.0.1";
    }

    /**
     * 判断是否在同一网段
     */
    public boolean isInSameSubnet(String clientIp, String serverIp) {
        try {
            long clientIpLong = ipToLong(clientIp);
            long serverIpLong = ipToLong(serverIp);
            long maskLong = ipToLong(subnetMask);

            return (clientIpLong & maskLong) == (serverIpLong & maskLong);
        } catch (Exception e) {
            logger.error("网段判断失败: clientIp={}, serverIp={}", clientIp, serverIp, e);
            return false;
        }
    }

    /**
     * 验证 IPv4 格式
     */
    public boolean isValidIPv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * IP 转 Long
     */
    private long ipToLong(String ip) {
        if (!isValidIPv4(ip)) {
            throw new IllegalArgumentException("非法 IPv4 地址: " + ip);
        }
        String[] parts = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = result << 8 | Integer.parseInt(parts[i]);
        }
        return result;
    }
}
