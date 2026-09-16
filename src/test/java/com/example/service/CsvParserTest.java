package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Nested
    @DisplayName("파싱")
    class Parse {

        @Test
        @DisplayName("기본 행을 열로 나눈다")
        void 기본_행() {
            List<List<String>> rows = CsvParser.parse("""
                    날짜,구분,카테고리,금액,거래처,메모
                    2026-09-14,지출,식비,12500,스타벅스 강남점,팀 미팅""");

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsExactly("날짜", "구분", "카테고리", "금액", "거래처", "메모");
            assertThat(rows.get(1)).containsExactly(
                    "2026-09-14", "지출", "식비", "12500", "스타벅스 강남점", "팀 미팅");
        }

        @Test
        @DisplayName("따옴표 안의 콤마는 열을 나누지 않는다 (split(\",\") 이면 열이 밀린다)")
        void 따옴표_안_콤마() {
            List<List<String>> rows = CsvParser.parse(
                    "2026-09-14,지출,식비,12500,스타벅스,\"팀 미팅, 커피 2잔\"");

            assertThat(rows.get(0)).hasSize(6);
            assertThat(rows.get(0).get(5)).isEqualTo("팀 미팅, 커피 2잔");
        }

        @Test
        @DisplayName("따옴표 안의 \"\" 는 따옴표 한 개다")
        void 이스케이프된_따옴표() {
            List<List<String>> rows = CsvParser.parse("a,\"그는 \"\"좋다\"\" 고 말했다\",c");

            assertThat(rows.get(0)).containsExactly("a", "그는 \"좋다\" 고 말했다", "c");
        }

        @Test
        @DisplayName("따옴표 안의 줄바꿈은 행을 나누지 않는다")
        void 따옴표_안_줄바꿈() {
            List<List<String>> rows = CsvParser.parse("a,\"첫 줄\n둘째 줄\",c\nd,e,f");

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).get(1)).isEqualTo("첫 줄\n둘째 줄");
            assertThat(rows.get(1)).containsExactly("d", "e", "f");
        }

        @Test
        @DisplayName("CRLF 와 LF 를 모두 줄바꿈으로 처리한다")
        void 줄바꿈_형식() {
            assertThat(CsvParser.parse("a,b\r\nc,d")).hasSize(2);
            assertThat(CsvParser.parse("a,b\nc,d")).hasSize(2);
        }

        @Test
        @DisplayName("빈 필드와 후행 콤마를 유지한다")
        void 빈_필드() {
            assertThat(CsvParser.parse("a,,c").get(0)).containsExactly("a", "", "c");
            assertThat(CsvParser.parse("a,b,").get(0)).containsExactly("a", "b", "");
        }

        @Test
        @DisplayName("마지막 줄에 줄바꿈이 없어도 읽는다")
        void 마지막_줄바꿈_없음() {
            assertThat(CsvParser.parse("a,b\nc,d")).hasSize(2);
            assertThat(CsvParser.parse("a,b\nc,d\n")).hasSize(2);
        }

        @Test
        @DisplayName("빈 문자열은 빈 목록이다")
        void 빈_입력() {
            assertThat(CsvParser.parse("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("내보내기 이스케이프")
    class Escape {

        @Test
        @DisplayName("특수문자가 없으면 그대로 둔다")
        void 평범한_값() {
            assertThat(CsvParser.escape("스타벅스 강남점")).isEqualTo("스타벅스 강남점");
        }

        @Test
        @DisplayName("콤마·따옴표·줄바꿈이 있으면 따옴표로 감싼다")
        void 감싸야_하는_값() {
            assertThat(CsvParser.escape("팀 미팅, 커피")).isEqualTo("\"팀 미팅, 커피\"");
            assertThat(CsvParser.escape("그는 \"좋다\"")).isEqualTo("\"그는 \"\"좋다\"\"\"");
            assertThat(CsvParser.escape("첫 줄\n둘째 줄")).isEqualTo("\"첫 줄\n둘째 줄\"");
        }

        @Test
        @DisplayName("null 은 빈 문자열이다")
        void null_값() {
            assertThat(CsvParser.escape(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("왕복")
    class RoundTrip {

        @Test
        @DisplayName("내보낸 줄을 다시 파싱하면 원래 값이 나온다")
        void 왕복_일치() {
            List<String> original = List.of(
                    "2026-09-14", "지출", "식비", "12500",
                    "스타벅스, 강남점", "그는 \"좋다\" 고 했다\n다음 줄");

            String line = CsvParser.toLine(original);
            List<List<String>> parsed = CsvParser.parse(line);

            assertThat(parsed).hasSize(1);
            assertThat(parsed.get(0)).isEqualTo(original);
        }
    }
}
