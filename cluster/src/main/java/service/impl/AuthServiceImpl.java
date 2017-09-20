package service.impl;

import rx.Observable;
import service.AuthService;
import service.User;

public class AuthServiceImpl implements AuthService {

    @Override
    public Observable<User> authenticate (String username, String password) {
        return Observable.just (new User ("user123"));
    }
}
