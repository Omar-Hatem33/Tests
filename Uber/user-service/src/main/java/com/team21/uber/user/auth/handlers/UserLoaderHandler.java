package com.team21.uber.user.auth.handlers;

import com.team21.uber.user.auth.AuthContext;
import com.team21.uber.user.auth.AuthHandler;
import com.team21.uber.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;

public class UserLoaderHandler extends AuthHandler {
    private final UserRepository userRepository;

    public UserLoaderHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doHandle(AuthContext ctx) {
        if (ctx.getUserId() == null || !userRepository.existsById(ctx.getUserId())) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
        }
    }
}
