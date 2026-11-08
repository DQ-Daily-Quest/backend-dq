package com.dq.backend.domain.room.controller

import com.dq.backend.domain.room.dto.CreateRoomRequest
import com.dq.backend.domain.room.dto.CreateRoomResponse
import com.dq.backend.domain.room.dto.InviteCodeResponse
import com.dq.backend.domain.room.dto.JoinByInviteCodeRequest
import com.dq.backend.domain.room.dto.RoomDetailResponse
import com.dq.backend.domain.room.dto.RoomMemberResponse
import com.dq.backend.domain.room.dto.RoomSummaryResponse
import com.dq.backend.domain.room.dto.TransferOwnerRequest
import com.dq.backend.domain.room.dto.UpdateRoomRequest
import com.dq.backend.domain.room.service.RoomService
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

@Tag(name = "Room", description = "룸 (api.md 4장)")
@RestController
@RequestMapping("/api/v1/rooms")
class RoomController(private val roomService: RoomService) {

    @Operation(summary = "공개 룸 목록 (홈 Public 탭). 기본 정렬: 인기 점수")
    @GetMapping
    fun listPublic(
        @CurrentUser userId: Long,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false, defaultValue = "popularity,desc") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<RoomSummaryResponse> = roomService.listPublic(userId, keyword, sort, page, size)

    @Operation(summary = "내가 속한 룸 목록 (홈 Private 탭)")
    @GetMapping("/mine")
    fun listMine(@CurrentUser userId: Long): List<RoomSummaryResponse> = roomService.listMine(userId)

    @Operation(summary = "룸 생성 (생성자는 방장, 비공개면 초대코드 발급)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@CurrentUser userId: Long, @Valid @RequestBody request: CreateRoomRequest): CreateRoomResponse =
        roomService.create(userId, request)

    @Operation(summary = "초대코드로 비공개 룸 참여")
    @PostMapping("/join")
    fun joinByInviteCode(@CurrentUser userId: Long, @Valid @RequestBody request: JoinByInviteCodeRequest): RoomDetailResponse =
        roomService.joinByInviteCode(userId, request.inviteCode)

    @Operation(summary = "룸 상세 (비공개 룸은 멤버만)")
    @GetMapping("/{roomId}")
    fun detail(@CurrentUser userId: Long, @PathVariable roomId: Long): RoomDetailResponse =
        roomService.getDetail(userId, roomId)

    @Operation(summary = "룸 수정 (방장)")
    @PatchMapping("/{roomId}")
    fun update(@CurrentUser userId: Long, @PathVariable roomId: Long, @Valid @RequestBody request: UpdateRoomRequest): RoomDetailResponse =
        roomService.update(userId, roomId, request)

    @Operation(summary = "룸 삭제 (방장)")
    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@CurrentUser userId: Long, @PathVariable roomId: Long) = roomService.delete(userId, roomId)

    @Operation(summary = "공개 룸 참여")
    @PostMapping("/{roomId}/join")
    fun joinPublic(@CurrentUser userId: Long, @PathVariable roomId: Long): RoomDetailResponse =
        roomService.joinPublic(userId, roomId)

    @Operation(summary = "룸 나가기 (방장은 위임 후 가능, 마지막 멤버면 룸 삭제)")
    @DeleteMapping("/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(@CurrentUser userId: Long, @PathVariable roomId: Long) = roomService.leave(userId, roomId)

    @Operation(summary = "멤버 목록 (아바타 포함)")
    @GetMapping("/{roomId}/members")
    fun members(@CurrentUser userId: Long, @PathVariable roomId: Long): List<RoomMemberResponse> =
        roomService.listMembers(userId, roomId)

    @Operation(summary = "초대코드 재발급 (방장, 비공개 룸만, 기존 코드 즉시 무효)")
    @PostMapping("/{roomId}/invite-code")
    fun regenerateInviteCode(@CurrentUser userId: Long, @PathVariable roomId: Long): InviteCodeResponse =
        roomService.regenerateInviteCode(userId, roomId)

    @Operation(summary = "멤버 내보내기 (방장)")
    @DeleteMapping("/{roomId}/members/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun kick(@CurrentUser userId: Long, @PathVariable roomId: Long, @PathVariable targetUserId: Long) =
        roomService.kick(userId, roomId, targetUserId)

    @Operation(summary = "방장 위임 (방장). 기존 방장은 MEMBER로 남음")
    @PostMapping("/{roomId}/transfer-owner")
    fun transferOwner(@CurrentUser userId: Long, @PathVariable roomId: Long, @Valid @RequestBody request: TransferOwnerRequest): RoomDetailResponse =
        roomService.transferOwner(userId, roomId, request.userId)
}
