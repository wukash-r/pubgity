package org.traanite.pubgity.match

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MatchService(
    private val matchRepository: MatchRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
    }

    fun findByMatchId(matchId: String): Match? = matchRepository.findByMatchId(matchId)

    fun findByMatchIds(matchIds: Collection<String>): List<Match> = matchRepository.findByMatchIdIn(matchIds)

    fun existsByMatchId(matchId: String): Boolean = matchRepository.findByMatchId(matchId) != null

    fun findExistingMatchIds(matchIds: Collection<String>): Set<String> {
        return matchRepository.findByMatchIdIn(matchIds).map { it.matchId }.toSet()
    }

    fun saveMatch(match: Match): Match {
        val existing = matchRepository.findByMatchId(match.matchId)
        val toSave = if (existing != null) match.copy(id = existing.id) else match
        val saved = matchRepository.save(toSave)
        logger.debug("Saved match {} with {} rosters", saved.matchId, saved.rosters.size)
        return saved
    }
}

