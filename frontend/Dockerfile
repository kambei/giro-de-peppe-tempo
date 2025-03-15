# Build stage
FROM node:18-alpine as build

WORKDIR /app

# Copy source files
COPY src/ /app/

# Install dependencies if needed
# If you need to build the app, add build steps here

# Final stage
FROM nginx:alpine

# Copy built files from build stage
COPY --from=build /app/ /usr/share/nginx/html/

# Copy nginx configuration
COPY nginx/nginx.conf /etc/nginx/conf.d/default.conf

# Expose port
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
