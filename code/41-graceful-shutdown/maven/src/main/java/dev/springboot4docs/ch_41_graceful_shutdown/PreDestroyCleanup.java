package dev.springboot4docs.ch_41_graceful_shutdown;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

@Component
class PreDestroyCleanup {

	private static final Logger log = LoggerFactory.getLogger(PreDestroyCleanup.class);

	@PreDestroy
	void close() {
		log.info("@PreDestroy cleanup fired");
	}

}
