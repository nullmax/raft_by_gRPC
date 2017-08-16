// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: raft.proto

package com.ele.raft;

public final class RaftProto {
  private RaftProto() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_raft_VoteRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_raft_VoteRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_raft_VoteReply_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_raft_VoteReply_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_raft_LeaderRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_raft_LeaderRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_raft_FollowerReply_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_raft_FollowerReply_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_raft_Entry_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_raft_Entry_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\nraft.proto\022\004raft\"Z\n\013VoteRequest\022\014\n\004ter" +
      "m\030\001 \001(\005\022\023\n\013candidateId\030\002 \001(\005\022\024\n\014lastLogI" +
      "ndex\030\003 \001(\005\022\022\n\nlatLogTerm\030\004 \001(\005\".\n\tVoteRe" +
      "ply\022\014\n\004term\030\001 \001(\005\022\023\n\013voteGranted\030\002 \001(\010\"\216" +
      "\001\n\rLeaderRequest\022\014\n\004term\030\001 \001(\005\022\020\n\010leader" +
      "Id\030\002 \001(\005\022\024\n\014prevLogIndex\030\003 \001(\005\022\023\n\013prevLo" +
      "gTerm\030\004 \001(\005\022\034\n\007entries\030\005 \003(\0132\013.raft.Entr" +
      "y\022\024\n\014leaderCommit\030\006 \001(\005\"\213\001\n\rFollowerRepl" +
      "y\022\014\n\004term\030\001 \001(\005\022\017\n\007success\030\002 \001(\010\022\022\n\nmatc" +
      "hIndex\030\003 \001(\005\022\020\n\010stepDown\030\004 \001(\010\022\030\n\020sugges",
      "tNextIndex\030\005 \001(\005\022\033\n\023responseTocommandId\030" +
      "\006 \001(\005\"H\n\005Entry\022\014\n\004term\030\001 \001(\005\022\r\n\005index\030\002 " +
      "\001(\005\022\021\n\tcommandId\030\003 \001(\005\022\017\n\007command\030\004 \001(\t2" +
      "}\n\tConsensus\0223\n\013RequestVote\022\021.raft.VoteR" +
      "equest\032\017.raft.VoteReply\"\000\022;\n\rAppendEntri" +
      "es\022\023.raft.LeaderRequest\032\023.raft.FollowerR" +
      "eply\"\000B\"\n\014com.ele.raftB\tRaftProtoP\001\242\002\004RA" +
      "FTb\006proto3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_raft_VoteRequest_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_raft_VoteRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_raft_VoteRequest_descriptor,
        new java.lang.String[] { "Term", "CandidateId", "LastLogIndex", "LatLogTerm", });
    internal_static_raft_VoteReply_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_raft_VoteReply_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_raft_VoteReply_descriptor,
        new java.lang.String[] { "Term", "VoteGranted", });
    internal_static_raft_LeaderRequest_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_raft_LeaderRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_raft_LeaderRequest_descriptor,
        new java.lang.String[] { "Term", "LeaderId", "PrevLogIndex", "PrevLogTerm", "Entries", "LeaderCommit", });
    internal_static_raft_FollowerReply_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_raft_FollowerReply_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_raft_FollowerReply_descriptor,
        new java.lang.String[] { "Term", "Success", "MatchIndex", "StepDown", "SuggestNextIndex", "ResponseTocommandId", });
    internal_static_raft_Entry_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_raft_Entry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_raft_Entry_descriptor,
        new java.lang.String[] { "Term", "Index", "CommandId", "Command", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
