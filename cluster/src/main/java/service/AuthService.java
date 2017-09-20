package service;

import rx.Observable;

public interface AuthService {

    Observable<User> authenticate (String username, String password);
}
