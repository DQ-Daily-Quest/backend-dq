package com.dq.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BackendDqApplication

fun main(args: Array<String>) {
	runApplication<BackendDqApplication>(*args)
}
