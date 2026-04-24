package org.traanite.pubgity.client

import org.traanite.pubgity.model.GameModeStats
import org.traanite.pubgity.model.LifetimeStats
import org.traanite.pubgity.model.ModeStats

fun LifetimeStatsAttributes.toModel() = LifetimeStats(
    bestRankPoint = bestRankPoint,
    gameModeStats = gameModeStats.toModel()
)

fun ApiGameModeStats.toModel() = GameModeStats(
    duo = duo?.toModel(),
    duoFpp = duoFpp?.toModel(),
    solo = solo?.toModel(),
    soloFpp = soloFpp?.toModel(),
    squad = squad?.toModel(),
    squadFpp = squadFpp?.toModel()
)

fun ApiModeStats.toModel() = ModeStats(
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

