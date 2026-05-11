package com.cowork.mobile;

import com.cowork.budget.Expense;
import com.cowork.cohort.Department;
import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Mobile Session", description = "모바일 사진 업로드 세션 API — 영수증·사진을 모바일로 간편 촬영하여 데스크톱 세션에 전달")
@RestController
@RequestMapping("/api/mobile/sessions")
@RequiredArgsConstructor
public class MobileSessionController {

    private final MobileSessionService mobileSessionService;
    private final UserRepository userRepository;

    @Operation(
            summary = "모바일 세션 생성 (인증 필요)",
            description = """
                    모바일 업로드를 위한 임시 세션을 생성합니다.

                    **사용 시점:** 데스크톱에서 영수증/사진을 모바일로 촬영하고 싶을 때.

                    **워크플로우:**
                    1. 데스크톱에서 이 API로 세션 생성 → QR 코드 URL 반환
                    2. 모바일에서 QR 코드 스캔 → `/m/expense/{sessionToken}` 페이지 접속
                    3. 모바일에서 사진 촬영 → `POST /{token}/upload` 호출
                    4. 데스크톱에서 `GET /{token}/result`로 업로드된 사진 확인

                    세션은 일정 시간 후 자동 만료됩니다.
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "세션 생성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "sessionToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                        "qrUrl": "/m/expense/a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                        "expiresAt": "2025-05-10T12:30:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<MobileSessionResponse>> createSession(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "세션 생성 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "cohortId": 5 }
                            """)))
            @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        MobileSession session = mobileSessionService.createSession(request.getCohortId(), user.getId());
        return ResponseEntity.ok(ApiResponse.ok(MobileSessionResponse.of(session)));
    }

    @Operation(
            summary = "세션 상태 조회 (인증 불필요)",
            description = """
                    모바일 세션의 현재 상태를 조회합니다.

                    **사용 시점:**
                    - 데스크톱에서 폴링(polling)으로 업로드 완료 여부 확인
                    - 모바일에서 세션 유효성 확인

                    **인증 불필요** — 모바일 기기에서 접근합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "세션 상태 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "sessionToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                                        "used": false,
                                        "expired": false,
                                        "expiresAt": "2025-05-10T12:30:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션을 찾을 수 없음")
    })
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<MobileSessionStatusResponse>> getSession(
            @Parameter(description = "세션 토큰", required = true, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(MobileSessionStatusResponse.of(mobileSessionService.getSession(token))));
    }

    @Operation(
            summary = "모바일 사진 업로드 (인증 불필요)",
            description = """
                    모바일에서 촬영한 사진을 세션에 업로드합니다.

                    **사용 시점:** 모바일 기기에서 영수증이나 사진을 촬영한 후 업로드.

                    **인증 불필요** — 모바일 기기에서 접근합니다.

                    **요청 형식:** `multipart/form-data`

                    `extraData`는 추가 메타데이터를 JSON 문자열로 전달할 때 사용합니다 (선택사항).

                    업로드 후 데스크톱에서 `GET /{token}/result`로 결과를 확인하세요.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사진 업로드 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "used": true,
                                        "photoPath": "mobile/receipt_a1b2c3d4.jpg",
                                        "photoUrl": "/uploads/mobile/receipt_a1b2c3d4.jpg",
                                        "extraData": { "vendor": "스타벅스", "amount": "15000" },
                                        "expiresAt": "2025-05-10T12:30:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "세션이 이미 사용되었거나 만료됨",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "message": "만료된 세션입니다.",
                                      "code": "SESSION_EXPIRED"
                                    }
                                    """)))
    })
    @PostMapping("/{token}/upload")
    public ResponseEntity<ApiResponse<MobileUploadResponse>> upload(
            @Parameter(description = "세션 토큰", required = true, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") @PathVariable String token,
            @Parameter(description = "업로드할 사진 파일", required = true) @RequestParam("photo") MultipartFile photo,
            @Parameter(description = "추가 메타데이터 (JSON 문자열)", example = "{\"vendor\":\"스타벅스\",\"amount\":\"15000\"}") @RequestParam(required = false) String extraData) {
        MobileSession session = mobileSessionService.upload(token, photo, extraData);
        return ResponseEntity.ok(ApiResponse.ok(MobileUploadResponse.of(session)));
    }

    @Operation(
            summary = "업로드 결과 조회 (인증 불필요)",
            description = """
                    세션에 업로드된 사진 결과를 조회합니다.

                    **사용 시점:** 데스크톱에서 모바일 업로드 완료 여부와 사진 URL을 확인할 때.

                    **인증 불필요** — 공유 세션 토큰으로 접근합니다.

                    `photoUrl`이 null이면 아직 업로드되지 않은 상태입니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "결과 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "used": true,
                                        "photoPath": "mobile/receipt_a1b2c3d4.jpg",
                                        "photoUrl": "/uploads/mobile/receipt_a1b2c3d4.jpg",
                                        "extraData": null,
                                        "expiresAt": "2025-05-10T12:30:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping("/{token}/result")
    public ResponseEntity<ApiResponse<MobileUploadResponse>> getResult(
            @Parameter(description = "세션 토큰", required = true, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(MobileUploadResponse.of(mobileSessionService.getResult(token))));
    }

    @Operation(
            summary = "모바일 업로드 사진으로 지출 확정 (인증 불필요)",
            description = """
                    모바일에서 업로드한 영수증 사진을 실제 지출 내역으로 등록합니다.

                    **사용 시점:** 모바일 영수증 등록 화면에서 사진 업로드 후 지출 정보를 입력하고 저장할 때.

                    이미 지출로 확정된 세션이면 기존 지출 ID를 반환합니다.
                    """)
    @PostMapping("/{token}/expense")
    public ResponseEntity<ApiResponse<MobileExpenseResponse>> createExpense(
            @Parameter(description = "세션 토큰", required = true) @PathVariable String token,
            @RequestBody MobileExpenseRequest request) {
        Expense expense = mobileSessionService.createExpense(
                token,
                request.getDate(),
                request.getDepartment(),
                request.getCategory(),
                request.getVendor(),
                request.getDescription(),
                request.getAmount(),
                request.getPaymentMethod(),
                request.getNote(),
                request.getEventId(),
                request.getPhotoIds()
        );
        MobileSession session = mobileSessionService.getResult(token);
        return ResponseEntity.ok(ApiResponse.ok(MobileExpenseResponse.of(session, expense)));
    }

    @Operation(
            summary = "세션 삭제 (인증 필요)",
            description = """
                    모바일 세션을 삭제합니다.

                    **사용 시점:** 사용 완료된 세션을 명시적으로 정리할 때.
                    """,
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "세션 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "세션을 찾을 수 없음")
    })
    @DeleteMapping("/{token}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "세션 토큰", required = true, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") @PathVariable String token) {
        mobileSessionService.deleteSession(token);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    @Getter
    static class CreateSessionRequest {
        private Long cohortId;
    }

    @Getter
    static class MobileExpenseRequest {
        private LocalDate date;
        private Department department;
        private String category;
        private String vendor;
        private String description;
        private Long amount;
        private String paymentMethod;
        private String note;
        private Long eventId;
        private List<Long> photoIds;
    }

    record MobileSessionResponse(String sessionToken, String qrUrl, LocalDateTime expiresAt) {
        static MobileSessionResponse of(MobileSession session) {
            return new MobileSessionResponse(
                    session.getSessionToken(),
                    "/m/expense/" + session.getSessionToken(),
                    session.getExpiresAt()
            );
        }
    }

    record MobileSessionStatusResponse(String sessionToken, boolean used, boolean expired, Long expenseId,
                                       LocalDateTime expiresAt) {
        static MobileSessionStatusResponse of(MobileSession session) {
            return new MobileSessionStatusResponse(
                    session.getSessionToken(), session.isUsed(), session.isExpired(),
                    session.getExpenseId(), session.getExpiresAt()
            );
        }
    }

    record MobileUploadResponse(boolean used, String photoPath, String photoUrl,
                                Map<String, Object> extraData, Long expenseId, LocalDateTime expiresAt) {
        static MobileUploadResponse of(MobileSession session) {
            return new MobileUploadResponse(
                    session.isUsed(), session.getPhotoPath(),
                    session.getPhotoPath() != null ? "/uploads/" + session.getPhotoPath() : null,
                    session.getExtraData(), session.getExpenseId(), session.getExpiresAt()
            );
        }
    }

    record MobileExpenseResponse(Long expenseId, String receiptUrl, boolean used,
                                 LocalDateTime expiresAt) {
        static MobileExpenseResponse of(MobileSession session, Expense expense) {
            return new MobileExpenseResponse(
                    expense.getId(),
                    expense.getReceiptStoragePath() != null ? "/uploads/" + expense.getReceiptStoragePath() : null,
                    session.isUsed(),
                    session.getExpiresAt()
            );
        }
    }
}
