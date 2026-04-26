package org.traanite.pubgity.job

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pubg.job")
data class JobProperties(
    val statsTtl: Duration = Duration.ofDays(7)
)

