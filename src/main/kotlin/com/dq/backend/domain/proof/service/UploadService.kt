package com.dq.backend.domain.proof.service

import com.dq.backend.domain.proof.dto.UploadResponse
import com.dq.backend.domain.proof.entity.MediaType
import com.dq.backend.global.common.TimeUtils
import com.dq.backend.global.error.BusinessException
import com.dq.backend.global.error.ErrorCode
import com.dq.backend.global.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * 파일 업로드 (api.md 6.1). 이미지는 썸네일(최대 400px)을 함께 만든다.
 * 한도: 이미지 10MB(12장 #14 기본안), 영상 50MB(후순위).
 */
@Service
class UploadService(private val storage: StorageService) {

    companion object {
        const val IMAGE_MAX_BYTES = 10L * 1024 * 1024
        const val VIDEO_MAX_BYTES = 50L * 1024 * 1024
        const val THUMB_MAX_PX = 400
        private val IMAGE_TYPES = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp")
        private val VIDEO_TYPES = mapOf("video/mp4" to "mp4")
    }

    fun upload(file: MultipartFile, purpose: String?): UploadResponse {
        if (file.isEmpty) throw BusinessException(ErrorCode.FILE_EMPTY)
        val contentType = file.contentType?.lowercase() ?: throw BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        val folder = if (purpose?.uppercase() == "ROOM_THUMBNAIL") "rooms" else "proofs"
        val today = TimeUtils.todayKst()
        val keyPrefix = "$folder/${today.year}/%02d/%02d/${UUID.randomUUID()}".format(today.monthValue, today.dayOfMonth)

        return when {
            contentType in IMAGE_TYPES -> {
                if (file.size > IMAGE_MAX_BYTES) throw BusinessException(ErrorCode.FILE_TOO_LARGE, "이미지는 최대 10MB입니다.")
                val bytes = file.bytes
                val ext = IMAGE_TYPES.getValue(contentType)
                val url = storage.store("$keyPrefix.$ext", bytes, contentType)
                val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() // webp 등은 null
                val thumbUrl = image?.let { storage.store("${keyPrefix}_thumb.jpg", thumbnail(it), "image/jpeg") }
                UploadResponse(url, thumbUrl, MediaType.IMAGE, image?.width, image?.height)
            }
            contentType in VIDEO_TYPES -> {
                if (file.size > VIDEO_MAX_BYTES) throw BusinessException(ErrorCode.FILE_TOO_LARGE, "영상은 최대 50MB입니다.")
                val url = storage.store("$keyPrefix.${VIDEO_TYPES.getValue(contentType)}", file.bytes, contentType)
                UploadResponse(url, null, MediaType.VIDEO, null, null)
            }
            else -> throw BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "jpg, png, webp, mp4만 업로드할 수 있습니다.")
        }
    }

    private fun thumbnail(src: BufferedImage): ByteArray {
        val scale = minOf(1.0, THUMB_MAX_PX.toDouble() / maxOf(src.width, src.height))
        val w = maxOf(1, (src.width * scale).toInt())
        val h = maxOf(1, (src.height * scale).toInt())
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            drawImage(src.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(out, "jpg", it) }.toByteArray()
    }
}
