package session

import groovyx.gpars.actor.DefaultActor
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import org.HdrHistogram.Histogram

import java.util.concurrent.atomic.AtomicInteger

class SessionActor extends DefaultActor {

    private final String url

    private final String user
    private final String password

    private final AtomicInteger requestCount

    private final AtomicInteger errorCount

    private final AtomicInteger successCount

    private final Histogram histogram

    private boolean loggedIn

    HTTPBuilder http

    String token

    SessionActor (
            String url,
            String user,
            String password,
            AtomicInteger requestCount,
            AtomicInteger errorCount,
            AtomicInteger successCount,
            Histogram histogram,
            int connectTimeoutMS,
            int socketTimeoutMS
    ) {
        this.url = url
        this.user = user
        this.password = password
        this.requestCount = requestCount
        this.errorCount = errorCount
        this.successCount = successCount
        this.histogram = histogram
        this.loggedIn = false

        http = new HTTPBuilder (url)
        http.client.params ().setParameter ("http.connection.timeout", connectTimeoutMS)
        http.client.params.setParameter ("http.socket.timeout", socketTimeoutMS)
    }


    @Override
    protected void act () {
        loop {

            react {
                requestCount.incrementAndGet ()
                long start = System.currentTimeMillis ()

                try {
                    if (!loggedIn) {
                        login ()
                    } else {
                        if (requestCount.get () % 10 == 0) {
                            logout ()
                        } else {
                            getSession ()
                        }
                    }
                    successCount.incrementAndGet ()
                } catch (Exception e) {
                    println "Exception Sending Request ${e.message}"
                    errorCount.getAndIncrement ()
                } finally {
                    long end = System.currentTimeMillis ()
                    histogram.recordValue (end - start)

                    reply "Done"
                }
            }

        }
    }

    def getSession () {
        def resp = http.get (
                path: '/session/dms',
                contentType: ContentType.JSON
        )

        if (resp.status != 200) {
            throw new RuntimeException ("Get Session Failed")
        }

        if (requestCount.get () % 20) {
            println "Got Session for ${user}: ${resp.status}"
        }
    }

    def logout () {
        def resp = http.get (path: '/logout/dms')

        if (resp.status != 204) {
            throw new RuntimeException ("Logout Failed with status ${resp.status} msg: ${resp.data}")
        }
        http.defaultRequestHeaders.remove ('Cookie')
        loggedIn = false
    }

    def login () {

        def resp = http.post (
                path : '/login/dms',
                requestContentType : ContentType.URLENC,
                body : ["username": user,"password":password]
        )

        if (resp.status != 204) {
            throw new RuntimeException ("Login Failed status ${resp.status} msg: ${resp.data}")
        }

        def (key, value) = resp.headers.'Set-Cookie'.tokenize ('=')
        http.defaultRequestHeaders['Cookie'] = "X-Auth-Token=${value}"
        loggedIn = true

    }


}
