package org.apache.solr.config;

public class RequestDispatcherConfiguration {
  long uploadLimitKB = Long.MAX_VALUE;
  boolean enableRemoteStreams = true;
  boolean handleSelect = true;
  
  public long getUploadLimitKB() {
    return uploadLimitKB;
  }
  public void setUploadLimitKB(long uploadLimitKB) {
    this.uploadLimitKB = uploadLimitKB;
  }
  public boolean isEnableRemoteStreams() {
    return enableRemoteStreams;
  }
  public void setEnableRemoteStreams(boolean enableRemoteStreams) {
    this.enableRemoteStreams = enableRemoteStreams;
  }
  public boolean isHandleSelect() {
    return handleSelect;
  }
  public void setHandleSelect(boolean handleSelect) {
    this.handleSelect = handleSelect;
  }
}
