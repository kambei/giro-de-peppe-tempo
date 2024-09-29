#include <boost/beast/core.hpp>
#include <boost/beast/http.hpp>
#include <boost/asio.hpp>
#include <thread>
#include <iostream>
#include <opentelemetry/sdk/trace/batch_span_processor.h>
#include <opentelemetry/sdk/trace/tracer_provider.h>
#include <opentelemetry/exporters/otlp/otlp_http_exporter.h>
#include <opentelemetry/trace/provider.h>
#include <opentelemetry/nostd/shared_ptr.h>
#include <opentelemetry/sdk/resource/resource.h>
#include <opentelemetry/sdk/resource/semantic_conventions.h>
#include <opentelemetry/trace/span.h>
#include <opentelemetry/context/propagation/text_map_propagator.h>
#include <opentelemetry/trace/context.h>
#include <opentelemetry/nostd/span.h>
#include <sstream>
#include <iomanip>
#include <cstdlib>
#include <opentelemetry/trace/trace_id.h>
#include <opentelemetry/trace/span_id.h>
#include <opentelemetry/trace/trace_flags.h>
#include <opentelemetry/nostd/shared_ptr.h>

namespace beast = boost::beast;
namespace http = beast::http;
namespace net = boost::asio;
using tcp = boost::asio::ip::tcp;
namespace trace = opentelemetry::trace;
namespace resource = opentelemetry::sdk::resource;
namespace semantic_conventions = opentelemetry::sdk::resource::SemanticConventions;

std::string extract_trace_id(const std::string &traceparent_header)
{
    size_t first_dash = traceparent_header.find('-');
    size_t second_dash = traceparent_header.find('-', first_dash + 1);
    return traceparent_header.substr(first_dash + 1, second_dash - first_dash - 1);
}

std::string extract_span_id(const std::string &traceparent_header)
{
    size_t second_dash = traceparent_header.find('-', traceparent_header.find('-') + 1);
    size_t third_dash = traceparent_header.find('-', second_dash + 1);
    return traceparent_header.substr(second_dash + 1, third_dash - second_dash - 1);
}

std::array<uint8_t, 16> hex_to_bytes(const std::string &hex)
{
    std::array<uint8_t, 16> bytes;
    for (size_t i = 0; i < hex.size(); i += 2)
    {
        std::string byteString = hex.substr(i, 2);
        bytes[i / 2] = (uint8_t)strtol(byteString.c_str(), nullptr, 16);
    }
    return bytes;
}

std::array<uint8_t, 8> hex_to_bytes_span(const std::string &hex)
{
    std::array<uint8_t, 8> bytes;
    for (size_t i = 0; i < hex.size(); i += 2)
    {
        std::string byteString = hex.substr(i, 2);
        bytes[i / 2] = (uint8_t)strtol(byteString.c_str(), nullptr, 16);
    }
    return bytes;
}

void handle_request(http::request<http::string_body> const &req, http::response<http::string_body> &res, trace::Span &span)
{
    std::cout << "Handling request for target: " << req.target() << std::endl;

    auto traceparent_iter = req.find("traceparent");
    if (traceparent_iter != req.end())
    {
        std::string traceparent_header = req["traceparent"];
        std::string trace_id = extract_trace_id(traceparent_header);
        std::string span_id = extract_span_id(traceparent_header);

        std::cout << "Trace ID: " << trace_id << std::endl;
        std::cout << "Span ID: " << span_id << std::endl;

        span.SetAttribute("http.trace_id", trace_id);
        span.SetAttribute("http.span_id", span_id);
    }
    else
    {
        std::cout << "traceparent header not found" << std::endl;
        span.SetAttribute("error", "traceparent header not found");
    }

    if (req.target() == "/hello")
    {
        std::cout << "Responding with 'Hello, World!'" << std::endl;
        res.result(http::status::ok);
        res.body() = "Hello, World!";
        res.prepare_payload();
        span.SetAttribute("http.status_code", 200);
    }
    else
    {
        std::cout << "Resource not found" << std::endl;
        res.result(http::status::not_found);
        res.body() = "Resource not found";
        res.prepare_payload();
        span.SetAttribute("http.status_code", 404);
    }

    span.SetAttribute("http.method", std::string(req.method_string()));
    span.SetAttribute("http.target", std::string(req.target()));
    span.SetAttribute("http.url", std::string(req.target()));
}

