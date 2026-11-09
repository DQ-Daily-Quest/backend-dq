package com.dq.backend.domain.proof.service

import com.dq.backend.domain.mission.entity.Mission
import com.dq.backend.domain.mission.repository.MissionRepository
import com.dq.backend.domain.proof.dto.ApprovalResponse
import com.dq.backend.domain.proof.dto.FeedItemResponse
import com.dq.backend.domain.proof.dto.FeedMissionBrief
import com.dq.backend.domain.proof.dto.MissionBrief
import com.dq.backend.domain.proof.dto.ProofDetailResponse
import com.dq.backend.domain.proof.dto.ProofResponse
import com.dq.backend.domain.proof.dto.ProofUser
import com.dq.backend.domain.proof.dto.ReactionSummary
import com.dq.backend.domain.proof.dto.RoomRef
import com.dq.backend.domain.proof.dto.SubmitProofRequest
import com.dq.backend.domain.proof.dto.VoteResponse
import com.dq.backend.domain.proof.dto.VoterResponse
import com.dq.backend.domain.proof.entity.MediaType
import com.dq.backend.domain.proof.entity.Proof
import com.dq.backend.domain.proof.entity.ProofReaction
import com.dq.backend.domain.proof.entity.ProofReactionId
import com.dq.backend.domain.proof.entity.ProofStatus
import com.dq.backend.domain.proof.entity.ProofView
import com.dq.backend.domain.proof.entity.ProofViewId
import com.dq.backend.domain.proof.entity.ProofVote
import com.dq.backend.domain.proof.entity.ProofVoteId
import com.dq.backend.domain.proof.entity.Vote
import com.dq.backend.domain.proof.repository.ProofReactionRepository
import com.dq.backend.domain.proof.repository.ProofRepository
import com.dq.backend.domain.proof.repository.ProofViewRepository
import com.dq.backend.domain.proof.repository.ProofVoteRepository
import com.dq.backend.domain.reward.service.RewardService
import com.dq.backend.domain.room.repository.RoomMemberRepository
import com.dq.backend.domain.room.repository.RoomRepository
import com.dq.backend.domain.user.repository.UserRepository
import com.dq.backend.global.common.PageResponse
import com.dq.backend.global.error.BusinessException
import com.dq.backend.global.error.ErrorCode
import com.dq.backend.global.error.ForbiddenException
import com.dq.backend.global.error.NotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

