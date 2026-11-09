package com.dq.backend.domain.proof.controller

import com.dq.backend.domain.proof.dto.FeedItemResponse
import com.dq.backend.domain.proof.dto.ProofDetailResponse
import com.dq.backend.domain.proof.dto.ProofResponse
import com.dq.backend.domain.proof.dto.ReactionRequest
import com.dq.backend.domain.proof.dto.ReactionSummary
import com.dq.backend.domain.proof.dto.SubmitProofRequest
import com.dq.backend.domain.proof.dto.UploadResponse
import com.dq.backend.domain.proof.dto.VoteResponse
import com.dq.backend.domain.proof.entity.Vote
import com.dq.backend.domain.proof.service.ProofService
import com.dq.backend.domain.proof.service.UploadService
import com.dq.backend.global.common.PageResponse
import com.dq.backend.global.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@Tag(name = "Proof", description = "인증 제출 (api.md 6장)")
@RestController
@RequestMapping("/api/v1")
class ProofController(
    private val proofService: ProofService,
    private val uploadService: UploadService,
) {

    @Operation(summary = "파일 업로드 (jpg/png/webp 10MB, mp4 50MB). purpose = PROOF | ROOM_THUMBNAIL")
    @PostMapping("/uploads", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @CurrentUser userId: Long,
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) purpose: String?,
    ): UploadResponse = uploadService.upload(file, purpose)

    @Operation(summary = "인증 제출 (사진/텍스트, 혼자인 룸은 즉시 승인)")
    @PostMapping("/missions/{missionId}/proofs")
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(@CurrentUser userId: Long, @PathVariable missionId: Long, @Valid @RequestBody request: SubmitProofRequest): ProofResponse =
        proofService.submit(userId, missionId, request)

    @Operation(summary = "인증 상세 (조회 시 읽음 처리)")
    @GetMapping("/proofs/{proofId}")
    fun detail(@CurrentUser userId: Long, @PathVariable proofId: Long): ProofDetailResponse = proofService.detail(userId, proofId)

    @Operation(summary = "인증 삭제 (본인, PENDING 만)")
    @DeleteMapping("/proofs/{proofId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@CurrentUser userId: Long, @PathVariable proofId: Long) = proofService.delete(userId, proofId)

    @Operation(summary = "승인 (확정 시 보상 지급)")
    @PostMapping("/proofs/{proofId}/approve")
    fun approve(@CurrentUser userId: Long, @PathVariable proofId: Long): VoteResponse = proofService.vote(userId, proofId, Vote.APPROVE)

    @Operation(summary = "거절")
    @PostMapping("/proofs/{proofId}/reject")
    fun reject(@CurrentUser userId: Long, @PathVariable proofId: Long): VoteResponse = proofService.vote(userId, proofId, Vote.REJECT)

    @Operation(summary = "리액션 추가 (❤️ 🔥 👏 😂 😮)")
    @PostMapping("/proofs/{proofId}/reactions")
    fun react(@CurrentUser userId: Long, @PathVariable proofId: Long, @Valid @RequestBody request: ReactionRequest): List<ReactionSummary> =
        proofService.react(userId, proofId, request.emoji)

    @Operation(summary = "리액션 취소")
    @DeleteMapping("/proofs/{proofId}/reactions")
    fun unreact(@CurrentUser userId: Long, @PathVariable proofId: Long, @Valid @RequestBody request: ReactionRequest): List<ReactionSummary> =
        proofService.unreact(userId, proofId, request.emoji)

    @Operation(summary = "룸 피드 (기본 최근 24시간, since 로 조정)")
    @GetMapping("/rooms/{roomId}/feed")
    fun feed(
        @CurrentUser userId: Long,
        @PathVariable roomId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) since: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<FeedItemResponse> = proofService.feed(userId, roomId, since, page, size)

    @Operation(summary = "내가 승인해야 할 인증 목록 (미투표 PENDING, 본인 제외)")
    @GetMapping("/rooms/{roomId}/proofs/pending")
    fun pending(
        @CurrentUser userId: Long,
        @PathVariable roomId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<FeedItemResponse> = proofService.pendingForMe(userId, roomId, page, size)
}
