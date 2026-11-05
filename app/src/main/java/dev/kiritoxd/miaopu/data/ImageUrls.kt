package dev.kiritoxd.miaopu.data

/**
 * 虎扑有一部分老数据的图片还挂在 `http://p1.hoopchina.com.cn` 下（亚运会的韩国、阿联酋，
 * 德玛西亚杯的 RED 都是这种），而应用声明了 `usesCleartextTraffic="false"`，明文请求会被
 * 系统直接拒掉，表现就是队徽空白。同一路径在 https 下完全可用，所以这里统一升级协议；
 * 其它地址原样返回，别去动本来就是相对路径或 data: 的情况。
 */
internal fun String?.secureImageUrl(): String? {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return null
    return if (value.startsWith("http://", ignoreCase = true)) {
        "https://" + value.substring(7)
    } else {
        value
    }
}
