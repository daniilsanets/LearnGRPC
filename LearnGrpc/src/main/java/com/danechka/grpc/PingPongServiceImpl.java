package com.danechka.grpc;

import io.grpc.stub.StreamObserver;

public class PingPongServiceImpl extends com.danechka.grpc.PingPongServiceGrpc.PingPongServiceImplBase{
    @Override
    public void ping(com.danechka.grpc.PingRequest request, StreamObserver<com.danechka.grpc.PongResponse> responseObserver) {
        String clientMessage = request.getMessage();
        System.out.println("[Server] Client messsage was gotten: " + clientMessage);

        com.danechka.grpc.PongResponse response = com.danechka.grpc.PongResponse.newBuilder()
                .setMessage("PONG!!! Server return you: " + clientMessage)
                .build();

        responseObserver.onNext(response);

        responseObserver.onCompleted();
    }
}
