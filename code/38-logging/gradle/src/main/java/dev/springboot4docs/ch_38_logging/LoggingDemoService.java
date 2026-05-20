package dev.springboot4docs.ch_38_logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggingDemoService {

    private static final Logger log = LoggerFactory.getLogger(LoggingDemoService.class);

    public void writeExampleLogs() {
        log.trace("trace details for the current request");
        log.debug("debug details for the current request");
        log.info("business event completed");
        log.warn("business event completed with a recoverable warning");
        log.error("business event failed");
    }

}
