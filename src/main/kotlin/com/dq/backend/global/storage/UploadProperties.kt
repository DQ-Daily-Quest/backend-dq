package com.dq.backend.global.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dq.upload")
data class UploadProperties(
    /** 로컬 저장 디렉터리 */
    val dir: String,
    /** 저장된 파일이 서빙되는 기본 URL (예: http://localhost:8080/files) */
    val baseUrl: String,
)
