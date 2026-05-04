네! 이번에 추가된 내용을 기존 형식에 맞게 써드릴게요:

```markdown
## 🔐 SSO 로그인 흐름

### 1. SSO 로그인 페이지 이동
브라우저에서 아래 URL 접속:
https://smartid.ssu.ac.kr/Symtra_sso/smln.asp?apiReturnUrl=http://localhost:8080/api/auth/sso/callback

### 2. SSO 로그인 성공
- 숭실대 SmartID로 로그인
- 서버가 `sToken`, `sIdno`(학번) 수신
- SAINT 서버에 sToken 재검증 후 학생 정보(이름, 학과) 추출

### 3. 서버 콜백 처리 및 분기
- **기존 회원 (ACTIVE)** → JWT 발급 후 메인 페이지로 리다이렉트
  http://localhost:3000/main?accessToken=...&refreshToken=...
- **가입 대기 중 (PENDING)** → 대기 페이지로 리다이렉트
  http://localhost:3000/pending
- **신규 유저** → tempToken 발급 후 온보딩 페이지로 리다이렉트
  http://localhost:3000/onboarding?tempToken=xxxx

### 4. 프로필 조회 (신규 유저)
온보딩 페이지에서 tempToken으로 학생 정보 조회:
GET /api/auth/sso/profile?tempToken=xxxx
응답 예시:
```json
{
  "studentId": "20221844",
  "name": "배성찬",
  "department": "AI소프트웨어학부",
  "email": null
}
```

### 5. 회원가입 (신규 유저)
추가 정보 입력 후 가입 요청:
POST /api/auth/sso/register
요청 Body:
```json
{
  "tempToken": "xxxx",
  "email": "abc@ssu.ac.kr",
  "councilMember": false,
  "cohortLabel": "1기",
  "department": "AI소프트웨어학부"
}
```
- 가입 후 `PENDING` 상태로 생성
- 관리자 승인 후 `ACTIVE`로 변경
- 다음 SSO 로그인부터 JWT 발급 (accessToken, refreshToken 발급)

### 6. 관리자 승인
PENDING 유저 목록 조회:
GET /api/admin/users/pending
- Headers: `Authorization: Bearer {accessToken}`

유저 승인:
PATCH /api/admin/users/{userId}/approve
- Headers: `Authorization: Bearer {accessToken}`

유저 거절:
PATCH /api/admin/users/{userId}/reject
- Headers: `Authorization: Bearer {accessToken}`

---

## 📝 변경된 파일 목록

### 새로 추가된 파일
| 파일 | 설명 |
|------|------|
| `auth/SsoService.java` | SSO 로그인 처리 및 SAINT 재검증 로직 |
| `auth/SsoTempToken.java` | 온보딩용 임시 토큰 Entity |
| `auth/SsoTempTokenRepository.java` | 임시 토큰 Repository |
| `auth/dto/SsoProfileResponse.java` | SSO 프로필 응답 DTO |
| `auth/dto/SsoRegisterRequest.java` | SSO 회원가입 요청 DTO |
| `user/AdminController.java` | 관리자 승인/거절 API |
| `user/AdminService.java` | 관리자 승인/거절 비즈니스 로직 |
| `db/migration/V2__add_student_id.sql` | users 테이블 student_id 컬럼 추가 |
| `db/migration/V3__add_sso_temp_tokens.sql` | sso_temp_tokens 테이블 생성 |
| `db/migration/V4__add_organization_department.sql` | organizations 테이블 department 컬럼 추가 |

### 기존 파일 수정
| 파일 | 수정 내용 |
|------|---------|
| `user/User.java` | `studentId` 필드 추가 |
| `user/JoinStatus.java` | `REJECTED` 상태 추가 |
| `user/UserRepository.java` | `findByStudentId`, `findByJoinStatusWithOrganization` 추가 |
| `organization/Organization.java` | `department` 필드 추가 |
| `organization/OrganizationRepository.java` | `findByDepartment` 추가 |
| `auth/AuthController.java` | SSO 콜백, 프로필 조회, 회원가입 엔드포인트 추가 |
| `config/SecurityConfig.java` | SSO 및 관리자 경로 허용 추가 |
| `build.gradle` | `jsoup` 라이브러리 추가 |
```