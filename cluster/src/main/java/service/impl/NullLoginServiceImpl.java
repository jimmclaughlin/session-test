package service.impl;

import rx.Observable;
import service.Session;
import service.SessionService;

import java.util.UUID;

public class NullLoginServiceImpl implements SessionService {


    @Override
    public Observable<String> create (Session session) {
        return null;
    }

    @Override
    public Observable<Session> get (String app, String token) {
        return null;
    }

    @Override
    public Observable<Long> kill (String app, String token) {
        return null;
    }

    @Override
    public Observable<Long> killall (String app) {
        return null;
    }

    @Override
    public Observable<Long> killall (String app, String userId) {
        return null;
    }

    @Override
    public Observable<Long> countActiveUsers (String app, Long deltaMS) {
        return null;
    }

    @Override
    public Observable<Long> countActiveSessions (String app, String userId) {
        return null;
    }

    @Override
    public Observable<Session> getActiveSessions (String app, Long deltaMS) {
        return null;
    }

    @Override
    public Observable<Session> getActiveSessionsForUser (String app, String userId) {
        return null;
    }
}
