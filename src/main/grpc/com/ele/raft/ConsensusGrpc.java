package com.ele.raft;

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
 * <pre>
 * The raft-consensus service definition.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.4.0)",
    comments = "Source: raft.proto")
public final class ConsensusGrpc {

  private ConsensusGrpc() {}

  public static final String SERVICE_NAME = "raft.Consensus";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<com.ele.raft.VoteRequest,
      com.ele.raft.VoteReply> METHOD_REQUEST_VOTE =
      io.grpc.MethodDescriptor.<com.ele.raft.VoteRequest, com.ele.raft.VoteReply>newBuilder()
          .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
          .setFullMethodName(generateFullMethodName(
              "raft.Consensus", "RequestVote"))
          .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              com.ele.raft.VoteRequest.getDefaultInstance()))
          .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              com.ele.raft.VoteReply.getDefaultInstance()))
          .build();
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<com.ele.raft.LeaderRequest,
      com.ele.raft.FollowerReply> METHOD_APPEND_ENTRIES =
      io.grpc.MethodDescriptor.<com.ele.raft.LeaderRequest, com.ele.raft.FollowerReply>newBuilder()
          .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
          .setFullMethodName(generateFullMethodName(
              "raft.Consensus", "AppendEntries"))
          .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              com.ele.raft.LeaderRequest.getDefaultInstance()))
          .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
              com.ele.raft.FollowerReply.getDefaultInstance()))
          .build();

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ConsensusStub newStub(io.grpc.Channel channel) {
    return new ConsensusStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ConsensusBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new ConsensusBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ConsensusFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new ConsensusFutureStub(channel);
  }

  /**
   * <pre>
   * The raft-consensus service definition.
   * </pre>
   */
  public static abstract class ConsensusImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * 候选者发起投票
     * </pre>
     */
    public void requestVote(com.ele.raft.VoteRequest request,
        io.grpc.stub.StreamObserver<com.ele.raft.VoteReply> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_REQUEST_VOTE, responseObserver);
    }

    /**
     * <pre>
     * Leader发起同步
     * </pre>
     */
    public void appendEntries(com.ele.raft.LeaderRequest request,
        io.grpc.stub.StreamObserver<com.ele.raft.FollowerReply> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_APPEND_ENTRIES, responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            METHOD_REQUEST_VOTE,
            asyncUnaryCall(
              new MethodHandlers<
                com.ele.raft.VoteRequest,
                com.ele.raft.VoteReply>(
                  this, METHODID_REQUEST_VOTE)))
          .addMethod(
            METHOD_APPEND_ENTRIES,
            asyncUnaryCall(
              new MethodHandlers<
                com.ele.raft.LeaderRequest,
                com.ele.raft.FollowerReply>(
                  this, METHODID_APPEND_ENTRIES)))
          .build();
    }
  }

  /**
   * <pre>
   * The raft-consensus service definition.
   * </pre>
   */
  public static final class ConsensusStub extends io.grpc.stub.AbstractStub<ConsensusStub> {
    private ConsensusStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ConsensusStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ConsensusStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ConsensusStub(channel, callOptions);
    }

    /**
     * <pre>
     * 候选者发起投票
     * </pre>
     */
    public void requestVote(com.ele.raft.VoteRequest request,
        io.grpc.stub.StreamObserver<com.ele.raft.VoteReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_REQUEST_VOTE, getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Leader发起同步
     * </pre>
     */
    public void appendEntries(com.ele.raft.LeaderRequest request,
        io.grpc.stub.StreamObserver<com.ele.raft.FollowerReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_APPEND_ENTRIES, getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * The raft-consensus service definition.
   * </pre>
   */
  public static final class ConsensusBlockingStub extends io.grpc.stub.AbstractStub<ConsensusBlockingStub> {
    private ConsensusBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ConsensusBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ConsensusBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ConsensusBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 候选者发起投票
     * </pre>
     */
    public com.ele.raft.VoteReply requestVote(com.ele.raft.VoteRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_REQUEST_VOTE, getCallOptions(), request);
    }

    /**
     * <pre>
     * Leader发起同步
     * </pre>
     */
    public com.ele.raft.FollowerReply appendEntries(com.ele.raft.LeaderRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_APPEND_ENTRIES, getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * The raft-consensus service definition.
   * </pre>
   */
  public static final class ConsensusFutureStub extends io.grpc.stub.AbstractStub<ConsensusFutureStub> {
    private ConsensusFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ConsensusFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ConsensusFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ConsensusFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 候选者发起投票
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.ele.raft.VoteReply> requestVote(
        com.ele.raft.VoteRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_REQUEST_VOTE, getCallOptions()), request);
    }

    /**
     * <pre>
     * Leader发起同步
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.ele.raft.FollowerReply> appendEntries(
        com.ele.raft.LeaderRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_APPEND_ENTRIES, getCallOptions()), request);
    }
  }

  private static final int METHODID_REQUEST_VOTE = 0;
  private static final int METHODID_APPEND_ENTRIES = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final ConsensusImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(ConsensusImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REQUEST_VOTE:
          serviceImpl.requestVote((com.ele.raft.VoteRequest) request,
              (io.grpc.stub.StreamObserver<com.ele.raft.VoteReply>) responseObserver);
          break;
        case METHODID_APPEND_ENTRIES:
          serviceImpl.appendEntries((com.ele.raft.LeaderRequest) request,
              (io.grpc.stub.StreamObserver<com.ele.raft.FollowerReply>) responseObserver);
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

  private static final class ConsensusDescriptorSupplier implements io.grpc.protobuf.ProtoFileDescriptorSupplier {
    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.ele.raft.RaftProto.getDescriptor();
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ConsensusGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ConsensusDescriptorSupplier())
              .addMethod(METHOD_REQUEST_VOTE)
              .addMethod(METHOD_APPEND_ENTRIES)
              .build();
        }
      }
    }
    return result;
  }
}
