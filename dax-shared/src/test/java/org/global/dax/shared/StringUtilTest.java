package org.global.dax.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class StringUtilTest {

    @Test
    void limitValue() {
        String original = "ABCDEFG";
        assertEquals("ABCD", StringUtil.limit(original, 4));
        assertEquals(original, StringUtil.limitValue(original));
    }

    @Test
    void fixKey() {
        assertEquals("ABCD", StringUtil.limitKey("ABCDEFG"));
        assertEquals("ABC ", StringUtil.limitKey("ABC"));
    }

}
