package com.example.service;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 최소 집합만 지원하는 CSV 파서. opencsv·commons-csv 를 쓰지 않는다.
 *
 * ⚠️ split(",") 을 쓰지 않는다. 메모에 콤마가 들어가면 열이 밀린다.
 *    "2026-09-14,지출,식비,12500,스타벅스,\"팀 미팅, 커피\"" 를 split 하면 열이 7개가 되고,
 *    거래처 자리에 "팀 미팅"이, 메모 자리에 " 커피\"" 가 들어간다.
 *
 * 지원하는 것: 따옴표로 감싸기, 내부 따옴표를 "" 로 이스케이프, 따옴표 안의 콤마와 줄바꿈.
 * 지원하지 않는 것: 구분자 변경, 주석, 헤더 자동 인식.
 */
public final class CsvParser {

    private static final char DELIMITER = ',';
    private static final char QUOTE = '"';

    private CsvParser() {
    }

    /**
     * 전체 CSV 텍스트를 행 목록으로 파싱한다.
     * 따옴표 안의 줄바꿈은 행을 나누지 않는다.
     */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if (inQuotes) {
                if (c == QUOTE) {
                    // "" 는 따옴표 한 개를 뜻한다. 그 외의 따옴표는 인용 종료다.
                    if (i + 1 < text.length() && text.charAt(i + 1) == QUOTE) {
                        field.append(QUOTE);
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }

            if (c == QUOTE) {
                inQuotes = true;
                i++;
            } else if (c == DELIMITER) {
                fields.add(field.toString());
                field.setLength(0);
                i++;
            } else if (c == '\r') {
                // CRLF 도 LF 도 같은 줄바꿈으로 취급한다
                i++;
            } else if (c == '\n') {
                fields.add(field.toString());
                field.setLength(0);
                rows.add(fields);
                fields = new ArrayList<>();
                i++;
            } else {
                field.append(c);
                i++;
            }
        }

        // 마지막 줄에 줄바꿈이 없을 수 있다. 빈 꼬리는 버린다.
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            rows.add(fields);
        }
        return rows;
    }

    /**
     * 한 필드를 CSV 로 내보낼 때의 표현.
     * 콤마·따옴표·줄바꿈이 있으면 따옴표로 감싸고 내부 따옴표를 "" 로 이스케이프한다.
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(DELIMITER) >= 0
                || value.indexOf(QUOTE) >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuote) {
            return value;
        }
        return QUOTE + value.replace("\"", "\"\"") + QUOTE;
    }

    /** 한 행을 CSV 한 줄로 만든다 */
    public static String toLine(List<String> fields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                line.append(DELIMITER);
            }
            line.append(escape(fields.get(i)));
        }
        return line.toString();
    }
}
