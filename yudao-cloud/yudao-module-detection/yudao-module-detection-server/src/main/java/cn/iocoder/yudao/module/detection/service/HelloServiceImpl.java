package cn.iocoder.yudao.module.detection.service;

import cn.iocoder.yudao.module.detection.api.hello.HelloReply;
import cn.iocoder.yudao.module.detection.api.hello.HelloRequest;
import cn.iocoder.yudao.module.detection.api.hello.HelloServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello ==> " + request.getName())
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}