package com.checkin.service;

import com.checkin.entity.OperationLog;
import com.checkin.repository.OperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 操作日志服务
 */
@Service
public class OperationLogService {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogService.class);

    @Autowired
    private OperationLogRepository operationLogRepository;

    /**
     * 记录操作日志（独立事务，确保即使业务回滚也保留审计记录）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String userType, Long userId, String userName, String operation,
                    String targetType, Long targetId, String detail, String clientIp) {
        try {
            OperationLog log = new OperationLog();
            log.setUserType(userType);
            log.setUserId(userId);
            log.setUserName(userName);
            log.setOperation(operation);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setDetail(detail);
            log.setClientIp(clientIp);
            operationLogRepository.save(log);
        } catch (Exception e) {
            logger.error("记录操作日志失败: {}", e.getMessage());
        }
    }

    /**
     * 查询操作日志（分页）
     */
    public Page<OperationLog> getLogs(Pageable pageable) {
        return operationLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
