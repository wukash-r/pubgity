package org.traanite.pubgity.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.traanite.pubgity.model.GameModeStats
import org.traanite.pubgity.model.LifetimeStats
import org.traanite.pubgity.model.ModeStats
import org.traanite.pubgity.repository.MatchRepository
import org.traanite.pubgity.repository.PlayerRepository

@Controller
@RequestMapping("/players")
class PlayerController(
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun listPlayers(@RequestParam(required = false) filter: String?, model: Model): String {
        val players = if (filter.isNullOrBlank()) {
            playerRepository.findAll()
        } else {
            playerRepository.searchByPlayerName(filter)
        }
        logger.debug("Player list: filter='{}', found {} players", filter ?: "", players.size)
        model.addAttribute("players", players)
        model.addAttribute("filter", filter ?: "")
        return "players"
    }

    @GetMapping("/{accountId}")
    fun playerDetail(@PathVariable accountId: String, model: Model): String {
        logger.info("Loading detail page for accountId: {}", accountId)
        val player = playerRepository.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")

        val matches = matchRepository.findByMatchIdIn(player.matchIds)
            .sortedByDescending { it.createdAt }
        logger.info("Player '{}': loaded {} matches from DB", player.playerName, matches.size)

        val perMatchStats = matches.map { match ->
            val realParticipants = match.participants.filter { it.lifetimeStats != null }
            val modeExtractor = gameModeExtractor(match.gameMode)

            val aggregated = realParticipants.mapNotNull { p ->
                aggregateStats(p.lifetimeStats!!, modeExtractor)
            }

            val kills = aggregated.map { it.totalKills.toDouble() }
            val damage = aggregated.map { it.totalDamageDealt }
            val kd = aggregated.map { it.modeKD }
            val brpAll = aggregated.map { it.bestRankPoint }
            val brpNonZero = brpAll.filter { it > 0.0 }
            val timeSurvived = aggregated.map { it.totalTimeSurvived }
            val wins = aggregated.map { it.modeWins.toDouble() }
            val rounds = aggregated.map { it.modeRoundsPlayed.toDouble() }
            val hsKills = aggregated.map { it.modeHeadshotKills.toDouble() }
            val top10s = aggregated.map { it.totalTop10s.toDouble() }

            // Extract player's own snapshot from this match
            val playerSnapshot = match.participants
                .firstOrNull { it.accountId == accountId }
                ?.lifetimeStats
                ?.let { aggregateStats(it, modeExtractor) }

            PerMatchSkillData(
                matchId = match.matchId,
                label = "${match.gameMode} - ${match.mapName}",
                participantCount = realParticipants.size,
                botCount = match.botCount,
                minKills = min(kills),
                maxKills = max(kills),
                medianKills = median(kills),
                avgKills = avg(kills),
                minDamage = min(damage),
                maxDamage = max(damage),
                medianDamage = median(damage),
                avgDamage = avg(damage),
                minKD = min(kd),
                maxKD = max(kd),
                medianKD = median(kd),
                avgKD = avg(kd),
                minBestRankPoint = min(brpNonZero),
                maxBestRankPoint = max(brpNonZero),
                medianBestRankPoint = median(brpNonZero),
                avgBestRankPoint = avg(brpNonZero),
                rankedPlayerCount = brpNonZero.size,
                minTimeSurvived = min(timeSurvived),
                maxTimeSurvived = max(timeSurvived),
                medianTimeSurvived = median(timeSurvived),
                avgTimeSurvived = avg(timeSurvived),
                minWins = min(wins),
                maxWins = max(wins),
                medianWins = median(wins),
                avgWins = avg(wins),
                minRoundsPlayed = min(rounds),
                maxRoundsPlayed = max(rounds),
                medianRoundsPlayed = median(rounds),
                avgRoundsPlayed = avg(rounds),
                minHeadshotKills = min(hsKills),
                maxHeadshotKills = max(hsKills),
                medianHeadshotKills = median(hsKills),
                avgHeadshotKills = avg(hsKills),
                minTop10s = min(top10s),
                maxTop10s = max(top10s),
                medianTop10s = median(top10s),
                avgTop10s = avg(top10s),
                playerSnapshotKills = playerSnapshot?.totalKills?.toDouble() ?: 0.0,
                playerSnapshotDamage = playerSnapshot?.totalDamageDealt ?: 0.0,
                playerSnapshotKD = playerSnapshot?.modeKD ?: 0.0,
                playerSnapshotBestRankPoint = playerSnapshot?.bestRankPoint ?: 0.0,
                playerSnapshotTimeSurvived = playerSnapshot?.totalTimeSurvived ?: 0.0,
                playerSnapshotWins = playerSnapshot?.modeWins?.toDouble() ?: 0.0,
                playerSnapshotRoundsPlayed = playerSnapshot?.modeRoundsPlayed?.toDouble() ?: 0.0,
                playerSnapshotHeadshotKills = playerSnapshot?.modeHeadshotKills?.toDouble() ?: 0.0,
                playerSnapshotTop10s = playerSnapshot?.totalTop10s?.toDouble() ?: 0.0
            )
        }

        // Compute current aggregated stats for the player (all modes combined)
        val playerAggregated = player.lifetimeStats?.let { stats ->
            val allModes = listOfNotNull(
                stats.gameModeStats.solo, stats.gameModeStats.soloFpp,
                stats.gameModeStats.duo, stats.gameModeStats.duoFpp,
                stats.gameModeStats.squad, stats.gameModeStats.squadFpp
            )
            if (allModes.isEmpty()) null
            else PlayerAggregatedView(
                totalKills = allModes.sumOf { it.kills },
                totalDamage = allModes.sumOf { it.damageDealt },
                totalTop10s = allModes.sumOf { it.top10s },
                totalWins = allModes.sumOf { it.wins },
                totalRoundsPlayed = allModes.sumOf { it.roundsPlayed },
                totalHeadshotKills = allModes.sumOf { it.headshotKills },
                totalTimeSurvived = allModes.sumOf { it.timeSurvived },
                bestRankPoint = stats.bestRankPoint
            )
        }

        model.addAttribute("player", player)
        model.addAttribute("matches", matches)
        model.addAttribute("perMatchStats", perMatchStats)
        model.addAttribute("playerAggregated", playerAggregated)
        return "player-detail"
    }

    @GetMapping("/{accountId}/matches/{matchId}")
    fun matchDetail(
        @PathVariable accountId: String,
        @PathVariable matchId: String,
        model: Model
    ): String {
        logger.info("Loading match detail: accountId={}, matchId={}", accountId, matchId)
        val player = playerRepository.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")
        val match = matchRepository.findByMatchId(matchId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found")

        val modeExtractor = gameModeExtractor(match.gameMode)
        val participantsWithMode = match.participants
            .filter { it.lifetimeStats != null }
            .map { p ->
                val agg = aggregateStats(p.lifetimeStats!!, modeExtractor)
                ParticipantView(
                    accountId = p.accountId,
                    playerName = p.playerName,
                    kills = agg?.totalKills ?: 0,
                    damage = agg?.totalDamageDealt ?: 0.0,
                    wins = agg?.modeWins ?: 0,
                    roundsPlayed = agg?.modeRoundsPlayed ?: 0,
                    headshotKills = agg?.modeHeadshotKills ?: 0,
                    top10s = agg?.totalTop10s ?: 0,
                    timeSurvived = agg?.totalTimeSurvived ?: 0.0,
                    bestRankPoint = agg?.bestRankPoint ?: 0.0,
                    kd = agg?.modeKD ?: 0.0
                )
            }
            .sortedByDescending { it.kills }

        model.addAttribute("player", player)
        model.addAttribute("match", match)
        model.addAttribute("participants", participantsWithMode)
        return "match-detail"
    }

    private fun gameModeExtractor(gameMode: String): (GameModeStats) -> ModeStats? {
        return when {
            gameMode.contains("squad") && gameMode.contains("fpp") -> { g -> g.squadFpp }
            gameMode.contains("squad") -> { g -> g.squad }
            gameMode.contains("duo") && gameMode.contains("fpp") -> { g -> g.duoFpp }
            gameMode.contains("duo") -> { g -> g.duo }
            gameMode.contains("solo") && gameMode.contains("fpp") -> { g -> g.soloFpp }
            gameMode.contains("solo") -> { g -> g.solo }
            else -> { g -> g.squadFpp ?: g.duoFpp ?: g.soloFpp }
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun avg(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        return values.sum() / values.size
    }

    private fun min(values: List<Double>): Double = values.minOrNull() ?: 0.0

    private fun max(values: List<Double>): Double = values.maxOrNull() ?: 0.0

    /** Aggregates stats across all game modes for summable fields, keeps mode-specific for ratio fields. */
    private fun aggregateStats(
        stats: LifetimeStats,
        modeExtractor: (GameModeStats) -> ModeStats?
    ): AggregatedParticipantStats? {
        val allModes = listOfNotNull(
            stats.gameModeStats.solo,
            stats.gameModeStats.soloFpp,
            stats.gameModeStats.duo,
            stats.gameModeStats.duoFpp,
            stats.gameModeStats.squad,
            stats.gameModeStats.squadFpp
        )
        if (allModes.isEmpty()) return null

        val modeSpecific = modeExtractor(stats.gameModeStats)

        return AggregatedParticipantStats(
            totalKills = allModes.sumOf { it.kills },
            totalDamageDealt = allModes.sumOf { it.damageDealt },
            totalTop10s = allModes.sumOf { it.top10s },
            totalTimeSurvived = allModes.sumOf { it.timeSurvived },
            bestRankPoint = stats.bestRankPoint,
            modeWins = modeSpecific?.wins ?: 0,
            modeRoundsPlayed = modeSpecific?.roundsPlayed ?: 0,
            modeHeadshotKills = modeSpecific?.headshotKills ?: 0,
            modeKD = if ((modeSpecific?.roundsPlayed ?: 0) > 0)
                modeSpecific!!.kills.toDouble() / modeSpecific.roundsPlayed else 0.0
        )
    }
}

data class AggregatedParticipantStats(
    val totalKills: Int,
    val totalDamageDealt: Double,
    val totalTop10s: Int,
    val totalTimeSurvived: Double,
    val bestRankPoint: Double,
    val modeWins: Int,
    val modeRoundsPlayed: Int,
    val modeHeadshotKills: Int,
    val modeKD: Double
)

data class PerMatchSkillData(
    val matchId: String,
    val label: String,
    val participantCount: Int,
    val botCount: Int,
    val minKills: Double,
    val maxKills: Double,
    val medianKills: Double,
    val avgKills: Double,
    val minDamage: Double,
    val maxDamage: Double,
    val medianDamage: Double,
    val avgDamage: Double,
    val minKD: Double,
    val maxKD: Double,
    val medianKD: Double,
    val avgKD: Double,
    val minBestRankPoint: Double,
    val maxBestRankPoint: Double,
    val medianBestRankPoint: Double,
    val avgBestRankPoint: Double,
    val rankedPlayerCount: Int,
    val minTimeSurvived: Double,
    val maxTimeSurvived: Double,
    val medianTimeSurvived: Double,
    val avgTimeSurvived: Double,
    val minWins: Double,
    val maxWins: Double,
    val medianWins: Double,
    val avgWins: Double,
    val minRoundsPlayed: Double,
    val maxRoundsPlayed: Double,
    val medianRoundsPlayed: Double,
    val avgRoundsPlayed: Double,
    val minHeadshotKills: Double,
    val maxHeadshotKills: Double,
    val medianHeadshotKills: Double,
    val avgHeadshotKills: Double,
    val minTop10s: Double,
    val maxTop10s: Double,
    val medianTop10s: Double,
    val avgTop10s: Double,
    val playerSnapshotKills: Double,
    val playerSnapshotDamage: Double,
    val playerSnapshotKD: Double,
    val playerSnapshotBestRankPoint: Double,
    val playerSnapshotTimeSurvived: Double,
    val playerSnapshotWins: Double,
    val playerSnapshotRoundsPlayed: Double,
    val playerSnapshotHeadshotKills: Double,
    val playerSnapshotTop10s: Double
)

data class ParticipantView(
    val accountId: String,
    val playerName: String,
    val kills: Int,
    val damage: Double,
    val wins: Int,
    val roundsPlayed: Int,
    val headshotKills: Int,
    val top10s: Int,
    val timeSurvived: Double,
    val bestRankPoint: Double,
    val kd: Double
)

data class PlayerAggregatedView(
    val totalKills: Int,
    val totalDamage: Double,
    val totalTop10s: Int,
    val totalWins: Int,
    val totalRoundsPlayed: Int,
    val totalHeadshotKills: Int,
    val totalTimeSurvived: Double,
    val bestRankPoint: Double
)
