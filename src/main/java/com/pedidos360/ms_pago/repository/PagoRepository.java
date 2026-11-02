package com.pedidos360.ms_pago.repository;

import com.pedidos360.ms_pago.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Regla antifraude: cuantos intentos hubo con la misma tarjeta desde 'desde'
    long countByTarjetaHashAndFechaCreacionAfter(String tarjetaHash, LocalDateTime desde);

    List<Pago> findByOrdenIdOrderByFechaCreacionDesc(Long ordenId);

    List<Pago> findByUsuarioIdOrderByFechaCreacionDesc(String usuarioId);

    List<Pago> findAllByOrderByFechaCreacionDesc();
}