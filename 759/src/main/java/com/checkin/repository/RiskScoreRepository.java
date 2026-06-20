package com.checkin.repository;

import com.checkin.entity.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {
    Optional<RiskScore> findByCourseIdAndStudentId(Long courseId, Long studentId);
    List<RiskScore> findByCourseId(Long courseId);
    List<RiskScore> findByCourseIdAndRiskLevel(Long courseId, String riskLevel);
    void deleteByCourseIdAndStudentId(Long courseId, Long studentId);
    long countByCourseIdAndRiskLevel(Long courseId, String riskLevel);
}
