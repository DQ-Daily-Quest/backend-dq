package com.dq.backend.domain.room.entity

import com.dq.backend.global.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** rooms 테이블 (api.md 4장). 멤버십은 RoomMember 가 따로 가진다. */
@Entity
@Table(name = "rooms")
class Room(
    @Column(nullable = false, length = 20)
    var name: String,

    @Column(length = 100)
    var description: String?,

    @Column(name = "is_public", nullable = false)
    var isPublic: Boolean,

    @Column(name = "owner_id", nullable = false)
    var ownerId: Long,

    @Column(name = "max_members", nullable = false)
    var maxMembers: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /** 비공개 룸에만 존재. 공개 룸은 null. */
    @Column(name = "invite_code", unique = true, length = 6)
    var inviteCode: String? = null

    @Column(name = "thumbnail_url", length = 512)
    var thumbnailUrl: String? = null

    fun isOwner(userId: Long) = ownerId == userId
}
