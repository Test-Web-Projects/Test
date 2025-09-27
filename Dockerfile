FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN javac -d out $(find . -name "*.java")
CMD ["java", "-cp", "out", "Progetto_fine/ChatServer/src/ChatServer.java"]  # sostituisci con la tua classe Main
EXPOSE 8080
