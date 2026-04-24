package org.traanite.pubgity.controller

import org.bson.types.ObjectId
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

    @GetMapping
    fun admin(model: Model): String {
        model.addAttribute("players", playerRepository.findAll())
        model.addAttribute("jobs", jobRepository.findAllByOrderByCreatedAtDesc())
        return "admin"
    }

    @PostMapping("/players/add")
    fun addPlayer(@RequestParam playerName: String): String {
        val trimmed = playerName.trim()
        if (trimmed.isNotBlank() && playerRepository.findByPlayerName(trimmed) == null) {
            playerRepository.save(Player(playerName = trimmed))
        }
        return "redirect:/admin"
    }

    @PostMapping("/players/{id}/remove")
    fun removePlayer(@PathVariable id: String): String {
        playerRepository.deleteById(ObjectId(id))
        return "redirect:/admin"
    }

    @PostMapping("/players/{id}/update")
    fun updatePlayer(@PathVariable id: String): String {
        val player = playerRepository.findById(ObjectId(id)).orElseThrow()
        jobRepository.save(
            UpdateJob(
                accountId = player.accountId,
                playerName = player.playerName
            )
        )
        return "redirect:/admin"
    }
}

