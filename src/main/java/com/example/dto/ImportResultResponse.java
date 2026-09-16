package com.example.dto;

import java.util.List;

/**
 * 부분 성공을 허용한다.
 * 전부 롤백하면 100행 중 1행 오타로 99행을 다시 올려야 한다.
 * 실패 행만 건너뛰고 결과를 알린다.
 */
public record ImportResultResponse(int imported, int failed, List<RowError> errors) {

    /** line 은 파일 기준 행 번호다(헤더가 1행). 사용자가 엑셀에서 바로 찾아갈 수 있어야 한다 */
    public record RowError(int line, String reason) {}
}
