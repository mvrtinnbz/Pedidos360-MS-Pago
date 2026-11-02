package com.pedidos360.ms_pago.config;

import cl.transbank.common.IntegrationApiKeys;
import cl.transbank.common.IntegrationCommerceCodes;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebpayConfig {

    @Bean
    public WebpayPlus.Transaction webpayTransaction() {
        return WebpayPlus.Transaction.buildForIntegration(
                IntegrationCommerceCodes.WEBPAY_PLUS,
                IntegrationApiKeys.WEBPAY
        );
    }
}