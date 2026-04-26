package org.traanite.pubgity.pubgapi

import org.traanite.pubgity.match.MatchParticipantStats
import org.traanite.pubgity.match.ParticipantGameModeStats
import org.traanite.pubgity.match.ParticipantLifetimeStats
import org.traanite.pubgity.match.ParticipantModeStats
import org.traanite.pubgity.player.GameModeStats
import org.traanite.pubgity.player.LifetimeStats
import org.traanite.pubgity.player.ModeStats

// --- Map to player/ lifetime stats types ---

fun LifetimeStatsAttributes.toLifetimeStats() = LifetimeStats(
    bestRankPoint = bestRankPoint,
    gameModeStats = gameModeStats.toGameModeStats()
)

fun ApiGameModeStats.toGameModeStats() = GameModeStats(
    duo = duo?.toModeStats(),
    duoFpp = duoFpp?.toModeStats(),
    solo = solo?.toModeStats(),
    soloFpp = soloFpp?.toModeStats(),
    squad = squad?.toModeStats(),
    squadFpp = squadFpp?.toModeStats()
)

fun ApiModeStats.toModeStats() = ModeStats(
    assists = assists,
    boosts = boosts,
    dBNOs = dBNOs,
    dailyKills = dailyKills,
    dailyWins = dailyWins,
    damageDealt = damageDealt,
    days = days,
    headshotKills = headshotKills,
    heals = heals,
    killPoints = killPoints,
    kills = kills,
    longestKill = longestKill,
    longestTimeSurvived = longestTimeSurvived,
    losses = losses,
    maxKillStreaks = maxKillStreaks,
    mostSurvivalTime = mostSurvivalTime,
    rankPoints = rankPoints,
    rankPointsTitle = rankPointsTitle,
    revives = revives,
    rideDistance = rideDistance,
    roadKills = roadKills,
    roundMostKills = roundMostKills,
    roundsPlayed = roundsPlayed,
    suicides = suicides,
    swimDistance = swimDistance,
    teamKills = teamKills,
    timeSurvived = timeSurvived,
    top10s = top10s,
    vehicleDestroys = vehicleDestroys,
    walkDistance = walkDistance,
    weaponsAcquired = weaponsAcquired,
    weeklyKills = weeklyKills,
    weeklyWins = weeklyWins,
    winPoints = winPoints,
    wins = wins
)

// --- Map to match/ participant lifetime stats types ---

fun LifetimeStats.toParticipantLifetimeStats() = ParticipantLifetimeStats(
    bestRankPoint = bestRankPoint,
    gameModeStats = gameModeStats.toParticipantGameModeStats()
)

fun GameModeStats.toParticipantGameModeStats() = ParticipantGameModeStats(
    duo = duo?.toParticipantModeStats(),
    duoFpp = duoFpp?.toParticipantModeStats(),
    solo = solo?.toParticipantModeStats(),
    soloFpp = soloFpp?.toParticipantModeStats(),
    squad = squad?.toParticipantModeStats(),
    squadFpp = squadFpp?.toParticipantModeStats()
)

fun ModeStats.toParticipantModeStats() = ParticipantModeStats(
    assists = assists,
    boosts = boosts,
    dBNOs = dBNOs,
    dailyKills = dailyKills,
    dailyWins = dailyWins,
    damageDealt = damageDealt,
    days = days,
    headshotKills = headshotKills,
    heals = heals,
    killPoints = killPoints,
    kills = kills,
    longestKill = longestKill,
    longestTimeSurvived = longestTimeSurvived,
    losses = losses,
    maxKillStreaks = maxKillStreaks,
    mostSurvivalTime = mostSurvivalTime,
    rankPoints = rankPoints,
    rankPointsTitle = rankPointsTitle,
    revives = revives,
    rideDistance = rideDistance,
    roadKills = roadKills,
    roundMostKills = roundMostKills,
    roundsPlayed = roundsPlayed,
    suicides = suicides,
    swimDistance = swimDistance,
    teamKills = teamKills,
    timeSurvived = timeSurvived,
    top10s = top10s,
    vehicleDestroys = vehicleDestroys,
    walkDistance = walkDistance,
    weaponsAcquired = weaponsAcquired,
    weeklyKills = weeklyKills,
    weeklyWins = weeklyWins,
    winPoints = winPoints,
    wins = wins
)

// --- Map per-match participant stats from API ---

fun IncludedStats.toMatchParticipantStats() = MatchParticipantStats(
    kills = kills,
    assists = assists,
    dBNOs = dBNOs,
    damageDealt = damageDealt,
    deathType = deathType,
    headshotKills = headshotKills,
    heals = heals,
    boosts = boosts,
    killPlace = killPlace,
    killStreaks = killStreaks,
    longestKill = longestKill,
    revives = revives,
    rideDistance = rideDistance,
    roadKills = roadKills,
    swimDistance = swimDistance,
    teamKills = teamKills,
    timeSurvived = timeSurvived,
    vehicleDestroys = vehicleDestroys,
    walkDistance = walkDistance,
    weaponsAcquired = weaponsAcquired,
    winPlace = winPlace
)

