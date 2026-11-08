package com.dq.backend.domain.auth

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private fun signup(email: String, nickname: String, password: String = "P@ssw0rd!") =
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password","nickname":"$nickname"}"""
        }

    private fun login(email: String, password: String = "P@ssw0rd!") =
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }

    private fun tokens(email: String, nickname: String): Pair<String, String> {
        signup(email, nickname).andExpect { status { isCreated() } }
        val body = login(email).andExpect { status { isOk() } }.andReturn().response.contentAsString
        val json = objectMapper.readTree(body)
        return json.get("accessToken").asString() to json.get("refreshToken").asString()
    }

    @Test
    fun `회원가입 후 로그인하면 토큰과 사용자 요약을 받는다`() {
        signup("a1@test.com", "정원a1")
            .andExpect {
                status { isCreated() }
                jsonPath("$.userId") { exists() }
                jsonPath("$.email") { value("a1@test.com") }
                jsonPath("$.nickname") { value("정원a1") }
            }
        login("a1@test.com")
            .andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { exists() }
                jsonPath("$.refreshToken") { exists() }
                jsonPath("$.expiresIn") { value(1800) }
                jsonPath("$.user.nickname") { value("정원a1") }
                jsonPath("$.user.coins") { value(0) }
            }
    }

    @Test
    fun `중복 이메일과 닉네임은 409를 반환한다`() {
        signup("dup@test.com", "dupnick").andExpect { status { isCreated() } }
        signup("dup@test.com", "other").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DUPLICATE_EMAIL") }
        }
        signup("other@test.com", "dupnick").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DUPLICATE_NICKNAME") }
        }
    }

    @Test
    fun `유효성 실패는 400 VALIDATION_ERROR를 반환한다`() {
        signup("not-an-email", "x", password = "short").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }
    }

    @Test
    fun `잘못된 비밀번호는 401 INVALID_CREDENTIALS를 반환한다`() {
        signup("pw@test.com", "pwnick").andExpect { status { isCreated() } }
        login("pw@test.com", "wrong-password").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `Access Token으로 내 정보를 조회하고 수정한다`() {
        val (access, _) = tokens("me@test.com", "menick")
        mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer $access") }
            .andExpect {
                status { isOk() }
                jsonPath("$.email") { value("me@test.com") }
                jsonPath("$.stats.roomCount") { value(0) }
                jsonPath("$.avatar.hair") { value(null) }
            }
        mockMvc.patch("/api/v1/users/me") {
            header("Authorization", "Bearer $access")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"새닉네임","avatar":{"hair":12,"top":5}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nickname") { value("새닉네임") }
            jsonPath("$.avatar.hair") { value(12) }
            jsonPath("$.avatar.bottom") { value(null) }
        }
        mockMvc.put("/api/v1/users/me/fcm-token") {
            header("Authorization", "Bearer $access")
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"fcm-abc"}"""
        }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `공개 프로필은 이메일과 코인을 숨긴다`() {
        val (access, _) = tokens("pub@test.com", "pubnick")
        val meBody = mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer $access") }
            .andReturn().response.contentAsString
        val userId = objectMapper.readTree(meBody).get("userId").asLong()
        mockMvc.get("/api/v1/users/$userId") { header("Authorization", "Bearer $access") }
            .andExpect {
                status { isOk() }
                jsonPath("$.nickname") { value("pubnick") }
                jsonPath("$.email") { doesNotExist() }
                jsonPath("$.coins") { doesNotExist() }
            }
    }

    @Test
    fun `위조 토큰은 401을 반환한다`() {
        mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer not.a.jwt") }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `Refresh Token은 회전되고 이전 토큰은 무효가 된다`() {
        val (_, refresh) = tokens("rf@test.com", "rfnick")
        val body = mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$refresh"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val newRefresh = objectMapper.readTree(body).get("refreshToken").asString()
        assertNotEquals(refresh, newRefresh)

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$refresh"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `로그아웃하면 Refresh Token이 폐기된다`() {
        val (access, refresh) = tokens("lo@test.com", "lonick")
        mockMvc.post("/api/v1/auth/logout") { header("Authorization", "Bearer $access") }
            .andExpect { status { isNoContent() } }
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$refresh"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `비밀번호를 변경하면 새 비밀번호로만 로그인되고 Refresh Token은 폐기된다`() {
        val (access, refresh) = tokens("cp@test.com", "cpnick")
        mockMvc.patch("/api/v1/users/me/password") {
            header("Authorization", "Bearer $access")
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"wrong","newPassword":"N3wP@ss!!"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
        mockMvc.patch("/api/v1/users/me/password") {
            header("Authorization", "Bearer $access")
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"P@ssw0rd!","newPassword":"N3wP@ss!!"}"""
        }.andExpect { status { isNoContent() } }

        login("cp@test.com", "P@ssw0rd!").andExpect { status { isUnauthorized() } }
        login("cp@test.com", "N3wP@ss!!").andExpect { status { isOk() } }
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$refresh"}"""
        }.andExpect { status { isUnauthorized() } }
    }
}
