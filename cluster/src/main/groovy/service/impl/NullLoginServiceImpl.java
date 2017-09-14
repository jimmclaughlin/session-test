package service.impl;

import rx.Observable;
import service.LoginService;

import java.util.UUID;

public class NullLoginServiceImpl implements LoginService {

    @Override
    public Observable<String> login (String user, String pass) {
        return Observable.just (UUID.randomUUID ().toString ());
    }

    @Override
    public Observable<Long> logout (String user, String sessionId) {
        return Observable.just (1L);
    }

    @Override
    public Observable<Boolean> isAuthenticated (String user, String sessionId) {
        return Observable.just (true);
    }
}
