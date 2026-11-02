package com.pedidos360.ms_pago.service;

import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCommitResponse;
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

    private static final String PREFIJO_BUY_ORDER = "P360-";

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

    // ==========================================================
    // 1. INICIAR: crea la transaccion en Transbank
    // ==========================================================

    public InicioWebpay iniciar(Long ordenId, String usuarioId, String authorization) {

        // El monto sale de ms-orders, no del cliente
        OrdenClient.OrdenDto orden = ordenClient.obtener(ordenId, authorization);

        if (orden.usuarioId() == null || !orden.usuarioId().equalsIgnoreCase(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La orden no pertenece al usuario");
        }
        if ("PAGADA".equals(orden.estado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden ya fue pagada");
        }

        // Antifraude por usuario (con Webpay no conocemos la tarjeta)
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

        try {
            String buyOrder = PREFIJO_BUY_ORDER + pago.getId();   // max 26 caracteres
            String sessionId = "S-" + pago.getId();               // max 61 caracteres

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

    // ==========================================================
    // 2. RETORNO: Webpay devuelve al cliente y se confirma el pago
    // ==========================================================

    // Devuelve el pago actualizado, o null si no se pudo identificar.
    public Pago procesarRetorno(String tokenWs, String tbkToken, String tbkOrdenCompra) {

        // Caso A: el cliente presiono "Anular" en Webpay
        if (tbkToken != null) {
            Pago pago = pagoRepository.findByTokenWebpay(tbkToken).orElse(null);
            return cerrarSinCommit(pago, "Pago anulado por el usuario en Webpay");
        }

        // Caso B: se agoto el tiempo en el formulario (solo llega la orden de compra)
        if (tokenWs == null) {
            Pago pago = buscarPorBuyOrder(tbkOrdenCompra);
            return cerrarSinCommit(pago, "Tiempo agotado en el formulario de Webpay");
        }

        // Caso C: flujo normal -> confirmar (commit) con Transbank
        Pago pago = pagoRepository.findByTokenWebpay(tokenWs).orElse(null);
        if (pago == null) {
            log.warn("Retorno Webpay con token desconocido");
            return null;
        }

        // Si ya se proceso (ej: el cliente recargo la pagina), no se confirma de nuevo
        if (!Pago.PENDIENTE.equals(pago.getEstado())) {
            return pago;
        }

        try {
            WebpayPlusTransactionCommitResponse respuesta = transaction.commit(tokenWs);

            if (respuesta.getCardDetail() != null && respuesta.getCardDetail().getCardNumber() != null) {
                String numero = respuesta.getCardDetail().getCardNumber();
                pago.setTarjetaUltimos4(numero.length() > 4 ? numero.substring(numero.length() - 4) : numero);
            }

            if (respuesta.getResponseCode() == 0 && "AUTHORIZED".equals(respuesta.getStatus())) {
                pago.setEstado(Pago.APROBADO);
                pago.setDetalle("Aprobado por Webpay - codigo de autorizacion " + respuesta.getAuthorizationCode());
            } else {
                pago.setEstado(Pago.RECHAZADO);
                pago.setDetalle("Rechazado por Webpay (codigo de respuesta " + respuesta.getResponseCode() + ")");
            }

        } catch (Exception e) {
            pago.setEstado(Pago.RECHAZADO);
            pago.setDetalle("Error al confirmar el pago con Webpay");
            log.error("Error en commit Webpay (pago {}): {}", pago.getId(), e.getMessage());
        }

        pago = pagoRepository.save(pago);
        log.info("Webpay retorno: pago {} - orden {} -> {} ({})",
                pago.getId(), pago.getOrdenId(), pago.getEstado(), pago.getDetalle());

        // (Paso 2.3: aqui se avisara a ms-orders por RabbitMQ)

        return pago;
    }

    private Pago cerrarSinCommit(Pago pago, String motivo) {
        if (pago == null) {
            log.warn("Retorno Webpay sin pago asociado ({})", motivo);
            return null;
        }
        if (Pago.PENDIENTE.equals(pago.getEstado())) {
            pago.setEstado(Pago.RECHAZADO);
            pago.setDetalle(motivo);
            pago = pagoRepository.save(pago);
            log.info("Webpay retorno: pago {} - orden {} -> RECHAZADO ({})",
                    pago.getId(), pago.getOrdenId(), motivo);
        }
        return pago;
    }

    // "P360-7" -> pago 7
    private Pago buscarPorBuyOrder(String buyOrder) {
        if (buyOrder == null || !buyOrder.startsWith(PREFIJO_BUY_ORDER)) {
            return null;
        }
        try {
            Long id = Long.valueOf(buyOrder.substring(PREFIJO_BUY_ORDER.length()));
            return pagoRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            return null;
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