void do_session(tcp::socket socket)
{
    auto provider = trace::Provider::GetTracerProvider();
    auto tracer = provider->GetTracer("http-server");

    try
    {
        beast::flat_buffer buffer;
        http::request<http::string_body> req;

        // Read the request
        http::read(socket, buffer, req);
        std::cout << "Received request: " << req << std::endl;

        // Check if traceparent header is present and extract trace and span IDs
        auto traceparent_iter = req.find("traceparent");
        trace::StartSpanOptions span_options;

        if (traceparent_iter != req.end())
        {
            std::string traceparent_header(traceparent_iter->value());
            std::string trace_id_str = extract_trace_id(traceparent_header);
            std::string span_id_str = extract_span_id(traceparent_header);

            // Convert the extracted strings to TraceId and SpanId
            auto trace_id_bytes = hex_to_bytes(trace_id_str);
            auto trace_id = trace::TraceId(opentelemetry::nostd::span<const uint8_t, 16>(trace_id_bytes.data(), trace_id_bytes.size()));

            auto span_id_bytes = hex_to_bytes_span(span_id_str);
            auto span_id = trace::SpanId(opentelemetry::nostd::span<const uint8_t, 8>(span_id_bytes.data(), span_id_bytes.size()));

            // Create the parent SpanContext
            trace::SpanContext parent_ctx(
                trace_id,
                span_id,
                trace::TraceFlags{trace::TraceFlags::kIsSampled},  // Assuming sampled
                true // Remote span
            );

            // Set the parent context for the span
            span_options.parent = parent_ctx;
        }

        // Start a new span with parent context if available
        auto span = tracer->StartSpan("http-request", span_options);
        trace::Scope scope(span);

        // Prepare response
        http::response<http::string_body> res{http::status::ok, req.version()};

        // Handle the request
        handle_request(req, res, *span);

        // Write the response
        std::cout << "Sending response: " << res << std::endl;
        http::write(socket, res);

        // Shutdown the socket
        boost::system::error_code ec;
        socket.shutdown(tcp::socket::shutdown_send, ec);
        if (ec)
        {
            std::cerr << "Socket shutdown error: " << ec.message() << std::endl;
        }
    }
    catch (std::exception const &e)
    {
        std::cerr << "Error in do_session: " << e.what() << std::endl;
    }
}

void accept_connection(tcp::acceptor &acceptor, net::io_context &ioc)
{
    acceptor.async_accept([&](boost::system::error_code ec, tcp::socket socket)
                          {
        if (!ec) {
            // Handle connection in a new thread
            std::thread{[s = std::move(socket)]() mutable {
                try {
                    do_session(std::move(s));
                } catch (const std::exception& e) {
                    std::cerr << "Error in session thread: " << e.what() << std::endl;
                }
            }}.detach();
        }
        // Continue accepting connections
        accept_connection(acceptor, ioc); });
}

int main()
{
    try
    {
        auto exporter = std::unique_ptr<opentelemetry::sdk::trace::SpanExporter>(
            new opentelemetry::exporter::otlp::OtlpHttpExporter());

        // Create BatchSpanProcessorOptions
        opentelemetry::sdk::trace::BatchSpanProcessorOptions options;

        // Define resource with the service name
        auto resource = resource::Resource::Create({{semantic_conventions::kServiceName, "cpp-http-server"}});

        // Pass the exporter and options to the BatchSpanProcessor
        auto processor = std::make_unique<opentelemetry::sdk::trace::BatchSpanProcessor>(
            std::move(exporter), options);

        // Create a TracerProvider with the resource
        auto provider = std::make_shared<opentelemetry::sdk::trace::TracerProvider>(
            std::move(processor), resource);

        trace::Provider::SetTracerProvider(
            opentelemetry::nostd::shared_ptr<opentelemetry::trace::TracerProvider>(provider));

        net::io_context ioc;
        tcp::acceptor acceptor{ioc, tcp::endpoint(tcp::v4(), 8080)};
        std::cout << "Server listening on http://0.0.0.0:8080" << std::endl;

        accept_connection(acceptor, ioc); // Start accepting connections asynchronously
        ioc.run();                        // Run the IO context to handle events
    }
    catch (std::exception const &e)
    {
        std::cerr << "Error in main: " << e.what() << std::endl;
    }

    return 0;
}