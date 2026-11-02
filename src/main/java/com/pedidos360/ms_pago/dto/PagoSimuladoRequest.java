package com.pedidos360.ms_pago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class PagoSimuladoRequest {

    @NotNull(message = "ordenId es obligatorio")
    private Long ordenId;

    @NotNull(message = "monto es obligatorio")
    @Positive(message = "monto debe ser mayor a 0")
    private BigDecimal monto;

    // Solo se usa para calcular el hash y los ultimos 4. No se guarda.
    @NotBlank(message = "numeroTarjeta es obligatorio")
    @Pattern(regexp = "\\d{13,19}", message = "numeroTarjeta debe tener entre 13 y 19 digitos, sin espacios")
    private String numeroTarjeta;

    public Long getOrdenId() { return ordenId; }
    public void setOrdenId(Long ordenId) { this.ordenId = ordenId; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getNumeroTarjeta() { return numeroTarjeta; }
    public void setNumeroTarjeta(String numeroTarjeta) { this.numeroTarjeta = numeroTarjeta; }
}