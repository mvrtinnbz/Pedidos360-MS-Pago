package com.pedidos360.ms_pago.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

// Lee la identidad del usuario desde el JWT de Microsoft Entra ID (igual que ms-orders).
public final class UsuarioActual {

    private UsuarioActual() {
    }

    // Mismo valor que account.username en MSAL (UPN / correo).
    public static String usuarioId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return null;
        }
        var jwt = jwtAuth.getToken();
        for (String claim : new String[]{"preferred_username", "upn", "unique_name", "email"}) {
            String valor = jwt.getClaimAsString(claim);
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return jwt.getSubject();
    }
}