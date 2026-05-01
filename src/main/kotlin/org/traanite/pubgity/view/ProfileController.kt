package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.user.AppUserService

@Controller
@RequestMapping("/profile")
class ProfileController(
    private val appUserService: AppUserService,
    private val playerService: PlayerService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ProfileController::class.java)
    }

    @GetMapping
    fun profile(auth: Authentication, model: Model): String {
        val appUser = appUserService.findByAuth(auth) ?: return "redirect:/"
        val linkedPlayer = appUser.linkedPlayerId?.let { playerService.findById(it) }
        model.addAttribute("appUser", appUser)
        model.addAttribute("linkedPlayer", linkedPlayer)
        return "profile"
    }

    @PostMapping("/username")
    fun updateUsername(
        auth: Authentication,
        @RequestParam username: String,
        @RequestParam(required = false) playerSearch: String?
    ): String {
        val appUser = appUserService.findByAuth(auth) ?: return "redirect:/"
        try {
            appUserService.updateUsername(appUser.id!!, username)
        } catch (e: IllegalArgumentException) {
            return "redirect:/profile?error=invalidUsername"
        }
        return "redirect:/profile"
    }

    @PostMapping("/link-player")
    fun linkPlayer(auth: Authentication, @RequestParam playerName: String): String {
        val appUser = appUserService.findByAuth(auth) ?: return "redirect:/"
        val player = playerService.findByPlayerName(playerName.trim())
            ?: return "redirect:/profile?error=playerNotFound"
        appUserService.linkPlayer(appUser.id!!, player.id!!)
        logger.info("User {} linked to player '{}'", appUser.sub, playerName)
        return "redirect:/profile"
    }

    @PostMapping("/unlink-player")
    fun unlinkPlayer(auth: Authentication): String {
        val appUser = appUserService.findByAuth(auth) ?: return "redirect:/"
        appUserService.unlinkPlayer(appUser.id!!)
        return "redirect:/profile"
    }
}


