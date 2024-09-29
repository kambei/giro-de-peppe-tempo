FROM ubuntu:24.04 AS builder

# Install necessary packages
RUN apt-get update && \
    apt-get install -y \
        g++ \
        libbenchmark-dev \
        libboost-all-dev \
        libcurl4-openssl-dev \
        libgtest-dev \
        libgmock-dev \
        cmake \
        git \
        protobuf-compiler \
        libprotobuf-dev && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Clone OpenTelemetry C++ repository
RUN git clone --recursive https://github.com/open-telemetry/opentelemetry-cpp.git && \
    cd opentelemetry-cpp && \
    mkdir build && \
    cd build && \
    cmake -DWITH_OTLP_HTTP=ON -DCMAKE_INSTALL_PREFIX=/opentelemetry-cpp/build/install .. && \
    make -j$(nproc) && \
    make install

RUN apt-get update && apt-get install nlohmann-json3-dev -y && apt-get clean && rm -rf /var/lib/apt/lists/*

# Copy your application code
COPY . .

# Build your application
RUN mkdir build && cd build && cmake .. \
        -DCMAKE_VERBOSE_MAKEFILE=ON \
        -DCMAKE_INSTALL_PREFIX=/opentelemetry-cpp/build/install && \
    make VERBOSE=1
# RUN make clean && make VERBOSE=1

FROM ubuntu:24.04 AS runtime

# Copy built application and libraries from builder stage
COPY --from=builder /opentelemetry-cpp/build/install /opentelemetry-cpp/build/install
COPY --from=builder /build/http_server /usr/local/bin/http_server

RUN chmod +x /usr/local/bin/http_server

RUN apt-get update && \
    apt-get install -y \
        libcurl4 \
        libprotobuf-dev && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Expose port 8080
EXPOSE 8080

# Run your application
CMD ["/usr/local/bin/http_server"]