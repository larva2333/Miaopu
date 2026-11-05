package dev.kiritoxd.miaopu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlsTest {
    @Test fun `plain http logos are upgraded so the cleartext policy does not block them`() {
        // 亚运会的韩国队、阿联酋队（后者还是 .0 结尾），德玛西亚杯的 RED 都是这种老地址。
        assertEquals(
            "https://p1.hoopchina.com.cn/eg/lol/game/team/pic/102709640888320.png",
            "http://p1.hoopchina.com.cn/eg/lol/game/team/pic/102709640888320.png".secureImageUrl(),
        )
        assertEquals(
            "https://p1.hoopchina.com.cn/eg/lol/game/team/pic/102709648359424.0",
            "http://p1.hoopchina.com.cn/eg/lol/game/team/pic/102709648359424.0".secureImageUrl(),
        )
    }

    @Test fun `https and other schemes are left alone`() {
        val https = "https://i5.hoopchina.com.cn/eg-game/lol/game/team/pic/abcprod.png"
        assertEquals(https, https.secureImageUrl())
        assertEquals("HTTP://Example.com/a.png".secureImageUrl(), "https://Example.com/a.png")
        assertEquals("/local/path.png", "/local/path.png".secureImageUrl())
    }

    @Test fun `blank logos stay null`() {
        assertNull(null.secureImageUrl())
        assertNull("".secureImageUrl())
        assertNull("   ".secureImageUrl())
    }
}
