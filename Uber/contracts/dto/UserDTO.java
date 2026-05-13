package com.team21.uber.contracts.dto;

public record UserDTO(
        Long id,
        String name,
        String email,
        String phone,
        String role,
        String status
) {}