package com.checkai.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowRequest {
    private String bot_id;
    private String user_id;
    private boolean stream;
    private boolean auto_save_history;
    private List<AdditionalMessage> additional_messages;

    @Data
    public static class AdditionalMessage {
        private String role;
        private String content;
        private String content_type;
    }
}
