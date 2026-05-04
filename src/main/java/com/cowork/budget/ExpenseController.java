package com.cowork.budget;

import com.cowork.cohort.Department;
import com.cowork.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 지출 관리 컨트롤러 (ExpenseController)
 *
 * 역할:
 *   코호트의 예산 지출 내역을 기록·조회·수정·삭제하는 API 를 제공한다.
 *   기본 경로: /api/expenses
 *
 * 주요 기능:
 *   - 지출 CRUD (영수증 파일 업로드 포함)
 *   - 기간·부서·분류 필터링 목록 조회
 *   - 부서별·분류별 집계 통계 조회 (summary)
 *
 * 요청 형식:
 *   - POST/PUT : multipart/form-data (영수증 파일 함께 전송 가능)
 *   - GET      : query parameter
 *
 * 인증 필요: 모든 엔드포인트에 JWT Access Token 필요
 */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    /**
     * 지출 목록 조회
     *
     * 동작: cohortId 기준으로 지출 내역 목록을 조회하며, 부서·분류·날짜 범위로 필터링 가능.
     * 사용 시점: 예산 관리 화면에서 지출 내역 목록 표시 및 필터 적용 시.
     *
     * @param cohortId   필수. 조회할 코호트 ID
     * @param department 선택. 부서 필터
     * @param category   선택. 분류 필터 (예: "식비", "인쇄비")
     * @param dateFrom   선택. 조회 시작 날짜 (ISO DATE 형식: yyyy-MM-dd)
     * @param dateTo     선택. 조회 종료 날짜
     * @return 지출 내역 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
            @RequestParam Long cohortId,
            @RequestParam(required = false) Department department,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        List<ExpenseResponse> list = expenseService.getExpenses(cohortId, department, category, dateFrom, dateTo)
                .stream().map(ExpenseResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 지출 요약 통계 조회
     *
     * 동작: 특정 코호트의 총 지출액, 부서별 지출액, 분류별 지출액 등 집계 데이터를 반환.
     * 사용 시점: 예산 대시보드에서 수치 카드나 차트 데이터로 사용.
     *
     * @param cohortId 조회할 코호트 ID
     * @return { "total": 1500000, "byDepartment": {...}, "byCategory": {...} } 형태의 집계 맵
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(@RequestParam Long cohortId) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getSummary(cohortId)));
    }

    /**
     * 지출 내역 등록
     *
     * 동작: 새 지출 내역을 기록한다. 영수증 파일을 함께 업로드할 수 있다.
     * 사용 시점: 물품 구매·식비 지출 등 발생 즉시 기록할 때.
     *
     * Content-Type: multipart/form-data
     * @param date          필수. 지출 날짜 (ISO DATE: yyyy-MM-dd)
     * @param cohortId      필수. 소속 코호트 ID
     * @param department    필수. 지출 부서
     * @param category      필수. 지출 분류
     * @param vendor        필수. 거래처/공급업체 이름
     * @param description   선택. 상세 설명
     * @param amount        필수. 지출 금액 (원화)
     * @param paymentMethod 필수. 결제 수단
     * @param note          선택. 관리자 메모
     * @param eventId       선택. 연결 행사 ID
     * @param receipt       선택. 영수증 파일
     * @return 생성된 지출 내역
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @RequestParam LocalDate date,
            @RequestParam Long cohortId,
            @RequestParam Department department,
            @RequestParam String category,
            @RequestParam String vendor,
            @RequestParam(required = false) String description,
            @RequestParam Long amount,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) MultipartFile receipt) {
        Expense expense = expenseService.createExpense(cohortId, date, department, category, vendor,
                description, amount, paymentMethod, note, eventId, receipt);
        return ResponseEntity.ok(ApiResponse.ok(ExpenseResponse.of(expense)));
    }

    /**
     * 지출 내역 단건 조회
     *
     * 동작: 지출 ID 로 단일 지출 상세 정보를 반환.
     * 사용 시점: 지출 상세 화면 또는 수정 폼 진입 시.
     *
     * @param id 지출 ID
     * @return 지출 상세 정보 (영수증 경로 포함)
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpense(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(ExpenseResponse.of(expenseService.getExpense(id))));
    }

    /**
     * 지출 내역 수정
     *
     * 동작: 지출 정보를 수정한다. 영수증 교체도 가능 (receipt 파라미터 전달 시).
     *       null 인 파라미터는 기존 값을 유지한다 (서비스 레이어에서 처리).
     * 사용 시점: 잘못 입력된 지출 정보를 정정할 때.
     *
     * Content-Type: multipart/form-data
     * @param id      지출 ID
     * @param receipt 선택. 새 영수증 파일 (없으면 기존 파일 유지)
     * @return 수정된 지출 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> updateExpense(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Department department,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long amount,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) MultipartFile receipt) {
        Expense expense = expenseService.updateExpense(id, date, department, category, vendor,
                description, amount, paymentMethod, note, eventId, receipt);
        return ResponseEntity.ok(ApiResponse.ok(ExpenseResponse.of(expense)));
    }

    /**
     * 지출 내역 삭제
     *
     * 동작: 지출 레코드를 삭제한다. 영수증 파일도 스토리지에서 함께 삭제된다.
     * 사용 시점: 잘못 입력된 지출을 완전히 제거할 때.
     *
     * @param id 지출 ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 지출 응답 DTO */
    record ExpenseResponse(Long id, Long cohortId, LocalDate date, String department, String category,
                           String vendor, String description, Long amount, String paymentMethod,
                           String receiptStoragePath, String note, Long eventId,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        static ExpenseResponse of(Expense e) {
            return new ExpenseResponse(e.getId(), e.getCohortId(), e.getDate(),
                    e.getDepartment().name(), e.getCategory(), e.getVendor(), e.getDescription(),
                    e.getAmount(), e.getPaymentMethod(), e.getReceiptStoragePath(), e.getNote(),
                    e.getEventId(), e.getCreatedAt(), e.getUpdatedAt());
        }
    }
}
