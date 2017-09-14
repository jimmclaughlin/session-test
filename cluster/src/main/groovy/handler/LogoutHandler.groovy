package handler

import ratpack.handling.Context
import ratpack.handling.Handler
import service.LoginService

class LogoutHandler implements Handler {

    private final LoginService loginService

    LogoutHandler (LoginService loginService) {
        this.loginService = loginService
    }

    @Override
    void handle (Context ctx) throws Exception {

    }
}
