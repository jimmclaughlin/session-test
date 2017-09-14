package handler

import groovy.util.logging.Slf4j
import org.codehaus.groovy.runtime.StackTraceUtils
import ratpack.error.ServerErrorHandler
import ratpack.handling.Context

@Slf4j
class ErrorHandler implements ServerErrorHandler {
    @Override
    void error (Context context, Throwable throwable) throws Exception {
        log.warn "Exception ", throwable
        context.with {
            render "Exception handling request ${StackTraceUtils.deepSanitize(throwable)}"
        }
    }


}
