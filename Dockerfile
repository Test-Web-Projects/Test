# Usa immagine JRE leggera
FROM eclipse-temurin:17-jre

# Cartella di lavoro nel container
WORKDIR /app

# Copia il jar già compilato
COPY ProgettoChat.jar app.jar

# Copia i file web (HTML, CSS, JS) nella cartella static se servono
COPY static ./static

# Esponi la porta del server Java (cambia se il tuo server usa un'altra porta)
EXPOSE 8080

# Comando per avviare il server
CMD ["java", "-jar", "app.jar"]
