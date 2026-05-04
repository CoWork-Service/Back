package com.cowork.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByStudentId(String studentId); //학번으로 찾기
    boolean existsByEmail(String email);

    List<User> findByJoinStatus(JoinStatus joinStatus);
    List<User> findByOrganizationIdAndJoinStatus(Long organizationId, JoinStatus joinStatus);
    @Query("SELECT u FROM User u JOIN FETCH u.organization WHERE u.joinStatus = :joinStatus")
    List<User> findByJoinStatusWithOrganization(@Param("joinStatus") JoinStatus joinStatus);
}
