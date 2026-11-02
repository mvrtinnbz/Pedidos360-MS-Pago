package com.pedidos360.ms_pago.controller;

import com.pedidos360.ms_pago.entity.Pago;
import com.pedidos360.ms_pago.security.UsuarioActual;
import com.pedidos360.ms_pago.service.WebpayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestController
@RequestMapping("/api/pagos/webpay")
public class WebpayController {

    public record IniciarRequest(@NotNull(message = "ordenId es obligatorio") Long ordenId) {}

    public record IniciarResponse(Long pagoId, String estado, String url, String token, String detalle) {}

    private final WebpayService webpayService;
    private final String frontendUrl;

    public WebpayController(WebpayService webpayService,
                            @Value("${frontend.url}") String frontendUrl) {
        this.webpayService = webpayService;
        this.frontendUrl = frontendUrl;
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

    // Webpay devuelve aqui al navegador del cliente (GET o POST, segun el caso).
    // Se confirma el pago y se redirige al frontend con el resultado.
    @RequestMapping(value = "/retorno", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> retorno(@RequestParam(name = "token_ws", required = false) String tokenWs,
                                        @RequestParam(name = "TBK_TOKEN", required = false) String tbkToken,
                                        @RequestParam(name = "TBK_ORDEN_COMPRA", required = false) String tbkOrdenCompra) {

        Pago pago = webpayService.procesarRetorno(tokenWs, tbkToken, tbkOrdenCompra);

        String destino = (pago == null)
                ? frontendUrl + "/pago-resultado?estado=ERROR"
                : frontendUrl + "/pago-resultado?estado=" + pago.getEstado()
                        + "&ordenId=" + pago.getOrdenId()
                        + "&pagoId=" + pago.getId();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(destino))
                .build();
    }
}