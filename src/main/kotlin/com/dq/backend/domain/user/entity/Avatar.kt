package com.dq.backend.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 착용 중인 아이템 ID. null = 착용 안 함. (아이템 보유 검증은 feat/reward에서 추가) */
@Embeddable
class Avatar(
    @Column(name = "avatar_hair") var hair: Long? = null,
    @Column(name = "avatar_top") var top: Long? = null,
    @Column(name = "avatar_bottom") var bottom: Long? = null,
    @Column(name = "avatar_accessory") var accessory: Long? = null,
)
