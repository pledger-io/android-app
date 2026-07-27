package com.pledgerio.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtPayloadTest {

    @Test
    fun `detects PRE_VERIFICATION_USER role`() {
        val token = jwt(rolesJson = """["PRE_VERIFICATION_USER"]""")
        assertTrue(JwtPayload.requiresMfaVerification(token))
    }

    @Test
    fun `detects ROLE_PRE_VERIFICATION_USER role`() {
        val token = jwt(rolesJson = """["ROLE_PRE_VERIFICATION_USER"]""")
        assertTrue(JwtPayload.requiresMfaVerification(token))
    }

    @Test
    fun `full roles are not MFA required`() {
        val token = jwt(rolesJson = """["admin","accountant"]""")
        assertFalse(JwtPayload.requiresMfaVerification(token))
    }

    @Test
    fun `missing roles are not MFA required`() {
        val token = jwt(payloadJson = """{"sub":"alice"}""")
        assertFalse(JwtPayload.requiresMfaVerification(token))
    }

    @Test
    fun `malformed token is not MFA required`() {
        assertFalse(JwtPayload.requiresMfaVerification("not-a-jwt"))
    }

    private fun jwt(rolesJson: String? = null, payloadJson: String? = null): String {
        val payload = payloadJson ?: """{"roles":$rolesJson}"""
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "header.$encoded.signature"
    }
}
