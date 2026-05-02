package org.traanite.pubgity.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler
import org.traanite.pubgity.user.PubgityOidcUserService

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val oidcUserService: PubgityOidcUserService,
    private val clientRegistrationRepository: ClientRegistrationRepository
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers("/profile/**").authenticated()
                    .requestMatchers("/jobs/**").hasAnyRole("ADMIN", "MODERATOR")
                    .anyRequest().permitAll()
            }
            .oauth2Login { login ->
                login.userInfoEndpoint { endpoint ->
                    endpoint.oidcUserService(oidcUserService)
                }
            }
            .logout { logout ->
                logout.logoutSuccessHandler(oidcLogoutSuccessHandler())
            }
        return http.build()
    }

    /**
     * Performs OIDC RP-initiated logout — redirects to the provider's end_session_endpoint
     * after local session invalidation.
     */
    private fun oidcLogoutSuccessHandler(): LogoutSuccessHandler {
        val handler = OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository)
        handler.setPostLogoutRedirectUri("{baseUrl}/")
        return handler
    }
}
