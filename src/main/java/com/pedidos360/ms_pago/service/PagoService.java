package com.pedidos360.ms_pago.service;

import com.pedidos360.ms_pago.dto.PagoSimuladoRequest;
import com.pedidos360.ms_pago.entity.AlertaFraude;
import com.pedidos360.ms_pago.entity.Pago;
import com.pedidos360.ms_pago.repository.AlertaFraudeRepository;
import com.pedidos360.ms_pago.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final PagoRepository pagoRepository;
    private final AlertaFraudeRepository alertaRepository;
    private final AntifraudeService antifraudeService;

    public PagoService(PagoRepository pagoRepository,
                       AlertaFraudeRepository alertaRepository,
                       AntifraudeService antifraudeService) {
        this.pagoRepository = pagoRepository;
        this.alertaRepository = alertaRepository;
        this.antifraudeService = antifraudeService;
    }

    @Transactional
    public Pago pagarSimulado(PagoSimuladoRequest request, String usuarioId) {

        String numero = request.getNumeroTarjeta();
        String hash = antifraudeService.hashTarjeta(numero);
        String ultimos4 = numero.substring(numero.length() - 4);

        // Se cuenta ANTES de guardar el intento actual
        long previos = antifraudeService.intentosRecientes(hash);

        Pago pago = new Pago();
        pago.setOrdenId(request.getOrdenId());
        pago.setUsuarioId(usuarioId);
        pago.setMonto(request.getMonto());
        pago.setMetodo(Pago.METODO_SIMULADO);
        pago.setTarjetaHash(hash);
        pago.setTarjetaUltimos4(ultimos4);

        if (antifraudeService.esSospechoso(previos)) {
            pago.setEstado(Pago.BLOQUEADO_FRAUDE);
            pago.setDetalle("Bloqueado: " + (previos + 1) + " intentos con la tarjeta ****" + ultimos4
                    + " en menos de " + antifraudeService.getVentanaSegundos() + " segundos");
            pago = pagoRepository.save(pago);
            registrarAlerta(pago, previos + 1);
            return pago;
        }

        pago.setEstado(Pago.APROBADO);
        pago.setDetalle("Pago simulado aprobado");
        pago = pagoRepository.save(pago);
        log.info("Pago {} APROBADO - orden {} - tarjeta ****{}", pago.getId(), pago.getOrdenId(), ultimos4);
        return pago;
    }

    private void registrarAlerta(Pago pago, long intentos) {
        AlertaFraude alerta = new AlertaFraude();
        alerta.setPagoId(pago.getId());
        alerta.setOrdenId(pago.getOrdenId());
        alerta.setUsuarioId(pago.getUsuarioId());
        alerta.setTarjetaHash(pago.getTarjetaHash());
        alerta.setTarjetaUltimos4(pago.getTarjetaUltimos4());
        alerta.setIntentos((int) intentos);
        alerta.setMotivo("Mas de " + antifraudeService.getMaxIntentos() + " intentos en "
                + antifraudeService.getVentanaSegundos() + " segundos con la misma tarjeta");
        alertaRepository.save(alerta);

        log.warn("ALERTA FRAUDE: pago {} bloqueado - orden {} - tarjeta ****{} - {} intentos",
                pago.getId(), pago.getOrdenId(), pago.getTarjetaUltimos4(), intentos);
    }

    public List<Pago> listarTodos() {
        return pagoRepository.findAllByOrderByFechaCreacionDesc();
    }

    public List<Pago> listarPorUsuario(String usuarioId) {
        return pagoRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId);
    }

    public List<Pago> listarPorOrden(Long ordenId) {
        return pagoRepository.findByOrdenIdOrderByFechaCreacionDesc(ordenId);
    }

    public List<AlertaFraude> listarAlertas() {
        return alertaRepository.findAllByOrderByFechaDesc();
    }
}