package com.pedidos360.ms_pago.service;

import com.pedidos360.ms_pago.repository.PagoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AntifraudeService {

    private final PagoRepository pagoRepository;
    private final int maxIntentos;
    private final int ventanaSegundos;
    private final String hashSecreto;

    public AntifraudeService(PagoRepository pagoRepository,
                             @Value("${antifraude.max-intentos}") int maxIntentos,
                             @Value("${antifraude.ventana-segundos}") int ventanaSegundos,
                             @Value("${pagos.hash-secreto}") String hashSecreto) {
        this.pagoRepository = pagoRepository;
        this.maxIntentos = maxIntentos;
        this.ventanaSegundos = ventanaSegundos;
        this.hashSecreto = hashSecreto;
    }

    // Huella irreversible de la tarjeta: misma tarjeta -> mismo hash,
    // pero desde el hash no se puede recuperar el numero.
    public String hashTarjeta(String numeroTarjeta) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(numeroTarjeta.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el hash de la tarjeta", e);
        }
    }

    // Intentos previos con esta tarjeta dentro de la ventana de tiempo.
    public long intentosRecientes(String tarjetaHash) {
        LocalDateTime desde = LocalDateTime.now().minusSeconds(ventanaSegundos);
        return pagoRepository.countByTarjetaHashAndFechaCreacionAfter(tarjetaHash, desde);
    }

    // Si ya hubo 'maxIntentos' (3) en la ventana, el intento actual es sospechoso.
    public boolean esSospechoso(long intentosPrevios) {
        return intentosPrevios >= maxIntentos;
    }

    public int getMaxIntentos() { return maxIntentos; }
    public int getVentanaSegundos() { return ventanaSegundos; }
}