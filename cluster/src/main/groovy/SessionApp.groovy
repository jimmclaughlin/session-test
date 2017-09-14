import handler.LoginHandler
import handler.LogoutHandler
import ratpack.guice.Guice
import ratpack.rx.RxRatpack
import ratpack.server.RatpackServer

class SessionApp {

    static void main (String... args) throws Exception {

        RxRatpack.initialize ()

        RatpackServer.start (
                {
                    server -> server.registry ({
                        b -> b.module (LoginModule.class)
                    }).handlers ({
                        chain -> chain.path ("login", LoginHandler.class)
                            .path ("logout", LogoutHandler.class)


                    })
                }
        )
    }
}
