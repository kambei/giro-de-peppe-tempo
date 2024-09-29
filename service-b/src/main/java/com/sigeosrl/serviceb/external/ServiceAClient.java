package com.sigeosrl.serviceb.external;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey="service-a")
//@RegisterClientHeaders
public interface ServiceAClient {

    @GET
    @Path("/hello")
    Response hello();
}