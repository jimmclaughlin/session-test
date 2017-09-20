package service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.lambdaworks.redis.Range;
import com.lambdaworks.redis.ReadFrom;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.masterslave.MasterSlave;
import com.lambdaworks.redis.masterslave.StatefulRedisMasterSlaveConnection;
import com.lambdaworks.redis.support.ConnectionPoolSupport;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.subjects.AsyncSubject;
import service.Session;
import service.SessionService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RedisSentinelSessionServiceImpl implements SessionService {


    private final Object lock = new Object ();
    private final RedisClient redisClient;
    private final String redisURI;
    private volatile StatefulRedisMasterSlaveConnection<String, String> connection;

    private final String namespace;

    private final GenericObjectPool<StatefulRedisConnection<String, String>> pool;

    private final ObjectMapper mapper;


    private final int numReplicate;

    private final int replicateTimeoutMS;

    private final Logger logger;

    public RedisSentinelSessionServiceImpl (
            RedisClient redisClient,
            String redisURI,
            String namespace,
            ObjectMapper mapper,
            int maxIdle,
            int maxTotal,
            int minIdle,
            int numReplicate,
            int replicateTimeoutMS
    ) {
        this.logger = LoggerFactory.getLogger (getClass ());
        this.redisClient = redisClient;
        this.redisURI = redisURI;
        this.namespace = Objects.isNull (namespace) ? "stratus" : namespace;
        this.mapper = mapper;
        this.numReplicate = numReplicate;
        this.replicateTimeoutMS = replicateTimeoutMS;
        connection = MasterSlave.connect (redisClient, new Utf8StringCodec (), RedisURI.create (redisURI));
        connection.setReadFrom (ReadFrom.NEAREST);

        GenericObjectPoolConfig config = new GenericObjectPoolConfig ();
        config.setMaxIdle (maxIdle);
        config.setMaxTotal (maxTotal);
        config.setMinIdle (minIdle);

        this.pool = ConnectionPoolSupport.createGenericObjectPool (
                () -> MasterSlave.connect (redisClient, new Utf8StringCodec (), RedisURI.create (redisURI)), config);

    }

    @Override
    public Observable<String> create (Session session) {
        return withConnection (commands -> commands.multi ()
                .flatMap (multiResponse -> {
                    String token = _makeToken ();
                    // check if multiResponse is "OK"
                    updateTracking (commands, session.getApp (), session.getUserId (), token, session.getTtl ())
                            .subscribe ();
                    updateSessionsByUser (commands, session, token)
                            .subscribe ();
                    createSessionHash (commands, session, token)
                            .subscribe ();
                    return commands.exec ()
                            .toList ()
                            .flatMap (responseList -> {
                                logger.debug ("createSession ResponseList {}", responseList);
                                return commands.waitForReplication (numReplicate, replicateTimeoutMS)
                                      .map (replicas -> token);
                            });
                }));
    }

    private String _makeToken () {
        return UUID.randomUUID ().toString ();
    }

    @Override
    public Observable<Session> get (String app, String token) {
        return get (app, token, true);
    }

    @Override
    public Observable<Long> kill (String app, String token) {
        // clear session
        // remove from:
        //      SESSIONS_BY_USERID
        //      USER_SESSIONS
        //      ACTIVE_SESSIONS
        //      SESSION_HASH


        // if USER_SESSIONS no longer exists
        // then must remove from ACTIVE_USERS also
        return withConnection (commands -> get (app, token, false)
                .flatMap (session -> commands.multi ()
                        .flatMap (multiResponse -> {
                            clearSession (commands, session).subscribe ();
                            return commands.exec ()
                                    .toList ()
                                    .flatMap (execResponse -> {
                                        // execResponse is List<Long>

                                        // the fourth element contains the result of the delete command
                                        Long deleteResponse = (Long) execResponse.get (3);
                                        // the fifth element contains result of exists
                                        // if the result of exists is 0L, then must delete user from ACTIVE_USERS
                                        Long exists = (Long) execResponse.get (4);
                                        if (exists.equals (0L)) {
                                            return commands.zrem (USERS_BY_APP_KEY (namespace, app), session.getUserId ())
                                                    .map (tmp -> deleteResponse);
                                        } else {
                                            return Observable.just (deleteResponse);
                                        }
                                    });
                        })

                )

        );
    }

    @Override
    public Observable<Long> killall (String app) {

        // Get All Sessions for App from SESSIONS_BY_APP which holds $userId:token
        // For each one, we need to delete
        //      the response ($token:userId) from SESSIONS_BY_USERID
        //
        return withConnection (commands -> commands.zrange (SESSIONS_BY_APP_KEY (namespace, app), 0L, -1L)
                .toList ()
                .flatMap (userSessions -> commands.multi ()
                        .flatMap (multiResponse  -> {

                            // Remove from ACTIVE_SESSIONS_KEY
                            deleteFromActiveSessions (commands, app, userSessions).subscribe ();
                            // Remove from SESSIONS_BY_APP
                            deleteFromSessionsByApp (commands, app, userSessions).subscribe ();
                            // Remove from USERS_BY_APP
                            deleteFromUsersByApp (commands, app, userSessions).subscribe ();
                            // Remove from SESSIONS_BY_USER
                            deleteFromSessionsByUser (commands, app, userSessions).subscribe ();
                            // Remove from SESSION_HASH

                            deleteSessionHashes (commands, app, userSessions).subscribe ();

                            return commands.exec ()
                                    .toList ()
                                    .map (execResponse -> 1L);

                        })

                ));
    }

    @Override
    public Observable<Long> killall (String app, String userId) {
        return withConnection (commands -> commands.smembers (SESSIONS_BY_USER_KEY (namespace, app, userId))
                .toList ()
                .flatMap (userTokensList -> commands.multi ()
                        .flatMap (multiResponse -> {
                            // delete from active sessions
                            Observable.merge (userTokensList.stream ()
                                    .map (token -> deleteFromActiveSessions (commands, app, token, userId))
                                    .collect(Collectors.toList()))
                                    .subscribe ();
                            // delete from sessions by app
                            Observable.merge (userTokensList.stream ()
                                    .map (token -> deleteFromSessionsByApp (commands, app, token, userId))
                                    .collect(Collectors.toList()))
                                    .subscribe ();

                            // delete from sessions by user

                            Observable.merge (userTokensList.stream ()
                                    .map (token -> deleteFromSessionsByUser (commands, app, userId, token))
                                    .collect(Collectors.toList()))
                                    .subscribe ();

                            // delete session hash
                            return commands.exec ()
                                    .toList ()
                                    .flatMap (execResponse -> {
                                        Long exists = (Long) execResponse.get (execResponse.size () - 1);
                                        if (exists.equals (0L)) {
                                            return commands.zrem (USERS_BY_APP_KEY (namespace, app), userId)
                                                    .map (ignore -> Long.valueOf (execResponse.size ()));
                                        } else {
                                            return Observable.just (Long.valueOf (execResponse.size ()));
                                        }
                                    });
                        })));
    }

    @Override
    public Observable<Long> countActiveUsers (String app, Long deltaMS) {
        long since = System.currentTimeMillis () - deltaMS;
        return withConnection (commands -> commands.zcount (USERS_BY_APP_KEY (namespace, app),
                Range.from (Range.Boundary.including (since), Range.Boundary.unbounded ())));
    }

    @Override
    public Observable<Long> countActiveSessions (String app, String userId) {
        return withConnection (commands -> commands.scard (SESSIONS_BY_USER_KEY (namespace, app, userId)));
    }

    @Override
    public Observable<Session> getActiveSessions (String app, Long deltaMS) {
        long since = System.currentTimeMillis () - deltaMS;
        return withConnection (commands -> commands.zrange (SESSIONS_BY_APP_KEY (namespace, app), since, Long.MAX_VALUE)
                .flatMap (tokenAndUser -> getSession (commands, app, tokenAndUser.split (":")[0])));
    }

    @Override
    public Observable<Session> getActiveSessionsForUser (String app, String userId) {

        return withConnection (commands -> commands.smembers (SESSIONS_BY_USER_KEY (namespace, app, userId))
                .flatMap (token -> getSession (commands, app, token)));
    }



    private Observable<List<Long>> updateTracking (
            RedisReactiveCommands<String, String> commands,
            String app,
            String userId,
            String token,
            long ttl) {


        return Observable.defer (() -> {
            double now = System.currentTimeMillis () / 1000;
            return Observable.merge (
                    commands.zadd (SESSIONS_BY_APP_KEY (namespace, app), now, SESSIONS_BY_APP_VALUE (token, userId)),
                    commands.zadd (USERS_BY_APP_KEY (namespace, app), now, userId),
                    commands.zadd (ACTIVE_SESSIONS_KEY (namespace), now + ttl, ACTIVE_SESSIONS_VALUE (app, token, userId)))
                    .toList ();
        });
    }


    private Observable<Long> updateSessionsByUser (RedisReactiveCommands<String,String> commands, Session session, String token) {
        return commands.sadd (SESSIONS_BY_USER_KEY (namespace, session.getApp (), session.getUserId ()), token);
    }

    private Observable<String> createSessionHash (RedisReactiveCommands<String, String> commands, Session session, String token) {
        // hmset to ns:app:session.token
        //     id   => userId
        //     r    => 1
        //     w    => 1
        //     ip   => options.ip
        //     last_access   => now
        //     ttl  => time to live in ms
        //     data => json string of data


        Map<String, String> hash = ImmutableMap.<String,String>builder ()
                .put ("id", session.getUserId ())
                .put ("r", "1")
                .put ("w", "1")
                .put ("ip", session.getIp ())
                .put ("last_access", "" + System.currentTimeMillis ())
                .put ("ttl", "" + session.getTtl ())
                .build ();
        return commands.hmset (SESSION_HASH_KEY (namespace, session.getApp (), token), hash);
    }

    private Observable<Session> get (String app, String token, boolean update) {
        if (update) {
            return withConnection (commands -> getSession (commands, app, token)
                .flatMap (session -> commands.multi ()
                    .flatMap (multiResponse -> {
                        // update tracking
                        updateTracking (commands, app, session.getUserId (), token, session.getTtl ())
                                .subscribe ();
                        // update the read count
                        final String sessionHashKey = SESSION_HASH_KEY (namespace, app, token);
                        commands.hincrby (sessionHashKey, "r", 1)
                                .subscribe ();
                        // update last access
                        long now = System.currentTimeMillis ();
                        commands.hset (sessionHashKey, "last_access", ""+ now)
                                .subscribe ();
                        return commands.exec ()
                                .map (resultList -> session);

                    })
                )
            );
        } else {
            return withConnection (commands -> getSession (commands, app, token));
        }
    }

    private Observable<Session> getSession (RedisReactiveCommands<String, String> commands, String app, String token) {
        return commands.hgetall (SESSION_HASH_KEY (namespace, app, token))
                .map (map -> map (map, app, token));
    }

    private Observable<Long> deleteSessionHash (RedisReactiveCommands<String, String> commands, String app, String token) {
        return commands.del (SESSION_HASH_KEY (namespace, app, token));
    }

    private Observable<Long> deleteSessionHashes (RedisReactiveCommands<String, String> commands, String app, List<String> tokenAndUserIds) {
        List<Observable<Long>> byUser = tokenAndUserIds.stream ()
                .map (tokenAndUserId -> {
                    String[] arr = tokenAndUserId.split (":");
                    return deleteSessionHash (commands, app, arr[0]);
                }).collect(Collectors.toList());
        return Observable.merge (byUser);
    }

    private Observable<Long> deleteFromSessionsByUser (RedisReactiveCommands<String, String> commands, String app, List<String> tokenAndUserIds) {
        List<Observable<Long>> byUser = tokenAndUserIds.stream ()
                .map (tokenAndUserId -> {
                    String[] arr = tokenAndUserId.split (":");
                    return deleteFromSessionsByUser (commands, app, arr[1], arr[0]);
                }).collect(Collectors.toList());
        return Observable.merge (byUser);
    }

    private Observable<Long> deleteFromSessionsByUser (RedisReactiveCommands<String, String> commands, String app, String userId, String token) {
        return commands.srem (SESSIONS_BY_USER_KEY (namespace, app, userId), token);
    }

    private Observable<Long> deleteFromUsersByApp (RedisReactiveCommands<String, String> commands, String app, List<String> tokenAndUserIds) {
        List<Observable<Long>> byUser = tokenAndUserIds.stream ()
                .map (tokenAndUserId -> {
                    String[] arr = tokenAndUserId.split (":");
                    return deleteFromUserByApp (commands, app, arr[1]);
                }).collect(Collectors.toList());
        return Observable.merge (byUser);
    }

    private Observable<Long> deleteFromUserByApp (RedisReactiveCommands<String, String> commands, String app, String userId) {
        return commands.zrem (USERS_BY_APP_KEY (namespace, app), userId);
    }

    private Observable<Long> deleteFromSessionsByApp (RedisReactiveCommands<String, String> commands, String app, List<String> tokenAndUserIds) {
        List<Observable<Long>> byUser = tokenAndUserIds.stream ()
                .map (tokenAndUserId -> {
                    String[] arr = tokenAndUserId.split (":");
                    return deleteFromSessionsByApp (commands, app, arr[0], arr[1]);
                }).collect(Collectors.toList());
        return Observable.merge (byUser);
    }

    private Observable<Long> deleteFromSessionsByApp (RedisReactiveCommands<String, String> commands, String app, String token, String userId) {
        return commands.zrem (SESSIONS_BY_APP_KEY (namespace, app), SESSIONS_BY_APP_VALUE (token, userId));
    }

    private Observable<Long> deleteFromActiveSessions (RedisReactiveCommands<String, String> commands, String app, List<String> tokenAndUserIds) {
        List<Observable<Long>> byUser = tokenAndUserIds.stream ()
                .map (tokenAndUserId -> {
                    String[] arr = tokenAndUserId.split (":");
                    return deleteFromActiveSessions (commands, app, arr[0], arr[1]);
                }).collect(Collectors.toList());
        return Observable.merge (byUser);
    }

    private Observable<Long> deleteFromActiveSessions (RedisReactiveCommands<String, String> commands, String app, String token, String userId) {
        return commands.zrem (ACTIVE_SESSIONS_KEY (namespace), ACTIVE_SESSIONS_VALUE (app, token, userId));
    }

    private <R> Observable<R> withConnection (Function<RedisReactiveCommands<String,String>, Observable<R>> closure) {
        return Observable.using (
                this::borrowConnection,
                connection -> closure.apply (connection.reactive ()),
                this::returnConnection
        );
    }



    private Observable<Long> clearSession (RedisReactiveCommands<String, String> commands, Session session) {
        return Observable.merge (
                commands.zrem (SESSIONS_BY_APP_KEY (namespace, session.getApp ()), SESSIONS_BY_APP_VALUE (session.getToken (), session.getUserId ())),
                commands.srem (SESSIONS_BY_USER_KEY (namespace, session.getApp (), session.getUserId ()), session.getToken ()),
                commands.zrem (ACTIVE_SESSIONS_KEY (namespace), ACTIVE_SESSIONS_VALUE (session.getApp (), session.getToken (), session.getUserId ())),
                commands.del (SESSION_HASH_KEY (namespace, session.getApp (), session.getToken ())),
                commands.exists (SESSIONS_BY_USER_KEY (namespace, session.getApp (), session.getUserId ()))
        );
    }

    private StatefulRedisConnection<String, String> borrowConnection () {
        try {
            return pool.borrowObject ();
        } catch (Exception e) {
            throw new RuntimeException (e);
        }
    }

    private void returnConnection (StatefulRedisConnection<String,String> connection) {
        pool.returnObject (connection);
    }


    private Session map (Map<String, String> map, String app, String token) {
        final Map<String, Object> data;
        if (map.containsKey ("data") && null != map.get ("data")) {
            data = toMap (map.get ("data"));
        } else {
            data = null;
        }
        return new Session (
                app,
                map.get ("id"),
                Integer.valueOf (map.get ("r")),
                Integer.valueOf (map.get ("w")),
                map.get ("ip"),
                Integer.valueOf (map.get ("ttl")),
                Long.valueOf (map.get ("last_access")),
                token,
                data);
    }

    private Map<String,Object> toMap (String json) {
        try {
            return mapper.readValue (json, new TypeReference<Map<String,Object>> () {});
        } catch (IOException e) {
            throw new RuntimeException (e);
        }
    }

    /**
     * Creates Key for set to track App sessions in Redis
     *
     * example: stratus:dms:_sessions
     *
     * the value for this key is $token:$userId
     * score for each value is unix timestamp in millis of last access
     *
     * @param namespace the namespace for this session service
     * @param app       the app to which the session belongs
     * @return the key
     */
    private static String SESSIONS_BY_APP_KEY (String namespace, String app) {
        return namespace + ":" + app + ":_sessions";
    }

    /**
     * Creates Set value for tracking App sessions
     *
     * example: asdf665:user123
     *
     * @see #SESSIONS_BY_APP_KEY(String, String)
     *
     * @param token     the session token
     * @param userId    the userId
     * @return the value
     */
    private static String SESSIONS_BY_APP_VALUE (String token, String userId) {
        return token + ":" + userId;
    }

    /**
     * Creates Key for set to track active users in Redis
     *
     * example: stratus:dms:_users
     *
     * values for the set are userIds
     * score for each value is the unix timestamp in millis of last access
     *
     * @param namespace the namespace for this session service
     * @param app       the app for the session
     * @return the key
     */
    private static String USERS_BY_APP_KEY (String namespace, String app) {
        return namespace + ":" + app + ":_users";
    }

    /**
     * Creates key for tracking sessions for app by user
     *
     * example: stratus:dms:us:user123
     *
     * values for the set are session tokens
     *
     * @param namespace the namespace for this session service
     * @param app       the app for this session
     * @param userId    the userId for the user who owns the sessions
     * @return the key
     */
    private static String SESSIONS_BY_USER_KEY (String namespace, String app, String userId) {
        return namespace + ":" + app + ":us:" + userId;
    }

    /**
     * Creates Key for tracking active sessions for all apps
     *
     * example: stratus:SESSIONS
     *
     * Value is $app:$token:$userId
     * score is the unix timestamp in millis of last access *plus* ttl
     *
     * @param namespace the namespace for the session service
     * @return the key
     */
    private static String ACTIVE_SESSIONS_KEY (String namespace) {
        return namespace + ":SESSIONS";
    }

    /**
     * Creates value for Set to track Active Sessions for all apps
     *
     * @see #ACTIVE_SESSIONS_KEY(String)
     *
     * @param app       the app for the session
     * @param token     the session token
     * @param userId    the user to whom the session belongs
     * @return the formatted value
     */
    private static String ACTIVE_SESSIONS_VALUE (String app, String token, String userId) {
        return app + ":" + token + ":" + userId;
    }

    /**
     * Creates Key for storing hash of user session details
     * @param namespace     the namespace for the session service
     * @param app           the app to which this session belongs
     * @param token         the token for this session
     * @return the key
     */
    private static String SESSION_HASH_KEY (String namespace, String app, String token) {
        return namespace + ":" + app + ":" + token;
    }
}
