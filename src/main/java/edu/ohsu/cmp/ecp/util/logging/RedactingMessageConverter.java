package edu.ohsu.cmp.ecp.util.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class RedactingMessageConverter extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return LogRedactor.redact(event.getFormattedMessage());
    }
}
