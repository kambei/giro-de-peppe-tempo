# Giro de Peppe
## Quarkus - Go - C++ - Tempo | Playground

This is a playground project to test the integration between Quarkus, Go, C++ and Tempo. It demonstrates distributed tracing across multiple services.

- Run the following command to start the project:

```bash
docker compose pull
```

```bash
docker compose up -d
```

```bash
curl http://localhost:1666/hello
```

- Access the frontend at [http://localhost:8090](http://localhost:8090) to interact with the application and generate traces from the browser.
   
- Go to Grafana ([http://localhost:3000](http://localhost:3000)), add Tempo datasource ([http://tempo:3200](http://tempo:3200)) and Enjoy the traces in the Explore view!


---


![GiroDePeppe](imgs/giro-de-peppe.png)
![SchermataGrafanaExplore](imgs/Schermata.png)
