package com.auth.dtos;

public record BffLoginResponse(
    boolean authenticated,
    String challenge
) {}