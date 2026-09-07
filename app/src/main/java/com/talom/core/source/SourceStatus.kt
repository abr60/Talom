package com.talom.core.source

import java.time.Instant

data class SourceStatus(
    val sourceId: String,
    val displayName: String,
    val state: State,
    val lastSuccessfulPull: Instant? = null,
    val detail: String? = null,
) {
    enum class State {
        NOT_CONFIGURED,
        READY,
        RUNNING,
        FAILED,
        STALE,
    }
}
