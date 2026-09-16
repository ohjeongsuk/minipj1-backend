package com.example.service;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * CSV 의 인코딩·숫자·날짜 해석. 엑셀 왕복 내성을 담당한다(CSV-07).
 *
 * 사용자는 내려받은 CSV 를 그대로 다시 올리지 않는다. 엑셀에서 열어 편집한 뒤 올린다.
 * 그 사이에 엑셀이 인코딩·천단위 서식·날짜 구분자를 전부 바꿔 놓는다.
 *
 * 관대하게 읽되 내보내기 형식은 바꾸지 않는다.
 * 양쪽을 다 넓히면 왕복 검증의 기준 자체가 사라진다.
 */
public final class CsvCodec {

    /** UTF-8 BOM. 없으면 Excel 이 시스템 기본 인코딩(한국어 Windows = CP949)으로 읽어 한글이 깨진다 */
    public static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final char BOM_CHAR = '﻿';

    /** 내보내기 형식은 이 하나뿐이다 */
    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 가져오기는 엑셀이 바꿔놓은 형식까지 받아준다 */
    private static final List<DateTimeFormatter> IMPORT_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    private CsvCodec() {
    }

    /**
     * 업로드 바이트를 문자열로 만든다.
     *
     * ⚠️ new String(bytes, UTF_8) 을 쓰지 않는다.
     *    깨진 바이트에 예외를 던지지 않고 조용히 U+FFFD 로 치환한다.
     *    그러면 파싱은 "성공"하고 카테고리 이름만 깨진 문자로 바뀌어
     *    전 행이 "카테고리를 찾을 수 없습니다"로 실패한다.
     *    사용자도 개발자도 인코딩 문제를 카테고리 문제로 오인한다.
     *
     * ⚠️ REPORT 가 핵심이다. 기본값 REPLACE 로 두면 예외가 나지 않아 폴백이 영영 동작하지 않는다.
     *
     * ⚠️ EUC-KR 이 아니라 MS949 를 쓴다. 엑셀이 쓰는 것은 EUC-KR 의 상위집합인 CP949 이고,
     *    EUC-KR 로 읽으면 확장 음절(똠·믜 등)에서 다시 깨진다.
     */
    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("MS949"));
        }
    }

    /**
     * BOM 제거. ⚠️ 반드시 디코딩 이후에 한다.
     * 순서를 뒤집어 바이트 단계에서 잘라내면 CP949 파일에서 엉뚱한 3바이트를 날린다.
     * 제거하지 않으면 첫 헤더가 "﻿날짜"가 되어 열 매칭이 실패한다
     * — 자기가 내보낸 파일을 자기가 못 읽는 상태가 된다.
     */
    public static String stripBom(String text) {
        if (!text.isEmpty() && text.charAt(0) == BOM_CHAR) {
            return text.substring(1);
        }
        return text;
    }

    /** 내보내기: 콤마 없는 숫자 하나 */
    public static String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    public static String formatDate(LocalDate date) {
        return date.format(EXPORT_DATE);
    }

    /**
     * 가져오기: 엑셀의 천단위 서식("12,500")과 통화 기호를 걷어낸다.
     * 프론트의 금액 입력이 쓰는 것과 같은 정규식이다.
     */
    public static BigDecimal parseAmount(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("금액이 비어 있습니다.");
        }
        String cleaned = raw.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty() || cleaned.equals(".")) {
            throw new IllegalArgumentException("금액을 읽을 수 없습니다: " + raw);
        }
        return new BigDecimal(cleaned);
    }

    /** 가져오기: 우리 내보내기 형식을 먼저 시도하고, 엑셀이 바꾼 형식을 차례로 시도한다 */
    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("날짜가 비어 있습니다.");
        }
        String trimmed = raw.trim();
        for (DateTimeFormatter format : IMPORT_DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (DateTimeParseException ignored) {
                // 다음 후보를 시도한다
            }
        }
        throw new IllegalArgumentException("날짜 형식을 읽을 수 없습니다: " + raw);
    }
}
