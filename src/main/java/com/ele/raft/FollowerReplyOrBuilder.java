// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: raft.proto

package com.ele.raft;

public interface FollowerReplyOrBuilder extends
    // @@protoc_insertion_point(interface_extends:raft.FollowerReply)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>int32 term = 1;</code>
   */
  int getTerm();

  /**
   * <code>bool success = 2;</code>
   */
  boolean getSuccess();

  /**
   * <code>int32 matchIndex = 3;</code>
   */
  int getMatchIndex();

  /**
   * <code>bool stepDown = 4;</code>
   */
  boolean getStepDown();

  /**
   * <code>int32 suggestNextIndex = 5;</code>
   */
  int getSuggestNextIndex();
}
