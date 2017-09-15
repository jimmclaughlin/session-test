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
import ratpack.rx.RxRatpack
import service.LoginService

import static ratpack.groovy.Groovy.ratpack

final Logger logger = LoggerFactory.getLogger (ratpack)

ratpack {

//    serverConfig {
//        props ("application.properties")
//    }

    bindings {

        def sentinel1 = 'sentinel1.service.imanagecloud.com:26379'
        def sentinel2 = 'sentinel2.service.imanagecloud.com:26379'
        def sentinel3 = 'sentinel3.service.imanagecloud.com:26379'
        def loginProps = ['redis.uri' :"redis-sentinel://${sentinel1},${sentinel2},${sentinel3}/0#redismaster"]

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
                    def authResult = parse (Form)
                            .observe ()
                            .flatMap { Form form -> loginService.login (form.username, form.password) }

                    RxRatpack.promise (authResult)
                            .then ({ String result ->
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
                    def authResult = loginService.isAuthenticated (user, cookie.value ())
                    RxRatpack.promise (authResult)
                            .then ({authenticated ->
                                if (authenticated) {
                                    response.status (Status.of (204))
                                } else {
                                    response.status (Status.of (404))
                                }
                                response.send ()
                            })
                }
            }
        }

        path ("logout/:user") {
            byMethod {
                get {
                    def user = pathTokens ["user"]
                    def cookie = request.cookies.find { it.name () == 'X-Auth-Token' }
                    if (!cookie) {
                        response.status (Status.of (400))
                        response.send ("Logout requires Token")
                    } else {
                        
                        def logoutResult = loginService.logout (user, cookie.value ())
                        RxRatpack.promise (logoutResult)
                            .then (
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
}
