package com.dq.backend.domain.room.repository

import com.dq.backend.domain.room.entity.RoomMember
import com.dq.backend.domain.room.entity.RoomMemberId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RoomMemberRepository : JpaRepository<RoomMember, RoomMemberId> {
    fun findByIdRoomIdAndIdUserId(roomId: Long, userId: Long): RoomMember?
    fun existsByIdRoomIdAndIdUserId(roomId: Long, userId: Long): Boolean
    fun countByIdRoomId(roomId: Long): Long
    fun countByIdUserId(userId: Long): Long
    fun findAllByIdRoomIdOrderByJoinedAtAsc(roomId: Long): List<RoomMember>

    @Query("select m.id.roomId from RoomMember m where m.id.userId = :userId and m.id.roomId in :roomIds")
    fun findJoinedRoomIds(userId: Long, roomIds: Collection<Long>): Set<Long>

    @Modifying
    @Query("delete from RoomMember m where m.id.roomId = :roomId")
    fun deleteAllByRoomId(roomId: Long): Int
}
