package com.checkai.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private boolean success;
    private String token;
    private String userId;
    private String username;
    private String nickname;
    private String message;
}
