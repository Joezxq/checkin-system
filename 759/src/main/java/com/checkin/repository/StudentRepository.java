package com.checkin.repository;

import com.checkin.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByStudentNo(String studentNo);

    boolean existsByStudentNo(String studentNo);

    @Query("SELECT s FROM Student s WHERE (s.name LIKE %:keyword% OR s.studentNo LIKE %:keyword%) AND (:className IS NULL OR s.className = :className)")
    Page<Student> searchByKeyword(@Param("keyword") String keyword, @Param("className") String className, Pageable pageable);

    @Query("SELECT DISTINCT s.className FROM Student s WHERE s.className IS NOT NULL ORDER BY s.className")
    List<String> findDistinctClassNames();

    List<Student> findByIdIn(List<Long> ids);
}
