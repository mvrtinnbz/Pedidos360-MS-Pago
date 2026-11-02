package com.pedidos360.ms_pago.repository;

import com.pedidos360.ms_pago.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Regla antifraude por tarjeta (pago simulado)
    long countByTarjetaHashAndFechaCreacionAfter(String tarjetaHash, LocalDateTime desde);

    // Regla antifraude por usuario (Webpay: no conocemos la tarjeta)
    long countByUsuarioIdAndFechaCreacionAfter(String usuarioId, LocalDateTime desde);

    // Para encontrar el pago cuando Webpay vuelve con su token
    Optional<Pago> findByTokenWebpay(String tokenWebpay);

    List<Pago> findByOrdenIdOrderByFechaCreacionDesc(Long ordenId);

    List<Pago> findByUsuarioIdOrderByFechaCreacionDesc(String usuarioId);

    List<Pago> findAllByOrderByFechaCreacionDesc();
}