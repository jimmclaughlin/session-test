package service

interface LoginService {

    rx.Observable<String> login (String user, String pass)

    rx.Observable<Long> logout (String user, String sessionId)

    rx.Observable<Boolean> isAuthenticated (String user, String sessionId)

}