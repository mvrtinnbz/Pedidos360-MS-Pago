package com.pedidos360.ms_pago.messaging;

import com.pedidos360.ms_pago.config.RabbitMQConfig;
import com.pedidos360.ms_pago.entity.Pago;
import com.pedidos360.ms_pago.event.PagoResultadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PagoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PagoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PagoEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // Publica pago.aprobado o pago.rechazado. Si RabbitMQ falla, solo se registra el error
    // (el cliente igual debe volver al frontend con su resultado).
    public void publicarResultado(Pago pago) {

        String routingKey = Pago.APROBADO.equals(pago.getEstado())
                ? RabbitMQConfig.ROUTING_KEY_PAGO_APROBADO
                : RabbitMQConfig.ROUTING_KEY_PAGO_RECHAZADO;

        PagoResultadoEvent evento = new PagoResultadoEvent();
        evento.setPagoId(pago.getId());
        evento.setOrdenId(pago.getOrdenId());
        evento.setUsuarioId(pago.getUsuarioId());
        evento.setEstado(pago.getEstado());
        evento.setMonto(pago.getMonto());
        evento.setMetodo(pago.getMetodo());
        evento.setDetalle(pago.getDetalle());

        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, evento);
            log.info("Evento publicado [{}] -> pago {} de la orden {}", routingKey, pago.getId(), pago.getOrdenId());
        } catch (Exception e) {
            log.error("No se pudo publicar [{}] del pago {}: {}", routingKey, pago.getId(), e.getMessage());
        }
    }
}