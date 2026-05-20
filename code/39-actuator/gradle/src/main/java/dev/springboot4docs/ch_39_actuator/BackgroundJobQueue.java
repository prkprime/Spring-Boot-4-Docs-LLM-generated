package dev.springboot4docs.ch_39_actuator;

import org.springframework.stereotype.Component;

@Component
class BackgroundJobQueue {

    int backlog() {
        return 7;
    }

}
