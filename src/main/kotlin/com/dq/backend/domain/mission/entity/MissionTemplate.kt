package com.dq.backend.domain.mission.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** APP 미션 풀 (시드 데이터). */
@Entity
@Table(name = "mission_templates")
class MissionTemplate(
    @Column(nullable = false, length = 100)
    val content: String,

    @Column(name = "reward_item_id")
    val rewardItemId: Long? = null,

    @Column(nullable = false)
    var active: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
