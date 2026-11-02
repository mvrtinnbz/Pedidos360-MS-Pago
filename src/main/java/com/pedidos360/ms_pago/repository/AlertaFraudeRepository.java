package com.pedidos360.ms_pago.repository;

import com.pedidos360.ms_pago.entity.AlertaFraude;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaFraudeRepository extends JpaRepository<AlertaFraude, Long> {

    List<AlertaFraude> findAllByOrderByFechaDesc();
}