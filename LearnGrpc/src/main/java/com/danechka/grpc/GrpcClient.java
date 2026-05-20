package com.danechka.grpc;

import com.danechka.grpc.PingRequest;
import com.danechka.grpc.PongResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class GrpcClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        com.danechka.grpc.PingPongServiceGrpc.PingPongServiceBlockingStub stub =
                com.danechka.grpc.PingPongServiceGrpc.newBlockingStub(channel);

        PingRequest request = PingRequest.newBuilder()
                .setMessage("hello from client :)")
                .build();
        System.out.println("[Client] Sending request to server....");

        PongResponse response = stub.ping(request);

        System.out.println("[Client] Received response from server: " + response.getMessage());

        channel.shutdown();
    }
}
