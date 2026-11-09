package com.dq.backend.global.storage

/**
 * 업로드 파일 저장소 추상화 (api.md 6.1 구현 메모).
 * 지금은 로컬 디스크(LocalStorageService), 배포 시 S3/Firebase Storage 구현으로 교체한다.
 */
interface StorageService {
    /** 바이트를 저장하고 공개 URL 을 돌려준다. key 예: proofs/2026/11/09/uuid.jpg */
    fun store(key: String, bytes: ByteArray, contentType: String): String
}
