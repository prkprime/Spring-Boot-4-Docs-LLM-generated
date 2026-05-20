package dev.springboot4docs.ch_05_your_first_test;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public Greeting greet(String name) {
        return new Greeting("Hello, " + name + "!");
    }

}
