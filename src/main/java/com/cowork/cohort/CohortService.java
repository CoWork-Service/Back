package com.cowork.cohort;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.organization.Organization;
import com.cowork.organization.OrganizationRepository;
import com.cowork.user.JoinStatus;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CohortService {
    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 16;

    private final CohortRepository cohortRepository;
    private final CohortMemberRepository cohortMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public List<Cohort> getCohorts(Long organizationId) {
        return cohortRepository.findByOrganizationIdOrderByYearDesc(organizationId);
    }

    public Cohort getCohort(Long id) {
        return cohortRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COHORT_NOT_FOUND));
    }

    @Transactional
    public Cohort createCohort(Long organizationId, String label, Integer year) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
        Cohort cohort = Cohort.builder()
                .organization(org)
                .label(label)
                .year(year != null ? year : LocalDateTime.now().getYear())
                .build();
        return cohortRepository.save(cohort);
    }

    @Transactional
    public Cohort updateCohort(Long id, String label, Integer year) {
        Cohort cohort = cohortRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COHORT_NOT_FOUND));
        cohort.update(label, year);
        return cohort;
    }

    public List<CohortMember> getMembers(Long cohortId) {
        return cohortMemberRepository.findByCohortId(cohortId);
    }

    @Transactional
    public CohortMember updateMember(Long memberId, MemberRole role, Department department) {
        CohortMember member = cohortMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        member.update(role, department);
        return member;
    }

    @Transactional
    public void deleteMember(Long memberId) {
        cohortMemberRepository.deleteById(memberId);
    }

    public List<User> getPendingUsers(Long organizationId) {
        return userRepository.findByOrganizationIdAndJoinStatus(organizationId, JoinStatus.PENDING);
    }

    @Transactional
    public void approveUser(Long userId, Long cohortId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.activate();

        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COHORT_NOT_FOUND));

        if (!cohortMemberRepository.existsByCohortIdAndUserId(cohortId, userId)) {
            CohortMember member = CohortMember.builder()
                    .cohort(cohort)
                    .user(user)
                    .role(MemberRole.EDITOR)
                    .build();
            cohortMemberRepository.save(member);
        }
    }

    @Transactional
    public void rejectUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        userRepository.delete(user);
    }

    public String regenerateInviteCode(Long organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND));
        String newCode = generateCode();
        org.regenerateInviteCode(newCode);
        organizationRepository.save(org);
        return newCode;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        java.util.Random random = new java.security.SecureRandom();
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(random.nextInt(INVITE_CODE_CHARS.length())));
        }
        String code = sb.toString();
        if (organizationRepository.findByInviteCode(code).isPresent()) {
            return generateCode();
        }
        return code;
    }
}
