package com.auth.config;

import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** Only receives JWTs AFTER NimbusJwtDecoder verified signature, issuer and custom validators. */
public final class CognitoAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new LinkedHashSet<>(scopes.convert(jwt));
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null) {
            for (String group : groups) {
                if ("ADMIN".equals(group)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                } else if ("USER".equals(group)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                }
            }
        }
        // Do not elevate a role from email addresses, usernames, or a client-supplied header.
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
