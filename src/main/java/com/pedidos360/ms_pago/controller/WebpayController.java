package com.pedidos360.ms_pago.controller;

import com.pedidos360.ms_pago.entity.Pago;
import com.pedidos360.ms_pago.security.UsuarioActual;
import com.pedidos360.ms_pago.service.WebpayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pagos/webpay")
public class WebpayController {

    public record IniciarRequest(@NotNull(message = "ordenId es obligatorio") Long ordenId) {}

    public record IniciarResponse(Long pagoId, String estado, String url, String token, String detalle) {}

    private final WebpayService webpayService;

    public WebpayController(WebpayService webpayService) {
        this.webpayService = webpayService;
    }

    // 201 -> transaccion creada (url + token para ir a Webpay)
    // 403 -> bloqueado por antifraude
    @PostMapping("/iniciar")
    public ResponseEntity<IniciarResponse> iniciar(@Valid @RequestBody IniciarRequest request,
                                                   @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String usuarioId = UsuarioActual.usuarioId();
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo determinar el usuario");
        }

        WebpayService.InicioWebpay inicio = webpayService.iniciar(request.ordenId(), usuarioId, authorization);
        Pago pago = inicio.pago();

        IniciarResponse body = new IniciarResponse(
                pago.getId(), pago.getEstado(), inicio.url(), inicio.token(), pago.getDetalle());

        HttpStatus status = Pago.BLOQUEADO_FRAUDE.equals(pago.getEstado())
                ? HttpStatus.FORBIDDEN
                : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(body);
    }
}