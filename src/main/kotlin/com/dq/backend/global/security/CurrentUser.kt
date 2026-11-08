package com.dq.backend.global.security

import org.springframework.security.core.annotation.AuthenticationPrincipal

/** 컨트롤러 파라미터에 붙이면 로그인한 사용자의 userId(Long)가 주입된다. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@AuthenticationPrincipal
annotation class CurrentUser
