package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Utf8ToolsTest {
    private static Stream<Arguments> provideStringsAndLengths() {
        return Stream.of(
                Arguments.of("", 0),
                Arguments.of(" ", 1),
                Arguments.of("a", 1),
                Arguments.of("\n", 1),
                Arguments.of("not blank", 9)
        );
    }
    @ParameterizedTest
    @MethodSource("provideStringsAndLengths")
    void encodedLength(String testStr, int expectedLength) {
        assertEquals(expectedLength, assertDoesNotThrow(() -> Utf8Tools.encodedLength(testStr)));
        assertEquals(testStr.getBytes(StandardCharsets.UTF_8).length, assertDoesNotThrow(() -> Utf8Tools.encodedLength(testStr)));
    }

    @ParameterizedTest
    @MethodSource("provideStringsAndLengths")
    void encodeUtf8(String testStr, int expectedLength) {
        BufferedData bufferedData = BufferedData.allocate(1024);
        try {
            Utf8Tools.encodeUtf8(testStr, bufferedData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        bufferedData.flip();
        byte[] bytes = new byte[(int)bufferedData.length()];
        bufferedData.getBytes(0, bytes);
        assertEquals(HexFormat.of().formatHex(testStr.getBytes(StandardCharsets.UTF_8)), HexFormat.of().formatHex(bytes));
        assertEquals(expectedLength, bytes.length);
    }
}
