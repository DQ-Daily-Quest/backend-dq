package com.dq.backend.global.common

import org.springframework.data.domain.Page

/** 목록 응답 형식 (api.md 1.3). */
data class PageResponse<T : Any>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T : Any> from(page: Page<T>): PageResponse<T> = PageResponse(
            content = page.content,
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )

        fun <T : Any, R : Any> from(page: Page<T>, mapper: (T) -> R): PageResponse<R> =
            from(page.map(mapper))
    }
}
