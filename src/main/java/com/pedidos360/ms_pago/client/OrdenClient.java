package com.pedidos360.ms_pago.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

// Consulta a ms-orders para obtener el monto real de la orden. Reenvia el JWT del usuario.
@Component
public class OrdenClient {

    private static final Logger log = LoggerFactory.getLogger(OrdenClient.class);

    // Solo los campos que ms-pago necesita de la orden
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrdenDto(Long id, String usuarioId, BigDecimal total, String estado) {}

    private final RestClient restClient;

    public OrdenClient(@Value("${ordenes.api.url}") String ordenesUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(10000);

        this.restClient = RestClient.builder()
                .baseUrl(ordenesUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public OrdenDto obtener(Long ordenId, String authorization) {
        try {
            return restClient.get()
                    .uri("/api/ordenes/{id}", ordenId)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(OrdenDto.class);

        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La orden " + ordenId + " no existe");

        } catch (RestClientException e) {
            log.error("No se pudo consultar ms-orders (orden {}): {}", ordenId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar la orden en ms-orders");
        }
    }
}