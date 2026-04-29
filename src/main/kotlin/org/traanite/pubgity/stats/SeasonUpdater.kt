package org.traanite.pubgity.stats

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.traanite.pubgity.pubgapi.PubgApiClient

@Service
class SeasonUpdater(
    val seasonRepository: SeasonRepository, val pubgApiClient: PubgApiClient
) {

    companion object {
        private val logger = LoggerFactory.getLogger(SeasonUpdater::class.java)
    }

    @EventListener(ApplicationReadyEvent::class)
    private fun init() {
        updateSeasons()
    }

    @Scheduled(cron = "\${pubg.season.updateSeasonsCron}")
    private fun updateSeasonsScheduledTask() {
        logger.info("Updating seasons scheduled task")
        updateSeasons()
    }

    // todo not thread safe atm
    fun updateSeasons() {
        logger.info("Updating seasons")
        val seasons = pubgApiClient.getSeasons()
        val existingSeasonsById = seasonRepository.findAll().associateBy { it.id }

        seasons.data.mapNotNull {
            try {
                Season(
                    seasonId = it.id, isCurrentSeason = it.attributes!!.isCurrentSeason
                )
            } catch (e: Exception) {
                logger.error("Failed to parse season ${it.id}", e)
                null
            }
        }.mapNotNull { season ->
            val existing = existingSeasonsById[season.id]
            if (existing == null) {
                logger.info("Adding new season: {}", season.seasonId)
                season
            } else if (existing.isCurrentSeason != season.isCurrentSeason) {
                logger.info("Updating season {} current status to {}", season.seasonId, season.isCurrentSeason)
                existing.copy(isCurrentSeason = season.isCurrentSeason)
            } else {
                null
            }
        }.toList().let {
            seasonRepository.saveAll(it)
            logger.info("Updated {} seasons", it.size)
        }
    }
}