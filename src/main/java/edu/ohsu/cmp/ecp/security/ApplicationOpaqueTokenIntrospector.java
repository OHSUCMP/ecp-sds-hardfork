package edu.ohsu.cmp.ecp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.NimbusOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collection;

@Service
public class ApplicationOpaqueTokenIntrospector implements OpaqueTokenIntrospector {
	private static final Logger logger = LoggerFactory.getLogger(ApplicationOpaqueTokenIntrospector.class);

	private final OpaqueTokenIntrospector introspector;

	public ApplicationOpaqueTokenIntrospector(OAuth2ResourceServerProperties properties) {
		this.introspector = new NimbusOpaqueTokenIntrospector(
			properties.getOpaquetoken().getIntrospectionUri(),
			new RestTemplate()
		);
		logger.info("created introspector with uri={}", properties.getOpaquetoken().getIntrospectionUri());
	}

	@Override
	public OAuth2AuthenticatedPrincipal introspect(String token) {
		return withAdditionalRole("USER", introspector.introspect(token));
	}

	private OAuth2AuthenticatedPrincipal withAdditionalRole(String role, OAuth2AuthenticatedPrincipal principal) {
		Collection<GrantedAuthority> authorities = new ArrayList<>();
		authorities.addAll(principal.getAuthorities());
		authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
		return new DefaultOAuth2AuthenticatedPrincipal(principal.getAttributes(), authorities);
	}
}