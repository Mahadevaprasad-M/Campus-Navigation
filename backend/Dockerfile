# Use an official OpenJDK runtime as a base image
FROM openjdk:17-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the compiled .class files and data files into the container
COPY . /app

# The command that runs when the container starts
CMD ["java", "NavigationServer"]