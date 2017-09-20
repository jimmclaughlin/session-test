import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.lambdaworks.redis.RedisClient
import service.AuthService
import service.SessionService
import service.impl.AuthServiceImpl
import service.impl.RedisSentinelSessionServiceImpl

class SessionModule extends AbstractModule {

    private final Map<String, String> configData

    private final String redisUri

    private final String namespace

    private final int maxIdle

    private final int maxTotal

    private final int minIdle

    private final int numReplicate

    private final int replicateTimeoutMS

    SessionModule (Map<String,String> configData) {
        this.configData = configData
        this.redisUri = configData.get ("redis.uri")
        this.namespace = configData.get ("app.namespace")
        this.maxIdle = Integer.valueOf (configData.get ("redis.pool.maxIdle"))
        this.maxTotal = Integer.valueOf (configData.get ("redis.pool.maxTotal"))
        this.minIdle = Integer.valueOf (configData.get ("redis.pool.minIdle"))
        this.numReplicate = Integer.valueOf (configData.get ("redis.numReplicate"))
        this.replicateTimeoutMS = Integer.valueOf (configData.get ("redis.replicateTimeoutMS"))
    }


    @Override
    protected void configure () {

        bind (AuthService.class).to (AuthServiceImpl.class).in (Singleton.class)

    }

    @Provides
    @Singleton
    protected ObjectMapper mapper () {
        ObjectMapper mapper = new ObjectMapper()
        return mapper
    }

    @Provides
    @Singleton
    protected SessionService loginService (ObjectMapper mapper) {
        RedisClient client = RedisClient.create ()
        return new RedisSentinelSessionServiceImpl (client, redisUri, namespace, mapper, maxIdle, maxTotal, minIdle, numReplicate, replicateTimeoutMS)
    }
}
