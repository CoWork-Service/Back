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
  "isCouncilMember": false,
  "cohortLabel": "1기",
  "department": "AI소프트웨어학부"
}
```
- 가입 후 `PENDING` 상태로 생성
- 관리자 승인 후 `ACTIVE`로 변경
  UPDATE users SET join_status = 'ACTIVE' WHERE student_id = '20221844'; 실행 예시 cmd mysql터미널
- 다음 SSO 로그인부터 JWT 발급
  재로그인시 accessToken, RefreshToken 발급


기존 파일 수정한 것들
User.java → studentId 필드 추가
Organization.java → department 필드 추가
AuthController.java → 엔드포인트 추가
SecurityConfig.java → 경로 허용 추가
UserRepository.java → findByStudentId 추가
OrganizationRepository.java → findByDepartment 추가
