package br.com.camplana.security;

import br.com.camplana.handler.SecurityErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final UsuarioDetailsService usuarioDetailsService;
    private final SecurityErrorHandler securityErrorHandler;
    private  final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(req -> {

                    // QUALQUER UM -> HEALTHCHECK E LOGIN
                    req.requestMatchers(HttpMethod.GET, "/api/health").permitAll();
                    req.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll();

                    req.requestMatchers("/error").permitAll();

                    // GERENTE e SUPERVISOR -> CHAT E SIMULACOES
                    req.requestMatchers("/api/chat/**").hasAnyRole("GERENTE", "SUPERVISOR");
                    req.requestMatchers("/api/simulacoes/**").hasAnyRole("GERENTE", "SUPERVISOR");

                    // SUPERVISOR -> DECIDE E RE-SIMULA CAMPANHA
                    req.requestMatchers(HttpMethod.POST, "/api/campanhas/*/decidir").hasRole("SUPERVISOR");
                    req.requestMatchers(HttpMethod.POST, "/api/campanhas/*/versoes/*/resimular").hasRole("SUPERVISOR");

                    // SUPERVISOR -> FILA DE APROVACAO (antes do /** abaixo: vale a primeira linha que casa)
                    req.requestMatchers(HttpMethod.GET, "/api/campanhas/fila").hasRole("SUPERVISOR");

                    // GERENTE e SUPERVISOR -> CRIA CAMPANHA
                    req.requestMatchers("/api/campanhas/**").hasAnyRole("GERENTE", "SUPERVISOR");

                    //ADMIN -> CONTROLE SOBRE USUARIOS
                    req.requestMatchers("/api/usuarios/**").hasRole("ADMIN");

                    //SUPERVISOR -> PODE IMPORTAR DADOS
                    req.requestMatchers("/api/importacao/**").hasRole("SUPERVISOR");

                    req.anyRequest().denyAll();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }


    // CONFERE LOGIN ( EMAIL E SENHA ) E BUSCA BANCO
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider(usuarioDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    // IMPEDE O SPRING BOOT DE REGISTRAR O SecurityFilter TAMBÉM NO TOMCAT
    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilterRegistration(SecurityFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
