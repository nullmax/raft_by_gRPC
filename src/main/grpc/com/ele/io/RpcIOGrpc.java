package com.ele.io;

import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.4.0)",
    comments = "Source: rpcio.proto")
public final class RpcIOGrpc {

  private RpcIOGrpc() {}

  public static final String SERVICE_NAME = "io.RpcIO";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<com.ele.io.ClientRequest,
      com.ele.io.ServerReply> METHOD_COMMAND =
      io.grpc.MethodDescriptor.<com.ele.io.ClientRequest, com.ele.io.ServerReply>newBuilder()
          .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
          .setFullMethodName(generateFullMethodName(
              "io.RpcIO", "Command"))
          .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              com.ele.io.ClientRequest.getDefaultInstance()))
          .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              com.ele.io.ServerReply.getDefaultInstance()))
          .build();
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<com.ele.io.ClientRequest,
      com.ele.io.ResultUnit> METHOD_QUERY =
      io.grpc.MethodDescriptor.<com.ele.io.ClientRequest, com.ele.io.ResultUnit>newBuilder()
          .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
          .setFullMethodName(generateFullMethodName(
              "io.RpcIO", "Query"))
          .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              com.ele.io.ClientRequest.getDefaultInstance()))
          .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              com.ele.io.ResultUnit.getDefaultInstance()))
          .build();

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static RpcIOStub newStub(io.grpc.Channel channel) {
    return new RpcIOStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static RpcIOBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new RpcIOBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static RpcIOFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new RpcIOFutureStub(channel);
  }

  /**
   */
  public static abstract class RpcIOImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Client向Server发送DELETE或SET请求
     * </pre>
     */
    public void command(com.ele.io.ClientRequest request,
        io.grpc.stub.StreamObserver<com.ele.io.ServerReply> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_COMMAND, responseObserver);
    }

    /**
     * <pre>
     * Client向Server发送GET请求
     * </pre>
     */
    public void query(com.ele.io.ClientRequest request,
        io.grpc.stub.StreamObserver<com.ele.io.ResultUnit> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_QUERY, responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            METHOD_COMMAND,
            asyncUnaryCall(
              new MethodHandlers<
                com.ele.io.ClientRequest,
                com.ele.io.ServerReply>(
                  this, METHODID_COMMAND)))
          .addMethod(
            METHOD_QUERY,
            asyncServerStreamingCall(
              new MethodHandlers<
                com.ele.io.ClientRequest,
                com.ele.io.ResultUnit>(
                  this, METHODID_QUERY)))
          .build();
    }
  }

  /**
   */
  public static final class RpcIOStub extends io.grpc.stub.AbstractStub<RpcIOStub> {
    private RpcIOStub(io.grpc.Channel channel) {
      super(channel);
    }

    private RpcIOStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RpcIOStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new RpcIOStub(channel, callOptions);
    }

    /**
     * <pre>
     * Client向Server发送DELETE或SET请求
     * </pre>
     */
    public void command(com.ele.io.ClientRequest request,
        io.grpc.stub.StreamObserver<com.ele.io.ServerReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_COMMAND, getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Client向Server发送GET请求
     * </pre>
     */
    public void query(com.ele.io.ClientRequest request,
        io.grpc.stub.StreamObserver<com.ele.io.ResultUnit> responseObserver) {
      asyncServerStreamingCall(
          getChannel().newCall(METHOD_QUERY, getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class RpcIOBlockingStub extends io.grpc.stub.AbstractStub<RpcIOBlockingStub> {
    private RpcIOBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private RpcIOBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RpcIOBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new RpcIOBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Client向Server发送DELETE或SET请求
     * </pre>
     */
    public com.ele.io.ServerReply command(com.ele.io.ClientRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_COMMAND, getCallOptions(), request);
    }

    /**
     * <pre>
     * Client向Server发送GET请求
     * </pre>
     */
    public java.util.Iterator<com.ele.io.ResultUnit> query(
        com.ele.io.ClientRequest request) {
      return blockingServerStreamingCall(
          getChannel(), METHOD_QUERY, getCallOptions(), request);
    }
  }

  /**
   */
  public static final class RpcIOFutureStub extends io.grpc.stub.AbstractStub<RpcIOFutureStub> {
    private RpcIOFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private RpcIOFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected RpcIOFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new RpcIOFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Client向Server发送DELETE或SET请求
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.ele.io.ServerReply> command(
        com.ele.io.ClientRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_COMMAND, getCallOptions()), request);
    }
  }

  private static final int METHODID_COMMAND = 0;
  private static final int METHODID_QUERY = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final RpcIOImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(RpcIOImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_COMMAND:
          serviceImpl.command((com.ele.io.ClientRequest) request,
              (io.grpc.stub.StreamObserver<com.ele.io.ServerReply>) responseObserver);
          break;
        case METHODID_QUERY:
          serviceImpl.query((com.ele.io.ClientRequest) request,
              (io.grpc.stub.StreamObserver<com.ele.io.ResultUnit>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static final class RpcIODescriptorSupplier implements io.grpc.protobuf.ProtoFileDescriptorSupplier {
    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.ele.io.ClientProto.getDescriptor();
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (RpcIOGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new RpcIODescriptorSupplier())
              .addMethod(METHOD_COMMAND)
              .addMethod(METHOD_QUERY)
              .build();
        }
      }
    }
    return result;
  }
}
