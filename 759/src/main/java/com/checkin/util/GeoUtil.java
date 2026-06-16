package com.checkin.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * 地理位置工具类
 */
public class GeoUtil {

    private static final Random RANDOM = new Random();

    // 固定坐标（杭州西湖附近）
    private static final double BASE_LAT = 30.274084;
    private static final double BASE_LNG = 120.155070;

    // 随机偏移范围（±0.001）
    private static final double OFFSET_RANGE = 0.001;

    /**
     * 生成模拟地理位置
     */
    public static BigDecimal[] generateMockLocation() {
        double lat = BASE_LAT + (RANDOM.nextDouble() * 2 - 1) * OFFSET_RANGE;
        double lng = BASE_LNG + (RANDOM.nextDouble() * 2 - 1) * OFFSET_RANGE;

        return new BigDecimal[]{
            BigDecimal.valueOf(lat).setScale(6, RoundingMode.HALF_UP),
            BigDecimal.valueOf(lng).setScale(6, RoundingMode.HALF_UP)
        };
    }
}
