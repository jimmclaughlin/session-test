package service;

import rx.Observable;

public interface SessionService {
    Observable<String> create (Session session);

    Observable<Session> get (String app, String token);

    Observable<Long> kill (String app, String token);

    Observable<Long> killall (String app);

    Observable<Long> killall (String app, String userId);

    Observable<Long> countActiveUsers (String app, Long deltaMS);

    Observable<Long> countActiveSessions (String app, String userId);

    Observable<Session> getActiveSessions (String app, Long deltaMS);

    Observable<Session> getActiveSessionsForUser (String app, String userId);
}
