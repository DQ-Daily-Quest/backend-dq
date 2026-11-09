package com.dq.backend.domain.proof

import com.dq.backend.domain.mission.entity.MissionTemplate
import com.dq.backend.domain.mission.repository.MissionTemplateRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ProofFlowTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var templateRepository: MissionTemplateRepository

    companion object { private val seq = AtomicInteger() }

    @BeforeTest
    fun seedTemplates() {
        if (templateRepository.count() == 0L) {
            templateRepository.saveAll(listOf("하늘 사진 찍기", "물 마시기").map { MissionTemplate(content = it) })
        }
    }

    private val json = MediaType.APPLICATION_JSON

    private fun user(prefix: String): String {
        val n = seq.incrementAndGet()
        val email = "$prefix$n@proof.test"
        mockMvc.post("/api/v1/auth/signup") {
            contentType = json
            content = """{"email":"$email","password":"P@ssw0rd!","nickname":"p$prefix$n"}"""
        }.andExpect { status { isCreated() } }
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = json
            content = """{"email":"$email","password":"P@ssw0rd!"}"""
        }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("accessToken").asString()
    }

    private fun parse(body: String): JsonNode = objectMapper.readTree(body)

    private fun room(token: String): Long = parse(
        mockMvc.post("/api/v1/rooms") {
            header("Authorization", "Bearer $token"); contentType = json
            content = """{"name":"Proof Room","isPublic":true}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
    ).get("roomId").asLong()

    private fun join(token: String, roomId: Long) =
        mockMvc.post("/api/v1/rooms/$roomId/join") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }

    private fun memberMission(token: String, roomId: Long, coins: Int = 100, dueAt: Instant? = null): Long {
        val due = dueAt?.let { ""","dueAt":"$it"""" } ?: ""
        return parse(
            mockMvc.post("/api/v1/rooms/$roomId/missions") {
                header("Authorization", "Bearer $token"); contentType = json
                content = """{"content":"테스트 미션","rewardCoins":$coins$due}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("missionId").asLong()
    }

    private fun submitText(token: String, missionId: Long, text: String = "했어요") =
        mockMvc.post("/api/v1/missions/$missionId/proofs") {
            header("Authorization", "Bearer $token"); contentType = json
            content = """{"mediaType":"TEXT","text":"$text"}"""
        }

    private fun me(token: String): JsonNode =
        parse(mockMvc.get("/api/v1/users/me") { header("Authorization", "Bearer $token") }.andReturn().response.contentAsString)

    @Test
    fun `혼자인 룸에서 인증을 올리면 즉시 승인되고 코인이 지급된다`() {
        val solo = user("solo")
        val roomId = room(solo)
        val missionId = memberMission(solo, roomId, coins = 100)
        submitText(solo, missionId).andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.approval.required") { value(1) }
            jsonPath("$.user.nickname") { exists() }
        }
        val me = me(solo)
        assertEquals(100, me.get("coins").asInt())
        assertEquals(1, me.get("stats").get("completedMissionCount").asInt())
        // 같은 미션에 다시 제출 불가
        submitText(solo, missionId).andExpect { status { isConflict() }; jsonPath("$.code") { value("PROOF_ALREADY_SUBMITTED") } }
    }

    @Test
    fun `멤버 승인으로 확정되면 보상이 한 번 지급되고 이후 투표는 거부된다`() {
        val owner = user("own"); val a = user("a"); val b = user("b")
        val roomId = room(owner); join(a, roomId); join(b, roomId)
        val missionId = memberMission(owner, roomId, coins = 50)
        val proofId = parse(
            submitText(a, missionId).andExpect {
                status { isCreated() }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.approval.required") { value(1) } // ceil((3-1)/2) = 1
            }.andReturn().response.contentAsString,
        ).get("proofId").asLong()

        // 본인 투표 금지
        mockMvc.post("/api/v1/proofs/$proofId/approve") { header("Authorization", "Bearer $a") }
            .andExpect { status { isForbidden() }; jsonPath("$.code") { value("CANNOT_VOTE_OWN_PROOF") } }
        // 미션 응답에 myProof / proofCount 반영
        mockMvc.get("/api/v1/rooms/$roomId/missions") { header("Authorization", "Bearer $a"); param("type", "MEMBER") }
            .andExpect { jsonPath("$.content[0].myProof.status") { value("PENDING") }; jsonPath("$.content[0].proofCount") { value(1) } }
        // 승인 대기 목록: b 에게 보이고 a(본인)에게는 안 보임
        mockMvc.get("/api/v1/rooms/$roomId/proofs/pending") { header("Authorization", "Bearer $b") }
            .andExpect { jsonPath("$.totalElements") { value(1) } }
        mockMvc.get("/api/v1/rooms/$roomId/proofs/pending") { header("Authorization", "Bearer $a") }
            .andExpect { jsonPath("$.totalElements") { value(0) } }
        mockMvc.get("/api/v1/rooms/mine") { header("Authorization", "Bearer $b") }
            .andExpect { jsonPath("$[0].pendingProofCount") { value(1) } }

        // owner 승인 → 확정 + 코인 지급
        mockMvc.post("/api/v1/proofs/$proofId/approve") { header("Authorization", "Bearer $owner") }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("APPROVED") }
                jsonPath("$.rewardGranted.type") { value("COIN") }
                jsonPath("$.rewardGranted.amount") { value(50) }
            }
        assertEquals(50, me(a).get("coins").asInt())
        // 확정 후 투표 불가, 대기 목록에서 사라짐
        mockMvc.post("/api/v1/proofs/$proofId/approve") { header("Authorization", "Bearer $b") }
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("PROOF_ALREADY_FINALIZED") } }
        mockMvc.get("/api/v1/rooms/$roomId/proofs/pending") { header("Authorization", "Bearer $b") }
            .andExpect { jsonPath("$.totalElements") { value(0) } }
        // 삭제도 불가 (확정됨)
        mockMvc.delete("/api/v1/proofs/$proofId") { header("Authorization", "Bearer $a") }
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `거절이 기준에 도달하면 REJECTED 되고 재제출할 수 있으며 투표는 변경 가능하다`() {
        val owner = user("own"); val a = user("a"); val b = user("b"); val c = user("c")
        val roomId = room(owner); join(a, roomId); join(b, roomId); join(c, roomId)
        val missionId = memberMission(owner, roomId)
        val proofId = parse(submitText(a, missionId).andReturn().response.contentAsString).get("proofId").asLong()
        // 4명 → required = ceil(3/2) = 2
        mockMvc.post("/api/v1/proofs/$proofId/reject") { header("Authorization", "Bearer $b") }
            .andExpect { jsonPath("$.status") { value("PENDING") }; jsonPath("$.approval.rejected") { value(1) }; jsonPath("$.approval.required") { value(2) } }
        // b 가 마음을 바꿔 승인 → 거절 0, 승인 1
        mockMvc.post("/api/v1/proofs/$proofId/approve") { header("Authorization", "Bearer $b") }
            .andExpect { jsonPath("$.approval.approved") { value(1) }; jsonPath("$.approval.rejected") { value(0) } }
        // c, owner 거절 → REJECTED
        mockMvc.post("/api/v1/proofs/$proofId/reject") { header("Authorization", "Bearer $c") }.andExpect { jsonPath("$.status") { value("PENDING") } }
        mockMvc.post("/api/v1/proofs/$proofId/reject") { header("Authorization", "Bearer $owner") }
            .andExpect { jsonPath("$.status") { value("REJECTED") }; jsonPath("$.rewardGranted") { value(null) } }
        assertEquals(0, me(a).get("coins").asInt())
        // 거절된 뒤 재제출 가능
        submitText(a, missionId, "다시 했어요").andExpect { status { isCreated() }; jsonPath("$.status") { value("PENDING") } }
    }

    @Test
    fun `피드는 최근 인증을 최신순으로 주고 상세를 보면 viewed 가 true 가 된다`() {
        val owner = user("own"); val a = user("a")
        val roomId = room(owner); join(a, roomId)
        val m1 = memberMission(owner, roomId); val m2 = memberMission(owner, roomId)
        val p1 = parse(submitText(a, m1).andReturn().response.contentAsString).get("proofId").asLong()
        val p2 = parse(submitText(a, m2).andReturn().response.contentAsString).get("proofId").asLong()

        val feed = parse(
            mockMvc.get("/api/v1/rooms/$roomId/feed") { header("Authorization", "Bearer $owner") }
                .andExpect { status { isOk() }; jsonPath("$.totalElements") { value(2) } }.andReturn().response.contentAsString,
        ).get("content")
        assertEquals(p2, feed[0].get("proofId").asLong())
        assertEquals(false, feed[0].get("viewed").asBoolean())

        mockMvc.get("/api/v1/proofs/$p1") { header("Authorization", "Bearer $owner") }
            .andExpect {
                status { isOk() }
                jsonPath("$.mission.missionId") { value(m1) }
                jsonPath("$.room.roomId") { value(roomId) }
                jsonPath("$.approval.voters") { isArray() }
            }
        val after = parse(mockMvc.get("/api/v1/rooms/$roomId/feed") { header("Authorization", "Bearer $owner") }.andReturn().response.contentAsString).get("content")
        assertEquals(true, after.first { it.get("proofId").asLong() == p1 }.get("viewed").asBoolean())
        // 비멤버는 피드 403
        mockMvc.get("/api/v1/rooms/$roomId/feed") { header("Authorization", "Bearer ${user("x")}") }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `오늘의 APP 미션에 인증하면 멤버 목록과 내 룸 목록의 todayMissionDone 이 true 가 된다`() {
        val owner = user("own"); val a = user("a")
        val roomId = room(owner); join(a, roomId)
        val todayId = parse(
            mockMvc.get("/api/v1/rooms/$roomId/missions/today") { header("Authorization", "Bearer $a") }.andReturn().response.contentAsString,
        ).get("missionId").asLong()
        submitText(a, todayId, "하늘 찍음").andExpect { status { isCreated() } }

        val members = parse(mockMvc.get("/api/v1/rooms/$roomId/members") { header("Authorization", "Bearer $owner") }.andReturn().response.contentAsString)
        val byRole = members.associateBy { it.get("role").asString() }
        assertEquals(true, byRole.getValue("MEMBER").get("todayMissionDone").asBoolean())
        assertEquals(false, byRole.getValue("OWNER").get("todayMissionDone").asBoolean())
        mockMvc.get("/api/v1/rooms/mine") { header("Authorization", "Bearer $a") }
            .andExpect { jsonPath("$[0].todayMissionDone") { value(true) } }
    }

    @Test
    fun `대기 중인 본인 인증은 삭제할 수 있고 타인은 삭제할 수 없다`() {
        val owner = user("own"); val a = user("a")
        val roomId = room(owner); join(a, roomId)
        val missionId = memberMission(owner, roomId)
        val proofId = parse(submitText(a, missionId).andReturn().response.contentAsString).get("proofId").asLong()
        mockMvc.delete("/api/v1/proofs/$proofId") { header("Authorization", "Bearer $owner") }.andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/proofs/$proofId") { header("Authorization", "Bearer $a") }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/proofs/$proofId") { header("Authorization", "Bearer $a") }
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("PROOF_NOT_FOUND") } }
        // 삭제 후 다시 제출 가능
        submitText(a, missionId).andExpect { status { isCreated() } }
    }

    @Test
    fun `리액션은 화이트리스트만 허용되고 추가·취소된다`() {
        val owner = user("own"); val a = user("a")
        val roomId = room(owner); join(a, roomId)
        val missionId = memberMission(owner, roomId)
        val proofId = parse(submitText(a, missionId).andReturn().response.contentAsString).get("proofId").asLong()
        mockMvc.post("/api/v1/proofs/$proofId/reactions") { header("Authorization", "Bearer $owner"); contentType = json; content = """{"emoji":"🍕"}""" }
            .andExpect { status { isBadRequest() }; jsonPath("$.code") { value("INVALID_EMOJI") } }
        mockMvc.post("/api/v1/proofs/$proofId/reactions") { header("Authorization", "Bearer $owner"); contentType = json; content = """{"emoji":"❤️"}""" }
            .andExpect { status { isOk() }; jsonPath("$[0].emoji") { value("❤️") }; jsonPath("$[0].count") { value(1) }; jsonPath("$[0].reacted") { value(true) } }
        mockMvc.post("/api/v1/proofs/$proofId/reactions") { header("Authorization", "Bearer $owner"); contentType = json; content = """{"emoji":"❤️"}""" }
            .andExpect { jsonPath("$[0].count") { value(1) } } // 중복 1회만
        mockMvc.delete("/api/v1/proofs/$proofId/reactions") { header("Authorization", "Bearer $owner"); contentType = json; content = """{"emoji":"❤️"}""" }
            .andExpect { status { isOk() }; jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `마감된 미션에는 인증을 올릴 수 없다`() {
        val owner = user("own")
        val roomId = room(owner)
        val missionId = memberMission(owner, roomId, dueAt = Instant.now().plusMillis(1200))
        Thread.sleep(1500)
        submitText(owner, missionId).andExpect { status { isConflict() }; jsonPath("$.code") { value("MISSION_EXPIRED") } }
    }

    @Test
    fun `이미지를 업로드하면 썸네일과 크기를 돌려주고 파일이 서빙된다`() {
        val owner = user("own")
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        val body = mockMvc.multipart(HttpMethod.POST, "/api/v1/uploads") {
            file(MockMultipartFile("file", "sky.png", "image/png", png))
            param("purpose", "PROOF")
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.mediaType") { value("IMAGE") }
            jsonPath("$.width") { value(800) }
            jsonPath("$.height") { value(600) }
            jsonPath("$.thumbnailUrl") { exists() }
        }.andReturn().response.contentAsString
        val url = parse(body).get("url").asString()
        assertTrue(url.contains("/files/proofs/"))
        val path = url.substringAfter("http://localhost:8080")
        mockMvc.get(path).andExpect { status { isOk() } } // 공개 경로, 인증 불필요

        mockMvc.multipart(HttpMethod.POST, "/api/v1/uploads") {
            file(MockMultipartFile("file", "a.gif", "image/gif", byteArrayOf(1, 2, 3)))
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isBadRequest() }; jsonPath("$.code") { value("UNSUPPORTED_MEDIA_TYPE") } }
    }
}
