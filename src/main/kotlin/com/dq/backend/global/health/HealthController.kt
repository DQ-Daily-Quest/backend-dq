package com.dq.backend.global.health

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Tag(name = "Health", description = "서버 상태 확인")
@RestController
@RequestMapping("/api/v1/health")
class HealthController {

    @Operation(summary = "헬스체크")
    @GetMapping
    fun health(): HealthResponse = HealthResponse(status = "UP", timestamp = Instant.now())
}

data class HealthResponse(val status: String, val timestamp: Instant)
