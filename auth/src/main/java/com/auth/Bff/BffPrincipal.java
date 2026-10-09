
package com.auth.bff;

import java.io.Serializable;

public record BffPrincipal(
    String sub,
    String email,
    String displayName
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public BffPrincipal {
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException(
                "Identidad verificada requerida"
            );
        }
    }
}
