FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copia tutti i file sorgente
COPY . .

# Compila tutti i .java nella cartella out
RUN javac -d out $(find . -name "*.java")

# Esegui la classe principale (sostituisci col package corretto)
CMD ["java", "-cp", "out", "Progetto_fine.ChatServer.ChatServer"]

EXPOSE 8080
