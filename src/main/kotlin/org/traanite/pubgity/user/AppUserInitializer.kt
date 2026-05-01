package org.traanite.pubgity.user

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * One-time first-admin bootstrap.
 *
 * Set the `APP_ADMIN_SUB` environment variable to the OIDC `sub` of the user you want to promote
 * to ADMIN before the app receives its first login. Idempotent – does nothing once any ADMIN exists.
 *
 * How to find the `sub`: see OIDC_SETUP.md.
 */
@Component
class AppUserInitializer(
    private val appUserService: AppUserService
) : ApplicationRunner {

    companion object {
        private val logger = LoggerFactory.getLogger(AppUserInitializer::class.java)
    }

    override fun run(args: ApplicationArguments) {
        val adminSub = System.getenv("APP_ADMIN_SUB")
        if (adminSub.isNullOrBlank()) {
            logger.debug("APP_ADMIN_SUB not set – skipping first-admin bootstrap")
            return
        }
        appUserService.promoteFirstAdmin(adminSub)
    }
}

