<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - Mail Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-mail-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-mail-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-mail-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-mail-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-mail-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Flink-it%2Fgovpay-bom%2Fmain%2Fpom.xml&query=%2F%2F*%5Blocal-name()%3D'spring-boot.version'%5D&label=Spring%20Boot&color=brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

Batch Spring Boot destinato alla spedizione via email dei promemoria di GovPay (avvisi di pagamento, scadenze e ricevute), oggi in fase di sviluppo.

## Sommario

> **Stato dello sviluppo.** Su `main` il progetto è uno scheletro: un'applicazione Spring Boot con datasource, repository JDBC dei metadati Spring Batch, actuator e pipeline di validazione e rilascio, ma **senza nessun job**. Non legge la tabella `promemoria` e non spedisce email: avviato, resta in attesa (scheduler interno) o termina subito (profilo `cron`). I promemoria continuano a essere spediti dal monolite GovPay. Il porting è descritto nella issue [#3](https://github.com/link-it/govpay-mail-batch/issues/3).

Il batch prenderà il posto dei timer `gestione-promemoria` e `spedizione-promemoria` del monolite:

| Tipo di promemoria | Origine | Destinatari |
|--------------------|---------|-------------|
| Avviso di pagamento | Pendenze da avvisare (`versamenti.data_notifica_avviso`) | Debitore, con l'avviso PDF allegato se configurato |
| Scadenza | Pendenze in scadenza (`versamenti.avv_mail_data_prom_scadenza`) | Debitore |
| Ricevuta | Acquisizione della ricevuta di una pendenza pagata | Versante, in copia il debitore, con la ricevuta PDF allegata se configurato |

Le righe da spedire sono nella tabella `promemoria` (stato `DA_SPEDIRE`); oggetto e messaggio si ricavano dai template FreeMarker della configurazione di GovPay e del tipo pendenza, il server SMTP dalla configurazione `mail_batch` di GovPay.

Il job si avvierà da uno scheduler interno (modalità di default) o una sola volta con il profilo `cron`; le esecuzioni saranno coordinate tra più nodi tramite i metadati Spring Batch sul database, come negli altri batch GovPay.

Storico delle modifiche nel [ChangeLog](ChangeLog).

## Integrazione con GovPay

