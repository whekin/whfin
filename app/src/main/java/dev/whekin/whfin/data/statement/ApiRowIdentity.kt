package dev.whekin.whfin.data.statement

open class ApiRowIdentity(private val MOBILE: String, private val SUFFIX: String) {
    fun mobileId(raw: String): String = MOBILE + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
    fun isMobileId(id: String?) = id?.startsWith(MOBILE) == true
    fun isMobileOnly(key: String?) = key?.contains("|id|$MOBILE") == true
    fun hasMobile(key: String?, id: String): Boolean = key?.endsWith("|id|$id") == true || key?.endsWith(SUFFIX + id.removePrefix(MOBILE)) == true
    fun hasFile(key: String?, fileKey: String): Boolean = key == fileKey || key?.startsWith(fileKey + SUFFIX) == true
    fun join(fileKey: String, mobileId: String): String = fileKey + SUFFIX + mobileId.removePrefix(MOBILE)
    fun mobileFromKey(key: String): String? = when {
        isMobileOnly(key) -> key.substringAfterLast("|id|")
        SUFFIX in key -> MOBILE + key.substringAfterLast(SUFFIX)
        else -> null
    }
}
