# GovPay Mail Batch - Immagine Docker

Immagine del batch destinato alla spedizione via email dei promemoria di GovPay. Il batch è ancora in sviluppo: su `main` non contiene job e non spedisce email (vedi il [README](../README.md#sommario)). Cosa fa il batch, come si configura e l'elenco completo delle variabili d'ambiente sono nel [README](../README.md#esecuzione-con-docker): qui solo ciò che riguarda l'immagine.

## Contenuto

| Percorso | Contenuto |
|----------|-----------|
| `/opt/govpay-mail/govpay-mail-batch.jar` | Fat jar del batch |
| `/opt/jdbc-drivers` | Driver JDBC, da montare: non sono inclusi nell'immagine |
| `/opt/sql` | Script SQL di `sql.zip`: schema Spring Batch per database (`spring-batch/`) e svecchiamento dei metadati (`cleanup/`) |
| `/opt/hsqldb-<versione>` | SqlTool di HSQLDB, usato per creare lo schema Spring Batch all'avvio |
| `/var/log/govpay-mail/govpay-mail.log` | File di log dell'applicazione |

Il processo gira con l'utente `govpay`; l'entrypoint è `/usr/local/bin/entrypoint.sh`. I messaggi dell'entrypoint e della JVM vanno nel log del container (`docker logs`).

## Costruzione

```bash
cd docker
./build_image.sh                       # ultima release pubblicata su GitHub
./build_image.sh -v <versione>         # una release specifica
./build_image.sh -l ../target/govpay-mail-batch.jar -t govpay-mail-batch:locale   # jar compilato in locale
```

Con una release, jar e `sql.zip` sono scaricati da GitHub e verificati con lo sha256 letto dalle API delle release (`-s` e `-q` per indicarlo a mano); oggi non ci sono release, quindi si usa `-l`. Con `-l` si usa `Dockerfile.daFile` e gli script SQL vengono da `../target/sql.zip`, prodotto da `mvn clean install -Pjar,dist`: senza, l'immagine non ha gli script per creare lo schema. `./build_image.sh -h` elenca tutte le opzioni.

## Modalità di esecuzione

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (`GOVPAY_MAIL_BATCH_USA_CRON` non impostata o falsa) | Il container resta attivo ed espone gli endpoint actuator sulla porta `10001`; eseguirà il job a intervalli |
| Esecuzione singola | `GOVPAY_MAIL_BATCH_USA_CRON=true` (anche `si`, `yes`, `1`) | Profilo `cron`, senza server HTTP: eseguirà il job una volta e terminerà. La pianificazione è esterna (cron di sistema, CronJob Kubernetes). Oggi il container parte e termina subito |

Con lo scheduler interno l'intervallo si indica in minuti con `GOVPAY_MAIL_BATCH_INTERVALLO_CRON` (default `120`), che l'entrypoint converte nella proprietà `scheduler.mailJob.fixedDelayString`. Se è impostata direttamente `SCHEDULER_MAILJOB_FIXEDDELAYSTRING` (in millisecondi), vale quella e `GOVPAY_MAIL_BATCH_INTERVALLO_CRON` viene ignorata. Il valore usato è scritto nel log di avvio:

```
[INFO] ... - Porta HTTP: 10001, Intervallo scheduler: 120 minuti (7200000ms)
```

## Schema Spring Batch

Con `GOVPAY_MAIL_POP_DB_SKIP=FALSE` l'entrypoint attende il database, controlla se esiste la tabella `GOVPAY_MAIL_DB_CHECK_TABLE` (default `batch_job_execution_context`) e, se manca, crea le tabelle dei metadati Spring Batch con lo script `/opt/sql/spring-batch/schema-<database>.sql`. Le tabelle di GovPay non vengono create: sono degli script di installazione di GovPay.

## Server SMTP e Giornale degli Eventi

Il server SMTP non si configura con variabili del container: sarà letto dalla configurazione `mail_batch` di GovPay (tabella `configurazione`), impostata dalla console. Il batch non invia eventi al GDE; la variabile `GOVPAY_MAIL_GDE_URL`, presente in versioni precedenti di questa documentazione, non ha effetto: se è impostata l'entrypoint lo segnala con un avviso nel log.

## Scansione delle vulnerabilità

Le immagini pubblicate dalla pipeline sono scansionate prima della pubblicazione: vedi [SCANSIONE.md](SCANSIONE.md).