| Componente | Quando serve | Obbligatorio | Ruolo |
|------------|--------------|--------------|-------|
| Database di GovPay | runtime | sì | Datasource dell'applicazione. Oggi nessuna tabella di GovPay viene letta o scritta. Con il porting: `promemoria` (lettura e aggiornamento dello stato), `versamenti`, `tipi_versamento`, `tipi_vers_domini`, `domini`, `rpt` e `configurazione` (`mail_batch`, `avvisatura_mail`) |
| Tabelle Spring Batch | runtime | no, oggi | Metadati delle esecuzioni (`batch_job_*`, `batch_step_*`), su cui punta il repository JDBC di `govpay-common`. Senza job non vengono usate e l'applicazione parte anche se mancano; serviranno con il primo job. Lo schema è nel pacchetto `sql.zip` (vedi [Compilazione](#compilazione)) e l'immagine Docker può crearlo all'avvio |
| [govpay-bom](https://github.com/link-it/govpay-bom) | build | sì | Parent Maven: versioni delle dipendenze e dei plugin, profilo `dist` |
| [govpay-common](https://github.com/link-it/govpay-common) | build | sì | Libreria comune: repository JDBC dei metadati Spring Batch e tracciatura di transaction id e correlation id. Con il porting: invio delle email (`AbstractMailService`, configurazione `mail_batch`), coordinamento dei job, API REST del batch |
| [govpay-common-pendenze](https://github.com/link-it/govpay-common-pendenze) | build | previsto | Modello delle pendenze e delle ricevute usato dal porting; oggi non è una dipendenza |
| Server SMTP | runtime | previsto | Server di posta dell'Ente. Host, porta, credenziali, mittente, timeout, STARTTLS e SSL non sono proprietà del batch: sono nella configurazione `mail_batch` di GovPay (tabella `configurazione`), impostata dalla console |
| [govpay-stampe-api](https://github.com/link-it/govpay-stampe-api) | runtime | previsto | PDF dell'avviso di pagamento e della ricevuta da allegare ai promemoria |
| GovPay (monolite) | runtime | sì, oggi | Crea le righe di `promemoria` e, finché il batch non è completo, le spedisce con i propri timer. Quando il batch sarà in uso, i timer del monolite si disattivano con `it.govpay.batch.gestionePromemoria.enabled` e `it.govpay.batch.spedizionePromemoria.enabled` |
| [govpay-gde-api](https://github.com/link-it/govpay-gde-api) | runtime | no | Il batch non invia eventi al Giornale degli Eventi. La variabile `GOVPAY_MAIL_GDE_URL` dell'immagine non ha effetto (vedi [Esecuzione con Docker](#esecuzione-con-docker)) |

## Compilazione

Requisiti: JDK 21 e Maven 3.8.1 o successivo.

```bash
mvn clean install
```

Il risultato è il fat jar `target/govpay-mail-batch.jar`. La build esegue i test e l'analisi OWASP Dependency-Check delle dipendenze (esecuzione `check-owasp` di `govpay-bom`); per escludere l'analisi OWASP, per esempio in sviluppo:

```bash
mvn clean install -Dowasp.phase=none
```

Per produrre anche `target/sql.zip`, con lo schema Spring Batch per ogni database e gli script di svecchiamento dei metadati, usato dall'immagine Docker e allegato alle release:

```bash
mvn clean install -Pjar,dist
```

Il profilo `jar` va indicato insieme a `dist`: è quello che lascia i driver JDBC fuori dal jar.

## Configurazione

Le proprietà si impostano in `application.properties`, da riga di comando (`-D<proprietà>=<valore>`) o con le variabili d'ambiente equivalenti di Spring Boot, indicate nelle tabelle. Con l'immagine Docker si usano anche le variabili descritte in [Esecuzione con Docker](#esecuzione-con-docker).

Il server SMTP non si configura con proprietà del batch: vedi [Integrazione con GovPay](#integrazione-con-govpay).

### Esecuzione e cluster

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `scheduler.mailJob.fixedDelayString` | `SCHEDULER_MAILJOB_FIXEDDELAYSTRING` | `7200000` | Intervallo tra un'esecuzione e la successiva, in ms (2 ore). Predisposta per il job: oggi non è letta |
| `scheduler.initialDelayString` | `SCHEDULER_INITIALDELAYSTRING` | `1` | Ritardo della prima esecuzione dopo l'avvio, in ms. Predisposta per il job: oggi non è letta |
| `govpay.batch.cluster-id` | `GOVPAY_BATCH_CLUSTERID` | `GovPay-Mail-Batch` | Identificativo del nodo, da registrare nelle esecuzioni; nel container è l'indirizzo IP. Oggi non è letta |
| `govpay.batch.stale-threshold-minutes` | `GOVPAY_BATCH_STALETHRESHOLDMINUTES` | `120` | Minuti senza aggiornamenti dopo cui un'esecuzione in corso sarà considerata bloccata. Oggi non è letta |
| `govpay.batch.enabled` | `GOVPAY_BATCH_ENABLED` | `true` | Oggi non è letta |
| `govpay.batch.job-repository.jdbc.enabled` | `GOVPAY_BATCH_JOBREPOSITORY_JDBC_ENABLED` | `true` | Metadati Spring Batch su database (da `govpay-common`); senza, il coordinamento tra nodi non funziona |
| `spring.batch.job.enabled` | `SPRING_BATCH_JOB_ENABLED` | `false` | Avvio automatico dei job da parte di Spring Boot: resta `false`, i job saranno avviati dallo scheduler o dal profilo `cron` |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `default` | `cron` per l'esecuzione singola (vedi [Esecuzione](#esecuzione)) |
| `server.port` | `SERVER_PORT` | `8080` (`10001` nel container) | Porta HTTP degli endpoint actuator |
| `spring.application.name` | `SPRING_APPLICATION_NAME` | `govpay-mail-batch` | Nome dell'applicazione nei log |
| `spring.main.banner-mode` | `SPRING_MAIN_BANNERMODE` | `off` | Banner di Spring Boot all'avvio |
| `spring.jackson.default-property-inclusion` | `SPRING_JACKSON_DEFAULTPROPERTYINCLUSION` | `non_null` | Esclude i campi nulli dal JSON |
| `spring.jackson.time-zone` | `SPRING_JACKSON_TIMEZONE` | `Europe/Rome` | Fuso orario delle date nel JSON |

### Database

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | - | URL JDBC del database di GovPay (obbligatoria) |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | - | Utente del database |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | - | Password del database |
| `spring.datasource.driver-class-name` | `SPRING_DATASOURCE_DRIVERCLASSNAME` | dedotto dall'URL | Classe del driver JDBC |
| `spring.jpa.database-platform` | `SPRING_JPA_DATABASEPLATFORM` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.properties.hibernate.dialect` | `SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.hibernate.ddl-auto` | `SPRING_JPA_HIBERNATE_DDLAUTO` | `none` (`create-drop` con database embedded) | Gestione dello schema: lasciare `none` o `validate`, lo schema è di GovPay |
| `spring.datasource.hikari.minimum-idle` | `SPRING_DATASOURCE_HIKARI_MINIMUMIDLE` | `10` (`2` nel container) | Connessioni minime inattive del pool |
| `spring.datasource.hikari.maximum-pool-size` | `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | `10` | Connessioni massime del pool |
| `spring.datasource.hikari.connection-timeout` | `SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT` | `30000` (`20000` nel container) | Attesa massima di una connessione, in ms |
| `spring.datasource.hikari.idle-timeout` | `SPRING_DATASOURCE_HIKARI_IDLETIMEOUT` | `600000` (`10000` nel container) | Tempo dopo cui una connessione inattiva viene chiusa, in ms |
| `spring.datasource.hikari.max-lifetime` | `SPRING_DATASOURCE_HIKARI_MAXLIFETIME` | `1800000` (`600000` nel container) | Durata massima di una connessione, in ms |

I driver JDBC non sono inclusi nel jar: vedi [Esecuzione](#esecuzione).

### Logging

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `logging.file.name` | `LOGGING_FILE_NAME` | - (solo console) | File di log; nel container `/var/log/govpay-mail/govpay-mail.log` |
| `logging.level.it.govpay` | `LOGGING_LEVEL_IT_GOVPAY` | `INFO` | Livello di log dei package GovPay |
| `logging.pattern.level` | `LOGGING_PATTERN_LEVEL` | `%5p [%X{transactionId:-},%X{correlationId:-}]` | Aggiunge a ogni riga transaction id e correlation id |
| `govpay.logging.livelli-dinamici.enabled` | `GOVPAY_LOGGING_LIVELLIDINAMICI_ENABLED` | `true` | Livelli di log letti dalla riga `log_level` della tabella `configurazione`. **Oggi senza effetto**: il servizio di configurazione di `govpay-common` non viene caricato, quindi i livelli si cambiano solo con le proprietà `logging.level.*` |
| `govpay.logging.livelli-dinamici.intervallo-refresh` | `LOG_LEVEL_REFRESH_INTERVAL` | `60s` | Intervallo di rilettura dei livelli dal database; `0` li legge solo all'avvio. Oggi senza effetto |
| `govpay.logging.livelli-dinamici.ritardo-iniziale` | `GOVPAY_LOGGING_LIVELLIDINAMICI_RITARDOINIZIALE` | `60s` | Ritardo della prima rilettura dopo quella di avvio. Oggi senza effetto |
| `govpay.logging.livelli-dinamici.logger-gestiti[0]` | `GOVPAY_LOGGING_LIVELLIDINAMICI_LOGGERGESTITI_0` | `it.govpay` | Prefissi dei logger modificabili da database. Oggi senza effetto |
| `govpay.logging.tracciatura.enabled` | `GOVPAY_LOGGING_TRACCIATURA_ENABLED` | `true` | Filtro HTTP di `govpay-common` che assegna transaction id e correlation id a ogni richiesta e li restituisce negli header di risposta |
| `govpay.logging.tracciatura.correlation-inbound-headers` | `GOVPAY_LOGGING_TRACCIATURA_CORRELATIONINBOUNDHEADERS` | `X-Correlation-ID,X-Request-ID` | Header letti per il correlation id, in ordine di precedenza |
| `govpay.logging.tracciatura.correlation-response-headers` | `GOVPAY_LOGGING_TRACCIATURA_CORRELATIONRESPONSEHEADERS` | `X-Correlation-ID,X-Request-ID` | Header di risposta con il correlation id |
| `govpay.logging.tracciatura.correlation-downstream-headers` | `GOVPAY_LOGGING_TRACCIATURA_CORRELATIONDOWNSTREAMHEADERS` | `X-Correlation-ID,X-Request-ID` | Header con il correlation id sulle chiamate verso altri servizi |
| `govpay.logging.tracciatura.transaction-response-headers` | `GOVPAY_LOGGING_TRACCIATURA_TRANSACTIONRESPONSEHEADERS` | `X-Transaction-ID,X-Govpay-IdTransazione` | Header di risposta con il transaction id |
| `govpay.logging.tracciatura.lunghezza-massima-id` | `GOVPAY_LOGGING_TRACCIATURA_LUNGHEZZAMASSIMAID` | `128` | Lunghezza massima di un identificativo ricevuto; oltre viene sostituito da uno generato |
| `govpay.logging.tracciatura.pattern-id-valido` | `GOVPAY_LOGGING_TRACCIATURA_PATTERNIDVALIDO` | `[A-Za-z0-9._:\-]+` | Espressione regolare che un identificativo ricevuto deve soddisfare per essere riusato |
| `govpay.logging.tracciatura.ordine` | `GOVPAY_LOGGING_TRACCIATURA_ORDINE` | `-2147483548` (`Integer.MIN_VALUE + 100`) | Ordine del filtro nella catena servlet |

### Monitoraggio

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `management.server.port` | `MANAGEMENT_SERVER_PORT` | porta del servizio | Porta dedicata agli endpoint actuator |
| `management.endpoints.web.base-path` | `MANAGEMENT_ENDPOINTS_WEB_BASEPATH` | `/actuator` | Base path degli endpoint actuator |
| `management.endpoints.web.exposure.include` | `MANAGEMENT_ENDPOINTS` | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `management.endpoint.health.show-details` | `MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS` | `when_authorized` | Dettagli dell'health: il servizio non ha autenticazione, quindi restituisce solo lo stato |
| `management.health.db.enabled` | `MANAGEMENT_HEALTH_DB_ENABLED` | `true` | Include la connessione al database nell'health |
| `management.metrics.tags.application` | `MANAGEMENT_METRICS_TAGS_APPLICATION` | `govpay-mail-batch` | Tag che identifica il servizio nelle metriche |
| `govpay.metrics.enabled` | `GOVPAY_METRICS_ENABLED` | `false` | Metriche di `govpay-common` sui tempi delle richieste HTTP e delle chiamate verso altri servizi |

## Esecuzione

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e vanno forniti a runtime. Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato (versioni gestite da `govpay-bom`):

| Database   | Driver |
|------------|--------|
| PostgreSQL | [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar) |
| MySQL      | [mysql-connector-j-26.10.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar) |
| Oracle     | [ojdbc11-23.26.3.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar) |
| SQL Server | [mssql-jdbc-13.6.1.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.6.1.jre11/mssql-jdbc-13.6.1.jre11.jar) |
| H2         | [h2-2.5.252.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252.jar) |

### Schema Spring Batch

Le tabelle dei metadati Spring Batch si creano nel database di GovPay con lo script `sql/spring-batch/schema-<database>.sql` di `sql.zip`; l'immagine Docker lo esegue da sola se `GOVPAY_MAIL_POP_DB_SKIP=FALSE`. Gli script in `sql/cleanup/` cancellano i metadati delle esecuzioni vecchie e vanno pianificati periodicamente. Finché il batch non ha job le tabelle non vengono usate.

### Jar standalone

Il jar usa `PropertiesLauncher`: la proprietà `loader.path` indica la directory dei driver JDBC. Configurazione minima per PostgreSQL, con lo scheduler interno:

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -jar target/govpay-mail-batch.jar
```

Modalità di esecuzione:

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (nessun profilo) | Il processo resta attivo ed espone gli endpoint actuator; eseguirà il job ogni `scheduler.mailJob.fixedDelayString` ms. Oggi non esegue nulla |
| Esecuzione singola | `--spring.profiles.active=cron --spring.main.web-application-type=none` | Eseguirà il job una volta e terminerà, con la pianificazione affidata a un cron esterno. Oggi l'applicazione parte e termina subito con exit code `0`. Senza `spring.main.web-application-type=none` il server HTTP tiene attivo il processo (nel container lo imposta l'entrypoint) |

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -jar target/govpay-mail-batch.jar --spring.profiles.active=cron --spring.main.web-application-type=none
```

## Esecuzione con Docker

L'immagine sarà pubblicata su Docker Hub come [linkitaly/govpay-mail-batch](https://hub.docker.com/r/linkitaly/govpay-mail-batch) (release) e `linkitaly/govpay-mail-batch-dev` (sviluppo, a ogni push su `main`); oggi non c'è ancora nessuna release. In locale si costruisce con `docker/build_image.sh` (vedi [docker/DOCKER.md](docker/DOCKER.md)). Con lo scheduler interno il servizio risponde sulla porta `10001`; i driver JDBC si montano in `/opt/jdbc-drivers`.

```bash
docker run -d --name govpay-mail-batch -p 10001:10001 \
  -v ./jdbc-drivers:/opt/jdbc-drivers \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=postgres:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=<password> \
  linkitaly/govpay-mail-batch
```

Variabili d'ambiente dell'immagine, oltre alle proprietà Spring Boot in forma di variabile indicate in [Configurazione](#configurazione):

| Variabile | Obbligatoria | Default | Descrizione |
|-----------|--------------|---------|-------------|
| `GOVPAY_DB_TYPE` | sì | - | Tipo di database: `postgresql`, `mysql`, `mariadb`, `oracle` |
| `GOVPAY_DB_SERVER` | sì | - | Server del database, `host` o `host:porta` (porta di default del database se omessa) |
| `GOVPAY_DB_NAME` | sì | - | Nome del database (per Oracle, service name o SID) |
| `GOVPAY_DB_USER` | sì | - | Utente del database |
| `GOVPAY_DB_PASSWORD` | sì | - | Password del database (se manca l'avvio prosegue con un avviso) |
| `GOVPAY_DS_CONN_PARAM` | no | - | Parametri aggiunti all'URL JDBC (es. `zeroDateTimeBehavior=convertToNull` per MySQL) |
| `GOVPAY_DS_JDBC_LIBS` | no | `/opt/jdbc-drivers` | Directory dei driver JDBC |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | no | `servicename` | Formato dell'URL Oracle: `servicename` o `sid` |
| `ORACLE_TNS_ADMIN` | no | - | Directory dei file TNS di Oracle |
| `GOVPAY_MAIL_MIN_POOL` | no | `2` | Connessioni minime inattive del pool |
| `GOVPAY_MAIL_MAX_POOL` | no | `10` | Connessioni massime del pool |
| `GOVPAY_MAIL_BATCH_USA_CRON` | no | - | `true`, `si`, `yes` o `1`: esecuzione singola con il profilo `cron`, senza server HTTP; il container termina a fine esecuzione e va pianificato dall'esterno (es. CronJob Kubernetes). Altrimenti scheduler interno |
| `GOVPAY_MAIL_BATCH_INTERVALLO_CRON` | no | `120` | Minuti tra un'esecuzione e l'altra con lo scheduler interno: l'entrypoint la converte in `SCHEDULER_MAILJOB_FIXEDDELAYSTRING`, sovrascrivendo un valore passato direttamente. Oggi senza effetto, perché il job non c'è |
| `SERVER_PORT` | no | `10001` | Porta HTTP con lo scheduler interno |
| `MANAGEMENT_SERVER_PORT` | no | porta del servizio | Porta dedicata agli endpoint actuator |
| `MANAGEMENT_ENDPOINTS` | no | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `LOG_LEVEL_REFRESH_INTERVAL` | no | `60s` | Intervallo di rilettura dei livelli di log dal database (oggi senza effetto, vedi [Logging](#logging)) |
| `GOVPAY_MAIL_GDE_URL` | no | - | **Senza effetto**: l'entrypoint ne ricava `GOVPAY_GDE_ENABLED` e `GOVPAY_GDE_BASE_URL`, che il batch non legge. Negli altri batch l'invio al GDE si configura con il connettore `govpay_gde_api` di GovPay |
| `GOVPAY_MAIL_POP_DB_SKIP` | no | `TRUE` | `FALSE` crea all'avvio le tabelle Spring Batch, se la tabella `GOVPAY_MAIL_DB_CHECK_TABLE` non esiste |
| `GOVPAY_MAIL_DB_CHECK_TABLE` | no | `batch_job_execution_context` | Tabella che indica che lo schema Spring Batch è già presente |
| `GOVPAY_MAIL_LIVE_DB_CHECK_SKIP` | no | `FALSE` | Salta l'attesa della porta del database prima dell'inizializzazione |
| `GOVPAY_MAIL_LIVE_DB_CHECK_MAX_RETRY` | no | `30` | Tentativi di connessione alla porta del database |
| `GOVPAY_MAIL_LIVE_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_MAIL_LIVE_DB_CHECK_CONNECT_TIMEOUT` | no | `5` | Timeout di ogni tentativo di connessione, in secondi |
| `GOVPAY_MAIL_READY_DB_CHECK_SKIP` | no | `FALSE` | Salta la verifica della tabella e crea comunque lo schema |
| `GOVPAY_MAIL_READY_DB_CHECK_MAX_RETRY` | no | `5` | Tentativi della verifica della tabella |
| `GOVPAY_MAIL_READY_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_MAIL_JVM_MAX_RAM_PERCENTAGE` | no | `80` | `-XX:MaxRAMPercentage` della JVM |
| `GOVPAY_MAIL_JVM_INITIAL_RAM_PERCENTAGE` | no | - | `-XX:InitialRAMPercentage` della JVM |
| `GOVPAY_MAIL_JVM_MIN_RAM_PERCENTAGE` | no | - | `-XX:MinRAMPercentage` della JVM |
| `GOVPAY_MAIL_JVM_MAX_METASPACE_SIZE` | no | - | `-XX:MaxMetaspaceSize` della JVM |
| `GOVPAY_MAIL_JVM_MAX_DIRECT_MEMORY_SIZE` | no | - | `-XX:MaxDirectMemorySize` della JVM |
| `JAVA_OPTS` | no | - | Opzioni aggiuntive della JVM |

L'identificativo del nodo (`govpay.batch.cluster-id`) è impostato dall'entrypoint, con la variabile `GOVPAY_BATCH_CLUSTER_ID`, all'indirizzo IP del container o al suo hostname se l'IP non si ricava: un valore passato dall'esterno viene sovrascritto. Le altre variabili che compaiono negli script dell'immagine (es. `INTERVALLO_MINUTI`, `HSQLDB_SHA256`) sono interne e non vanno impostate. Il Dockerfile imposta anche `GOVPAY_BATCH_THREAD_POOL_SIZE`, `GOVPAY_BATCH_HEADERS_CHUNK_SIZE`, `GOVPAY_BATCH_METADATA_CHUNK_SIZE`, `GOVPAY_BATCH_PAYMENTS_CHUNK_SIZE`, `GOVPAY_BATCH_SKIP_LIMIT`, `LOGGING_DIRECTORYPATH` e `LOGGING_FILEPREFIX`, che il batch non legge. Il log di avvio dell'entrypoint è in `/tmp/entrypoint_debug.log` dentro il container.

Costruzione dell'immagine in locale, scansione delle vulnerabilità e pacchetti inclusi sono descritti in [docker/DOCKER.md](docker/DOCKER.md) e [docker/SCANSIONE.md](docker/SCANSIONE.md).

## Monitoraggio

Con lo scheduler interno gli endpoint actuator rispondono di default sulla porta del servizio (`8080` con il jar, `10001` nel container):

```bash
curl http://localhost:10001/actuator/health       # stato, compresa la connessione al database
curl http://localhost:10001/actuator/prometheus   # metriche in formato Prometheus
```

Per spostarli su una porta dedicata impostare `management.server.port` (nel container `MANAGEMENT_SERVER_PORT`). L'endpoint `loggers`, che permette di cambiare i livelli di log a caldo, non è esposto di default perché il servizio non ha autenticazione: abilitarlo solo insieme a una porta dedicata e non raggiungibile dall'esterno, per esempio `MANAGEMENT_SERVER_PORT=9090` e `MANAGEMENT_ENDPOINTS=health,info,metrics,prometheus,loggers`.

Il batch non espone ancora l'API REST comune ai batch GovPay (`/api/batch`, avvio manuale e consultazione delle esecuzioni): arriverà con il job. Con il profilo `cron` non c'è server HTTP: l'esito si ricava dall'exit code del processo e dai log.

## Licenza

GovPay Mail Batch è distribuito con licenza [GNU General Public License v3.0](LICENSE).