@Service
@Transactional(readOnly = true)
class ProofService(
    private val proofRepository: ProofRepository,
    private val voteRepository: ProofVoteRepository,
    private val reactionRepository: ProofReactionRepository,
    private val viewRepository: ProofViewRepository,
    private val missionRepository: MissionRepository,
    private val roomRepository: RoomRepository,
    private val roomMemberRepository: RoomMemberRepository,
    private val userRepository: UserRepository,
    private val rewardService: RewardService,
) {
    companion object {
        /** 허용 이모지 (api.md 6.5, 12장 #21 기본안) */
        val ALLOWED_EMOJIS = setOf("❤️", "🔥", "👏", "😂", "😮")
        val DEFAULT_FEED_WINDOW: Duration = Duration.ofHours(24)
        const val MAX_PAGE_SIZE = 100

        /** 확정에 필요한 표 수 = ceil((멤버 수 - 1) / 2), 최소 1 (12장 #15 기본안) */
        fun requiredVotes(memberCount: Long): Int = maxOf(1, ceil((memberCount - 1) / 2.0).toInt())
    }

    // ---------- 6.2 제출 ----------

    @Transactional
    fun submit(userId: Long, missionId: Long, request: SubmitProofRequest): ProofResponse {
        val mission = getMission(missionId)
        requireMember(mission.roomId, userId)
        if (!mission.isActive()) throw BusinessException(ErrorCode.MISSION_EXPIRED)
        validate(request)
        if (proofRepository.existsByMissionIdAndUserIdAndStatusIn(missionId, userId, listOf(ProofStatus.PENDING, ProofStatus.APPROVED))) {
            throw BusinessException(ErrorCode.PROOF_ALREADY_SUBMITTED)
        }
        val proof = try {
            proofRepository.saveAndFlush(
                Proof(
                    missionId = missionId,
                    userId = userId,
                    mediaType = request.mediaType,
                    mediaUrl = request.mediaUrl?.trim()?.ifBlank { null },
                    thumbnailUrl = request.thumbnailUrl?.trim()?.ifBlank { null },
                    text = request.text?.trim()?.ifBlank { null },
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.PROOF_ALREADY_SUBMITTED) // 동시 제출 경합
        }
        // 멤버가 혼자인 룸은 제출 즉시 자동 승인 (12장 #16 기본안)
        if (roomMemberRepository.countByIdRoomId(mission.roomId) <= 1) {
            proof.finalize(ProofStatus.APPROVED)
            rewardService.grantForApprovedProof(userId, mission, proof.id)
        }
        return toResponse(proof, userId)
    }

    private fun validate(r: SubmitProofRequest) {
        val hasMedia = !r.mediaUrl.isNullOrBlank()
        val hasText = !r.text.isNullOrBlank()
        if (!hasMedia && !hasText) throw BusinessException(ErrorCode.VALIDATION_ERROR, "mediaUrl 또는 text 중 하나는 필수입니다.")
        if (r.mediaType == MediaType.TEXT && !hasText) throw BusinessException(ErrorCode.VALIDATION_ERROR, "TEXT 인증에는 text가 필요합니다.")
        if (r.mediaType != MediaType.TEXT && !hasMedia) throw BusinessException(ErrorCode.VALIDATION_ERROR, "${r.mediaType} 인증에는 mediaUrl이 필요합니다.")
    }

    // ---------- 6.3 상세 ----------

    @Transactional
    fun detail(userId: Long, proofId: Long): ProofDetailResponse {
        val proof = getProof(proofId)
        val mission = getMission(proof.missionId)
        requireMember(mission.roomId, userId)
        val room = roomRepository.findByIdOrNull(mission.roomId) ?: throw NotFoundException(ErrorCode.ROOM_NOT_FOUND)
        // 읽음 표시 (스토리 링)
        if (proof.userId != userId) {
            val id = ProofViewId(proofId, userId)
            viewRepository.findByIdOrNull(id)?.let { it.viewedAt = Instant.now() } ?: viewRepository.save(ProofView(id))
        }
        return ProofDetailResponse.from(toResponse(proof, userId, withVoters = true), MissionBrief.from(mission), RoomRef(room.id, room.name))
    }

    // ---------- 6.4 승인 / 거절 ----------

    @Transactional
    fun vote(voterId: Long, proofId: Long, vote: Vote): VoteResponse {
        val proof = proofRepository.findByIdForUpdate(proofId) ?: throw NotFoundException(ErrorCode.PROOF_NOT_FOUND)
        val mission = getMission(proof.missionId)
        requireMember(mission.roomId, voterId)
        if (proof.userId == voterId) throw BusinessException(ErrorCode.CANNOT_VOTE_OWN_PROOF)
        if (!proof.isPending()) throw BusinessException(ErrorCode.PROOF_ALREADY_FINALIZED)

        // 확정 전에는 투표 변경 허용 (12장 #18 기본안)
        val existing = voteRepository.findByIdProofIdAndIdVoterId(proofId, voterId)
        if (existing == null) voteRepository.save(ProofVote(ProofVoteId(proofId, voterId), vote)) else existing.vote = vote
        voteRepository.flush()

        val votes = voteRepository.findAllByIdProofId(proofId)
        val approved = votes.count { it.vote == Vote.APPROVE }
        val rejected = votes.count { it.vote == Vote.REJECT }
        val required = requiredVotes(roomMemberRepository.countByIdRoomId(mission.roomId))

        var reward = null as com.dq.backend.domain.mission.dto.RewardResponse?
        when {
            approved >= required -> {
                proof.finalize(ProofStatus.APPROVED)
                reward = rewardService.grantForApprovedProof(proof.userId, mission, proof.id) // 같은 트랜잭션, 1회
            }
            rejected >= required -> proof.finalize(ProofStatus.REJECTED)
        }
        return VoteResponse(
            proofId = proof.id,
            status = proof.status,
            approval = ApprovalResponse(approved, rejected, required, vote),
            rewardGranted = reward,
        )
    }

    // ---------- 6.5 리액션 ----------

    @Transactional
    fun react(userId: Long, proofId: Long, emoji: String): List<ReactionSummary> {
        val proof = getProof(proofId)
        requireMember(getMission(proof.missionId).roomId, userId)
        if (emoji !in ALLOWED_EMOJIS) throw BusinessException(ErrorCode.INVALID_EMOJI)
        val id = ProofReactionId(proofId, userId, emoji)
        if (!reactionRepository.existsById(id)) reactionRepository.save(ProofReaction(id))
        return reactions(proofId, userId)
    }

    @Transactional
    fun unreact(userId: Long, proofId: Long, emoji: String): List<ReactionSummary> {
        val proof = getProof(proofId)
        requireMember(getMission(proof.missionId).roomId, userId)
        reactionRepository.deleteById(ProofReactionId(proofId, userId, emoji))
        return reactions(proofId, userId)
    }

    // ---------- 6.6 피드 / 6.7 승인 대기 ----------

    fun feed(userId: Long, roomId: Long, since: Instant?, page: Int, size: Int): PageResponse<FeedItemResponse> {
        requireMember(roomId, userId)
        val from = since ?: Instant.now().minus(DEFAULT_FEED_WINDOW)
        val result = proofRepository.findFeed(roomId, from, PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE)))
        return PageResponse(toFeedItems(result.content, userId), result.number, result.size, result.totalElements, result.totalPages)
    }

    fun pendingForMe(userId: Long, roomId: Long, page: Int, size: Int): PageResponse<FeedItemResponse> {
        requireMember(roomId, userId)
        val result = proofRepository.findPendingForVoter(roomId, userId, PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE)))
        return PageResponse(toFeedItems(result.content, userId), result.number, result.size, result.totalElements, result.totalPages)
    }

    // ---------- 6.8 삭제 ----------

    @Transactional
    fun delete(userId: Long, proofId: Long) {
        val proof = getProof(proofId)
        if (proof.userId != userId) throw ForbiddenException()
        if (!proof.isPending()) throw BusinessException(ErrorCode.PROOF_ALREADY_FINALIZED)
        voteRepository.deleteAllByProofId(proofId)
        reactionRepository.deleteAllByProofId(proofId)
        viewRepository.deleteAllByProofId(proofId)
        proofRepository.delete(proof)
    }

    // ---------- 내부 ----------

    private fun getProof(proofId: Long): Proof =
        proofRepository.findByIdOrNull(proofId) ?: throw NotFoundException(ErrorCode.PROOF_NOT_FOUND)

    private fun getMission(missionId: Long): Mission =
        missionRepository.findByIdOrNull(missionId) ?: throw NotFoundException(ErrorCode.MISSION_NOT_FOUND)

    private fun requireMember(roomId: Long, userId: Long) {
        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) throw ForbiddenException()
    }

    private fun reactions(proofId: Long, userId: Long): List<ReactionSummary> =
        reactionRepository.findAllByIdProofId(proofId)
            .groupBy { it.id.emoji }
            .map { (emoji, list) -> ReactionSummary(emoji, list.size, list.any { it.id.userId == userId }) }
            .sortedByDescending { it.count }

    private fun toResponse(proof: Proof, viewerId: Long, withVoters: Boolean = false): ProofResponse {
        val mission = getMission(proof.missionId)
        val owner = userRepository.findByIdOrNull(proof.userId) ?: throw NotFoundException(ErrorCode.USER_NOT_FOUND)
        val votes = voteRepository.findAllByIdProofId(proof.id)
        val required = requiredVotes(roomMemberRepository.countByIdRoomId(mission.roomId))
        val voters = if (withVoters) {
            val users = userRepository.findAllById(votes.map { it.id.voterId }).associateBy { it.id }
            votes.mapNotNull { v -> users[v.id.voterId]?.let { VoterResponse(it.id, it.nickname, v.vote) } }
        } else null
        return ProofResponse(
            proofId = proof.id,
            missionId = proof.missionId,
            user = ProofUser.from(owner),
            mediaUrl = proof.mediaUrl,
            thumbnailUrl = proof.thumbnailUrl,
            mediaType = proof.mediaType,
            text = proof.text,
            status = proof.status,
            approval = ApprovalResponse(
                approved = votes.count { it.vote == Vote.APPROVE },
                rejected = votes.count { it.vote == Vote.REJECT },
                required = required,
                myVote = votes.firstOrNull { it.id.voterId == viewerId }?.vote,
                voters = voters,
            ),
            reactions = reactions(proof.id, viewerId),
            createdAt = proof.createdAt,
        )
    }

    private fun toFeedItems(proofs: List<Proof>, viewerId: Long): List<FeedItemResponse> {
        if (proofs.isEmpty()) return emptyList()
        val users = userRepository.findAllById(proofs.map { it.userId }.distinct()).associateBy { it.id }
        val missions = missionRepository.findAllById(proofs.map { it.missionId }.distinct()).associateBy { it.id }
        val viewed = viewRepository.findViewedProofIds(viewerId, proofs.map { it.id })
        return proofs.mapNotNull { p ->
            val u = users[p.userId] ?: return@mapNotNull null
            val m = missions[p.missionId] ?: return@mapNotNull null
            FeedItemResponse(
                proofId = p.id,
                user = ProofUser.from(u),
                mission = FeedMissionBrief(m.id, m.content),
                mediaType = p.mediaType,
                thumbnailUrl = p.thumbnailUrl,
                status = p.status,
                viewed = p.userId == viewerId || p.id in viewed,
                createdAt = p.createdAt,
            )
        }
    }
}
