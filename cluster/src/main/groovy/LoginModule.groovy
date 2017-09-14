import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import com.lambdaworks.redis.RedisClient
import ratpack.config.ConfigData
import service.LoginService
import service.impl.NullLoginServiceImpl
import service.impl.RedisSentinelLoginServiceImpl

class LoginModule extends AbstractModule {

    private final Map<String, String> configData

    LoginModule (Map<String,String> configData) {
        this.configData = configData
    }


    @Override
    protected void configure () {

        //bind (LoginService.class).to (NullLoginServiceImpl.class).in (Singleton.class)
    }

    @Provides
    @Singleton
    protected LoginService loginService () {
        RedisClient client = RedisClient.create ()
        return new RedisSentinelLoginServiceImpl (client, configData.get ("redis.uri"))
    }
}
