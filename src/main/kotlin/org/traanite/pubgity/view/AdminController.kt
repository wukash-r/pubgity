package org.traanite.pubgity.view

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.user.AppRole
import org.traanite.pubgity.user.AppUserService

@Controller
@RequestMapping("/admin")
class AdminController(
    private val appUserService: AppUserService,
    private val playerService: PlayerService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AdminController::class.java)
    }

    @GetMapping("/users")
    fun listUsers(model: Model): String {
        model.addAttribute("users", appUserService.findAll())
        model.addAttribute("players", playerService.findAll())
        model.addAttribute("roles", AppRole.entries)
        return "admin/users"
    }

    @PostMapping("/users/{id}/role")
    fun changeRole(@PathVariable id: String, @RequestParam role: AppRole): String {
        appUserService.assignRole(ObjectId(id), role)
        logger.info("Admin changed role of user {} to {}", id, role)
        return "redirect:/admin/users"
    }

    @PostMapping("/users/{id}/moderator-constraints")
    fun updateModeratorConstraints(
        @PathVariable id: String,
        @RequestParam(defaultValue = "5") maxQueueSize: Int,
        @RequestParam(required = false) allowedPlayerIds: List<String>?
    ): String {
        val playerIds = allowedPlayerIds?.map { ObjectId(it) }?.toSet() ?: emptySet()
        appUserService.updateModeratorConstraints(ObjectId(id), playerIds, maxQueueSize)
        logger.info("Admin updated moderator constraints for user {}: players={}, maxQueue={}", id, playerIds.size, maxQueueSize)
        return "redirect:/admin/users"
    }
}

