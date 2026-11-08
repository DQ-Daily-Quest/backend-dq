package com.dq.backend.domain.mission

import com.dq.backend.domain.mission.repository.MissionTemplateRepository
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
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class MissionFlowTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var templateRepository: MissionTemplateRepository

    companion object { private val seq = AtomicInteger() }

    /** H2 테스트 DB에는 Flyway 시드가 없으므로 템플릿을 직접 넣는다 */
    @BeforeTest
    fun seedTemplates() {
        if (templateRepository.count() == 0L) {
            templateRepository.saveAll(
                listOf("하늘 사진 찍기", "물 마시기", "산책하기").map { com.dq.backend.domain.mission.entity.MissionTemplate(content = it) },
            )
        }
    }

    private fun user(prefix: String): String {
        val n = seq.incrementAndGet()
        val email = "$prefix$n@mission.test"
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"P@ssw0rd!","nickname":"m$prefix$n"}"""
        }.andExpect { status { isCreated() } }
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"P@ssw0rd!"}"""
        }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("accessToken").asString()
    }

    private fun room(token: String, isPublic: Boolean = true): Long {
        val body = mockMvc.post("/api/v1/rooms") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Mission Room","isPublic":$isPublic}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("roomId").asLong()
    }

    private fun json(body: String): JsonNode = objectMapper.readTree(body)

    @Test
    fun `오늘의 APP 미션은 처음 조회할 때 생성되고 다시 조회해도 같은 미션이다`() {
        val owner = user("own")
        val roomId = room(owner)
        val first = json(
            mockMvc.get("/api/v1/rooms/$roomId/missions/today") { header("Authorization", "Bearer $owner") }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.type") { value("APP") }
                    jsonPath("$.reward.type") { value("ITEM") }
                    jsonPath("$.createdBy") { value(null) }
                    jsonPath("$.date") { exists() }
                    jsonPath("$.myProof") { value(null) }
                    jsonPath("$.proofCount") { value(0) }
                }.andReturn().response.contentAsString,
        )
        val second = json(
            mockMvc.get("/api/v1/rooms/$roomId/missions/today") { header("Authorization", "Bearer $owner") }
                .andReturn().response.contentAsString,
        )
        assertEquals(first.get("missionId").asLong(), second.get("missionId").asLong())
        assertTrue(Instant.parse(first.get("dueAt").asString()).isAfter(Instant.now()))

        // 룸 상세에도 같은 미션이 요약으로 들어간다
        mockMvc.get("/api/v1/rooms/$roomId") { header("Authorization", "Bearer $owner") }
            .andExpect { jsonPath("$.todayMission.missionId") { value(first.get("missionId").asLong()) } }

        // 비멤버는 403
        mockMvc.get("/api/v1/rooms/$roomId/missions/today") { header("Authorization", "Bearer ${user("x")}") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `멤버 미션을 만들고 수정하고 삭제한다`() {
        val owner = user("own")
        val member = user("mem")
        val roomId = room(owner)
        mockMvc.post("/api/v1/rooms/$roomId/join") { header("Authorization", "Bearer $member") }.andExpect { status { isOk() } }

        // 10단위 아님 → 400
        mockMvc.post("/api/v1/rooms/$roomId/missions") {
            header("Authorization", "Bearer $member")
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"10분 산책","rewardCoins":55}"""
        }.andExpect { status { isBadRequest() } }

        val created = json(
            mockMvc.post("/api/v1/rooms/$roomId/missions") {
                header("Authorization", "Bearer $member")
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"10분 산책","rewardCoins":100}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.type") { value("MEMBER") }
                jsonPath("$.reward.type") { value("COIN") }
                jsonPath("$.reward.amount") { value(100) }
                jsonPath("$.createdBy.nickname") { exists() }
                jsonPath("$.date") { value(null) }
            }.andReturn().response.contentAsString,
        )
        val missionId = created.get("missionId").asLong()
        // 기본 마감: 약 7일 뒤
        val due = Instant.parse(created.get("dueAt").asString())
        assertTrue(due.isAfter(Instant.now().plusSeconds(6 * 86400)))

        // 작성자가 아닌 방장은 수정 불가, 작성자는 가능
        mockMvc.patch("/api/v1/missions/$missionId") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"20분 산책"}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.patch("/api/v1/missions/$missionId") {
            header("Authorization", "Bearer $member")
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"20분 산책"}"""
        }.andExpect { status { isOk() }; jsonPath("$.content") { value("20분 산책") } }

        // 상세
        mockMvc.get("/api/v1/missions/$missionId") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isOk() }; jsonPath("$.proofs") { isArray() } }

        // 방장은 삭제 가능
        mockMvc.delete("/api/v1/missions/$missionId") { header("Authorization", "Bearer $owner") }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/missions/$missionId") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("MISSION_NOT_FOUND") } }
    }

    @Test
    fun `APP 미션은 수정·삭제할 수 없다`() {
        val owner = user("own")
        val roomId = room(owner)
        val appId = json(
            mockMvc.get("/api/v1/rooms/$roomId/missions/today") { header("Authorization", "Bearer $owner") }.andReturn().response.contentAsString,
        ).get("missionId").asLong()
        mockMvc.patch("/api/v1/missions/$appId") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"바꾸기"}"""
        }.andExpect { status { isConflict() }; jsonPath("$.code") { value("APP_MISSION_IMMUTABLE") } }
        mockMvc.delete("/api/v1/missions/$appId") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("APP_MISSION_IMMUTABLE") } }
    }

    @Test
    fun `룸 미션 목록은 오늘의 APP 미션을 포함하고 type과 status로 필터된다`() {
        val owner = user("own")
        val roomId = room(owner)
        mockMvc.post("/api/v1/rooms/$roomId/missions") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"멤버 미션","rewardCoins":50}"""
        }.andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/rooms/$roomId/missions") { header("Authorization", "Bearer $owner") }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.content[0].type") { value("APP") } // 오늘 마감이 먼저
            }
        mockMvc.get("/api/v1/rooms/$roomId/missions") { header("Authorization", "Bearer $owner"); param("type", "MEMBER") }
            .andExpect { jsonPath("$.totalElements") { value(1) }; jsonPath("$.content[0].type") { value("MEMBER") } }
        mockMvc.get("/api/v1/rooms/$roomId/missions") { header("Authorization", "Bearer $owner"); param("status", "EXPIRED") }
            .andExpect { jsonPath("$.totalElements") { value(0) } }
        mockMvc.get("/api/v1/rooms/$roomId/missions") { header("Authorization", "Bearer $owner"); param("status", "WRONG") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `내 미션 모아보기는 룸별로 묶여서 온다`() {
        val me = user("me")
        val r1 = room(me)
        val r2 = room(me)
        mockMvc.post("/api/v1/rooms/$r2/missions") {
            header("Authorization", "Bearer $me")
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"방2 멤버 미션","rewardCoins":30}"""
        }.andExpect { status { isCreated() } }

        val body = mockMvc.get("/api/v1/missions/mine") { header("Authorization", "Bearer $me") }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        val groups = json(body)
        assertEquals(2, groups.size())
        val byRoom = groups.associateBy { it.get("room").get("roomId").asLong() }
        assertEquals(1, byRoom.getValue(r1).get("missions").size()) // APP 만
        assertEquals(2, byRoom.getValue(r2).get("missions").size()) // APP + MEMBER
    }
}
