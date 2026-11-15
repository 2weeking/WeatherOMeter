package com.example.weather_o_meter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Makes the assignment's Austin request. Call only from a background thread. */
public final class WeatherRepository {
    public static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=30.28&longitude=-97.76"
            + "&hourly=temperature_2m&temperature_unit=fahrenheit"
            + "&timezone=America%2FChicago&forecast_days=7";

    public String fetchForecast() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(FORECAST_URL).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setUseCaches(false);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new HttpException(status);
            }
            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int length;
                while ((length = reader.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Request cancelled.");
                    }
                    json.append(buffer, 0, length);
                }
            }
            return json.toString();
        } finally {
            connection.disconnect();
        }
    }

    public static final class HttpException extends IOException {
        private final int statusCode;

        HttpException(int statusCode) {
            super("Weather request failed: HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
