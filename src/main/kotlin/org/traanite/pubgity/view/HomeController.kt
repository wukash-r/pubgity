package org.traanite.pubgity.view

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.user.AppUserService

@Controller
class HomeController(
    private val appUserService: AppUserService,
    private val playerService: PlayerService,
    private val playerStatsViewService: PlayerStatsViewService
) {

    @GetMapping("/")
    fun home(auth: Authentication?, model: Model): String {
        val isLoggedIn = auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken
        if (!isLoggedIn) return "home"

        val appUser = appUserService.findByAuth(auth!!) ?: return "home"
        val linkedPlayerId = appUser.linkedPlayerId

        if (linkedPlayerId == null) {
            model.addAttribute("noLinkedPlayer", true)
            return "home"
        }

        val player = playerService.findById(linkedPlayerId)
        if (player == null) {
            model.addAttribute("noLinkedPlayer", true)
            return "home"
        }

        playerStatsViewService.populateModel(player, model)
        return "home"
    }
}
