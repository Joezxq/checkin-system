package com.checkin.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class IPUtilTest {

    private IPUtil ipUtil;

    @BeforeEach
    void setUp() {
        ipUtil = new IPUtil();
        ReflectionTestUtils.setField(ipUtil, "subnetMask", "255.255.255.0");
        ReflectionTestUtils.setField(ipUtil, "serverIpOverride", "");
        ReflectionTestUtils.setField(ipUtil, "enableForwardedHeader", false);
        ReflectionTestUtils.setField(ipUtil, "trustedProxies", java.util.Collections.emptyList());
    }

    @Nested
    @DisplayName("isValidIPv4 - IPv4 格式校验")
    class IsValidIPv4Test {

        @Test
        void 标准IPv4地址() {
            assertTrue(ipUtil.isValidIPv4("192.168.1.1"));
            assertTrue(ipUtil.isValidIPv4("10.0.0.1"));
            assertTrue(ipUtil.isValidIPv4("172.16.0.1"));
        }

        @Test
        void 边界值() {
            assertTrue(ipUtil.isValidIPv4("0.0.0.0"));
            assertTrue(ipUtil.isValidIPv4("255.255.255.255"));
            assertTrue(ipUtil.isValidIPv4("1.1.1.1"));
        }

        @Test
        void 超出范围() {
            assertFalse(ipUtil.isValidIPv4("256.1.1.1"));
            assertFalse(ipUtil.isValidIPv4("1.256.1.1"));
            assertFalse(ipUtil.isValidIPv4("1.1.256.1"));
            assertFalse(ipUtil.isValidIPv4("1.1.1.256"));
        }

        @Test
        void 格式错误() {
            assertFalse(ipUtil.isValidIPv4("192.168.1"));       // 缺少段
            assertFalse(ipUtil.isValidIPv4("192.168.1.1.1"));   // 多余段
            assertFalse(ipUtil.isValidIPv4("abc.def.ghi.jkl")); // 非数字
            assertFalse(ipUtil.isValidIPv4("192.168.1."));      // 尾部点号
            assertFalse(ipUtil.isValidIPv4(".192.168.1.1"));     // 头部点号
        }

        @Test
        void 空值和null() {
            assertFalse(ipUtil.isValidIPv4(null));
            assertFalse(ipUtil.isValidIPv4(""));
            assertFalse(ipUtil.isValidIPv4(" "));
        }
    }

    @Nested
    @DisplayName("isInSameSubnet - 子网判断")
    class IsInSameSubnetTest {

        @Test
        void 同一网段_24掩码() {
            ReflectionTestUtils.setField(ipUtil, "subnetMask", "255.255.255.0");
            assertTrue(ipUtil.isInSameSubnet("192.168.1.50", "192.168.1.100"));
            assertTrue(ipUtil.isInSameSubnet("192.168.1.1", "192.168.1.254"));
        }

        @Test
        void 不同网段_24掩码() {
            ReflectionTestUtils.setField(ipUtil, "subnetMask", "255.255.255.0");
            assertFalse(ipUtil.isInSameSubnet("192.168.1.50", "192.168.2.50"));
            assertFalse(ipUtil.isInSameSubnet("10.0.0.1", "192.168.1.1"));
            assertFalse(ipUtil.isInSameSubnet("169.150.249.166", "192.168.1.100"));
        }

        @Test
        void 同一IP() {
            assertTrue(ipUtil.isInSameSubnet("192.168.1.1", "192.168.1.1"));
            assertTrue(ipUtil.isInSameSubnet("10.0.0.1", "10.0.0.1"));
        }

        @Test
        void 不同掩码_16() {
            ReflectionTestUtils.setField(ipUtil, "subnetMask", "255.255.0.0");
            assertTrue(ipUtil.isInSameSubnet("192.168.1.50", "192.168.2.50"));
            assertFalse(ipUtil.isInSameSubnet("192.168.1.50", "192.169.1.50"));
        }

        @Test
        void 严格掩码_30() {
            ReflectionTestUtils.setField(ipUtil, "subnetMask", "255.255.255.252");
            // /30 网段只有4个IP: .164, .165, .166, .167
            assertTrue(ipUtil.isInSameSubnet("169.150.249.165", "169.150.249.166"));
            assertFalse(ipUtil.isInSameSubnet("169.150.249.166", "169.150.249.170"));
        }

        @Test
        void 非法IP返回false() {
            assertFalse(ipUtil.isInSameSubnet("invalid", "192.168.1.1"));
            assertFalse(ipUtil.isInSameSubnet("192.168.1.1", "invalid"));
        }
    }

    @Nested
    @DisplayName("getServerIP - 服务器 IP 获取")
    class GetServerIPTest {

        @Test
        void 手动配置优先() {
            ReflectionTestUtils.setField(ipUtil, "serverIpOverride", "192.168.1.100");
            assertEquals("192.168.1.100", ipUtil.getServerIP());
        }

        @Test
        void 空配置时自动识别() {
            ReflectionTestUtils.setField(ipUtil, "serverIpOverride", "");
            String ip = ipUtil.getServerIP();
            // 自动识别应返回一个有效的 IPv4 地址或兜底的 127.0.0.1
            assertNotNull(ip);
            assertTrue(ipUtil.isValidIPv4(ip), "自动识别的 IP 应为有效 IPv4: " + ip);
        }

        @Test
        void 缓存生效() {
            ReflectionTestUtils.setField(ipUtil, "serverIpOverride", "");
            String ip1 = ipUtil.getServerIP();
            String ip2 = ipUtil.getServerIP();
            assertEquals(ip1, ip2, "两次调用应返回相同的缓存 IP");
        }
    }
}
