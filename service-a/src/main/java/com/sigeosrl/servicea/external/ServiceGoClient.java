package com.sigeosrl.servicea.external;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey="service-go")
//@RegisterClientHeaders
public interface ServiceGoClient {

    @GET
    @Path("/hello")
    Response hello();

    @GET
    @Path("/end")
    Response end();
}