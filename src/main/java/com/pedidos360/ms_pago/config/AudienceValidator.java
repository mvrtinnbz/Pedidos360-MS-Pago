package com.pedidos360.ms_pago.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final String EXPECTED_AUDIENCE =
            "api://d0261291-fbfe-40fb-b1b0-fae49a04f31f";

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {

        if (jwt.getAudience() != null &&
            jwt.getAudience().contains(EXPECTED_AUDIENCE)) {

            return OAuth2TokenValidatorResult.success();
        }

        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "El token no está destinado a Pedidos360-API",
                null
        );

        return OAuth2TokenValidatorResult.failure(error);
    }
}