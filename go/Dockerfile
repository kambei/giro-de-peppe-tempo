# Stage 1: Build the Go binary
FROM golang:1.23 AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the Go module files and download dependencies
COPY go.mod go.sum ./
RUN go mod download

# Copy the rest of the application source code
COPY . .

# Build the Go binary
RUN go build -o app main.go

# Stage 2: Use Ubuntu as the base image to run the Go application
FROM ubuntu:24.04

# Install certificates to handle HTTPS requests (if necessary)
RUN apt-get update && apt-get install -y ca-certificates

# Set the working directory inside the container
WORKDIR /app

# Copy the binary from the builder stage
COPY --from=builder /app/app .

# Expose port (if your app listens on a specific port, e.g., 8080)
EXPOSE 8080

# Run the binary
CMD ["./app"]
