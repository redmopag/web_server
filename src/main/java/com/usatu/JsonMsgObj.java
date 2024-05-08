package com.usatu;

// Объект для маппинга JSON-данных
public class JsonMsgObj {
    private String to;
    private String from;
    private String text;

    public JsonMsgObj() {
        this.to = "";
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getTo() {
        return to;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFrom() {
        return from;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

}
