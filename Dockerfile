FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copia tutti i file sorgente
COPY . .

# Compila tutti i .java nella cartella out
RUN javac -d out $(find . -name "*.java")

# Esegui la classe principale senza package
CMD ["java", "-cp", "out", "ChatServer"]

EXPOSE 8080
