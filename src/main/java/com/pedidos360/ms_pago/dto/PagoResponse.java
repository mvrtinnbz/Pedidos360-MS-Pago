package com.pedidos360.ms_pago.dto;

import com.pedidos360.ms_pago.entity.Pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Lo que se devuelve al cliente: sin el hash de la tarjeta.
public record PagoResponse(
        Long id,
        Long ordenId,
        String usuarioId,
        BigDecimal monto,
        String metodo,
        String estado,
        String tarjetaUltimos4,
        String detalle,
        LocalDateTime fechaCreacion
) {
    public static PagoResponse desde(Pago pago) {
        return new PagoResponse(
                pago.getId(),
                pago.getOrdenId(),
                pago.getUsuarioId(),
                pago.getMonto(),
                pago.getMetodo(),
                pago.getEstado(),
                pago.getTarjetaUltimos4(),
                pago.getDetalle(),
                pago.getFechaCreacion()
        );
    }
}