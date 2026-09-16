package com.example.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.Transaction;
import com.example.domain.TransactionRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.ImportResultResponse;
import com.example.dto.ImportResultResponse.RowError;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

/**
 * CSV 내보내기 / 가져오기.
 *
 * 가져오기는 중복을 검사하지 않는다. 같은 파일을 두 번 올리면 거래가 두 번 등록된다.
 * 의도된 결정이다 — 주 용도가 「엑셀 가계부 이전」이라는 1회성 시나리오이고,
 * 실패분만 골라 다시 올리는 부분 재업로드를 전제하기 때문이다.
 * 해시 기반 중복 제거를 넣으면 dedup_hash 컬럼 + 부분 유니크 인덱스 + 상호명 정규화가
 * 따라붙는데, 얻는 것은 "전체를 실수로 다시 올린 경우" 하나뿐이다.
 */
@Service
public class DataService {

    /** 헤더는 고정이다. 내보내기·가져오기가 같은 형식을 쓴다 */
    static final List<String> HEADER = List.of("날짜", "구분", "카테고리", "금액", "거래처", "메모");

    private static final String INCOME_LABEL = "수입";
    private static final String EXPENSE_LABEL = "지출";

    /** 상한. 초과 시 400 INVALID_CSV */
    private static final int MAX_ROWS = 5000;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public DataService(TransactionRepository transactionRepository,
                       CategoryRepository categoryRepository,
                       UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    // ---------- 내보내기 ----------

    /**
     * ⚠️ 본문 맨 앞에 UTF-8 BOM 3바이트를 쓴다.
     *    없으면 Excel 이 CP949 로 읽어 한글이 전부 깨진다.
     *    VS Code·메모장은 UTF-8 을 자동 감지해 멀쩡히 보여주므로 개발 환경에서는 발견되지 않는다.
     *
     * 범위가 from·to 로 제한되므로 byte[] 로 만들어 반환한다.
     * 건수가 수십만 규모가 되면 StreamingResponseBody 로 바꿔야 한다.
     */
    @Transactional(readOnly = true)
    public byte[] export(Long userId, LocalDate from, LocalDate to) {
        StringBuilder csv = new StringBuilder();
        csv.append(CsvParser.toLine(HEADER)).append('\n');

        for (Transaction t : transactionRepository.findForExport(userId, from, to)) {
            csv.append(CsvParser.toLine(List.of(
                    CsvCodec.formatDate(t.getTxnDate()),
                    label(t.getType()),
                    t.getCategory().getName(),
                    CsvCodec.formatAmount(t.getAmount()),
                    t.getMerchant() == null ? "" : t.getMerchant(),
                    t.getMemo() == null ? "" : t.getMemo()))).append('\n');
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(CsvCodec.BOM.length + body.length);
        try {
            out.write(CsvCodec.BOM);
            out.write(body);
        } catch (IOException e) {
            throw new IllegalStateException("CSV 생성에 실패했습니다.", e);
        }
        return out.toByteArray();
    }

    /** from·to 가 같은 달이면 moneylog_2026-09.csv, 아니면 범위를, 미지정이면 all */
    public String exportFileName(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return "moneylog_all.csv";
        }
        if (from != null && to != null) {
            YearMonth fromMonth = YearMonth.from(from);
            YearMonth toMonth = YearMonth.from(to);
            if (fromMonth.equals(toMonth)) {
                return "moneylog_%s.csv".formatted(fromMonth);
            }
            return "moneylog_%s_%s.csv".formatted(fromMonth, toMonth);
        }
        return "moneylog_%s.csv".formatted(YearMonth.from(from != null ? from : to));
    }

    // ---------- 가져오기 ----------

    @Transactional
    public ImportResultResponse importCsv(Long userId, byte[] content) {
        // 1) 디코딩 → 2) BOM 제거. 순서를 뒤집으면 CP949 파일에서 엉뚱한 바이트를 잘라낸다
        String text = CsvCodec.stripBom(CsvCodec.decode(content));
        List<List<String>> rows = CsvParser.parse(text);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_CSV, "빈 파일입니다.");
        }
        validateHeader(rows.get(0));
        if (rows.size() - 1 > MAX_ROWS) {
            throw new BusinessException(ErrorCode.INVALID_CSV,
                    "한 번에 %,d행까지 가져올 수 있습니다.".formatted(MAX_ROWS));
        }

        User user = userRepository.getReferenceById(userId);
        Map<String, Category> categories = categoryIndex(userId);

        int imported = 0;
        List<RowError> errors = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;   // 파일 기준 행 번호(헤더가 1행)
            List<String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            try {
                transactionRepository.save(toTransaction(user, categories, row));
                imported++;
            } catch (RuntimeException e) {
                errors.add(new RowError(lineNumber, reasonOf(e)));
            }
        }
        return new ImportResultResponse(imported, errors.size(), errors);
    }

    // ---------- 내부 ----------

    private void validateHeader(List<String> header) {
        List<String> normalized = header.stream().map(String::trim).toList();
        if (!normalized.equals(HEADER)) {
            throw new BusinessException(ErrorCode.INVALID_CSV,
                    "헤더가 올바르지 않습니다. 기대: " + String.join(",", HEADER));
        }
    }

    private Transaction toTransaction(User user, Map<String, Category> categories, List<String> row) {
        if (row.size() < 4) {
            throw new IllegalArgumentException("열이 부족합니다. 6개 열이 필요합니다.");
        }
        LocalDate txnDate = CsvCodec.parseDate(value(row, 0));
        TransactionType type = parseType(value(row, 1));
        String categoryName = value(row, 2) == null ? "" : value(row, 2).trim();
        BigDecimal amount = CsvCodec.parseAmount(value(row, 3));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다.");
        }

        Category category = categories.get(categoryName);
        if (category == null) {
            // 자동 생성하지 않는다. 오타 하나로 카테고리가 증식하는 것을 막는다
            throw new IllegalArgumentException("카테고리 '%s'를 찾을 수 없습니다.".formatted(categoryName));
        }
        if (category.getType() != type) {
            throw new IllegalArgumentException(
                    "카테고리 '%s'는 %s 카테고리입니다.".formatted(categoryName, label(category.getType())));
        }

        return Transaction.create(user, category, type, amount, txnDate,
                emptyToNull(value(row, 4)), emptyToNull(value(row, 5)));
    }

    private TransactionType parseType(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (EXPENSE_LABEL.equals(value)) {
            return TransactionType.EXPENSE;
        }
        if (INCOME_LABEL.equals(value)) {
            return TransactionType.INCOME;
        }
        throw new IllegalArgumentException("구분은 '수입' 또는 '지출'이어야 합니다: " + raw);
    }

    private Map<String, Category> categoryIndex(Long userId) {
        Map<String, Category> index = new HashMap<>();
        for (Category category : categoryRepository
                .findByUserIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(userId)) {
            index.put(category.getName(), category);
        }
        return index;
    }

    private String label(TransactionType type) {
        return type == TransactionType.INCOME ? INCOME_LABEL : EXPENSE_LABEL;
    }

    private String value(List<String> row, int index) {
        return index < row.size() ? row.get(index) : null;
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(field -> field == null || field.isBlank());
    }

    /** 내부 예외 메시지를 그대로 노출하지 않고, 우리가 만든 사유만 내보낸다 */
    private String reasonOf(RuntimeException e) {
        if (e instanceof IllegalArgumentException || e instanceof BusinessException) {
            return e.getMessage();
        }
        return "저장에 실패했습니다.";
    }
}
