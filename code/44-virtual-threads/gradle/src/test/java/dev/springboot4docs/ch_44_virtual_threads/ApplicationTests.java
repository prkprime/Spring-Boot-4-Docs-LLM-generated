package dev.springboot4docs.ch_44_virtual_threads;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    private AsyncTaskExecutor applicationTaskExecutor;

    @Test
    void sleepEndpointRunsOnVirtualThread() throws Exception {
        MvcResult request = this.mvc.perform(get("/sleep").param("ms", "10"))
            .andExpect(request().asyncStarted())
            .andReturn();

        this.mvc.perform(asyncDispatch(request))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("VirtualThread")));
    }

    @Test
    void pinEndpointStillRunsOnVirtualThread() throws Exception {
        MvcResult request = this.mvc.perform(get("/pin").param("ms", "10"))
            .andExpect(request().asyncStarted())
            .andReturn();

        this.mvc.perform(asyncDispatch(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.thread", containsString("VirtualThread")))
            .andExpect(jsonPath("$.virtual").value(true));
    }

    @Test
    void springApplicationTaskExecutorUsesVirtualThreads() throws Exception {
        Future<String> thread = this.applicationTaskExecutor.submit(() -> Thread.currentThread().toString());

        assertThat(thread.get(5, TimeUnit.SECONDS)).contains("VirtualThread");
    }

}
