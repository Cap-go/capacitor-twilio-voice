package com.twilio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.javalin.http.Context;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LoggingFilter {

    private static final String TEMPLATE_WITH_BODY =
            "\nRequest {} {} {} HEADERS:[{}] BODY: {}\nResponse {} HEADERS:[{}] BODY: {} ";
    private static final String TEMPLATE_WITH_NO_BODY =
            "\nRequest {} {} {} HEADERS:[{}] \nResponse {} HEADERS:[{}]";
    private static Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    public static void logRequestResponse(Context ctx) {
        String requestHeaderString = buildHeadersString(ctx.headerMap().keySet(),
                h -> ctx.header(h));
        String responseHeaderString = buildHeadersString(ctx.res().getHeaderNames(),
                h -> ctx.res().getHeader(h));
        String template;
        Object[] params;
        if(logger.isDebugEnabled()) {
            template = TEMPLATE_WITH_BODY;
            params = new Object[] {
                    ctx.method(),
                    ctx.path(),
                    ctx.protocol(),
                    requestHeaderString,
                    ctx.body(),
                    ctx.status(),
                    responseHeaderString,
                    "[Response body not accessible in after filter]"
            };
        } else {
            template = TEMPLATE_WITH_NO_BODY;
            params = new Object[] {
                    ctx.method(),
                    ctx.path(),
                    ctx.protocol(),
                    requestHeaderString,
                    ctx.status(),
                    responseHeaderString,
            };
        }
        logger.info(template, params);
    }

    private static String buildHeadersString(Collection<String> headers, Function<String, String> getHeader) {
        return headers
                .stream()
                .map(h -> h + ":" + getHeader.apply(h))
                .collect(Collectors.joining(", "));
    }
}
