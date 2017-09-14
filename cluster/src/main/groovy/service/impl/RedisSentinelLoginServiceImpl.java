package service.impl;

import com.lambdaworks.redis.ReadFrom;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.masterslave.MasterSlave;
import com.lambdaworks.redis.masterslave.StatefulRedisMasterSlaveConnection;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import rx.Observable;
import service.LoginService;

import java.util.Objects;
import java.util.UUID;

public class RedisSentinelLoginServiceImpl implements LoginService {

    private final Object lock = new Object ();
    private final RedisClient redisClient;
    private final String redisURI;
    private volatile StatefulRedisMasterSlaveConnection<String, String> connection;

    public RedisSentinelLoginServiceImpl (RedisClient redisClient, String redisURI) {
        this.redisClient = redisClient;
        this.redisURI = redisURI;
        connection = MasterSlave.connect (redisClient, new Utf8StringCodec (), RedisURI.create (redisURI));
        connection.setReadFrom (ReadFrom.NEAREST);
    }

    @Override
    public Observable<String> login (final String user, String pass) {

        return newRedisCommand ()
                .flatMap (commands -> commands.exists ("SESSION_" + user)
                        .flatMap (numKeys -> {
                            if (numKeys > 0) {
                                return Observable.error (new IllegalArgumentException ("Login Session already exists"));
                            } else {
                                String sessionId = UUID.randomUUID ().toString ();
                                return commands.set ("SESSION_" + user, sessionId)
                                        .flatMap (simpleResponse -> {
                                            if ("OK".equals (simpleResponse)) {
                                                return Observable.just (sessionId);
                                            } else {
                                                return Observable.error (new RuntimeException ("Redis returned unexpected Response " + simpleResponse));
                                            }
                                        });
                            }
                        }));


    }

    @Override
    public Observable<Long> logout (String user, String sessionId) {
        return newRedisCommand ()
                .flatMap (commands -> commands.exists ("SESSION_" + user)
                        .flatMap (numKeys -> {
                            if (numKeys < 1) {
                                return Observable.just (0L);
                            } else {
                                return commands.del ("SESSION_" + user);
                            }
                        })
                );
    }

    @Override
    public Observable<Boolean> isAuthenticated (String user, String sessionId) {
        return newRedisCommand ()
                .flatMap (commands -> commands.get ("SESSION_" + user)
                        .map (result -> !Objects.isNull (result) && result.equals (sessionId))
                );
    }


    private Observable<RedisReactiveCommands<String, String>> newRedisCommand () {
        return Observable.defer (() -> Observable.just (connection.reactive ()));
    }



}
