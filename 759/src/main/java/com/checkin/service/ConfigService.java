package com.checkin.service;

import com.checkin.entity.SystemConfig;
import com.checkin.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务
 */
@Service
public class ConfigService {

    @Autowired
    private SystemConfigRepository configRepository;

    @Autowired
    private OperationLogService logService;

    // 内存缓存
    private final Map<String, String> cache = new HashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public void refreshCache() {
        List<SystemConfig> configs = configRepository.findAll();
        for (SystemConfig c : configs) {
            cache.put(c.getConfigKey(), c.getConfigValue());
        }
    }

    public String getValue(String key, String defaultValue) {
        return cache.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(cache.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(cache.getOrDefault(key, String.valueOf(defaultValue)));
    }

    public List<SystemConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    @Transactional
    public SystemConfig updateConfig(String key, String value, Long adminId) {
        SystemConfig config = configRepository.findByConfigKey(key)
            .orElseThrow(() -> new com.checkin.exception.BusinessException("配置项不存在: " + key));
        config.setConfigValue(value);
        config.setUpdatedBy(adminId);
        config.setUpdatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        SystemConfig saved = configRepository.save(config);
        cache.put(key, value);
        logService.log("ADMIN", adminId, null, "CONFIG_CHANGE", "SystemConfig", config.getId(),
            key + "=" + value, null);
        return saved;
    }
}
