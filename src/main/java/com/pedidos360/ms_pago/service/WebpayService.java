package com.pedidos360.ms_pago.service;

import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCreateResponse;
import com.pedidos360.ms_pago.client.OrdenClient;
import com.pedidos360.ms_pago.entity.AlertaFraude;
import com.pedidos360.ms_pago.entity.Pago;
import com.pedidos360.ms_pago.repository.AlertaFraudeRepository;
import com.pedidos360.ms_pago.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebpayService {

    private static final Logger log = LoggerFactory.getLogger(WebpayService.class);

    // Resultado de iniciar: el pago registrado + a donde redirigir al cliente
    public record InicioWebpay(Pago pago, String url, String token) {}

    private final PagoRepository pagoRepository;
    private final AlertaFraudeRepository alertaRepository;
    private final AntifraudeService antifraudeService;
    private final OrdenClient ordenClient;
    private final WebpayPlus.Transaction transaction;
    private final String returnUrl;

    public WebpayService(PagoRepository pagoRepository,
                         AlertaFraudeRepository alertaRepository,
                         AntifraudeService antifraudeService,
                         OrdenClient ordenClient,
                         WebpayPlus.Transaction transaction,
                         @Value("${webpay.return-url}") String returnUrl) {
        this.pagoRepository = pagoRepository;
        this.alertaRepository = alertaRepository;
        this.antifraudeService = antifraudeService;
        this.ordenClient = ordenClient;
        this.transaction = transaction;
        this.returnUrl = returnUrl;
    }

    public InicioWebpay iniciar(Long ordenId, String usuarioId, String authorization) {

        // 1. El monto sale de ms-orders, no del cliente
        OrdenClient.OrdenDto orden = ordenClient.obtener(ordenId, authorization);

        if (orden.usuarioId() == null || !orden.usuarioId().equalsIgnoreCase(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La orden no pertenece al usuario");
        }
        if ("PAGADA".equals(orden.estado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden ya fue pagada");
        }

        // 2. Antifraude por usuario (con Webpay no conocemos la tarjeta)
        long previos = antifraudeService.intentosRecientesUsuario(usuarioId);

        Pago pago = new Pago();
        pago.setOrdenId(ordenId);
        pago.setUsuarioId(usuarioId);
        pago.setMonto(orden.total());
        pago.setMetodo(Pago.METODO_WEBPAY);

        if (antifraudeService.esSospechoso(previos)) {
            pago.setEstado(Pago.BLOQUEADO_FRAUDE);
            pago.setDetalle("Bloqueado: " + (previos + 1) + " intentos de pago del usuario en menos de "
                    + antifraudeService.getVentanaSegundos() + " segundos");
            pago = pagoRepository.save(pago);
            registrarAlerta(pago, previos + 1);
            return new InicioWebpay(pago, null, null);
        }

        pago.setEstado(Pago.PENDIENTE);
        pago.setDetalle("Esperando pago en Webpay");
        pago = pagoRepository.save(pago);

        // 3. Crear la transaccion en Transbank
        try {
            String buyOrder = "P360-" + pago.getId();   // max 26 caracteres
            String sessionId = "S-" + pago.getId();     // max 61 caracteres

            WebpayPlusTransactionCreateResponse respuesta = transaction.create(
                    buyOrder, sessionId, pago.getMonto().doubleValue(), returnUrl);

            pago.setTokenWebpay(respuesta.getToken());
            pago = pagoRepository.save(pago);

            log.info("Webpay iniciado: pago {} - orden {} - monto {} - buyOrder {}",
                    pago.getId(), ordenId, pago.getMonto(), buyOrder);

            return new InicioWebpay(pago, respuesta.getUrl(), respuesta.getToken());

        } catch (Exception e) {
            pago.setEstado(Pago.RECHAZADO);
            pago.setDetalle("Error al crear la transaccion en Webpay");
            pagoRepository.save(pago);
            log.error("Error al crear transaccion Webpay (pago {}): {}", pago.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo iniciar el pago con Webpay");
        }
    }

    private void registrarAlerta(Pago pago, long intentos) {
        AlertaFraude alerta = new AlertaFraude();
        alerta.setPagoId(pago.getId());
        alerta.setOrdenId(pago.getOrdenId());
        alerta.setUsuarioId(pago.getUsuarioId());
        alerta.setIntentos((int) intentos);
        alerta.setMotivo("Mas de " + antifraudeService.getMaxIntentos() + " intentos de pago del mismo usuario en "
                + antifraudeService.getVentanaSegundos() + " segundos (Webpay)");
        alertaRepository.save(alerta);

        log.warn("ALERTA FRAUDE (Webpay): pago {} bloqueado - orden {} - usuario {} - {} intentos",
                pago.getId(), pago.getOrdenId(), pago.getUsuarioId(), intentos);
    }
}