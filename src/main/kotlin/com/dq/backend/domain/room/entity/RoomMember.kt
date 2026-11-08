package com.dq.backend.domain.room.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

enum class RoomRole { OWNER, MEMBER }

@Embeddable
data class RoomMemberId(
    @Column(name = "room_id") val roomId: Long = 0,
    @Column(name = "user_id") val userId: Long = 0,
) : Serializable

/** room_members 테이블. PK (room_id, user_id). */
@Entity
@Table(name = "room_members")
class RoomMember(
    @EmbeddedId
    val id: RoomMemberId,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var role: RoomRole,
) {
    @Column(name = "joined_at", nullable = false, updatable = false)
    val joinedAt: Instant = Instant.now()

    val roomId: Long get() = id.roomId
    val userId: Long get() = id.userId
}
