package com.settle.apps.scheduler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EntityScan(basePackages = ["com.settle"])
@EnableScheduling
@SpringBootApplication(scanBasePackages = ["com.settle"])
class SchedulerApplication

fun main(args: Array<String>) {
    runApplication<SchedulerApplication>(*args)
}
