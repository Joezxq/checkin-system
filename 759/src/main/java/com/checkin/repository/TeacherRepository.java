package com.checkin.repository;

import com.checkin.entity.Teacher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByTeacherNo(String teacherNo);

    boolean existsByTeacherNo(String teacherNo);

    @Query("SELECT t FROM Teacher t WHERE " +
           "(:keyword IS NULL OR t.teacherNo LIKE %:keyword% OR t.name LIKE %:keyword%)")
    Page<Teacher> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
