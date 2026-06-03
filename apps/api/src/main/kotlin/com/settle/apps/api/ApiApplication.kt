package com.settle.apps.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication

@EntityScan(basePackages = ["com.settle"])
@SpringBootApplication(scanBasePackages = ["com.settle"])
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
