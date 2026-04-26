package org.traanite.pubgity.player

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pubg.player")
data class PlayerProperties(
    val statsTtl: Duration = Duration.ofDays(7)
)