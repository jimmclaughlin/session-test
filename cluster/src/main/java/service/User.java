package service;

import groovy.transform.Immutable;

import java.util.HashMap;

@Immutable
public class User {

    public User (String userId) {
        this.userId = userId;
    }


    public final String getUserId () {
        return userId;
    }

    private final String userId;
}
