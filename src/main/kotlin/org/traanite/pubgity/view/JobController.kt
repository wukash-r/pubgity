package org.traanite.pubgity.view

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.traanite.pubgity.import.JobService
import org.traanite.pubgity.import.JobType
import org.traanite.pubgity.player.PlayerService

@Controller
@RequestMapping("/jobs")
class JobController(
    private val playerService: PlayerService,
    private val jobService: JobService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JobController::class.java)
    }

    @GetMapping
    fun jobs(model: Model): String {
        val players = playerService.findAll()
        // todo jobDTO, sort jobs by createdAt desc but also group into queued and the rest
        val jobs = jobService.getJobs()
        logger.debug("Jobs page loaded: {} players, {} jobs", players.size, jobs.size)
        model.addAttribute("players", players)
        model.addAttribute("fetchMatchStatsJobs", jobs.filter { it.jobType == JobType.FETCH_MATCH_STATS })
        model.addAttribute("fetchPlayerMatchesJobs", jobs.filter { it.jobType == JobType.FETCH_PLAYER_MATCHES })
        return "jobs"
    }

    @PostMapping("/{jobId}/retry")
    fun retryJob(@PathVariable jobId: ObjectId): String {
        jobService.retryJob(jobId)
        return "redirect:/jobs"
    }

    @PostMapping("/players/add")
    fun addPlayer(@RequestParam playerName: String): String {
        playerService.addPlayer(playerName)
        return "redirect:/jobs"
    }

    @PostMapping("/players/{id}/remove")
    fun removePlayer(@PathVariable id: String): String {
        playerService.removePlayer(ObjectId(id))
        return "redirect:/jobs"
    }

    @PostMapping("/players/{id}/update")
    fun updatePlayer(@PathVariable id: ObjectId, @RequestParam(defaultValue = "5") matchCount: Int): String {
        // todo this should be in jobService entirely
        jobService.queueJob(id, matchCount)
        return "redirect:/jobs"
    }

    @PostMapping("/{id}/cancel")
    fun cancelJob(@PathVariable id: String): String {
        jobService.cancelJob(ObjectId(id))
        return "redirect:/jobs"
    }
}