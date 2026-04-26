package org.traanite.pubgity.job

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/jobs")
class JobController(
    private val jobService: JobService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun jobs(model: Model): String {
        val players = jobService.getPlayers()
        val jobs = jobService.getJobs()
        logger.debug("Jobs page loaded: {} players, {} jobs", players.size, jobs.size)
        model.addAttribute("players", players)
        model.addAttribute("jobs", jobs)
        return "jobs"
    }

    @PostMapping("/players/add")
    fun addPlayer(@RequestParam playerName: String): String {
        jobService.addPlayer(playerName)
        return "redirect:/jobs"
    }

    @PostMapping("/players/{id}/remove")
    fun removePlayer(@PathVariable id: String): String {
        jobService.removePlayer(ObjectId(id))
        return "redirect:/jobs"
    }

    @PostMapping("/players/{id}/update")
    fun updatePlayer(@PathVariable id: String, @RequestParam(defaultValue = "5") matchCount: Int): String {
        val player = jobService.getPlayers().firstOrNull { it.id.toString() == id }
            ?: return "redirect:/jobs"
        jobService.queueJob(player.accountId, player.playerName, matchCount)
        return "redirect:/jobs"
    }

    @PostMapping("/{id}/cancel")
    fun cancelJob(@PathVariable id: String): String {
        jobService.cancelJob(ObjectId(id))
        return "redirect:/jobs"
    }
}

