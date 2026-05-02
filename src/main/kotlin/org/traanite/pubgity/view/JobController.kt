package org.traanite.pubgity.view

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.traanite.pubgity.import.JobService
import org.traanite.pubgity.import.JobType
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.user.AppRole
import org.traanite.pubgity.user.AppUserService

@Controller
@RequestMapping("/jobs")
class JobController(
    private val playerService: PlayerService,
    private val jobService: JobService,
    private val appUserService: AppUserService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JobController::class.java)
    }

    @GetMapping
    fun jobs(model: Model, auth: Authentication): String {
        val appUser = appUserService.findByAuth(auth)
        // todo use security context holder
        //  and pathmatchers already rescript this endpoint so just check if moderator
        val (players, allJobs) = when {
            appUser?.hasRole(AppRole.ADMIN) == true -> playerService.findAll() to jobService.getJobs()
            appUser?.hasRole(AppRole.MODERATOR) == true -> {
                val allowedIds = appUser.moderatorConstraints?.allowedPlayerIds ?: emptySet()
                val allowedPlayers = allowedIds.mapNotNull { playerService.findById(it) }
                val allowedAccountIds = allowedPlayers.mapNotNull { it.accountId }.toSet()
                val jobs = if (allowedAccountIds.isNotEmpty()) jobService.getJobsForAccountIds(allowedAccountIds) else emptyList()
                allowedPlayers to jobs
            }
            else -> playerService.findAll() to jobService.getJobs()
        }

        val isAdmin = appUser?.hasRole(AppRole.ADMIN) == true
        logger.debug("Jobs page: roles={}, {} players, {} jobs", appUser?.roles, players.size, allJobs.size)

        model.addAttribute("players", players)
        model.addAttribute("fetchMatchStatsJobs", allJobs.filter { it.jobType == JobType.FETCH_MATCH_STATS })
        model.addAttribute("fetchPlayerMatchesJobs", allJobs.filter { it.jobType == JobType.FETCH_PLAYER_MATCHES })
        model.addAttribute("canManagePlayers", isAdmin)
        return "jobs"
    }

    @PostMapping("/{jobId}/retry")
    fun retryJob(@PathVariable jobId: ObjectId, auth: Authentication, redirectAttributes: RedirectAttributes): String {
        if (!canAccessJob(jobId, auth)) {
            redirectAttributes.addFlashAttribute("error", "You are not allowed to retry this job.")
            return "redirect:/jobs"
        }
        jobService.retryJob(jobId)
        return "redirect:/jobs"
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/players/add")
    fun addPlayer(@RequestParam playerName: String): String {
        playerService.addPlayer(playerName)
        return "redirect:/jobs"
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/players/{id}/remove")
    fun removePlayer(@PathVariable id: String): String {
        playerService.removePlayer(ObjectId(id))
        return "redirect:/jobs"
    }

    @PostMapping("/players/{id}/update")
    fun updatePlayer(
        @PathVariable id: ObjectId,
        @RequestParam(defaultValue = "5") matchCount: Int,
        auth: Authentication,
        redirectAttributes: RedirectAttributes
    ): String {
        val appUser = appUserService.findByAuth(auth)

        // todo simplify...
        if (appUser?.hasRole(AppRole.MODERATOR) == true && !appUser.hasRole(AppRole.ADMIN)) {
            val constraints = appUser.moderatorConstraints
            if (constraints == null || !constraints.allowedPlayerIds.contains(id)) {
                redirectAttributes.addFlashAttribute("error", "You are not allowed to queue jobs for this player.")
                return "redirect:/jobs"
            }
            if (!jobService.canModeratorQueue(constraints.allowedPlayerIds, constraints.maxQueueSize)) {
                redirectAttributes.addFlashAttribute(
                    "error",
                    "Queue limit reached (max ${constraints.maxQueueSize} active jobs). Wait for existing jobs to complete."
                )
                return "redirect:/jobs"
            }
        }

        jobService.queueJob(id, matchCount)
        return "redirect:/jobs"
    }

    @PostMapping("/{id}/cancel")
    fun cancelJob(@PathVariable id: String, auth: Authentication, redirectAttributes: RedirectAttributes): String {
        val jobId = ObjectId(id)
        if (!canAccessJob(jobId, auth)) {
            redirectAttributes.addFlashAttribute("error", "You are not allowed to cancel this job.")
            return "redirect:/jobs"
        }
        jobService.cancelJob(jobId)
        return "redirect:/jobs"
    }

    /**
     * Checks whether the current user may interact with a job.
     * Admins may access any job. Moderators may only access jobs belonging to their allowed players.
     */
    private fun canAccessJob(jobId: ObjectId, auth: Authentication): Boolean {
        val appUser = appUserService.findByAuth(auth) ?: return false
        if (appUser.hasRole(AppRole.ADMIN)) return true

        val allowedIds = appUser.moderatorConstraints?.allowedPlayerIds ?: return false
        val allowedAccountIds = allowedIds.mapNotNull { playerService.findById(it)?.accountId }.toSet()
        val job = jobService.findJobById(jobId) ?: return false
        return job.accountId != null && job.accountId in allowedAccountIds
    }
}