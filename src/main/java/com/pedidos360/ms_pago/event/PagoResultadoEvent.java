package com.pedidos360.ms_pago.event;

import java.math.BigDecimal;

// Evento que ms-pago publica cuando un pago termina (aprobado o rechazado).
public class PagoResultadoEvent {

    private Long pagoId;
    private Long ordenId;
    private String usuarioId;
    private String estado;      // APROBADO | RECHAZADO
    private BigDecimal monto;
    private String metodo;      // WEBPAY
    private String detalle;

    public PagoResultadoEvent() {
    }

    public Long getPagoId() { return pagoId; }
    public void setPagoId(Long pagoId) { this.pagoId = pagoId; }

    public Long getOrdenId() { return ordenId; }
    public void setOrdenId(Long ordenId) { this.ordenId = ordenId; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
}