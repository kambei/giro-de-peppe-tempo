package com.sigeosrl.serviceb.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Path("/changelog")
public class ChangeLogResource {

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getChangelog() {
        // Accessing CHANGELOG.md from the classpath (inside src/main/resources)
        InputStream in = getClass().getResourceAsStream("/CHANGELOG.md");

        // Check if the input stream is null, which means the file was not found
        if (in == null) {
            // Return a 404 Not Found response if the file cannot be found
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("CHANGELOG.md file not found")
                    .build();
        }

        try {
            // Read the entire stream into a byte array
            byte[] content = in.readAllBytes();
            // Return the byte array with a response type of application/octet-stream
            return Response.ok(content, MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"CHANGELOG.md\"")
                    .build();
        } catch (Exception e) {
            // Return a 500 Internal Server Error response if an exception occurs while reading the file
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error reading CHANGELOG.md: " + e.getMessage())
                    .build();
        } finally {
            try {
                in.close();  // Ensure the InputStream is closed after use
            } catch (Exception e) {
                // Log or handle the failure to close the InputStream, if necessary
            }
        }
    }

    @GET
    @Path("/string")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getChangelogAsString() {
        // Accessing CHANGELOG.md from the classpath (inside src/main/resources)
        InputStream in = getClass().getResourceAsStream("/CHANGELOG.md");

        // Check if the input stream is null, which means the file was not found
        if (in == null) {
            // Return a 404 Not Found response if the file cannot be found
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("CHANGELOG.md file not found")
                    .build();
        }

        try {
            // Read the entire stream into a byte array
            byte[] content = in.readAllBytes();
            // Convert the byte array to a string using UTF-8 encoding
            String changelog = new String(content, StandardCharsets.UTF_8);
            // Return the string with a response type of text/plain
            return Response.ok(changelog, MediaType.TEXT_PLAIN)
                    .build();
        } catch (Exception e) {
            // Return a 500 Internal Server Error response if an exception occurs while reading the file
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error reading CHANGELOG.md: " + e.getMessage())
                    .build();
        } finally {
            try {
                in.close();  // Ensure the InputStream is closed after use
            } catch (Exception e) {
                // Log or handle the failure to close the InputStream, if necessary
            }
        }
    }
}
