package com.walletapp.backend.walletimport.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Habilita @Async para ImportProcessor.run (research.md #7: ejecución en segundo plano con el
// TaskExecutor por defecto de Spring, sin sistema de colas dedicado — ver Complexity Tracking en plan.md).
@Configuration
@EnableAsync
public class AsyncConfig {
}
