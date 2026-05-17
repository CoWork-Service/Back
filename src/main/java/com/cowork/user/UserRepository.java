package com.cowork.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByStudentId(String studentId);

    @EntityGraph(attributePaths = "organization")
    Optional<User> findWithOrganizationById(Long id);

    boolean existsByEmail(String email);

    boolean existsByStudentId(String studentId);

    List<User> findByOrganizationIdAndJoinStatus(Long organizationId, JoinStatus joinStatus);
}
