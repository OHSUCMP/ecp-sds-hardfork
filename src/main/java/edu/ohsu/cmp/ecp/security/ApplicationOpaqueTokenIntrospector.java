package edu.ohsu.cmp.ecp.security;

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

	private final OAuth2ResourceServerProperties properties;

	public ApplicationOpaqueTokenIntrospector(OAuth2ResourceServerProperties properties) {
		this.properties = properties;
	}

	@Override
	public OAuth2AuthenticatedPrincipal introspect(String token) {
		return withAdditionalRole("USER", introspectorForToken(token).introspect(token));
	}

	private OAuth2AuthenticatedPrincipal withAdditionalRole(String role, OAuth2AuthenticatedPrincipal principal) {
		Collection<GrantedAuthority> authorities = new ArrayList<>();
		authorities.addAll(principal.getAuthorities());
		authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
		return new DefaultOAuth2AuthenticatedPrincipal(principal.getAttributes(), authorities);
	}

	private OpaqueTokenIntrospector introspectorForToken(String token) {
		return introspectorWithUri(properties.getOpaquetoken().getIntrospectionUri());
	}

	private OpaqueTokenIntrospector introspectorWithUri(String introspectionUri) {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getInterceptors().add(new IntrospectorReflexiveAuthenticationInterceptor());
		OpaqueTokenIntrospector introspector = new NimbusOpaqueTokenIntrospector(introspectionUri, restTemplate);
		return introspector;
	}
}
