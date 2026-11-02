package com.pedidos360.ms_pago.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "pedidos360.exchange";

    public static final String ROUTING_KEY_PAGO_APROBADO = "pago.aprobado";
    public static final String ROUTING_KEY_PAGO_RECHAZADO = "pago.rechazado";

    // Cola que consume ms-orders (recibe pago.aprobado y pago.rechazado)
    public static final String QUEUE_ORDENES_PAGOS = "ordenes.pagos.queue";

    @Bean
    public TopicExchange pedidos360Exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue ordenesPagosQueue() {
        return QueueBuilder.durable(QUEUE_ORDENES_PAGOS).build();
    }

    // "pago.*" -> recibe pago.aprobado y pago.rechazado
    @Bean
    public Binding bindingOrdenesPagos() {
        return BindingBuilder
                .bind(ordenesPagosQueue())
                .to(pedidos360Exchange())
                .with("pago.*");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter("com.pedidos360");
    }
}