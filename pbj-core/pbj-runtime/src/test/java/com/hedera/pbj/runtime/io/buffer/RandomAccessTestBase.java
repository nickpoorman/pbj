package com.hedera.pbj.runtime.io.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.ReadableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class RandomAccessTestBase extends ReadableTestBase {

    @NonNull
    protected abstract RandomAccessData randomAccessData(@NonNull final byte[] bytes);

    @Test
    void sliceLength() {
        final var buf = randomAccessData(TEST_BYTES);
        assertThat(buf.slice(2, 5).length()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "a", "ab", "abc", "✅" })
    void utf8Strings(final String s) {
        final var buf = randomAccessData(s.getBytes(StandardCharsets.UTF_8));
        assertThat(buf.asUtf8String()).isEqualTo(s);
    }

    @Test
    void getBytesGoodLength() {
        final var buf = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
        final byte[] dst = new byte[8];
        Arrays.fill(dst, (byte) 0);
        assertThat(buf.getBytes(4, dst, 0, 4)).isEqualTo(4);
        assertThat(dst).isEqualTo(new byte[] {4, 5, 6, 7, 0, 0, 0, 0});
    }

    @Test
    void getBytesExtraSrcLength() {
        final var buf = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
        final byte[] dst = new byte[8];
        Arrays.fill(dst, (byte) 0);
        assertThat(buf.getBytes(3, dst, 0, 6)).isEqualTo(5);
        assertThat(dst).isEqualTo(new byte[] {3, 4, 5, 6, 7, 0, 0, 0});
    }

    @Test
    void getBytesExtraDstLength() {
        final var buf = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
        final byte[] dst = new byte[8];
        Arrays.fill(dst, (byte) 0);
        assertThatThrownBy(() -> buf.getBytes(4, dst, 6, 4))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void matchesPrefixByteArray() {
        final RandomAccessData data = randomAccessData(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09});

        assertTrue(data.matchesPrefix(new byte[]{0x01}));
        assertTrue(data.matchesPrefix(new byte[]{0x01,0x02}));
        assertTrue(data.matchesPrefix(new byte[]{0x01,0x02,0x03,0x04,}));
        assertTrue(data.matchesPrefix(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09}));

        assertFalse(data.matchesPrefix(new byte[]{0x02}));
        assertFalse(data.matchesPrefix(new byte[]{0x01,0x02,0x03,0x02}));
        assertFalse(data.matchesPrefix(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x00}));
    }

    @Test
    void matchesPrefixBytes() {
        final RandomAccessData data = randomAccessData(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09});
        assertTrue(data.matchesPrefix(Bytes.wrap(new byte[]{0x01})));
        assertTrue(data.matchesPrefix(Bytes.wrap(new byte[]{0x01,0x02})));
        assertTrue(data.matchesPrefix(Bytes.wrap(new byte[]{0x01,0x02,0x03,0x04,})));
        assertTrue(data.matchesPrefix(Bytes.wrap(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09})));

        assertFalse(data.matchesPrefix(Bytes.wrap(new byte[]{0x02})));
        assertFalse(data.matchesPrefix(Bytes.wrap(new byte[]{0x01,0x02,0x03,0x02})));
        assertFalse(data.matchesPrefix(Bytes.wrap(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x00})));
    }

    @Test
    void matchesPrefixEmpty_issue37() {
        final var data1 = randomAccessData(new byte[0]);
        final var data2 = randomAccessData(new byte[0]);
        assertTrue(data1.matchesPrefix(data2));
    }

    @Test
    void containsZeroOffset() {
        final RandomAccessData data = randomAccessData(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06});
        assertTrue(data.contains(0, new byte[]{0x01}));
        assertTrue(data.contains(0, new byte[]{0x01,0x02}));
        assertTrue(data.contains(0, new byte[]{0x01,0x02,0x03,0x04,0x05,0x06}));
        assertFalse(data.contains(0, new byte[]{0x01,0x02,0x02}));
        assertFalse(data.contains(0, new byte[]{0x02,0x02}));
        assertFalse(data.contains(0, new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07}));

        final RandomAccessData slice = data.slice(1, 4);
        assertTrue(slice.contains(0, new byte[]{0x02}));
        assertTrue(slice.contains(0, new byte[]{0x02,0x03}));
        assertTrue(slice.contains(0, new byte[]{0x02,0x03,0x04,0x05}));
        assertFalse(slice.contains(0, new byte[]{0x01}));
        assertFalse(slice.contains(0, new byte[]{0x02,0x02}));
        assertFalse(slice.contains(0, new byte[]{0x02,0x03,0x04,0x05,0x06}));
    }

    @Test
    void containsNonZeroOffset() {
        final RandomAccessData data = randomAccessData(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06});
        assertTrue(data.contains(1, new byte[]{0x02}));
        assertTrue(data.contains(1, new byte[]{0x02,0x03}));
        assertTrue(data.contains(1, new byte[]{0x02,0x03,0x04,0x05,0x06}));
        assertFalse(data.contains(1, new byte[]{0x02,0x03,0x03}));
        assertFalse(data.contains(1, new byte[]{0x03,0x03}));
        assertFalse(data.contains(1, new byte[]{0x02,0x03,0x04,0x05,0x06,0x07}));

        final RandomAccessData slice = data.slice(1, 4);
        assertTrue(slice.contains(1, new byte[]{0x03}));
        assertTrue(slice.contains(1, new byte[]{0x03,0x04}));
        assertTrue(slice.contains(1, new byte[]{0x03,0x04,0x05}));
        assertFalse(slice.contains(1, new byte[]{0x02}));
        assertFalse(slice.contains(1, new byte[]{0x03,0x03}));
        assertFalse(slice.contains(1, new byte[]{0x03,0x04,0x05,0x06}));
    }

    @Test
    void getInt() {
        final RandomAccessData data = randomAccessData(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06});
        assertEquals(0x01020304, data.getInt(0));
        assertEquals(0x02030405, data.getInt(1));

        final RandomAccessData slice = data.slice(1, 5);
        assertEquals(0x02030405, slice.getInt(0));
        assertEquals(0x03040506, slice.getInt(1));
    }

    @Test
    void getLong() {
        final RandomAccessData data = randomAccessData(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A});
        assertEquals(0x0102030405060708L, data.getLong(0));
        assertEquals(0x0203040506070809L, data.getLong(1));

        final RandomAccessData slice = data.slice(1, 9);
        assertEquals(0x0203040506070809L, slice.getLong(0));
        assertEquals(0x030405060708090AL, slice.getLong(1));
    }
}
