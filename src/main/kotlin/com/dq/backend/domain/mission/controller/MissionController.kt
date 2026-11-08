package com.dq.backend.domain.mission.controller

import com.dq.backend.domain.mission.dto.CreateMemberMissionRequest
import com.dq.backend.domain.mission.dto.MissionDetailResponse
import com.dq.backend.domain.mission.dto.MissionResponse
import com.dq.backend.domain.mission.dto.MyMissionsByRoomResponse
import com.dq.backend.domain.mission.dto.UpdateMemberMissionRequest
import com.dq.backend.domain.mission.service.MissionService
import com.dq.backend.global.common.PageResponse
import com.dq.backend.global.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Mission", description = "미션 (api.md 5장)")
@RestController
@RequestMapping("/api/v1")
class MissionController(private val missionService: MissionService) {

    @Operation(summary = "룸 미션 목록. status=ACTIVE(기본)|EXPIRED|ALL, type=APP|MEMBER|생략")
    @GetMapping("/rooms/{roomId}/missions")
    fun listByRoom(
        @CurrentUser userId: Long,
        @PathVariable roomId: Long,
        @RequestParam(required = false, defaultValue = "ACTIVE") status: String,
        @RequestParam(required = false) type: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<MissionResponse> = missionService.listByRoom(userId, roomId, status, type, page, size)

    @Operation(summary = "오늘의 APP 미션 (없으면 이 시점에 생성)")
    @GetMapping("/rooms/{roomId}/missions/today")
    fun today(@CurrentUser userId: Long, @PathVariable roomId: Long): MissionResponse = missionService.today(userId, roomId)

    @Operation(summary = "멤버 미션 생성 (코인 보상, 기본 마감 7일)")
    @PostMapping("/rooms/{roomId}/missions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@CurrentUser userId: Long, @PathVariable roomId: Long, @Valid @RequestBody request: CreateMemberMissionRequest): MissionResponse =
        missionService.createMemberMission(userId, roomId, request)

    @Operation(summary = "내 룸 전체의 진행 중 미션 (하단 탭 Missions)")
    @GetMapping("/missions/mine")
    fun mine(@CurrentUser userId: Long): List<MyMissionsByRoomResponse> = missionService.mine(userId)

    @Operation(summary = "미션 상세 (인증 목록 포함)")
    @GetMapping("/missions/{missionId}")
    fun detail(@CurrentUser userId: Long, @PathVariable missionId: Long): MissionDetailResponse =
        missionService.detail(userId, missionId)

    @Operation(summary = "멤버 미션 수정 (작성자, 인증 제출 전까지)")
    @PatchMapping("/missions/{missionId}")
    fun update(@CurrentUser userId: Long, @PathVariable missionId: Long, @Valid @RequestBody request: UpdateMemberMissionRequest): MissionResponse =
        missionService.update(userId, missionId, request)

    @Operation(summary = "멤버 미션 삭제 (작성자 또는 방장, 인증 제출 전까지)")
    @DeleteMapping("/missions/{missionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@CurrentUser userId: Long, @PathVariable missionId: Long) = missionService.delete(userId, missionId)
}
