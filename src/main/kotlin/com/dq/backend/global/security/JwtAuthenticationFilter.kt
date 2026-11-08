package com.dq.backend.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `Authorization: Bearer {accessToken}` 헤더를 읽어 SecurityContext에 userId(Long)를 principal로 넣는다.
 * 토큰이 없으면 통과(공개 경로는 SecurityConfig가 허용, 보호 경로는 401).
 * 토큰이 만료됐으면 즉시 401 TOKEN_EXPIRED 를 내려 클라이언트가 refresh 하도록 한다.
 */
@Component
class JwtAuthenticationFilter(private val jwtProvider: JwtProvider) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = resolveToken(request)
        if (token == null) {
            chain.doFilter(request, response)
            return
        }
        when (val result = jwtProvider.parse(token)) {
            is JwtProvider.ParseResult.Valid -> {
                val auth = UsernamePasswordAuthenticationToken(
                    result.userId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER")),
                ).apply { details = WebAuthenticationDetailsSource().buildDetails(request) }
                SecurityContextHolder.getContext().authentication = auth
                chain.doFilter(request, response)
            }
            JwtProvider.ParseResult.Expired -> writeUnauthorized(response, "TOKEN_EXPIRED", "토큰이 만료되었습니다.")
            JwtProvider.ParseResult.Invalid -> writeUnauthorized(response, "UNAUTHORIZED", "인증이 필요합니다.")
        }
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return header.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()?.ifEmpty { null }
    }

    private fun writeUnauthorized(response: HttpServletResponse, code: String, message: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("""{"code":"$code","message":"$message"}""")
    }
}
