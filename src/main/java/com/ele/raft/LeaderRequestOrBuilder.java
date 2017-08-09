// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: raft.proto

package com.ele.raft;

public interface LeaderRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:raft.LeaderRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>int32 term = 1;</code>
   */
  int getTerm();

  /**
   * <code>int32 leaderId = 2;</code>
   */
  int getLeaderId();

  /**
   * <code>int32 prevLogIndex = 3;</code>
   */
  int getPrevLogIndex();

  /**
   * <code>int32 prevLogTerm = 4;</code>
   */
  int getPrevLogTerm();

  /**
   * <pre>
   *true 进行日志复制;false 相当于heartbeat包
   * </pre>
   *
   * <code>bool getEntries = 5;</code>
   */
  boolean getGetEntries();

  /**
   * <code>int32 leaderCommit = 6;</code>
   */
  int getLeaderCommit();
}
