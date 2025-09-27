# Usa JDK 17 ufficiale
FROM eclipse-temurin:17-jdk

# Cartella di lavoro dentro il container
WORKDIR /app

# Copia il JAR e le eventuali risorse
COPY my-webapp.jar ./
COPY static/ ./static/

# Esponi la porta del server (ad esempio 8080)
EXPOSE 8080

# Comando per avviare il server
CMD ["java", "-jar", "my-webapp.jar"]
