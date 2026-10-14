# Use Eclipse Temurin JDK 17 (the official replacement for openjdk)
FROM eclipse-temurin:17-jdk

# Set the working directory inside the container
WORKDIR /app

# Copy the Java sources and data files
COPY . /app

# Compile all .java files into .class files
RUN javac *.java

# Run the server
CMD ["java", "NavigationServer"]