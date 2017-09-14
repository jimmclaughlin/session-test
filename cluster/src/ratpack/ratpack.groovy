import handler.ErrorHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ratpack.config.ConfigData
import ratpack.config.ConfigDataBuilder
import ratpack.dropwizard.metrics.DropwizardMetricsConfig
import ratpack.dropwizard.metrics.DropwizardMetricsModule
import ratpack.error.ServerErrorHandler
import ratpack.form.Form
import ratpack.func.Action
import ratpack.groovy.template.MarkupTemplateModule
import ratpack.handling.RequestLogger
import ratpack.http.Status
import ratpack.hystrix.HystrixModule
import service.LoginService

import static ratpack.groovy.Groovy.ratpack

final Logger logger = LoggerFactory.getLogger (ratpack)

ratpack {

//    serverConfig {
//        props ("application.properties")
//    }

    bindings {


        def loginProps = ['redis.uri' :'redis-sentinel://sentinel1:26379,sentinel2:26379,sentinel3:26379/0#SessionMaster']

        module MarkupTemplateModule
        module new HystrixModule ().sse ()
        module new DropwizardMetricsModule(), { it.jmx ()}
        module new LoginModule (loginProps)

        bind ServerErrorHandler, ErrorHandler
    }

    handlers { LoginService loginService ->
        all RequestLogger.ncsa (logger)

        path ("login") {
            byMethod {
                post {
                    parse (Form)
                            .observe ()
                            .flatMap { Form form -> loginService.login (form.username, form.password) }
                            .single ()
                            .subscribe (
                            { String result ->
                                response.cookie ("X-Auth-Token", result)
                                response.send ("OK")
                            })

                }
            }
        }

        path ("authenticated/:user") {
            byMethod {
                get {
                    def user = pathTokens ["user"]
                    def cookie = request.cookies.find { it.name () == 'X-Auth-Token' }
                    loginService.isAuthenticated (user, cookie.value ())
                            .subscribe (
                            {authenticated ->
                                if (authenticated) {
                                    response.status (Status.of (204))
                                } else {
                                    response.status (Status.of (404))
                                }
                                response.send ()
                            }
                    )
                }
            }
        }

        path ("logout/:user") {
            byMethod {
                get {
                    def user = pathTokens ["user"]
                    def cookie = request.cookies.find { it.name () == 'X-Auth-Token' }
                    loginService.logout (user, cookie.value ())
                            .subscribe (
                            { Long result ->
                                response.expireCookie ("X-Auth-Token")
                                response.status (Status.of (204))
                                response.send ()
                            })

                }
            }
        }

    }
}
