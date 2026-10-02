"""FlashStock permission projection for verified AWS HTTP API JWT authorizer context.

SECURITY: This function MUST be bound only to a route with authorization_type=JWT.
Raw Authorization headers or user-submitted bodies are never parsed/trusted as identity.
It is a permissions/introspection endpoint, NOT a global authorizer for other API routes.
"""
import json
import os
import re

ALLOWED_GROUPS = {"USER", "ADMIN"}


def _response(code, message, data=None):
    return {
        "statusCode": code,
        "headers": {"content-type": "application/json", "cache-control": "no-store"},
        "body": json.dumps({"message": message, "data": data}, separators=(",", ":")),
    }


def _groups(value):
    # HTTP API implementations may serialize list claims as JSON/list/text.
    if isinstance(value, list):
        candidates = value
    elif isinstance(value, str):
        value = value.strip()
        if value.startswith("[") and value.endswith("]"):
            try:
                maybe = json.loads(value)
            except json.JSONDecodeError:
                maybe = None
            if isinstance(maybe, list):
                candidates = maybe
            else:
                candidates = value[1:-1].split(",")
        else:
            candidates = value.split(",")
    else:
        return set()
    return {g.strip().strip('"\'') for g in candidates if isinstance(g, str) and g.strip().strip('"\'') in ALLOWED_GROUPS}


def lambda_handler(event, context):
    # API Gateway has already verified JWKS signature, issuer, time and audience/client_id.
    # Also check claims defensively and fail closed if route/configuration is wrong.
    route = event.get("routeKey") or ""
    if route != "GET /api/auth/permissions":
        return _response(403, "Ruta no permitida")
    jwt = ((event.get("requestContext") or {}).get("authorizer") or {}).get("jwt")
    if not isinstance(jwt, dict) or not isinstance(jwt.get("claims"), dict):
        return _response(401, "JWT validado por API Gateway requerido")
    claims = jwt["claims"]
    issuer = os.environ.get("COGNITO_ISSUER_URL", "")
    client_id = os.environ.get("COGNITO_APP_CLIENT_ID", "")
    if (not issuer or not client_id or claims.get("iss") != issuer
            or claims.get("token_use") != "access" or claims.get("client_id") != client_id
            or not isinstance(claims.get("sub"), str) or not claims["sub"]):
        return _response(401, "Token o configuracion incompatible")
    scopes_raw = claims.get("scope", "")
    scopes = {p for p in scopes_raw.split() if p} if isinstance(scopes_raw, str) else set()
    if "openid" not in scopes:
        return _response(403, "Scope openid requerido")
    groups = _groups(claims.get("cognito:groups"))
    admin = "ADMIN" in groups
    user = "USER" in groups or admin
    roles = (["ROLE_USER"] if user else []) + (["ROLE_ADMIN"] if admin else [])
    permissions = ["auth:me"]
    if user or admin:
        permissions.extend(["orders:create", "orders:read:self", "shipping:read"])
    if admin:
        permissions.extend(["admin:metrics", "inventory:write", "orders:read:all", "orders:status:write", "shipping:write"])
    return _response(200, "Permisos autenticados", {
        "authenticated": True,
        "admin": admin,
        "email": claims.get("email") if isinstance(claims.get("email"), str) else None,
        "displayName": claims.get("username") or "Usuario",
        "subject": claims["sub"],
        "authorities": roles,
        "scopes": sorted(scopes),
        "permissions": permissions,
    })
