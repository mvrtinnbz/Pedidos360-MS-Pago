package com.pedidos360.ms_pago.controller;

import com.pedidos360.ms_pago.dto.PagoResponse;
import com.pedidos360.ms_pago.dto.PagoSimuladoRequest;
import com.pedidos360.ms_pago.entity.AlertaFraude;
import com.pedidos360.ms_pago.entity.Pago;
import com.pedidos360.ms_pago.security.UsuarioActual;
import com.pedidos360.ms_pago.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    // Pago simulado con regla antifraude
    // 201 -> APROBADO | 403 -> BLOQUEADO_FRAUDE
    @PostMapping("/simulado")
    public ResponseEntity<PagoResponse> pagarSimulado(@Valid @RequestBody PagoSimuladoRequest request) {
        String usuarioId = UsuarioActual.usuarioId();
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo determinar el usuario");
        }

        Pago pago = pagoService.pagarSimulado(request, usuarioId);
        HttpStatus status = Pago.BLOQUEADO_FRAUDE.equals(pago.getEstado())
                ? HttpStatus.FORBIDDEN
                : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(PagoResponse.desde(pago));
    }

    @GetMapping
    public List<PagoResponse> listarTodos() {
        return pagoService.listarTodos().stream().map(PagoResponse::desde).toList();
    }

    @GetMapping("/mis-pagos")
    public List<PagoResponse> misPagos() {
        return pagoService.listarPorUsuario(UsuarioActual.usuarioId())
                .stream().map(PagoResponse::desde).toList();
    }

    @GetMapping("/orden/{ordenId}")
    public List<PagoResponse> porOrden(@PathVariable Long ordenId) {
        return pagoService.listarPorOrden(ordenId).stream().map(PagoResponse::desde).toList();
    }

    @GetMapping("/alertas")
    public List<AlertaFraude> alertas() {
        return pagoService.listarAlertas();
    }
}