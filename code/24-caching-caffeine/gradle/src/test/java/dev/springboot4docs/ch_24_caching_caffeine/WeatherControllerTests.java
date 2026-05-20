package dev.springboot4docs.ch_24_caching_caffeine;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WeatherController.class)
class WeatherControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeatherService weatherService;

    @Test
    void getWeatherReturnsForecast() throws Exception {
        when(weatherService.forecast("London")).thenReturn("Forecast for London: sunny");

        mockMvc.perform(get("/weather/{city}", "London"))
            .andExpect(status().isOk())
            .andExpect(content().string("Forecast for London: sunny"));
    }

}
