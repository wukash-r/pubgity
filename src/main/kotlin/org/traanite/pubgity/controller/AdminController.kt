package org.traanite.pubgity.controller

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.traanite.pubgity.model.Player
import org.traanite.pubgity.model.UpdateJob
import org.traanite.pubgity.repository.PlayerRepository
import org.traanite.pubgity.repository.UpdateJobRepository

@Controller
@RequestMapping("/admin")
class AdminController(
    private val playerRepository: PlayerRepository,
    private val jobRepository: UpdateJobRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun admin(model: Model): String {
        val players = playerRepository.findAll()
        val jobs = jobRepository.findAllByOrderByCreatedAtDesc()
        logger.debug("Admin page loaded: {} players, {} jobs", players.size, jobs.size)
        model.addAttribute("players", players)
        model.addAttribute("jobs", jobs)
        return "admin"
    }

    @PostMapping("/players/add")
    fun addPlayer(@RequestParam playerName: String): String {
        val trimmed = playerName.trim()
        if (trimmed.isNotBlank() && playerRepository.findByPlayerName(trimmed) == null) {
            playerRepository.save(Player(playerName = trimmed))
            logger.info("Added player to watchlist: {}", trimmed)
        } else {
            logger.warn("Player '{}' already exists or name is blank, skipping add", trimmed)
        }
        return "redirect:/admin"
    }

    @PostMapping("/players/{id}/remove")
    fun removePlayer(@PathVariable id: String): String {
        logger.info("Removing player with id: {}", id)
        playerRepository.deleteById(ObjectId(id))
        return "redirect:/admin"
    }

    @PostMapping("/players/{id}/update")
    fun updatePlayer(@PathVariable id: String): String {
        val player = playerRepository.findById(ObjectId(id)).orElseThrow()
        val job = jobRepository.save(
            UpdateJob(
                accountId = player.accountId,
                playerName = player.playerName
            )
        )
        logger.info("Queued update job {} for player '{}' (accountId={})", job.id, player.playerName, player.accountId)
        return "redirect:/admin"
    }
}
