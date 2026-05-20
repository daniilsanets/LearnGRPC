package com.danechka.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class GrpcServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(9090)
                .addService(new PingPongServiceImpl())
                .build();

        server.start();
        System.out.println("Server started, listening on " + server.getPort());

        server.awaitTermination();
    }
}
