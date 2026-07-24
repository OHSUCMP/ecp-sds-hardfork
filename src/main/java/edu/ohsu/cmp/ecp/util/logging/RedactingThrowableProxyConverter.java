package edu.ohsu.cmp.ecp.util.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class RedactingThrowableProxyConverter extends ThrowableProxyConverter {
    @Override
    public String convert(ILoggingEvent throwableProxy) {
        return LogRedactor.redact(super.convert(throwableProxy));
    }
}
