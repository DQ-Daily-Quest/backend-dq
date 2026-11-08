package com.dq.backend.domain.mission.entity

import com.dq.backend.global.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

enum class MissionType { APP, MEMBER }
enum class RewardType { ITEM, COIN }

/** missions 테이블 (api.md 5장). APP 은 서버가 매일 생성, MEMBER 는 멤버가 생성. */
@Entity
@Table(name = "missions")
class Mission(
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val type: MissionType,

    @Column(nullable = false, length = 100)
    var content: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 10)
    val rewardType: RewardType,

    @Column(name = "due_at", nullable = false)
    var dueAt: Instant,

    @Column(name = "template_id")
    val templateId: Long? = null,

    @Column(name = "reward_item_id")
    val rewardItemId: Long? = null,

    @Column(name = "reward_coins")
    val rewardCoins: Int? = null,

    @Column(name = "created_by")
    val createdBy: Long? = null,

    /** APP 미션의 KST 날짜. MEMBER 는 null. */
    @Column(name = "date")
    val date: LocalDate? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    fun isActive(now: Instant = Instant.now()) = dueAt.isAfter(now)
    fun isApp() = type == MissionType.APP
}
