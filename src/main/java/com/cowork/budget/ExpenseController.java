package com.cowork.budget;

import com.cowork.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Expense", description = "지출 관리 API — 예산 지출 내역 CRUD, 집계 통계, 영수증 업로드")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final BankStatementParser bankStatementParser;
    private final ExpenseService expenseService;
    private final OcrService ocrService;
    private final ReceiptMatchService receiptMatchService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Operation(summary = "영수증 OCR",
            description = "영수증 이미지를 업로드하면 지출 등록 폼에 맞는 날짜, 사용처, 금액, 결제수단, 카테고리 등을 자동 추출합니다.")
    @PostMapping("/ocr")
    public ResponseEntity<ApiResponse<ReceiptOcrResponse>> ocrReceipt(
            @Parameter(description = "영수증 이미지 파일") @RequestParam MultipartFile file) throws Exception {
        OcrService.OcrResult result = ocrService.parseReceipt(file);
        return ResponseEntity.ok(ApiResponse.ok(ReceiptOcrResponse.of(result)));
    }

    @Operation(summary = "영수증-통장 매칭",
            description = """
                영수증 이미지와 통장 엑셀을 함께 업로드하면,
                OCR로 추출한 결제 시각·금액을 기준으로 ±1분 이내의 거래 내역을 찾아 반환합니다.
                """)
    @PostMapping("/match-receipt")
    public ResponseEntity<ApiResponse<MatchResultResponse>> matchReceipt(
            @Parameter(description = "영수증 이미지 파일") @RequestParam MultipartFile receipt,
            @Parameter(description = "통장 거래 내역 엑셀 (.xlsx / .xls)") @RequestParam MultipartFile bankStatement
    ) throws Exception {
        OcrService.OcrResult ocr = ocrService.parseReceipt(receipt);
        List<BankStatementParser.BankRow> bankRows = bankStatementParser.parse(bankStatement);
        ReceiptMatchService.MatchResult match = receiptMatchService.match(ocr, bankRows);
        ReceiptOcrResponse receiptResponse = ReceiptOcrResponse.of(ocr);

        MatchedBankRow matchedRow = match.bankRow() != null ? new MatchedBankRow(
                match.bankRow().dateTime() != null ? match.bankRow().dateTime().format(DT_FMT) : null,
                match.bankRow().vendor(),
                match.bankRow().amount(),
                match.bankRow().description(),
                match.timeDifferenceMinutes()
        ) : null;

        return ResponseEntity.ok(ApiResponse.ok(
                new MatchResultResponse(
                        receiptResponse,
                        receiptResponse.dateTime(),
                        receiptResponse.amount(),
                        matchedRow,
                        match.matched(),
                        match.confidence(),
                        match.reason(),
                        match.candidateCount()
                )
        ));
    }

    record MatchResultResponse(ReceiptOcrResponse receipt, String receiptDateTime, Long receiptAmount,
                               MatchedBankRow matchedBankRow, boolean matched, double matchConfidence,
                               String matchReason, int candidateCount) {}

    record MatchedBankRow(String dateTime, String vendor, Long amount, String description,
                          Long timeDifferenceMinutes) {}

    @Operation(
            summary = "통장 엑셀 파싱",
            description = """
                통장 거래 내역 엑셀 파일을 업로드하면 거래일시·금액·거래처를 추출합니다.
                
                **지원 형식:** .xlsx, .xls
                
                **자동 인식 컬럼:** 거래일시, 거래금액, 거래처, 적요/메모
                """)
    @PostMapping("/parse-excel")
    public ResponseEntity<ApiResponse<List<BankStatementParser.BankRow>>> parseExcel(
            @Parameter(description = "통장 엑셀 파일") @RequestParam MultipartFile file) throws java.io.IOException {
        List<BankStatementParser.BankRow> rows = bankStatementParser.parse(file);
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }
    @Operation(
            summary = "지출 목록 조회",
            description = """
                    코호트의 지출 내역 목록을 조회합니다.

                    **사용 시점:** 예산 관리 화면에서 지출 내역 목록 표시 및 필터 적용 시.

                    **필터 옵션:**
                    - `department`: 부서별 필터 (PLANNING, MARKETING, OPERATION, FINANCE, GENERAL)
                    - `category`: "식비", "인쇄비", "비품" 등 자유 문자열
                    - `dateFrom` / `dateTo`: 날짜 범위 필터 (ISO 형식: `yyyy-MM-dd`)
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지출 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "cohortId": 5,
                                          "date": "2025-05-01",
                                          "department": "PLANNING",
                                          "category": "식비",
                                          "vendor": "BBQ치킨",
                                          "description": "기획팀 회식",
                                          "amount": 120000,
                                          "paymentMethod": "법인카드",
                                          "receiptStoragePath": "receipts/receipt_001.jpg",
                                          "note": null,
                                          "eventId": 3,
                                          "createdAt": "2025-05-01T20:00:00",
                                          "updatedAt": "2025-05-01T20:00:00"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "부서 필터") @RequestParam(required = false) String department,
            @Parameter(description = "분류 필터 (예: 식비, 인쇄비)", example = "식비") @RequestParam(required = false) String category,
            @Parameter(description = "조회 시작 날짜 (yyyy-MM-dd)", example = "2025-05-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "조회 종료 날짜 (yyyy-MM-dd)", example = "2025-05-31") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        List<Expense> expenses = expenseService.getExpenses(cohortId, department, category, dateFrom, dateTo);
        Map<Long, List<Long>> photoIdsByExpenseId = expenseService.getPhotoIdsByExpenseIds(
                expenses.stream().map(Expense::getId).toList()
        );
        List<ExpenseResponse> list = expenses
                .stream()
                .map(expense -> ExpenseResponse.of(
                        expense,
                        photoIdsByExpenseId.getOrDefault(expense.getId(), List.of())
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @Operation(
            summary = "지출 요약 통계 조회",
            description = """
                    코호트의 지출 집계 데이터(총액, 부서별, 분류별)를 반환합니다.

                    **사용 시점:** 예산 대시보드에서 수치 카드나 차트 데이터로 사용.

                    **응답 구조:**
                    ```json
                    {
                      "total": 총지출액,
                      "byDepartment": { "PLANNING": 금액, "MARKETING": 금액, ... },
                      "byCategory": { "식비": 금액, "인쇄비": 금액, ... }
                    }
                    ```
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지출 통계 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "total": 1580000,
                                        "byDepartment": {
                                          "PLANNING": 320000,
                                          "MARKETING": 850000,
                                          "OPERATION": 410000
                                        },
                                        "byCategory": {
                                          "식비": 450000,
                                          "인쇄비": 180000,
                                          "비품": 950000
                                        }
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getSummary(cohortId)));
    }

    @Operation(
            summary = "지출 내역 등록",
            description = """
                    새 지출 내역을 기록합니다. 영수증 파일을 함께 업로드할 수 있습니다.

                    **사용 시점:** 물품 구매·식비 지출 등 발생 즉시 기록할 때.

                    **요청 형식:** `multipart/form-data`

                    **부서(department) 값:** PLANNING / MARKETING / OPERATION / FINANCE / GENERAL

                    영수증 업로드 후 URL은 `/uploads/{receiptStoragePath}` 형식으로 접근합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지출 내역 등록 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 5,
                                        "cohortId": 5,
                                        "date": "2025-05-10",
                                        "department": "MARKETING",
                                        "category": "인쇄비",
                                        "vendor": "킨코스",
                                        "description": "포스터 100장 인쇄",
                                        "amount": 50000,
                                        "paymentMethod": "현금",
                                        "receiptStoragePath": "receipts/kinkos_20250510.jpg",
                                        "note": null,
                                        "eventId": null,
                                        "createdAt": "2025-05-10T11:00:00",
                                        "updatedAt": "2025-05-10T11:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @Parameter(description = "지출 날짜 (yyyy-MM-dd)", required = true, example = "2025-05-10") @RequestParam LocalDate date,
            @Parameter(description = "코호트 ID", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "지출 부서", required = true) @RequestParam String department,
            @Parameter(description = "지출 분류 (예: 식비, 인쇄비)", required = true, example = "인쇄비") @RequestParam String category,
            @Parameter(description = "거래처/공급업체", required = true, example = "킨코스") @RequestParam String vendor,
            @Parameter(description = "상세 설명", example = "포스터 100장 인쇄") @RequestParam(required = false) String description,
            @Parameter(description = "지출 금액 (원화)", required = true, example = "50000") @RequestParam Long amount,
            @Parameter(description = "결제 수단 (예: 법인카드, 현금, 계좌이체)", required = true, example = "현금") @RequestParam String paymentMethod,
            @Parameter(description = "관리자 메모") @RequestParam(required = false) String note,
            @Parameter(description = "연결할 행사 ID") @RequestParam(required = false) Long eventId,
            @Parameter(description = "증빙으로 연결할 행사 사진 ID 목록") @RequestParam(required = false) List<Long> photoIds,
            @Parameter(description = "영수증 파일 (이미지 또는 PDF)") @RequestParam(required = false) MultipartFile receipt) {
        Expense expense = expenseService.createExpense(cohortId, date, department, category, vendor,
                description, amount, paymentMethod, note, eventId, receipt, photoIds);
        return ResponseEntity.ok(ApiResponse.ok(ExpenseResponse.of(expense, expenseService.getPhotoIds(expense.getId()))));
    }

    @Operation(
            summary = "지출 내역 단건 조회",
            description = """
                    지출 ID로 단일 지출 상세 정보를 조회합니다.

                    **사용 시점:** 지출 상세 화면 또는 수정 폼 진입 시.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지출 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 1,
                                        "cohortId": 5,
                                        "date": "2025-05-01",
                                        "department": "PLANNING",
                                        "category": "식비",
                                        "vendor": "BBQ치킨",
                                        "description": "기획팀 회식",
                                        "amount": 120000,
                                        "paymentMethod": "법인카드",
                                        "receiptStoragePath": "receipts/receipt_001.jpg",
                                        "note": null,
                                        "eventId": 3,
                                        "createdAt": "2025-05-01T20:00:00",
                                        "updatedAt": "2025-05-01T20:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "지출 내역을 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpense(
            @Parameter(description = "지출 ID", required = true, example = "1") @PathVariable Long id) {
        Expense expense = expenseService.getExpense(id);
        return ResponseEntity.ok(ApiResponse.ok(ExpenseResponse.of(expense, expenseService.getPhotoIds(expense.getId()))));
    }

    @Operation(
            summary = "지출 내역 수정",
            description = """
                    지출 정보를 수정합니다. 영수증 교체도 가능합니다.

                    **사용 시점:** 잘못 입력된 지출 정보를 정정할 때.

                    **요청 형식:** `multipart/form-data`

                    전달하지 않은 필드는 기존 값이 유지됩니다.
                    `receipt` 파라미터를 전달하면 기존 영수증이 교체됩니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지출 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "지출 내역을 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> updateExpense(
            @Parameter(description = "지출 ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "지출 날짜 (yyyy-MM-dd)", example = "2025-05-10") @RequestParam(required = false) LocalDate date,
            @Parameter(description = "지출 부서") @RequestParam(required = false) String department,
            @Parameter(description = "지출 분류") @RequestParam(required = false) String category,
            @Parameter(description = "거래처") @RequestParam(required = false) String vendor,
            @Parameter(description = "상세 설명") @RequestParam(required = false) String description,
            @Parameter(description = "지출 금액") @RequestParam(required = false) Long amount,
            @Parameter(description = "결제 수단") @RequestParam(required = false) String paymentMethod,
            @Parameter(description = "관리자 메모") @RequestParam(required = false) String note,
            @Parameter(description = "연결 행사 ID") @RequestParam(required = false) Long eventId,
            @Parameter(description = "증빙으로 연결할 행사 사진 ID 목록") @RequestParam(required = false) List<Long> photoIds,
            @Parameter(description = "새 영수증 파일 (없으면 기존 파일 유지)") @RequestParam(required = false) MultipartFile receipt) {
        Expense expense = expenseService.updateExpense(id, date, department, category, vendor,
                description, amount, paymentMethod, note, eventId, receipt, photoIds);
        return ResponseEntity.ok(ApiResponse.ok(ExpenseResponse.of(expense, expenseService.getPhotoIds(expense.getId()))));
    }

    @Operation(
            summary = "지출 내역 삭제",
            description = """
                    지출 레코드를 삭제합니다. 영수증 파일도 스토리지에서 함께 삭제됩니다.

                    **사용 시점:** 잘못 입력된 지출을 완전히 제거할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지출 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "지출 내역을 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @Parameter(description = "지출 ID", required = true, example = "1") @PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    record ExpenseResponse(Long id, Long cohortId, LocalDate date, String department, String category,
                           String vendor, String description, Long amount, String paymentMethod,
                           String receiptStoragePath, String receiptUrl, List<Long> photoIds, String note, Long eventId,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        static ExpenseResponse of(Expense e, List<Long> photoIds) {
            return new ExpenseResponse(e.getId(), e.getCohortId(), e.getDate(),
                    e.getDepartment(), e.getCategory(), e.getVendor(), e.getDescription(),
                    e.getAmount(), e.getPaymentMethod(), e.getReceiptStoragePath(),
                    e.getReceiptStoragePath() != null ? "/uploads/" + e.getReceiptStoragePath() : null,
                    photoIds, e.getNote(),
                    e.getEventId(), e.getCreatedAt(), e.getUpdatedAt());
        }
    }
}
