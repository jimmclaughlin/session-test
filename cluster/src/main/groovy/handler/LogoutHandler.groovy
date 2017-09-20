package handler

import ratpack.handling.Context
import ratpack.handling.Handler
import service.SessionService

class LogoutHandler implements Handler {

    private final SessionService loginService

    LogoutHandler (SessionService loginService) {
        this.loginService = loginService
    }

    @Override
    void handle (Context ctx) throws Exception {

    }
}
