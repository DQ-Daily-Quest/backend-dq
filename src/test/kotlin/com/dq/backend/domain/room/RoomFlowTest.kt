package com.dq.backend.domain.room

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
class RoomFlowTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    companion object {
        private val seq = java.util.concurrent.atomic.AtomicInteger()
    }

    /** 가입 + 로그인 → access token */
    private fun user(prefix: String): String {
        val n = seq.incrementAndGet()
        val email = "$prefix$n@room.test"
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"P@ssw0rd!","nickname":"$prefix$n"}"""
        }.andExpect { status { isCreated() } }
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"P@ssw0rd!"}"""
        }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("accessToken").asString()
    }

    private fun createRoom(token: String, name: String, isPublic: Boolean, maxMembers: Int = 10): JsonNode {
        val body = mockMvc.post("/api/v1/rooms") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"$name","description":"desc","isPublic":$isPublic,"maxMembers":$maxMembers}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body)
    }

    private fun me(token: String): Long =
        objectMapper.readTree(
            mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer $token") }.andReturn().response.contentAsString,
        ).get("userId").asLong()

    @Test
    fun `공개 룸을 만들면 생성자가 방장이고 초대코드는 없다`() {
        val owner = user("own")
        val room = createRoom(owner, "Public Room", true)
        assertEquals(true, room.get("inviteCode").isNull)
        val roomId = room.get("roomId").asLong()
        mockMvc.get("/api/v1/rooms/$roomId") { header("Authorization", "Bearer $owner") }
            .andExpect {
                status { isOk() }
                jsonPath("$.myRole") { value("OWNER") }
                jsonPath("$.memberCount") { value(1) }
                jsonPath("$.owner.userId") { value(me(owner)) }
            }
    }

    @Test
    fun `비공개 룸은 초대코드로만 참여하고 비멤버는 상세를 볼 수 없다`() {
        val owner = user("own")
        val guest = user("gst")
        val room = createRoom(owner, "Secret", false)
        val roomId = room.get("roomId").asLong()
        val code = room.get("inviteCode").asString()
        assertEquals(6, code.length)

        mockMvc.get("/api/v1/rooms/$roomId") { header("Authorization", "Bearer $guest") }
            .andExpect { status { isForbidden() } }
        mockMvc.post("/api/v1/rooms/$roomId/join") { header("Authorization", "Bearer $guest") }
            .andExpect { status { isForbidden() }; jsonPath("$.code") { value("ROOM_NOT_PUBLIC") } }
        mockMvc.post("/api/v1/rooms/join") {
            header("Authorization", "Bearer $guest")
            contentType = MediaType.APPLICATION_JSON
            content = """{"inviteCode":"ZZZZZZ"}"""
        }.andExpect { status { isNotFound() }; jsonPath("$.code") { value("INVALID_INVITE_CODE") } }
        mockMvc.post("/api/v1/rooms/join") {
            header("Authorization", "Bearer $guest")
            contentType = MediaType.APPLICATION_JSON
            content = """{"inviteCode":"$code"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.myRole") { value("MEMBER") }
            jsonPath("$.memberCount") { value(2) }
            jsonPath("$.inviteCode") { value(code) }
        }
        mockMvc.post("/api/v1/rooms/join") {
            header("Authorization", "Bearer $guest")
            contentType = MediaType.APPLICATION_JSON
            content = """{"inviteCode":"$code"}"""
        }.andExpect { status { isConflict() }; jsonPath("$.code") { value("ALREADY_JOINED") } }
    }

    @Test
    fun `정원이 차면 참여할 수 없고 maxMembers를 현재 인원보다 작게 줄일 수 없다`() {
        val owner = user("own")
        val roomId = createRoom(owner, "Tiny", true, maxMembers = 2).get("roomId").asLong()
        val a = user("a")
        val b = user("b")
        mockMvc.post("/api/v1/rooms/$roomId/join") { header("Authorization", "Bearer $a") }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/rooms/$roomId/join") { header("Authorization", "Bearer $b") }
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("ROOM_FULL") } }
        mockMvc.patch("/api/v1/rooms/$roomId") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"maxMembers":1}"""
        }.andExpect { status { isBadRequest() } } // @Min(2)
        mockMvc.patch("/api/v1/rooms/$roomId") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"maxMembers":3}"""
        }.andExpect { status { isOk() }; jsonPath("$.maxMembers") { value(3) } }
        mockMvc.post("/api/v1/rooms/$roomId/join") { header("Authorization", "Bearer $b" ) }.andExpect { status { isOk() } }
        mockMvc.patch("/api/v1/rooms/$roomId") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"maxMembers":2}"""
        }.andExpect { status { isConflict() }; jsonPath("$.code") { value("MAX_MEMBERS_BELOW_CURRENT") } }
    }

    @Test
    fun `공개 룸 목록은 멤버 수 순으로 정렬되고 상위 룸에 isHot이 붙으며 keyword로 검색된다`() {
        val owner = user("own")
        val big = createRoom(owner, "Alpha Crew", true).get("roomId").asLong()
        createRoom(owner, "Beta Solo", true)
        createRoom(owner, "Hidden", false)
        repeat(2) { mockMvc.post("/api/v1/rooms/$big/join") { header("Authorization", "Bearer ${user("m")}") }.andExpect { status { isOk() } } }

        val viewer = user("v")
        val body = mockMvc.get("/api/v1/rooms") { header("Authorization", "Bearer $viewer"); param("keyword", "a") }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        val content = objectMapper.readTree(body).get("content")
        assertEquals("Alpha Crew", content[0].get("name").asString())
        assertEquals(3, content[0].get("memberCount").asInt())
        assertEquals(true, content[0].get("isHot").asBoolean())
        assertEquals(false, content[0].get("joined").asBoolean())
        content.forEach { assertEquals(true, it.get("isPublic").asBoolean()) }

        mockMvc.get("/api/v1/rooms/mine") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isOk() }; jsonPath("$.length()") { value(3) }; jsonPath("$[0].joined") { value(true) } }
    }

    @Test
    fun `방장은 멤버가 남아 있으면 못 나가고 위임 후 나갈 수 있으며 마지막 멤버가 나가면 룸이 삭제된다`() {
        val owner = user("own")
        val member = user("mem")
        val roomId = createRoom(owner, "Handover", true).get("roomId").asLong()
        val memberId = me(member)
        mockMvc.post("/api/v1/rooms/$roomId/join") { header("Authorization", "Bearer $member") }.andExpect { status { isOk() } }

        mockMvc.delete("/api/v1/rooms/$roomId/leave") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("OWNER_CANNOT_LEAVE") } }
        mockMvc.post("/api/v1/rooms/$roomId/transfer-owner") {
            header("Authorization", "Bearer $member")
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":$memberId}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.post("/api/v1/rooms/$roomId/transfer-owner") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":$memberId}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.owner.userId") { value(memberId) }
            jsonPath("$.myRole") { value("MEMBER") }
        }
        mockMvc.delete("/api/v1/rooms/$roomId/leave") { header("Authorization", "Bearer $owner") }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/rooms/$roomId/members") { header("Authorization", "Bearer $member") }
            .andExpect { status { isOk() }; jsonPath("$.length()") { value(1) }; jsonPath("$[0].role") { value("OWNER") } }
        mockMvc.delete("/api/v1/rooms/$roomId/leave") { header("Authorization", "Bearer $member") }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/rooms/$roomId") { header("Authorization", "Bearer $member") }
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("ROOM_NOT_FOUND") } }
    }

    @Test
    fun `방장은 멤버를 내보낼 수 있고 자기 자신이나 비멤버는 내보낼 수 없다`() {
        val owner = user("own")
        val member = user("mem")
        val stranger = user("str")
        val roomId = createRoom(owner, "Kick", true).get("roomId").asLong()
        val ownerId = me(owner); val memberId = me(member); val strangerId = me(stranger)
        mockMvc.post("/api/v1/rooms/$roomId/join") { header("Authorization", "Bearer $member") }.andExpect { status { isOk() } }

        mockMvc.delete("/api/v1/rooms/$roomId/members/$ownerId") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("CANNOT_KICK_SELF") } }
        mockMvc.delete("/api/v1/rooms/$roomId/members/$strangerId") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("NOT_A_MEMBER") } }
        mockMvc.delete("/api/v1/rooms/$roomId/members/$ownerId") { header("Authorization", "Bearer $member") }
            .andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/rooms/$roomId/members/$memberId") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/rooms/$roomId/members") { header("Authorization", "Bearer $member") }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer $owner") }
            .andExpect { jsonPath("$.stats.roomCount") { value(1) } }
    }

    @Test
    fun `초대코드 재발급은 비공개 룸에서만 되고 기존 코드는 무효가 된다`() {
        val owner = user("own")
        val guest = user("gst")
        val pub = createRoom(owner, "Pub", true).get("roomId").asLong()
        mockMvc.post("/api/v1/rooms/$pub/invite-code") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("ROOM_NOT_PRIVATE") } }

        val priv = createRoom(owner, "Priv", false)
        val privId = priv.get("roomId").asLong()
        val old = priv.get("inviteCode").asString()
        val body = mockMvc.post("/api/v1/rooms/$privId/invite-code") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        val fresh = objectMapper.readTree(body).get("inviteCode").asString()
        assertNotEquals(old, fresh)
        mockMvc.post("/api/v1/rooms/join") {
            header("Authorization", "Bearer $guest")
            contentType = MediaType.APPLICATION_JSON
            content = """{"inviteCode":"$old"}"""
        }.andExpect { status { isNotFound() } }

        // 비공개 → 공개 전환 시 초대코드 제거, 삭제는 방장만
        mockMvc.patch("/api/v1/rooms/$privId") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"isPublic":true}"""
        }.andExpect { status { isOk() }; jsonPath("$.inviteCode") { value(null) } }
        mockMvc.delete("/api/v1/rooms/$privId") { header("Authorization", "Bearer $guest") }.andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/rooms/$privId") { header("Authorization", "Bearer $owner") }.andExpect { status { isNoContent() } }
    }
}
