package it.govpay.mail.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principale di GovPay Mail Batch.
 * <p>
 * Estende {@link SpringBootServletInitializer} per il deploy del war (profilo
 * Maven {@code war}) su application server; con il fat jar parte da {@link #main}.
 * <p>
 * Lo scan di {@code it.govpay.common.client} carica la configurazione comune di
 * {@code govpay-common} ({@code GovPayClientAutoConfiguration}): connettori,
 * repository e {@code ConfigurazioneService}, da cui dipendono i livelli di log
 * letti dalla tabella {@code configurazione} ({@code govpay.logging.livelli-dinamici.*})
 * e, con il porting dei promemoria, la configurazione SMTP {@code mail_batch}.
 */
@SpringBootApplication(scanBasePackages = {"it.govpay.mail.batch", "it.govpay.common.client"})
@EnableScheduling
public class Application extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
