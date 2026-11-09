package com.dq.backend.global.storage

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** 로컬 디스크 저장. 저장된 파일은 /files 경로 아래로 서빙한다. */
@Service
class LocalStorageService(private val props: UploadProperties) : StorageService {

    private val root: Path = Paths.get(props.dir).toAbsolutePath().normalize()

    override fun store(key: String, bytes: ByteArray, contentType: String): String {
        val target = root.resolve(key).normalize()
        require(target.startsWith(root)) { "잘못된 저장 경로" }
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
        return "${props.baseUrl.trimEnd('/')}/$key"
    }
}

@Configuration
@EnableConfigurationProperties(UploadProperties::class)
class StaticFileConfig(private val props: UploadProperties) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val location = Paths.get(props.dir).toAbsolutePath().normalize().toUri().toString()
        registry.addResourceHandler("/files/**").addResourceLocations(location)
    }
}
