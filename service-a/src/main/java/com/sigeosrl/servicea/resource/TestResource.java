package com.sigeosrl.servicea.resource;

import com.sigeosrl.servicea.external.ServiceGoClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/hello")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TestResource {

    @Inject
    @RestClient
    ServiceGoClient serviceGoClient;

    @ConfigProperty(name = "service-go.enabled")
    boolean serviceGoEnabled;

    @GET
    public Response hello() {

        if (serviceGoEnabled) {
            Response response = serviceGoClient.hello();
            return Response.ok("Hello from Service A, that called Service Go: " + response.readEntity(String.class)).build();
        }
        return Response.ok("Hello from Service A").build();
    }
}
