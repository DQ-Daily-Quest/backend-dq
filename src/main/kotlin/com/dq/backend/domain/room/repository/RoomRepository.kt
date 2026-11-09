package com.dq.backend.domain.room.repository

import com.dq.backend.domain.room.entity.Room
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/** 목록 조회용 프로젝션: 룸 + 멤버 수 */
interface RoomWithCount {
    val room: Room
    val memberCount: Long
}

interface RoomRepository : JpaRepository<Room, Long> {

    fun findByInviteCode(inviteCode: String): Room?
    fun existsByInviteCode(inviteCode: String): Boolean
    fun existsByOwnerId(ownerId: Long): Boolean

    /** 참여/정원 검사 시 동시 참여 경합을 막기 위해 행 잠금 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    fun findByIdForUpdate(id: Long): Room?

    /**
     * 공개 룸 목록, 인기 점수 내림차순 (api.md 4.1).
     * 인기 점수 = (최근 7일 인증 제출 수 × 2) + 멤버 수. since = now - 7일.
     */
    @Query(
        value = """
            select r as room, count(m) as memberCount,
                   (count(m) + 2 * (select count(p) from Proof p join Mission mm on p.missionId = mm.id
                                    where mm.roomId = r.id and p.createdAt >= :since)) as score
            from Room r left join RoomMember m on m.id.roomId = r.id
            where r.isPublic = true and lower(r.name) like lower(concat('%', :keyword, '%'))
            group by r
            order by score desc, r.createdAt desc
        """,
        countQuery = """
            select count(r) from Room r
            where r.isPublic = true and lower(r.name) like lower(concat('%', :keyword, '%'))
        """,
    )
    fun findPublicByPopularity(keyword: String, since: Instant, pageable: Pageable): Page<RoomWithCount>

    @Query(
        value = """
            select r as room, count(m) as memberCount
            from Room r left join RoomMember m on m.id.roomId = r.id
            where r.isPublic = true and lower(r.name) like lower(concat('%', :keyword, '%'))
            group by r
            order by r.createdAt desc
        """,
        countQuery = """
            select count(r) from Room r
            where r.isPublic = true and lower(r.name) like lower(concat('%', :keyword, '%'))
        """,
    )
    fun findPublicByCreatedAt(keyword: String, pageable: Pageable): Page<RoomWithCount>

    /** 내가 속한 룸 + 멤버 수 (api.md 4.2) */
    @Query(
        """
        select r as room, count(m2) as memberCount
        from Room r
          join RoomMember m on m.id.roomId = r.id and m.id.userId = :userId
          left join RoomMember m2 on m2.id.roomId = r.id
        group by r
        order by r.createdAt desc
        """,
    )
    fun findMine(userId: Long): List<RoomWithCount>
}
