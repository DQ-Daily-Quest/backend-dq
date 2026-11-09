package com.dq.backend.domain.proof.repository

import com.dq.backend.domain.proof.entity.Proof
import com.dq.backend.domain.proof.entity.ProofStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface ProofCount { val missionId: Long; val cnt: Long }

interface ProofRepository : JpaRepository<Proof, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proof p where p.id = :id")
    fun findByIdForUpdate(id: Long): Proof?

    fun existsByMissionIdAndUserIdAndStatusIn(missionId: Long, userId: Long, statuses: Collection<ProofStatus>): Boolean
    fun existsByMissionId(missionId: Long): Boolean
    fun findAllByMissionIdOrderByCreatedAtDesc(missionId: Long): List<Proof>
    fun countByUserIdAndStatus(userId: Long, status: ProofStatus): Long

    /** 미션별 인증 수 */
    @Query("select p.missionId as missionId, count(p) as cnt from Proof p where p.missionId in :missionIds group by p.missionId")
    fun countByMissionIds(missionIds: Collection<Long>): List<ProofCount>

    /** 요청자가 제출한 (대기/승인) 인증 — 미션 응답의 myProof */
    @Query("select p from Proof p where p.userId = :userId and p.missionId in :missionIds and p.status in ('PENDING','APPROVED')")
    fun findMineByMissionIds(userId: Long, missionIds: Collection<Long>): List<Proof>

    /** 룸 피드: since 이후 인증, 최신순 (api.md 6.6) */
    @Query(
        value = """
            select p from Proof p join Mission m on p.missionId = m.id
            where m.roomId = :roomId and p.createdAt >= :since
            order by p.createdAt desc
        """,
        countQuery = "select count(p) from Proof p join Mission m on p.missionId = m.id where m.roomId = :roomId and p.createdAt >= :since",
    )
    fun findFeed(roomId: Long, since: Instant, pageable: Pageable): Page<Proof>

    /** 승인 대기: 내가 아직 투표하지 않은, 남이 올린 PENDING 인증 (api.md 6.7) */
    @Query(
        value = """
            select p from Proof p join Mission m on p.missionId = m.id
            where m.roomId = :roomId and p.status = 'PENDING' and p.userId <> :userId
              and not exists (select v from ProofVote v where v.id.proofId = p.id and v.id.voterId = :userId)
            order by p.createdAt desc
        """,
        countQuery = """
            select count(p) from Proof p join Mission m on p.missionId = m.id
            where m.roomId = :roomId and p.status = 'PENDING' and p.userId <> :userId
              and not exists (select v from ProofVote v where v.id.proofId = p.id and v.id.voterId = :userId)
        """,
    )
    fun findPendingForVoter(roomId: Long, userId: Long, pageable: Pageable): Page<Proof>

    @Query(
        """
        select count(p) from Proof p join Mission m on p.missionId = m.id
        where m.roomId = :roomId and p.status = 'PENDING' and p.userId <> :userId
          and not exists (select v from ProofVote v where v.id.proofId = p.id and v.id.voterId = :userId)
        """,
    )
    fun countPendingForVoter(roomId: Long, userId: Long): Long

    /** 오늘 APP 미션을 인증(대기/승인)한 사용자 ID 집합 */
    @Query("select p.userId from Proof p where p.missionId = :missionId and p.status in ('PENDING','APPROVED')")
    fun findUserIdsWithOpenProof(missionId: Long): Set<Long>

    /** 마감 + 유예시간 지난 PENDING 인증 만료 (api.md 6장 규칙) */
    @Modifying
    @Query(
        """
        update Proof p set p.status = 'EXPIRED', p.finalizedAt = :now
        where p.status = 'PENDING' and p.missionId in (select m.id from Mission m where m.dueAt < :dueBefore)
        """,
    )
    fun expirePending(dueBefore: Instant, now: Instant): Int
}
