package com.streamsheet.spring.tracing

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry

class MicrometerObservationStreamSheetTracer(
    private val registry: ObservationRegistry,
) : StreamSheetTracer {

    override fun <T> inSpan(name: String, block: () -> T): T {
        val observation = Observation.createNotStarted(name, registry)
            .start()

        return try {
            observation.openScope().use {
                block()
            }
        } catch (e: Exception) {
            observation.error(e)
            throw e
        } finally {
            observation.stop()
        }
    }
}
