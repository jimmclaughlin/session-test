package service;

import groovy.transform.Immutable;

import java.util.HashMap;
import java.util.Map;

@Immutable
public class Session {

    /**
     * The application the user is accessing
     */
    private final String app;
    /**
     * The userId
     */
    private final String userId;
    /**
     * Times session has been read
     */
    private final int reads;
    /**
     * Times session has been written
     */
    private final int writes;
    /**
     * Ip address from which this session request came
     */
    private final String ip;
    /**
     * Time-to-live for session in seconds.
     * <p>
     * *Optional*
     * If not provided default will be 1800
     */
    private final int ttl;
    /**
     * Millisecond timestamp of last_access
     */
    private final long lastAccess;
    /**
     * session token
     */
    private final String token;
    /**
     * Additional Data to store in session
     */
    private final Map<String, Object> data;

    public Session (String app, String userId, int reads, int writes, String ip, int ttl, long lastAccess, String token, Map<String, Object> data) {
        this.app = app;
        this.userId = userId;
        this.reads = reads;
        this.writes = writes;
        this.ip = ip;
        this.ttl = ttl;
        this.lastAccess = lastAccess;
        this.token = token;
        this.data = data;
    }


    public final String getApp () {
        return app;
    }

    public final String getUserId () {
        return userId;
    }

    public final int getReads () {
        return reads;
    }

    public final int getWrites () {
        return writes;
    }

    public final String getIp () {
        return ip;
    }

    public final int getTtl () {
        return ttl;
    }

    public final long getLastAccess () {
        return lastAccess;
    }

    public final String getToken () {
        return token;
    }

    public final Map<String, Object> getData () {
        return data;
    }


}
